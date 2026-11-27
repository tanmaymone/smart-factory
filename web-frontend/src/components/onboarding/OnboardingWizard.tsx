"use client";

import { useEffect, useRef, useState } from "react";
import { OnboardingStepBody } from "@/components/onboarding/OnboardingStepBody";
import { OnboardingStepper } from "@/components/onboarding/OnboardingStepper";
import { ProbeErrorAlert } from "@/components/onboarding/ProbeErrorAlert";
import { Button } from "@/components/ui/button";
import { runActivation } from "@/lib/onboardingActivate";
import { buildBatchRows } from "@/lib/onboardingBatch";
import { isStep2Pass, postOnboardingProbe, type ParsedProbeError } from "@/lib/onboardingApi";
import {
  applyCreateSuccess,
  applySaveSuccess,
  beginProbe,
  commitProbe,
  emptyWizard,
  invalidateFromStep1,
  invalidateFromStep2,
  restoreWizard,
  serializeWizard,
  storageKey,
  type WizardState,
} from "@/lib/onboardingWizardState";
import { createLine, createSite, getLine, getSite, importTagsBatch, listRegistryTags } from "@/lib/registryApi";
import { isValidRegistryNaturalKey } from "@/lib/registryNaturalKey";
import { parentsLookActive } from "@/lib/catalogSelection";
import type { SiteInfo } from "@/lib/sites";
import { useOnboardingSmokeStream } from "@/hooks/useOnboardingSmokeStream";

export function OnboardingWizard({
  siteId,
  lineId,
  sites,
  refreshCatalog,
  catalogError,
  replaceSelection,
}: {
  siteId: string;
  lineId: string;
  sites: SiteInfo[];
  refreshCatalog: () => Promise<void>;
  catalogError: boolean;
  replaceSelection: (siteId: string, lineId: string) => void;
}) {
  const [state, setState] = useState<WizardState>(() => emptyWizard(siteId, lineId));
  const [busy, setBusy] = useState<"create" | "save" | "probe" | "activate" | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [probeError, setProbeError] = useState<ParsedProbeError | null>(null);
  const [smokeOn, setSmokeOn] = useState(false);
  const stateRef = useRef(state);
  stateRef.current = state;

  useEffect(() => {
    const raw = sessionStorage.getItem(storageKey(siteId, lineId));
    const parsed = raw ? (JSON.parse(raw) as Partial<WizardState>) : null;
    setState(restoreWizard(parsed, emptyWizard(siteId, lineId)));
    return () => {
      setState((current) => ({
        ...current,
        wizardGeneration: current.wizardGeneration + 1,
        probeRunId: current.probeRunId + 1,
        step2Passed: false,
        probeDiagnostics: {},
        probeSubject: {},
        preActivationProbePassed: false,
      }));
    };
  }, [siteId, lineId]);

  useEffect(() => {
    sessionStorage.setItem(storageKey(state.siteId, state.lineId), JSON.stringify(serializeWizard(state)));
  }, [state]);

  const locked = busy != null;
  const smoke = useOnboardingSmokeStream(smokeOn, state.siteId, state.lineId, state.smokeGeneration);

  const createParent = async (which: "site" | "line") => {
    const captured = {
      generation: state.wizardGeneration,
      siteNameRevision: state.siteNameEditRevision,
      timezoneRevision: state.siteTimezoneEditRevision,
      lineRevision: state.lineNameEditRevision,
    };
    const code = which === "site" ? state.siteId : state.lineId;
    if (!isValidRegistryNaturalKey(code)) {
      setNotice("Use a site or line code that matches the edge site id pattern.");
      return;
    }
    setBusy("create");
    const existing = which === "site" ? await getSite(code) : await getLine(state.siteId, code);
    if (existing.status === 200) {
      setNotice(`${code} already exists. Select it instead of creating it.`);
      await refreshCatalog();
      setBusy(null);
      return;
    }
    if (existing.status !== 404) {
      setNotice("Could not confirm whether that code exists.");
      setBusy(null);
      return;
    }
    const created = which === "site"
      ? await createSite({ siteCode: state.siteId.trim(), name: state.siteName, timezone: state.siteTimezone, lifecycle: "DRAFT" })
      : await createLine({ siteCode: state.siteId.trim(), lineCode: state.lineId.trim(), name: state.lineName, lifecycle: "DRAFT" });
    setBusy(null);
    if (!created.ok) {
      setNotice(created.error.message);
      return;
    }
    if (!created.created) {
      setNotice(`${code} already exists. Select it instead of creating it.`);
      await refreshCatalog();
      return;
    }
    setState((current) => applyCreateSuccess(current, which, captured));
    await refreshCatalog();
    replaceSelection(state.siteId, state.lineId);
  };

  const probeConnection = async () => {
    if (busy) return;
    const snapshot = beginProbe(stateRef.current);
    const captured = { probeRunId: snapshot.probeRunId, generation: snapshot.wizardGeneration, step3EditRevision: snapshot.step3EditRevision };
    setState(snapshot);
    setBusy("probe");
    const result = await postOnboardingProbe({
      endpointUrl: snapshot.endpointUrl.trim(),
      securityMode: snapshot.securityMode,
      nodeIds: [],
    });
    setBusy(null);
    if (!result.ok) {
      setProbeError(result.error);
      return;
    }
    setProbeError(null);
    if (!isStep2Pass(result.body)) {
      setNotice(result.body.error ?? "The endpoint answered but the namespace table was not read.");
      return;
    }
    setState((current) => commitProbe(current, captured, {}, {}, true));
    setNotice(null);
  };

  const saveTags = async () => {
    if (busy === "probe" || busy === "save") return;
    const captured = { generation: state.wizardGeneration, step3EditRevision: state.step3EditRevision };
    setBusy("save");
    const existing = await listRegistryTags({ siteId: state.siteId, lineId: state.lineId });
    const byCode = Object.fromEntries(existing.map((tag) => [tag.tagCode.trim(), tag]));
    const built = buildBatchRows(state.drafts, {
      siteId: state.siteId,
      lineId: state.lineId,
      endpointUrl: state.endpointUrl,
      securityMode: state.securityMode,
    }, byCode, state.probeSubject);
    if (!built.rows) {
      setNotice(built.error);
      setBusy(null);
      return;
    }
    const saved = await importTagsBatch(built.rows);
    setBusy(null);
    if (!saved.ok) {
      setNotice(saved.error.message);
      return;
    }
    setState((current) => applySaveSuccess(current, built.rows!.map((row) => row.tagCode), captured));
  };

  const activate = async (allowBad: boolean) => {
    if (busy) return;
    const runGeneration = stateRef.current.wizardGeneration;
    setBusy("activate");
    const site = sites.find((entry) => entry.id === state.siteId);
    const line = site?.lines.find((entry) => entry.id === state.lineId);
    const result = await runActivation({
      state: stateRef.current,
      runGeneration,
      currentGeneration: () => stateRef.current.wizardGeneration,
      catalogSiteActive: site?.lifecycle === "ACTIVE",
      catalogLineActive: line?.lifecycle === "ACTIVE",
      catalogError,
      allowBad,
      refreshCatalog,
      onActivated: (codes) => setState((current) => ({ ...current, activatedTagCodes: codes })),
    });
    setBusy(null);
    setNotice(result.message);
    if (result.ok) setSmokeOn(true);
    if (result.step) setState((current) => ({ ...current, step: result.step! }));
  };

  return (
    <div className="flex flex-col gap-4 rounded-lg border border-slate-800 bg-slate-900 p-4">
      <OnboardingStepper step={state.step} disabled={locked} />
      {notice ? <p className="text-sm text-amber-300">{notice}</p> : null}
      <ProbeErrorAlert error={probeError} />
      <OnboardingStepBody
        state={state}
        locked={locked}
        sampleCount={Object.keys(smoke.tags).length}
        setState={setState}
        setNotice={setNotice}
        onCreate={(which) => void createParent(which)}
        onProbe={() => void probeConnection()}
        onSave={() => void saveTags()}
        onActivate={(allowBad) => void activate(allowBad)}
      />
      <div className="flex gap-2">
        <Button variant="secondary" disabled={locked || state.step === 1} onClick={() => setState((current) => ({ ...current, step: (current.step - 1) as 1 | 2 | 3 | 4 }))}>Back</Button>
        <Button disabled={locked || state.step === 4 || (state.step === 2 && !state.step2Passed) || (state.step === 3 && (state.step3Dirty || state.savedTagCodes.length === 0))} onClick={() => setState((current) => ({ ...current, step: (current.step + 1) as 1 | 2 | 3 | 4 }))}>Next</Button>
      </div>
      <p className="text-xs text-slate-500">{parentsLookActive(sites, state.siteId, state.lineId) ? "Catalog parents are ACTIVE." : "Catalog parents are not both ACTIVE yet."}</p>
    </div>
  );
}
