export type LineInfo = {
  id: string;
  name: string;
  lifecycle?: "DRAFT" | "VERIFIED" | "ACTIVE";
};

export type SiteInfo = {
  id: string;
  name: string;
  timezone: string;
  siteLabel?: string;
  lineLabel?: string;
  lifecycle?: "DRAFT" | "VERIFIED" | "ACTIVE";
  lines: LineInfo[];
};

export const SITE_CATALOG: SiteInfo[] = [
  {
    id: "INDORE",
    name: "Indore Plant",
    timezone: "Asia/Kolkata",
    lifecycle: "ACTIVE",
    lines: [
      { id: "LINE-01", name: "Reactor Line 1", lifecycle: "ACTIVE" },
      { id: "LINE-02", name: "Packaging Line", lifecycle: "ACTIVE" },
    ],
  },
  {
    id: "PUNE",
    name: "Pune Plant",
    timezone: "Asia/Kolkata",
    lifecycle: "ACTIVE",
    lines: [{ id: "LINE-01", name: "Reactor Line 1", lifecycle: "ACTIVE" }],
  },
];

export const DEFAULT_SITE_ID = "INDORE";
export const DEFAULT_LINE_ID = "LINE-01";

export function normalizeId(value: string | null | undefined): string {
  return (value ?? "").trim();
}

export type Selection = {
  siteId: string;
  lineId: string;
  site: SiteInfo;
  line: LineInfo;
};

export function resolveSelection(
  siteId: string | null | undefined,
  lineId: string | null | undefined,
  catalog: SiteInfo[] = SITE_CATALOG,
): Selection {
  const normalizedSite = normalizeId(siteId);
  const normalizedLine = normalizeId(lineId);

  const site =
    catalog.find((entry) => entry.id === normalizedSite) ??
    catalog.find((entry) => entry.id === DEFAULT_SITE_ID) ??
    catalog[0];

  const line =
    site.lines.find((entry) => entry.id === normalizedLine) ?? site.lines[0];

  return {
    siteId: site.id,
    lineId: line.id,
    site,
    line,
  };
}

export type DiscoveredPair = {
  siteId: string;
  lineId: string;
};

/** Merge discovered site/line pairs into the catalog without duplicating normalized ids. */
export function mergeCatalog(
  catalog: SiteInfo[],
  discovered: DiscoveredPair[],
): SiteInfo[] {
  const bySite = new Map<string, SiteInfo>();
  for (const site of catalog) {
    bySite.set(site.id, {
      ...site,
      lines: site.lines.map((line) => ({ ...line })),
    });
  }

  for (const pair of discovered) {
    const siteId = normalizeId(pair.siteId);
    const lineId = normalizeId(pair.lineId);
    if (!siteId || !lineId) {
      continue;
    }
    let site = bySite.get(siteId);
    if (!site) {
      site = {
        id: siteId,
        name: siteId,
        timezone: "Asia/Kolkata",
        lines: [],
      };
      bySite.set(siteId, site);
    }
    if (!site.lines.some((line) => line.id === lineId)) {
      site.lines.push({ id: lineId, name: lineId });
    }
  }

  const knownOrder = catalog.map((site) => site.id);
  const result: SiteInfo[] = [];
  for (const id of knownOrder) {
    const site = bySite.get(id);
    if (site) {
      result.push(site);
      bySite.delete(id);
    }
  }
  for (const site of bySite.values()) {
    result.push(site);
  }
  return result;
}
