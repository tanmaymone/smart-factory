import type { ProbeSubject } from "@/lib/onboardingProbe";
import type { TagMapping, TagMappingRequest } from "@/lib/types";

export function draftIssues(drafts: TagMappingRequest[]): string | null {
  if (drafts.length === 0) return "Add at least one tag.";
  const seen = new Set<string>();
  for (const draft of drafts) {
    const code = draft.tagCode.trim();
    if (!code) return "Tag code is required.";
    if (seen.has(code)) return `Duplicate tag code ${code}.`;
    seen.add(code);
    if (draft.protocol !== "OPC_UA") return "Only OPC UA tags can be onboarded.";
    if (!draft.externalAddress.trim()) return "OPC UA address is required.";
  }
  return null;
}

export function activeRowNeedsProbe(
  existing: TagMapping | undefined,
  draft: TagMappingRequest,
): boolean {
  if (existing?.lifecycle !== "ACTIVE") return false;
  return (
    existing.protocol !== draft.protocol ||
    existing.externalAddress.trim() !== draft.externalAddress.trim() ||
    existing.endpointUrl.trim() !== draft.endpointUrl.trim()
  );
}

export function probeCoversDraft(
  subject: ProbeSubject | undefined,
  draft: TagMappingRequest,
  securityMode: ProbeSubject["securityMode"],
): boolean {
  if (!subject || subject.quality !== "GOOD") return false;
  return (
    subject.protocol === "OPC_UA" &&
    subject.externalAddress === draft.externalAddress.trim() &&
    subject.endpointUrl === draft.endpointUrl.trim() &&
    subject.securityMode === securityMode
  );
}

export function buildBatchRows(
  drafts: TagMappingRequest[],
  context: { siteId: string; lineId: string; endpointUrl: string; securityMode: ProbeSubject["securityMode"] },
  existingByTagCode: Record<string, TagMapping>,
  subjects: Record<string, ProbeSubject>,
): { rows: TagMappingRequest[] | null; error: string | null } {
  const issue = draftIssues(drafts);
  if (issue) return { rows: null, error: issue };
  const rows: TagMappingRequest[] = [];
  for (const draft of drafts) {
    const code = draft.tagCode.trim();
    const existing = existingByTagCode[code];
    const endpointUrl = context.endpointUrl.trim();
    const next: TagMappingRequest = {
      ...(existing
        ? {
            ...tagToRequest(existing),
            description: draft.description,
            protocol: draft.protocol,
            externalAddress: draft.externalAddress.trim(),
            writable: draft.writable,
            deadband: draft.deadband,
            dataType: draft.dataType,
          }
        : { ...draft, lifecycle: "DRAFT" as const }),
      siteId: context.siteId,
      lineId: context.lineId,
      tagCode: code,
      endpointUrl,
      externalAddress: draft.externalAddress.trim(),
    };
    if (existing?.lifecycle) next.lifecycle = existing.lifecycle;
    if (activeRowNeedsProbe(existing, next) && !probeCoversDraft(subjects[code], next, context.securityMode)) {
      return { rows: null, error: `Probe ${code} before saving an ACTIVE address change.` };
    }
    rows.push(next);
  }
  return { rows, error: null };
}

function tagToRequest(tag: TagMapping): TagMappingRequest {
  return {
    siteId: tag.siteId,
    lineId: tag.lineId,
    tagCode: tag.tagCode.trim(),
    description: tag.description,
    protocol: tag.protocol,
    endpointUrl: tag.endpointUrl,
    externalAddress: tag.externalAddress,
    dataType: tag.dataType,
    unit: tag.unit,
    writable: tag.writable,
    minValue: tag.minValue,
    maxValue: tag.maxValue,
    deadband: tag.deadband,
    warningLow: tag.warningLow,
    warningHigh: tag.warningHigh,
    criticalLow: tag.criticalLow,
    criticalHigh: tag.criticalHigh,
    interlockRuleKey: tag.interlockRuleKey,
    lifecycle: tag.lifecycle,
  };
}
