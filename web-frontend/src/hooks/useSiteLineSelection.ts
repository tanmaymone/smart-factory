"use client";

import { useRegistryCatalogSelection } from "@/hooks/useRegistryCatalogSelection";

export function useSiteLineSelection() {
  const catalog = useRegistryCatalogSelection();
  return {
    selection: catalog.selection,
    catalog: catalog.catalog,
    replaceSelection: catalog.replaceSelection,
    catalogLoading: catalog.catalogLoading,
    catalogError: catalog.catalogError,
    hasValidLineSelection: catalog.hasValidLineSelection,
    refreshCatalog: catalog.refreshCatalog,
    sites: catalog.sites,
  };
}
