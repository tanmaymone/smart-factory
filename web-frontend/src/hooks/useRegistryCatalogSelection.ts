"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { listLines, listSites } from "@/lib/registryApi";
import { normalizeLineCode, normalizeSiteCode } from "@/lib/registryNormalize";
import { DEFAULT_LINE_LABEL, DEFAULT_SITE_LABEL } from "@/lib/entityLabels";
import {
  resolveSelection,
  SITE_CATALOG,
  type LineInfo,
  type Selection,
  type SiteInfo,
} from "@/lib/sites";

function toCatalog(
  sites: {
    siteCode?: string;
    siteId?: string;
    name: string;
    timezone: string;
    siteLabel?: string;
    lineLabel?: string;
    lifecycle: SiteInfo["lifecycle"];
  }[],
  lines: { siteCode: string; lineCode?: string; lineId?: string; name: string; lifecycle: LineInfo["lifecycle"] }[],
): SiteInfo[] {
  return sites.map((site) => {
    const id = normalizeSiteCode(site);
    return {
      id,
      name: site.name,
      timezone: site.timezone,
      siteLabel: site.siteLabel ?? DEFAULT_SITE_LABEL,
      lineLabel: site.lineLabel ?? DEFAULT_LINE_LABEL,
      lifecycle: site.lifecycle,
      lines: lines
        .filter((line) => line.siteCode === id)
        .map((line) => ({
          id: normalizeLineCode(line),
          name: line.name,
          lifecycle: line.lifecycle,
        })),
    };
  });
}

export function useRegistryCatalogSelection() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const [sites, setSites] = useState<SiteInfo[]>(SITE_CATALOG);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [catalogError, setCatalogError] = useState(false);
  const fetchGeneration = useRef(0);

  const refreshCatalog = useCallback(async () => {
    const generation = fetchGeneration.current + 1;
    fetchGeneration.current = generation;
    try {
      const siteRows = await listSites();
      const lineRows = await listLines();
      if (fetchGeneration.current !== generation) return;
      setSites(toCatalog(siteRows, lineRows));
      setCatalogError(false);
    } catch {
      if (fetchGeneration.current !== generation) return;
      setCatalogError(true);
    } finally {
      if (fetchGeneration.current === generation) setCatalogLoading(false);
    }
  }, []);

  useEffect(() => {
    void refreshCatalog();
  }, [refreshCatalog]);

  const rawSite = searchParams.get("siteId");
  const rawLine = searchParams.get("lineId");
  const selection = resolveSelection(rawSite, rawLine, sites);
  const site = sites.find((entry) => entry.id === selection.siteId);
  const line = site?.lines.find((entry) => entry.id === selection.lineId);
  const hasValidLineSelection = Boolean(site && line);
  const preserveUrl = catalogError && Boolean(rawSite && rawLine);

  useEffect(() => {
    if (catalogLoading || preserveUrl) return;
    if (rawSite === selection.siteId && rawLine === selection.lineId) return;
    const params = new URLSearchParams(searchParams.toString());
    params.set("siteId", selection.siteId);
    params.set("lineId", selection.lineId);
    router.replace(`${pathname}?${params.toString()}`);
  }, [catalogLoading, preserveUrl, rawSite, rawLine, selection.siteId, selection.lineId, router, searchParams, pathname]);

  const replaceSelection = (siteId: string, lineId: string) => {
    const next = resolveSelection(siteId, lineId, sites);
    const params = new URLSearchParams(searchParams.toString());
    params.set("siteId", next.siteId);
    params.set("lineId", next.lineId);
    router.replace(`${pathname}?${params.toString()}`);
  };

  return {
    selection: selection as Selection,
    sites,
    catalog: sites.filter((entry) => entry.lines.length > 0),
    catalogLoading,
    catalogError,
    hasValidLineSelection,
    refreshCatalog,
    replaceSelection,
  };
}
