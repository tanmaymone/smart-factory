"use client";

import { useState, type ReactNode } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { EntityLabelPicker } from "@/components/onboarding/EntityLabelPicker";
import {
  LINE_LABEL_PRESETS,
  resolveEntityLabels,
  SITE_LABEL_PRESETS,
} from "@/lib/entityLabels";
import { OnboardingStep3Templates } from "@/components/onboarding/OnboardingStep3Templates";
import { parseTagImport } from "@/lib/onboardingTagImport";
import type { SiteInfo } from "@/lib/sites";
import type { SecurityMode } from "@/lib/types";
import {
  bumpFieldEdit,
  bumpStep3Edit,
  invalidateFromStep1,
  invalidateFromStep2,
  type WizardState,
} from "@/lib/onboardingWizardState";

const STEP_INTRO: Record<Exclude<WizardState["step"], 1>, string> = {
  2: "Prove the plant OPC UA endpoint is reachable from the edge probe path.",
  3: "Import tag mappings, then save them to the registry as DRAFT.",
  4: "Activate parents and tags, then confirm live telemetry before opening the Live console.",
};

function OnboardingField({
  id,
  label,
  hint,
  children,
}: {
  id: string;
  label: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {hint ? <p className="text-xs text-slate-500">{hint}</p> : null}
      {children}
    </div>
  );
}

function catalogParents(sites: SiteInfo[], siteId: string, lineId: string) {
  const site = sites.find((entry) => entry.id === siteId);
  const line = site?.lines.find((entry) => entry.id === lineId);
  return { site, line };
}

export function OnboardingStepBody({
  state,
  locked,
  sampleCount,
  sites,
  setState,
  setNotice,
  onCreate,
  onSaveSiteLabels,
  onProbe,
  onSave,
  onActivate,
}: {
  state: WizardState;
  locked: boolean;
  sampleCount: number;
  sites: SiteInfo[];
  setState: (update: (current: WizardState) => WizardState) => void;
  setNotice: (message: string | null) => void;
  onCreate: (which: "site" | "line") => void;
  onSaveSiteLabels: () => void;
  onProbe: () => void;
  onSave: () => void;
  onActivate: (allowBad: boolean) => void;
}) {
  const { site: catalogSite, line: catalogLine } = catalogParents(sites, state.siteId, state.lineId);
  const entityLabels = resolveEntityLabels({
    siteLabel: state.siteLabel,
    lineLabel: state.lineLabel,
  });
  const siteWord = entityLabels.siteLabel;
  const lineWord = entityLabels.lineLabel;
  const siteLower = siteWord.toLowerCase();
  const lineLower = lineWord.toLowerCase();
  const [importText, setImportText] = useState("");

  if (state.step === 1) {
  const intro = (
    <p className="text-sm text-slate-400">
      Register or confirm the {siteLower} and {lineLower} in the central catalog before connecting OPC UA.
    </p>
  );
    const labelsDirty = state.siteLabelDirty || state.lineLabelDirty;
    return (
      <div className="flex flex-col gap-4">
        {intro}
        <div className="grid gap-3 md:grid-cols-2">
          <EntityLabelPicker
            id="onb-site-label"
            title={`${siteWord} word`}
            presets={SITE_LABEL_PRESETS}
            value={state.siteLabel}
            disabled={locked}
            onChange={(next) => setState((current) => bumpFieldEdit(current, "siteLabel", next))}
          />
          <EntityLabelPicker
            id="onb-line-label"
            title={`${lineWord} word`}
            presets={LINE_LABEL_PRESETS}
            value={state.lineLabel}
            disabled={locked}
            onChange={(next) => setState((current) => bumpFieldEdit(current, "lineLabel", next))}
          />
        </div>
        {catalogSite && labelsDirty ? (
          <Button variant="secondary" className="self-start" disabled={locked} onClick={onSaveSiteLabels}>
            Save names
          </Button>
        ) : null}
        {catalogSite && catalogLine ? (
          <p className="rounded-md border border-slate-800 bg-slate-950/60 px-3 py-2 text-xs text-slate-400">
            Selected in catalog:{" "}
            <span className="font-mono text-slate-200">{state.siteId}</span>
            {" · "}
            {catalogSite.name}
            {catalogSite.lifecycle ? ` (${catalogSite.lifecycle})` : ""}
            {" — "}
            <span className="font-mono text-slate-200">{state.lineId}</span>
            {" · "}
            {catalogLine.name}
            {catalogLine.lifecycle ? ` (${catalogLine.lifecycle})` : ""}
            . Skip create if both already exist.
          </p>
        ) : null}
        <div className="grid gap-4 md:grid-cols-2">
          <section className="flex flex-col gap-3 rounded-md border border-slate-800 p-3">
            <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-300">{siteWord}</h3>
            <OnboardingField
              id="onb-site-code"
              label={`${siteWord} code`}
              hint="Case-sensitive; must match plant edge EDGE_SITE_ID."
            >
              <Input
                id="onb-site-code"
                className="mono font-mono"
                value={state.siteId}
                disabled={locked}
                placeholder="INDORE"
                onChange={(event) =>
                  setState((current) => invalidateFromStep1(current, event.target.value.trim(), current.lineId))
                }
              />
            </OnboardingField>
            <OnboardingField
              id="onb-site-name"
              label={`${siteWord} name`}
              hint={`Display name shown in the ${siteLower} switcher.`}
            >
              <Input
                id="onb-site-name"
                value={state.siteName}
                disabled={locked}
                placeholder="Indore Plant"
                onChange={(event) => setState((current) => bumpFieldEdit(current, "siteName", event.target.value))}
              />
            </OnboardingField>
            <OnboardingField id="onb-site-timezone" label="Timezone" hint="IANA timezone for audit timestamps.">
              <Input
                id="onb-site-timezone"
                value={state.siteTimezone}
                disabled={locked}
                placeholder="Asia/Kolkata"
                onChange={(event) => setState((current) => bumpFieldEdit(current, "siteTimezone", event.target.value))}
              />
            </OnboardingField>
            <Button disabled={locked || Boolean(catalogSite)} onClick={() => onCreate("site")}>
              {`Create ${siteLower}`}
            </Button>
            {catalogSite ? (
              <p className="text-xs text-slate-500">{`This ${siteLower} code is already in the catalog.`}</p>
            ) : null}
          </section>
          <section className="flex flex-col gap-3 rounded-md border border-slate-800 p-3">
            <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-300">{lineWord}</h3>
            <OnboardingField
              id="onb-line-code"
              label={`${lineWord} code`}
              hint={`Unique ${lineLower} id under this ${siteLower}.`}
            >
              <Input
                id="onb-line-code"
                className="mono font-mono"
                value={state.lineId}
                disabled={locked}
                placeholder="LINE-01"
                onChange={(event) =>
                  setState((current) => invalidateFromStep1(current, current.siteId, event.target.value.trim()))
                }
              />
            </OnboardingField>
            <OnboardingField
              id="onb-line-name"
              label={`${lineWord} name`}
              hint={`Display name shown in the ${lineLower} switcher.`}
            >
              <Input
                id="onb-line-name"
                value={state.lineName}
                disabled={locked}
                placeholder="Reactor Line 1"
                onChange={(event) => setState((current) => bumpFieldEdit(current, "lineName", event.target.value))}
              />
            </OnboardingField>
            <Button disabled={locked || !catalogSite || Boolean(catalogLine)} onClick={() => onCreate("line")}>
              {`Create ${lineLower}`}
            </Button>
            {catalogLine ? (
              <p className="text-xs text-slate-500">{`This ${lineLower} code already exists under the ${siteLower}.`}</p>
            ) : catalogSite ? null : (
              <p className="text-xs text-slate-500">{`Create the ${siteLower} first, or pick an existing ${siteLower} in the header.`}</p>
            )}
          </section>
        </div>
      </div>
    );
  }
  if (state.step === 2) {
    return (
      <div className="flex flex-col gap-3">
        <p className="text-sm text-slate-400">{STEP_INTRO[2]}</p>
        <OnboardingField
          id="onb-endpoint"
          label="Endpoint URL"
          hint="OPC UA discovery URL as seen from the plant edge (not the cloud UI)."
        >
          <Input
            id="onb-endpoint"
            className="mono font-mono text-xs"
            value={state.endpointUrl}
            disabled={locked}
            placeholder="opc.tcp://localhost:53530/OPCUA/SimulationServer"
            onChange={(event) => setState((current) => invalidateFromStep2(current, { endpointUrl: event.target.value }))}
          />
        </OnboardingField>
        <div className="flex flex-col gap-1.5">
          <Label>Security mode</Label>
          <p className="text-xs text-slate-500">Must match what the OPC UA server offers (None for Prosys demo).</p>
          <Select
            value={state.securityMode}
            disabled={locked}
            onValueChange={(value) =>
              setState((current) => invalidateFromStep2(current, { securityMode: value as SecurityMode }))
            }
          >
            <SelectTrigger className="w-full sm:w-56">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="None">None</SelectItem>
              <SelectItem value="SignAndEncrypt">SignAndEncrypt</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <Button disabled={locked} onClick={onProbe}>
          Test connection
        </Button>
      </div>
    );
  }
  if (state.step === 3) {
    const applyImportText = (text: string) => {
      setImportText(text);
      const parsed = parseTagImport(text, {
        siteId: state.siteId,
        lineId: state.lineId,
        endpointUrl: state.endpointUrl,
      });
      if (parsed.error) setNotice(parsed.error);
      else {
        setNotice(null);
        setState((current) => ({ ...bumpStep3Edit(current), drafts: parsed.drafts }));
      }
    };
    const writableCount = state.drafts.filter((row) => row.writable).length;
    const types = [...new Set(state.drafts.map((row) => row.dataType))].join(", ") || "—";
    return (
      <div className="flex flex-col gap-3">
        <p className="text-sm text-slate-400">{STEP_INTRO[3]}</p>
        <OnboardingStep3Templates
          siteId={state.siteId}
          lineId={state.lineId}
          endpointUrl={state.endpointUrl}
          disabled={locked}
          draftCount={state.drafts.length}
          onLoadDrafts={(drafts) =>
            setState((current) => ({ ...bumpStep3Edit(current), drafts }))
          }
          onImportText={applyImportText}
        />
        <OnboardingField
          id="onb-tag-import"
          label="Tag import"
          hint="Paste JSON or CSV (19 batch columns). Templates round-trip losslessly into Save tags."
        >
          <Textarea
            id="onb-tag-import"
            className="mono min-h-40 font-mono text-xs"
            disabled={locked}
            value={importText}
            placeholder='[{"tagCode":"TEMP_101", ...}]'
            onChange={(event) => applyImportText(event.target.value)}
          />
        </OnboardingField>
        <p className="font-mono text-xs text-slate-400">
          Summary: {state.drafts.length} rows · types {types} · writable {writableCount}
        </p>
        <div className="flex gap-2">
          <Button disabled={locked} onClick={onSave}>
            Save tags
          </Button>
        </div>
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm text-slate-400">{STEP_INTRO[4]}</p>
      <p className="text-xs text-slate-500">
        Promotes site, line, and saved tags to ACTIVE when probes pass. Use Activate anyway only for deliberate cutover.
      </p>
      <Button disabled={locked} onClick={() => onActivate(false)}>
        Activate
      </Button>
      <Button disabled={locked} variant="secondary" onClick={() => onActivate(true)}>
        Activate anyway
      </Button>
      <p className="font-mono text-xs text-slate-400">Live samples: {sampleCount}</p>
      <Link className="text-sm text-cyan-400" href={`/?siteId=${encodeURIComponent(state.siteId)}&lineId=${encodeURIComponent(state.lineId)}`}>
        Open Live Console
      </Link>
    </div>
  );
}