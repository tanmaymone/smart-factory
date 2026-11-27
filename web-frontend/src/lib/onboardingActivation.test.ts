import { describe, expect, it } from "vitest";
import {
  activateBlockReason,
  buildSiteUpdate,
  needsCatalogRefresh,
  savedTagsReady,
  seedActivated,
  subjectDrift,
} from "@/lib/onboardingActivation";
import type { ProbeSubject } from "@/lib/onboardingProbe";
import type { TagMapping } from "@/lib/types";

function tag(partial: Partial<TagMapping>): TagMapping {
  return {
    id: "id",
    siteId: "SITE",
    lineId: "LINE",
    tagCode: "TEMP",
    description: "",
    protocol: "OPC_UA",
    endpointUrl: "opc.tcp://host:4840",
    externalAddress: "nsu=urn;i=1",
    dataType: "FLOAT",
    unit: null,
    writable: false,
    minValue: null,
    maxValue: null,
    deadband: 0,
    warningLow: null,
    warningHigh: null,
    criticalLow: null,
    criticalHigh: null,
    interlockRuleKey: null,
    lifecycle: "DRAFT",
    ...partial,
  };
}

const subject: ProbeSubject = {
  protocol: "OPC_UA",
  endpointUrl: "opc.tcp://host:4840",
  externalAddress: "nsu=urn;i=1",
  securityMode: "None",
  probeRunId: 1,
  wizardGeneration: 1,
  quality: "GOOD",
};

describe("SF-M35 onboarding activation", () => {
  it("blocks an empty saved set before any registry read", () => {
    expect(activateBlockReason({ step3Dirty: false, savedTagCodes: [] })).toMatch(/at least one/i);
    expect(activateBlockReason({ step3Dirty: true, savedTagCodes: ["TEMP"] })).toMatch(/Save/);
  });

  it("fails closed for a missing, blank, or non OPC UA saved tag", () => {
    expect(savedTagsReady(["GONE"], []).ok).toBe(false);
    expect(savedTagsReady(["TEMP"], [tag({ protocol: "S7" })]).ok).toBe(false);
    expect(savedTagsReady(["TEMP"], [tag({ externalAddress: " " })]).ok).toBe(false);
  });

  it("treats a drifted subject as stale and a matching one as current", () => {
    expect(subjectDrift("TEMP", tag({ externalAddress: "nsu=urn;i=9" }), subject, "None")).toBe(true);
    expect(subjectDrift("TEMP", tag({}), subject, "None")).toBe(false);
  });

  it("overlays only a dirty site name and keeps the fresh timezone", () => {
    expect(buildSiteUpdate(
      { siteCode: "SITE", name: "Fresh", timezone: "UTC", lifecycle: "ACTIVE" },
      { name: true, timezone: false },
      { name: "Typed", timezone: "Asia/Kolkata" },
    )).toEqual({ name: "Typed", timezone: "UTC", lifecycle: "ACTIVE" });
  });

  it("seeds only saved tags that are already ACTIVE", () => {
    expect(seedActivated(["TEMP", "OTHER"], [tag({ lifecycle: "ACTIVE" }), tag({ tagCode: "EXTRA", lifecycle: "ACTIVE" })])).toEqual(["TEMP"]);
  });

  it("refreshes the catalog on retry when the previous refresh failed", () => {
    expect(needsCatalogRefresh({
      parentPutRan: false,
      catalogSiteActive: false,
      catalogLineActive: true,
      catalogError: true,
    })).toBe(true);
    expect(needsCatalogRefresh({
      parentPutRan: false,
      catalogSiteActive: true,
      catalogLineActive: true,
      catalogError: false,
    })).toBe(false);
  });
});
