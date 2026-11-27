export function normalizeSiteCode(site: {
  siteCode?: string | null;
  siteId?: string | null;
}): string {
  return (site.siteCode ?? site.siteId ?? "").trim();
}

export function normalizeLineCode(line: {
  lineCode?: string | null;
  lineId?: string | null;
}): string {
  return (line.lineCode ?? line.lineId ?? "").trim();
}

export function encodeRegistryPathSegment(value: string): string {
  return encodeURIComponent(value.trim());
}
