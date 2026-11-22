import { describe, expect, it } from "vitest";
import {
  mergeCatalog,
  normalizeId,
  resolveSelection,
  SITE_CATALOG,
} from "@/lib/sites";

describe("SF-M17 site catalog", () => {
  it("defaults missing selection to INDORE / LINE-01", () => {
    const selection = resolveSelection(null, null);
    expect(selection.siteId).toBe("INDORE");
    expect(selection.lineId).toBe("LINE-01");
  });

  it("falls back to the site first line when line is unknown", () => {
    const selection = resolveSelection("PUNE", "LINE-99");
    expect(selection.siteId).toBe("PUNE");
    expect(selection.lineId).toBe("LINE-01");
  });

  it("normalizes mixed-case ids before catalog matching", () => {
    expect(normalizeId(" indore ")).toBe("INDORE");
    const selection = resolveSelection("pune", "line-01");
    expect(selection.siteId).toBe("PUNE");
    expect(selection.lineId).toBe("LINE-01");
  });

  it("does not duplicate catalog entries when discovery casing differs", () => {
    const merged = mergeCatalog(SITE_CATALOG, [
      { siteId: "indore", lineId: "line-01" },
      { siteId: "INDORE", lineId: "LINE-03" },
    ]);
    const indore = merged.find((site) => site.id === "INDORE");
    expect(indore).toBeDefined();
    expect(merged.filter((site) => site.id === "INDORE")).toHaveLength(1);
    expect(indore!.lines.map((line) => line.id)).toEqual([
      "LINE-01",
      "LINE-02",
      "LINE-03",
    ]);
  });
});
