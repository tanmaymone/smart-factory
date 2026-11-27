export const DEFAULT_SITE_LABEL = "Site";
export const DEFAULT_LINE_LABEL = "Line";

export const SITE_LABEL_PRESETS = [
  "Site",
  "Plant",
  "Facility",
  "Mill",
  "Factory",
  "Warehouse",
] as const;

export const LINE_LABEL_PRESETS = ["Line", "Area", "Station", "Floor", "Cell", "Zone"] as const;

export const ENTITY_LABEL_PATTERN = /^[\p{L}]+(?:[\s-][\p{L}]+)*$/u;
export const ENTITY_LABEL_MIN = 2;
export const ENTITY_LABEL_MAX = 24;

export type ResolvedEntityLabels = {
  siteLabel: string;
  lineLabel: string;
};

export function normalizeEntityLabel(raw: string | null | undefined, fallback: string): string {
  const trimmed = (raw ?? "").trim();
  return trimmed.length > 0 ? trimmed : fallback;
}

export function resolveEntityLabels(
  site?: { siteLabel?: string | null; lineLabel?: string | null } | null,
): ResolvedEntityLabels {
  return {
    siteLabel: normalizeEntityLabel(site?.siteLabel, DEFAULT_SITE_LABEL),
    lineLabel: normalizeEntityLabel(site?.lineLabel, DEFAULT_LINE_LABEL),
  };
}

export function entityLabelPatternOk(value: string): boolean {
  const trimmed = value.trim();
  if (trimmed.length < ENTITY_LABEL_MIN || trimmed.length > ENTITY_LABEL_MAX) {
    return false;
  }
  return ENTITY_LABEL_PATTERN.test(trimmed);
}

export function stepOneTitle(labels: ResolvedEntityLabels): string {
  return `${labels.siteLabel} & ${labels.lineLabel.toLowerCase()}`;
}

export function entityColumnHeader(labels: ResolvedEntityLabels): string {
  return `${labels.siteLabel} / ${labels.lineLabel}`;
}