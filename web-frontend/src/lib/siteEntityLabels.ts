import { entityLabelPatternOk } from "@/lib/entityLabels";
import { getSite, updateSite } from "@/lib/registryApi";
import type { RegistryErrorResponse, SiteResponse, SiteUpdateRequest } from "@/lib/types";

const INVALID_PATTERN_MESSAGE = "Choose valid display words (2–24 letters; spaces or hyphens only).";

export function buildSiteLabelUpdate(
  fresh: SiteResponse,
  siteLabel: string,
  lineLabel: string,
  overlay?: { name?: string; timezone?: string },
): SiteUpdateRequest {
  return {
    name: overlay?.name ?? fresh.name,
    timezone: overlay?.timezone ?? fresh.timezone,
    lifecycle: fresh.lifecycle,
    siteLabel: siteLabel.trim(),
    lineLabel: lineLabel.trim(),
  };
}

export type SaveSiteEntityLabelsResult =
  | { ok: true }
  | { ok: false; reason: "invalid_pattern" | "not_found" | "api"; message: string; error?: RegistryErrorResponse };

export async function saveSiteEntityLabels(
  siteCode: string,
  siteLabel: string,
  lineLabel: string,
  overlay?: { name?: string; timezone?: string },
): Promise<SaveSiteEntityLabelsResult> {
  if (!entityLabelPatternOk(siteLabel) || !entityLabelPatternOk(lineLabel)) {
    return { ok: false, reason: "invalid_pattern", message: INVALID_PATTERN_MESSAGE };
  }
  const fresh = await getSite(siteCode);
  if (!fresh.site) {
    return { ok: false, reason: "not_found", message: "Site was not found." };
  }
  const updated = await updateSite(siteCode, buildSiteLabelUpdate(fresh.site, siteLabel, lineLabel, overlay));
  if (!updated.ok) {
    return { ok: false, reason: "api", message: updated.error.message, error: updated.error };
  }
  return { ok: true };
}