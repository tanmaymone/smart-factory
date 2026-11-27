import { describe, expect, it } from "vitest";
import { buildSiteLabelUpdate } from "@/lib/siteEntityLabels";

describe("SF-LABEL site entity labels", () => {
  it("preserves registry fields and only trims vocabulary on update body", () => {
    expect(
      buildSiteLabelUpdate(
        {
          siteCode: "INDORE",
          name: "Indore Plant",
          timezone: "Asia/Kolkata",
          lifecycle: "ACTIVE",
          siteLabel: "Site",
          lineLabel: "Line",
        },
        " Plant ",
        " Shop Floor ",
      ),
    ).toEqual({
      name: "Indore Plant",
      timezone: "Asia/Kolkata",
      lifecycle: "ACTIVE",
      siteLabel: "Plant",
      lineLabel: "Shop Floor",
    });
  });

  it("applies optional name and timezone overlay for onboarding", () => {
    const body = buildSiteLabelUpdate(
      { siteCode: "X", name: "Old", timezone: "UTC", lifecycle: "DRAFT" },
      "Facility",
      "Area",
      { name: "New name", timezone: "Asia/Kolkata" },
    );
    expect(body.name).toBe("New name");
    expect(body.timezone).toBe("Asia/Kolkata");
    expect(body.siteLabel).toBe("Facility");
  });
});