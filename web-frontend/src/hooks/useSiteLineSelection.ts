"use client";

import { useEffect, useMemo } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import {
  mergeCatalog,
  resolveSelection,
  SITE_CATALOG,
  type DiscoveredPair,
  type Selection,
  type SiteInfo,
} from "@/lib/sites";

export function useSiteLineSelection(discovered: DiscoveredPair[] = []): {
  selection: Selection;
  catalog: SiteInfo[];
  replaceSelection: (siteId: string, lineId: string) => void;
} {
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const rawSite = searchParams.get("siteId");
  const rawLine = searchParams.get("lineId");
  const selection = resolveSelection(rawSite, rawLine);

  const catalog = useMemo(
    () => mergeCatalog(SITE_CATALOG, discovered),
    [discovered],
  );

  useEffect(() => {
    if (rawSite === selection.siteId && rawLine === selection.lineId) {
      return;
    }
    const params = new URLSearchParams(searchParams.toString());
    params.set("siteId", selection.siteId);
    params.set("lineId", selection.lineId);
    router.replace(`${pathname}?${params.toString()}`);
  }, [
    rawSite,
    rawLine,
    selection.siteId,
    selection.lineId,
    router,
    searchParams,
    pathname,
  ]);

  const replaceSelection = (siteId: string, lineId: string) => {
    const next = resolveSelection(siteId, lineId, catalog);
    const params = new URLSearchParams();
    params.set("siteId", next.siteId);
    params.set("lineId", next.lineId);
    router.replace(`${pathname}?${params.toString()}`);
  };

  return { selection, catalog, replaceSelection };
}
