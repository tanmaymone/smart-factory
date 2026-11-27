import { describe, expect, it } from "vitest";
import { applyCatalogFetch, consoleMountState } from "@/lib/catalogSelection";
import { SITE_CATALOG } from "@/lib/sites";

describe("SF-M35 catalog gating", () => {
  it("shows the error banner before the zero-line empty state", () => {
    expect(consoleMountState({ catalogLoading: true, catalogError: false, hasValidLineSelection: false })).toBe("loading");
    expect(consoleMountState({ catalogLoading: false, catalogError: true, hasValidLineSelection: false })).toBe("error");
    expect(consoleMountState({ catalogLoading: false, catalogError: false, hasValidLineSelection: false })).toBe("no-line");
  });

  it("keeps a catalog write when only the wizard generation moved", () => {
    const current = { sites: SITE_CATALOG, error: false, generation: 2 };
    expect(applyCatalogFetch(current, { sites: [], error: false, generation: 2 }).sites).toEqual([]);
    expect(applyCatalogFetch(current, { sites: [], error: false, generation: 1 })).toBe(current);
  });

  it("sets catalogError even when previous rows remain", () => {
    const next = applyCatalogFetch(
      { sites: SITE_CATALOG, error: false, generation: 4 },
      { sites: null, error: true, generation: 4 },
    );
    expect(next.error).toBe(true);
    expect(next.sites).toEqual(SITE_CATALOG);
  });
});
