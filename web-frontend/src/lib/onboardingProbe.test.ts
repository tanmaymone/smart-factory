import { describe, expect, it } from "vitest";
import { collectProbeNodeIds, fanOutDiagnostics, foldProbeChunks, type ProbeRow } from "@/lib/onboardingProbe";
import { beginProbe, commitProbe, emptyWizard } from "@/lib/onboardingWizardState";
import type { OnboardingProbeResponse } from "@/lib/types";

const shared = "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1007";

function row(tagCode: string, externalAddress: string): ProbeRow {
  return { tagCode, protocol: "OPC_UA", externalAddress, endpointUrl: "opc.tcp://host:4840" };
}

function envelope(error: string | null, tags: OnboardingProbeResponse["tags"] = []): OnboardingProbeResponse {
  return { endpointUrl: "opc.tcp://host:4840", securityMode: "None", reachable: error == null, error, tags };
}

describe("SF-M35 onboarding probe", () => {
  it("fans one node out to every tag that shares it", () => {
    const rows = [row("VALVE_101_POS", ` ${shared} `), row("REACTOR_TEMP_SP", shared), row("TEMP", "nsu=urn;i=1002")];
    expect(collectProbeNodeIds(rows)).toEqual([shared, "nsu=urn;i=1002"]);
    const mapped = fanOutDiagnostics(rows, [{ nodeId: shared, quality: "GOOD" }, { nodeId: "nsu=urn;i=1002", quality: "BAD" }]);
    expect(mapped.VALVE_101_POS.quality).toBe("GOOD");
    expect(mapped.REACTOR_TEMP_SP.quality).toBe("GOOD");
  });

  it("drops the whole buffer when a later chunk fails", () => {
    const failed = foldProbeChunks([
      { ok: true, body: envelope(null, [{ nodeId: shared, quality: "BAD" }]) },
      { ok: false, status: 502 },
    ]);
    expect(failed.commit).toBe(false);
    expect(failed.diagnostics).toEqual([]);
    expect(foldProbeChunks([{ ok: true, body: envelope("dial failed") }]).commit).toBe(false);
  });

  it("does not commit a probe after a newer run starts", () => {
    const started = beginProbe({
      ...emptyWizard("SITE", "LINE"),
      probeDiagnostics: { TEMP: { nodeId: "n", quality: "BAD" } },
      probeSubject: { TEMP: { protocol: "OPC_UA", endpointUrl: "a", externalAddress: "n", securityMode: "None", probeRunId: 1, wizardGeneration: 1, quality: "BAD" } },
    });
    expect(started.probeDiagnostics).toEqual({});
    const captured = { probeRunId: started.probeRunId, generation: started.wizardGeneration, step3EditRevision: started.step3EditRevision };
    const newer = beginProbe(started);
    const late = commitProbe(newer, captured, { TEMP: { nodeId: "n", quality: "GOOD" } }, {}, false);
    expect(late.probeDiagnostics).toEqual({});
  });
});
