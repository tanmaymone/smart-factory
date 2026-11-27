import { describe, expect, it } from "vitest";
import {
  DEFAULT_LINE_LABEL,
  DEFAULT_SITE_LABEL,
  entityLabelPatternOk,
  normalizeEntityLabel,
  resolveEntityLabels,
  stepOneTitle,
} from "@/lib/entityLabels";

describe("SF-LABEL entity labels", () => {
  it("falls back to Site and Line when labels are missing", () => {
    expect(resolveEntityLabels(null)).toEqual({
      siteLabel: DEFAULT_SITE_LABEL,
      lineLabel: DEFAULT_LINE_LABEL,
    });
    expect(normalizeEntityLabel("  ", DEFAULT_SITE_LABEL)).toBe(DEFAULT_SITE_LABEL);
    expect(normalizeEntityLabel("  Plant  ", DEFAULT_SITE_LABEL)).toBe("Plant");
  });

  it("accepts hyphenated and spaced words", () => {
    expect(entityLabelPatternOk("Shop Floor")).toBe(true);
    expect(entityLabelPatternOk("HVAC-Zone")).toBe(true);
    expect(entityLabelPatternOk("Line1")).toBe(false);
    expect(entityLabelPatternOk("Plant!")).toBe(false);
  });

  it("builds step one title with lowercased line word", () => {
    expect(stepOneTitle({ siteLabel: "Plant", lineLabel: "Area" })).toBe("Plant & area");
  });
});