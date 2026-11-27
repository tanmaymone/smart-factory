import { describe, expect, it } from "vitest";
import { buildBatchRows } from "@/lib/onboardingBatch";
import type { ProbeSubject } from "@/lib/onboardingProbe";
import type { TagMapping, TagMappingRequest } from "@/lib/types";

function draft(partial: Partial<TagMappingRequest> = {}): TagMappingRequest {
  return {
    siteId: "SITE",
    lineId: "LINE",
    tagCode: "TEMP",
    description: "temp",
    protocol: "OPC_UA",
    endpointUrl: "opc.tcp://host:4840",
    externalAddress: "nsu=urn;i=1",
    dataType: "FLOAT",
    unit: null,
    writable: false,
    minValue: null,
    maxValue: null,
    deadband: 1,
    warningLow: null,
    warningHigh: null,
    criticalLow: null,
    criticalHigh: null,
    interlockRuleKey: null,
    ...partial,
  };
}

function existing(partial: Partial<TagMapping>): TagMapping {
  return { id: "1", ...draft(partial), interlockRuleKey: "SAFE", warningHigh: 80, ...partial };
}

const subject: ProbeSubject = {
  protocol: "OPC_UA",
  endpointUrl: "opc.tcp://host:4840",
  externalAddress: "nsu=urn;i=2",
  securityMode: "None",
  probeRunId: 1,
  wizardGeneration: 1,
  quality: "GOOD",
};

describe("SF-M35 onboarding batch", () => {
  it("keeps an existing lifecycle and unedited interlock fields", () => {
    const built = buildBatchRows(
      [draft({ deadband: 2 })],
      { siteId: "SITE", lineId: "LINE", endpointUrl: " opc.tcp://host:4840 ", securityMode: "None" },
      { TEMP: existing({ lifecycle: "ACTIVE", externalAddress: "nsu=urn;i=1", endpointUrl: "opc.tcp://host:4840" }) },
      {},
    );
    expect(built.rows?.[0].lifecycle).toBe("ACTIVE");
    expect(built.rows?.[0].interlockRuleKey).toBe("SAFE");
    expect(built.rows?.[0].warningHigh).toBe(80);
    expect(built.rows?.[0].deadband).toBe(2);
    expect(built.rows?.[0].endpointUrl).toBe("opc.tcp://host:4840");
  });

  it("requires a GOOD probe before changing an ACTIVE address or protocol", () => {
    const context = { siteId: "SITE", lineId: "LINE", endpointUrl: "opc.tcp://host:4840", securityMode: "None" as const };
    const row = existing({ lifecycle: "ACTIVE", protocol: "MODBUS_TCP" });
    expect(buildBatchRows([draft()], context, { TEMP: row }, {}).error).toMatch(/Probe/);
    const probed = buildBatchRows([draft({ externalAddress: "nsu=urn;i=2" })], context, {
      TEMP: existing({ lifecycle: "ACTIVE", externalAddress: "nsu=urn;i=1" }),
    }, { TEMP: subject });
    expect(probed.rows?.[0].externalAddress).toBe("nsu=urn;i=2");
  });

  it("rejects an empty list, a blank code, a non OPC UA draft, and duplicates", () => {
    const context = { siteId: "SITE", lineId: "LINE", endpointUrl: "opc.tcp://host:4840", securityMode: "None" as const };
    expect(buildBatchRows([], context, {}, {}).error).toMatch(/at least one/i);
    expect(buildBatchRows([draft({ tagCode: "  " })], context, {}, {}).error).toMatch(/required/i);
    expect(buildBatchRows([draft({ protocol: "S7" })], context, {}, {}).error).toMatch(/OPC UA/);
    expect(buildBatchRows([draft({ externalAddress: " " })], context, {}, {}).error).toMatch(/address/i);
    expect(buildBatchRows([draft({ tagCode: "TEMP" }), draft({ tagCode: " TEMP " })], context, {}, {}).error).toMatch(/Duplicate/);
  });

  it("creates a new tag as DRAFT", () => {
    const built = buildBatchRows(
      [draft({ tagCode: "NEW" })],
      { siteId: "SITE", lineId: "LINE", endpointUrl: "opc.tcp://host:4840", securityMode: "None" },
      {},
      {},
    );
    expect(built.rows?.[0].lifecycle).toBe("DRAFT");
  });
});
