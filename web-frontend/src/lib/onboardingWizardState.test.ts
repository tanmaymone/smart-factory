import { describe, expect, it } from "vitest";
import {
  advanceAfterStep2,
  applyCreateSuccess,
  applyMetadataField,
  applySaveSuccess,
  bumpFieldEdit,
  bumpStep3Edit,
  classifyCreateStatus,
  commitProbe,
  emptyWizard,
  invalidateFromStep1,
  invalidateFromStep2,
  restoreWizard,
  serializeWizard,
} from "@/lib/onboardingWizardState";

describe("SF-M35 onboarding wizard state", () => {
  it("dirties step 3 when the endpoint changes and not when only the mode changes", () => {
    const saved = { ...emptyWizard("SITE-TEST", "REACTOR-01"), savedTagCodes: ["TEMP"], drafts: [] };
    const endpoint = invalidateFromStep2(saved, { endpointUrl: "opc.tcp://other:4840" });
    expect(endpoint.step3Dirty).toBe(true);
    expect(endpoint.wizardGeneration).toBe(saved.wizardGeneration + 1);
    const mode = invalidateFromStep2(saved, { securityMode: "SignAndEncrypt" });
    expect(mode.step3Dirty).toBe(false);
  });

  it("does not clear step3Dirty when the grid changed during the save", () => {
    const started = emptyWizard("SITE-TEST", "REACTOR-01");
    const edited = bumpStep3Edit(started);
    const applied = applySaveSuccess(edited, ["TEMP"], { generation: started.wizardGeneration, step3EditRevision: started.step3EditRevision });
    expect(applied.step3Dirty).toBe(true);
  });

  it("does not clear a site name edit that happened during create", () => {
    const started = bumpFieldEdit(emptyWizard("SITE-TEST", "REACTOR-01"), "siteName", "New");
    const applied = applyCreateSuccess(started, "site", {
      generation: started.wizardGeneration,
      siteNameRevision: started.siteNameEditRevision - 1,
      timezoneRevision: started.siteTimezoneEditRevision,
      lineRevision: started.lineNameEditRevision,
    });
    expect(applied.siteNameDirty).toBe(true);
  });

  it("treats GET 200 and POST 200 as collisions and POST 201 as create", () => {
    expect(classifyCreateStatus(200)).toBe("collision");
    expect(classifyCreateStatus(201)).toBe("created");
  });

  it("does not apply site A metadata after switching to site B", () => {
    const siteA = emptyWizard("SITE-A", "LINE");
    const siteB = bumpFieldEdit(invalidateFromStep1(siteA, "SITE-B", "LINE"), "siteName", "Edited");
    const applied = applyMetadataField(siteB, "siteName", "From A", {
      generation: siteA.wizardGeneration,
      revision: siteA.siteNameEditRevision,
    });
    expect(applied.siteName).toBe("Edited");
  });

  it("clamps a stored step 4 back to step 2 and keeps saved tags", () => {
    const restored = restoreWizard({
      step: 4,
      siteId: "SITE-TEST",
      lineId: "REACTOR-01",
      drafts: [],
      savedTagCodes: ["TEMP"],
      step3Dirty: false,
      endpointUrl: "opc.tcp://localhost:4840",
      securityMode: "None",
      siteName: "Test",
      siteTimezone: "Asia/Kolkata",
      lineName: "Reactor",
    }, emptyWizard("SITE-TEST", "REACTOR-01"));
    expect(restored.step).toBe(2);
    expect(restored.savedTagCodes).toEqual(["TEMP"]);
    expect(restored.step2Passed).toBe(false);
    expect(serializeWizard(restored).step3Dirty).toBe(false);
    expect("probeDiagnostics" in serializeWizard(restored)).toBe(false);
    const advanced = advanceAfterStep2({ ...restored, step2Passed: true });
    expect(advanced.step).toBe(4);
  });

  it("treats drafts without a dirty flag as unsaved", () => {
    const restored = restoreWizard({
      step: 3,
      siteId: "SITE-TEST",
      lineId: "REACTOR-01",
      drafts: [{ tagCode: "TEMP" } as never],
      endpointUrl: "",
      securityMode: "None",
      siteName: "",
      siteTimezone: "",
      lineName: "",
      savedTagCodes: [],
    }, emptyWizard("SITE-TEST", "REACTOR-01"));
    expect(restored.step3Dirty).toBe(true);
  });

  it("drops site A dirty metadata when the site code changes", () => {
    const dirty = bumpFieldEdit(emptyWizard("SITE-A", "LINE"), "siteName", "A");
    const next = invalidateFromStep1(dirty, "INDORE", "LINE");
    expect(next.siteNameDirty).toBe(false);
    expect(next.siteName).toBe("");
  });
});
