import { describe, expect, it } from "vitest";
import { parseTagImport, TAG_BATCH_CSV_HEADER } from "@/lib/onboardingTagImport";

describe("SF-M36 onboardingTagImport", () => {
  const context = {
    siteId: "SITE-COLD",
    lineId: "REACTOR-01",
    endpointUrl: "opc.tcp://localhost:53530/OPCUA/SimulationServer",
  };

  it("parses RFC-4180 quoted CSV with all 19 fields", () => {
    const csv = [
      TAG_BATCH_CSV_HEADER,
      'SITE-COLD,REACTOR-01,VALVE_101_POS,"Inlet, main",OPC_UA,opc.tcp://localhost:53530/OPCUA/SimulationServer,nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007,FLOAT,%,true,0,100,0.5,10,90,5,95,rule-a,DRAFT',
    ].join("\n");
    const parsed = parseTagImport(csv, context);
    expect(parsed.error).toBeNull();
    expect(parsed.drafts).toHaveLength(1);
    const row = parsed.drafts[0]!;
    expect(row.dataType).toBe("FLOAT");
    expect(row.minValue).toBe(0);
    expect(row.maxValue).toBe(100);
    expect(row.deadband).toBe(0.5);
    expect(row.warningLow).toBe(10);
    expect(row.warningHigh).toBe(90);
    expect(row.criticalLow).toBe(5);
    expect(row.criticalHigh).toBe(95);
    expect(row.interlockRuleKey).toBe("rule-a");
    expect(row.writable).toBe(true);
    expect(row.lifecycle).toBe("DRAFT");
    expect(row.description).toBe("Inlet, main");
  });

  it("parses JSON tag arrays losslessly", () => {
    const json = JSON.stringify({
      tags: [
        {
          siteId: "SITE-COLD",
          lineId: "REACTOR-01",
          tagCode: "REACTOR_TEMP_C",
          description: "Temperature",
          protocol: "OPC_UA",
          endpointUrl: context.endpointUrl,
          externalAddress: "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1002",
          dataType: "FLOAT",
          unit: "C",
          writable: false,
          minValue: -10,
          maxValue: 200,
          deadband: 0.2,
          warningLow: 70,
          warningHigh: 85,
          criticalLow: 60,
          criticalHigh: 95,
          interlockRuleKey: null,
          lifecycle: "DRAFT",
        },
      ],
    });
    const parsed = parseTagImport(json, context);
    expect(parsed.error).toBeNull();
    expect(parsed.drafts[0]?.dataType).toBe("FLOAT");
    expect(parsed.drafts[0]?.minValue).toBe(-10);
    expect(parsed.drafts[0]?.writable).toBe(false);
  });
});