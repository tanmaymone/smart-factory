import type { ProbeSubject } from "@/lib/onboardingProbe";
import type { LineResponse, SiteResponse, TagMapping, TagMappingRequest } from "@/lib/types";
import type { WizardState } from "@/lib/onboardingWizardState";

export function activateBlockReason(state: Pick<WizardState, "step3Dirty" | "savedTagCodes">): string | null {
  if (state.step3Dirty) return "Save tags before activation.";
  if (state.savedTagCodes.length === 0) return "Save at least one tag before activation.";
  return null;
}

export function savedTagsReady(
  saved: string[],
  fresh: TagMapping[],
): { ok: true } | { ok: false; reason: string } {
  const byCode = new Map(fresh.map((tag) => [tag.tagCode.trim(), tag]));
  for (const code of saved) {
    const row = byCode.get(code);
    if (!row) return { ok: false, reason: `${code} is no longer in the registry.` };
    if (row.protocol !== "OPC_UA") return { ok: false, reason: `${code} is not OPC UA.` };
    if (!row.externalAddress.trim() || !row.tagCode.trim()) {
      return { ok: false, reason: `${code} is missing an address.` };
    }
  }
  return { ok: true };
}

export function subjectDrift(
  code: string,
  row: TagMapping,
  subject: ProbeSubject | undefined,
  securityMode: WizardState["securityMode"],
): boolean {
  if (!subject) return true;
  return (
    subject.protocol !== row.protocol ||
    subject.externalAddress !== row.externalAddress.trim() ||
    subject.endpointUrl !== row.endpointUrl.trim() ||
    subject.securityMode !== securityMode
  );
}

export function buildSiteUpdate(
  fresh: SiteResponse,
  dirty: { name: boolean; timezone: boolean },
  edited: { name: string; timezone: string },
): { name: string; timezone: string; lifecycle: "ACTIVE" } {
  return {
    name: dirty.name ? edited.name : fresh.name,
    timezone: dirty.timezone ? edited.timezone : fresh.timezone,
    lifecycle: "ACTIVE",
  };
}

export function buildLineUpdate(
  fresh: LineResponse,
  nameDirty: boolean,
  editedName: string,
): { name: string; lifecycle: "ACTIVE" } {
  return { name: nameDirty ? editedName : fresh.name, lifecycle: "ACTIVE" };
}

export function shouldSkipParentPut(
  lifecycle: string | undefined,
  dirty: boolean,
): boolean {
  return lifecycle === "ACTIVE" && !dirty;
}

export function tagActivationBody(row: TagMapping): TagMappingRequest {
  return {
    siteId: row.siteId,
    lineId: row.lineId,
    tagCode: row.tagCode,
    description: row.description,
    protocol: row.protocol,
    endpointUrl: row.endpointUrl,
    externalAddress: row.externalAddress,
    dataType: row.dataType,
    unit: row.unit,
    writable: row.writable,
    minValue: row.minValue,
    maxValue: row.maxValue,
    deadband: row.deadband,
    warningLow: row.warningLow,
    warningHigh: row.warningHigh,
    criticalLow: row.criticalLow,
    criticalHigh: row.criticalHigh,
    interlockRuleKey: row.interlockRuleKey,
    lifecycle: "ACTIVE",
  };
}

export function seedActivated(saved: string[], fresh: TagMapping[]): string[] {
  const live = new Set(
    fresh.filter((tag) => tag.lifecycle === "ACTIVE").map((tag) => tag.tagCode.trim()),
  );
  return saved.filter((code) => live.has(code));
}

export function needsCatalogRefresh(input: {
  parentPutRan: boolean;
  catalogSiteActive: boolean;
  catalogLineActive: boolean;
  catalogError: boolean;
}): boolean {
  return (
    input.parentPutRan ||
    !input.catalogSiteActive ||
    !input.catalogLineActive ||
    input.catalogError
  );
}
