import { describe, expect, it, vi, beforeEach } from "vitest";
import {
  CHECKLIST_CATEGORY_ORDER,
  checklistCategoryLabel,
  fetchPreCutoverChecklist,
} from "@/lib/onboardingCatalogApi";
import { resolveSelection } from "@/lib/sites";

describe("SF-M36 onboardingCatalogApi", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("labels all five checklist domains", () => {
    expect(CHECKLIST_CATEGORY_ORDER).toHaveLength(5);
    expect(checklistCategoryLabel("EDGE_PROVISIONING")).toBe("Edge Provisioning");
  });

  it("maps runnable vs deferred checklist metadata", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          schemaVersion: 1,
          releaseScope: "M3.6",
          playbookPath: "PLANT_ONBOARDING_PLAYBOOK.md",
          summary: { total: 2, runnableNow: 1, deferredMilestoneGates: 1, cutoverBlockers: 2 },
          items: [
            {
              id: "A",
              category: "OPC_UA_SECURITY",
              title: "Runnable",
              description: "",
              verificationMethod: "MANUAL_COMMAND",
              verificationCommand: "",
              passCriteria: "",
              severity: "CUTOVER_BLOCKER",
              runnableInCurrentRelease: true,
              milestoneGate: null,
              currentDevPosture: "",
              productionRequirement: "",
            },
            {
              id: "B",
              category: "HEARTBEAT_AND_STALENESS",
              title: "Deferred",
              description: "",
              verificationMethod: "DEFERRED_MILESTONE_GATE",
              verificationCommand: "",
              passCriteria: "",
              severity: "CUTOVER_BLOCKER",
              runnableInCurrentRelease: false,
              milestoneGate: "M4.4",
              currentDevPosture: "",
              productionRequirement: "",
            },
          ],
        }),
      }),
    );
    const body = await fetchPreCutoverChecklist();
    expect(body.items.some((item) => item.runnableInCurrentRelease)).toBe(true);
    expect(body.items.some((item) => item.milestoneGate === "M4.4")).toBe(true);
    expect(JSON.stringify(body)).not.toContain("runtimeContext");
  });

  it("resolveSelection handles empty registry catalog", () => {
    const selection = resolveSelection("SITE-COLD", "REACTOR-01", []);
    expect(selection.site).toBeNull();
    expect(selection.siteId).toBe("SITE-COLD");
    expect(selection.lineId).toBe("REACTOR-01");
  });
});