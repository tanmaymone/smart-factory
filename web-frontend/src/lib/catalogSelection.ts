import type { SiteInfo } from "@/lib/sites";

export type ConsoleMount = "loading" | "error" | "no-line" | "body";

export function consoleMountState(input: {
  catalogLoading: boolean;
  catalogError: boolean;
  hasValidLineSelection: boolean;
}): ConsoleMount {
  if (input.catalogLoading) return "loading";
  if (input.catalogError) return "error";
  if (!input.hasValidLineSelection) return "no-line";
  return "body";
}

export type CatalogSnapshot = {
  sites: SiteInfo[];
  error: boolean;
  generation: number;
};

export function applyCatalogFetch(
  current: CatalogSnapshot,
  incoming: { sites: SiteInfo[] | null; error: boolean; generation: number },
): CatalogSnapshot {
  if (incoming.generation !== current.generation) {
    return current;
  }
  if (incoming.error) {
    return { ...current, error: true };
  }
  return { sites: incoming.sites ?? current.sites, error: false, generation: current.generation };
}

export function sitesWithLines(sites: SiteInfo[]): SiteInfo[] {
  return sites.filter((site) => site.lines.length > 0);
}

export function parentsLookActive(
  sites: SiteInfo[],
  siteId: string,
  lineId: string,
): boolean {
  const site = sites.find((entry) => entry.id === siteId);
  const line = site?.lines.find((entry) => entry.id === lineId);
  return site?.lifecycle === "ACTIVE" && line?.lifecycle === "ACTIVE";
}
