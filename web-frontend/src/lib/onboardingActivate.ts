import {
  activateBlockReason,
  buildLineUpdate,
  buildSiteUpdate,
  needsCatalogRefresh,
  savedTagsReady,
  seedActivated,
  shouldSkipParentPut,
  subjectDrift,
  tagActivationBody,
} from "@/lib/onboardingActivation";
import { isStep2Pass, postOnboardingProbe } from "@/lib/onboardingApi";
import { fanOutDiagnostics, foldProbeChunks, subjectsFromRows, type ProbeRow } from "@/lib/onboardingProbe";
import { resolveEntityLabels } from "@/lib/entityLabels";
import { getLine, getSite, listRegistryTags, updateLine, updateSite, updateRegistryTag } from "@/lib/registryApi";
import type { TagMapping } from "@/lib/types";
import type { WizardState } from "@/lib/onboardingWizardState";

export type ActivateHooks = {
  state: WizardState;
  runGeneration: number;
  currentGeneration: () => number;
  catalogSiteActive: boolean;
  catalogLineActive: boolean;
  catalogError: boolean;
  allowBad: boolean;
  refreshCatalog: () => Promise<void>;
  onActivated: (codes: string[]) => void;
};

export async function runActivation(hooks: ActivateHooks): Promise<{ ok: boolean; message: string; step?: 1 | 3 }> {
  const blocked = activateBlockReason(hooks.state);
  if (blocked) return { ok: false, message: blocked };
  if (hooks.runGeneration !== hooks.currentGeneration()) return { ok: false, message: "Activation cancelled." };

  const labels = resolveEntityLabels({
    siteLabel: hooks.state.siteLabel,
    lineLabel: hooks.state.lineLabel,
  });
  const siteGet = await getSite(hooks.state.siteId);
  if (hooks.runGeneration !== hooks.currentGeneration()) return { ok: false, message: "Activation cancelled." };
  if (siteGet.status === 404 || !siteGet.site) {
    return { ok: false, message: `${labels.siteLabel} was not found.`, step: 1 };
  }
  if (siteGet.status >= 400) return { ok: false, message: siteGet.error?.message ?? "Site lookup failed." };

  const lineGet = await getLine(hooks.state.siteId, hooks.state.lineId);
  if (hooks.runGeneration !== hooks.currentGeneration()) return { ok: false, message: "Activation cancelled." };
  if (lineGet.status === 404 || !lineGet.line) {
    return { ok: false, message: `${labels.lineLabel} was not found.`, step: 1 };
  }

  const tags = await listRegistryTags({ siteId: hooks.state.siteId, lineId: hooks.state.lineId });
  if (hooks.runGeneration !== hooks.currentGeneration()) return { ok: false, message: "Activation cancelled." };
  const ready = savedTagsReady(hooks.state.savedTagCodes, tags);
  if (!ready.ok) return { ok: false, message: ready.reason, step: 3 };

  const drifted = endpointDrift(hooks.state, tags);
  if (drifted) return { ok: false, message: drifted, step: 3 };

  if (!hooks.allowBad && subjectsStale(hooks.state, tags)) {
    const probed = await probeFresh(hooks.state, tags);
    if (!probed.ok) return probed;
  } else if (subjectsStale(hooks.state, tags)) {
    return { ok: false, message: "Probe results no longer match the registry.", step: 3 };
  }

  const freshSite = await getSite(hooks.state.siteId);
  const freshLine = await getLine(hooks.state.siteId, hooks.state.lineId);
  const freshTags = await listRegistryTags({ siteId: hooks.state.siteId, lineId: hooks.state.lineId });
  if (hooks.runGeneration !== hooks.currentGeneration()) return { ok: false, message: "Activation cancelled." };
  if (!freshSite.site || !freshLine.line) return { ok: false, message: "Parent record disappeared.", step: 1 };
  const again = savedTagsReady(hooks.state.savedTagCodes, freshTags);
  if (!again.ok) return { ok: false, message: again.reason, step: 3 };

  let parentPutRan = false;
  const siteDirty =
    hooks.state.siteNameDirty
    || hooks.state.siteTimezoneDirty
    || hooks.state.siteLabelDirty
    || hooks.state.lineLabelDirty;
  if (!shouldSkipParentPut(freshSite.site.lifecycle, siteDirty)) {
    const updated = await updateSite(hooks.state.siteId, buildSiteUpdate(freshSite.site, {
      name: hooks.state.siteNameDirty,
      timezone: hooks.state.siteTimezoneDirty,
      siteLabel: hooks.state.siteLabelDirty,
      lineLabel: hooks.state.lineLabelDirty,
    }, {
      name: hooks.state.siteName,
      timezone: hooks.state.siteTimezone,
      siteLabel: hooks.state.siteLabel.trim(),
      lineLabel: hooks.state.lineLabel.trim(),
    }));
    if (hooks.runGeneration !== hooks.currentGeneration()) return { ok: false, message: "Activation cancelled." };
    if (!updated.ok) return { ok: false, message: updated.error.message };
    parentPutRan = true;
  }
  if (!shouldSkipParentPut(freshLine.line.lifecycle, hooks.state.lineNameDirty)) {
    const updated = await updateLine(hooks.state.siteId, hooks.state.lineId, buildLineUpdate(
      freshLine.line,
      hooks.state.lineNameDirty,
      hooks.state.lineName,
    ));
    if (hooks.runGeneration !== hooks.currentGeneration()) return { ok: false, message: "Activation cancelled." };
    if (!updated.ok) return { ok: false, message: updated.error.message };
    parentPutRan = true;
  }

  if (needsCatalogRefresh({
    parentPutRan,
    catalogSiteActive: hooks.catalogSiteActive,
    catalogLineActive: hooks.catalogLineActive,
    catalogError: hooks.catalogError,
  })) {
    try {
      await hooks.refreshCatalog();
    } catch {
      /* catalogError is set by the store; tag activation continues */
    }
  }
  if (hooks.runGeneration !== hooks.currentGeneration()) return { ok: false, message: "Activation cancelled." };

  const activated = seedActivated(hooks.state.savedTagCodes, freshTags);
  for (const code of hooks.state.savedTagCodes) {
    const row = freshTags.find((tag) => tag.tagCode.trim() === code);
    if (!row || row.lifecycle === "ACTIVE") continue;
    const result = await updateRegistryTag(row.id, tagActivationBody(row));
    if (hooks.runGeneration !== hooks.currentGeneration()) return { ok: false, message: "Activation cancelled." };
    if (!result.ok) {
      hooks.onActivated(activated);
      return { ok: false, message: result.error.message };
    }
    activated.push(code);
  }
  hooks.onActivated(activated);
  return { ok: true, message: "Tags activated." };
}

function endpointDrift(state: WizardState, tags: TagMapping[]): string | null {
  for (const code of state.savedTagCodes) {
    const row = tags.find((tag) => tag.tagCode.trim() === code);
    if (row && row.endpointUrl.trim() !== state.endpointUrl.trim()) {
      return `${code} endpoint does not match Step 2. Save tags again.`;
    }
  }
  return null;
}

function subjectsStale(state: WizardState, tags: TagMapping[]): boolean {
  return state.savedTagCodes.some((code) => {
    const row = tags.find((tag) => tag.tagCode.trim() === code);
    return !row || subjectDrift(code, row, state.probeSubject[code], state.securityMode);
  });
}

async function probeFresh(state: WizardState, tags: TagMapping[]): Promise<{ ok: false; message: string; step: 3 } | { ok: true }> {
  const rows: ProbeRow[] = state.savedTagCodes.map((code) => {
    const row = tags.find((tag) => tag.tagCode.trim() === code)!;
    return { tagCode: code, protocol: row.protocol, externalAddress: row.externalAddress, endpointUrl: row.endpointUrl };
  });
  const response = await postOnboardingProbe({
    endpointUrl: state.endpointUrl.trim(),
    securityMode: state.securityMode,
    nodeIds: rows.map((row) => row.externalAddress.trim()).filter(Boolean),
  });
  if (!response.ok) return { ok: false, message: response.error.message, step: 3 };
  const folded = foldProbeChunks([{ ok: true, body: response.body }]);
  if (!folded.commit || !isStep2Pass(response.body)) {
    return { ok: false, message: response.body.error ?? "Probe failed.", step: 3 };
  }
  const mapped = fanOutDiagnostics(rows, response.body.tags ?? []);
  if (rows.some((row) => mapped[row.tagCode]?.quality !== "GOOD")) {
    return { ok: false, message: "One or more tags probed BAD or UNREACHABLE.", step: 3 };
  }
  return { ok: true };
}
