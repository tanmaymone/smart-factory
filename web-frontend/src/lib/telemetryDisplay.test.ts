import { describe, expect, it } from "vitest";
import { deriveTelemetryFeedState } from "@/lib/telemetryDisplay";

describe("SF-M35 telemetry feed state", () => {
  it("keeps an ACTIVE tag under a DRAFT site pending activation", () => {
    expect(deriveTelemetryFeedState({
      siteLifecycle: "DRAFT",
      lineLifecycle: "ACTIVE",
      tagLifecycle: "ACTIVE",
      hasSample: false,
    })).toBe("pendingActivation");
  });

  it("labels a live tag with no sample as no data yet and keeps UNCERTAIN distinct", () => {
    expect(deriveTelemetryFeedState({
      siteLifecycle: "ACTIVE",
      lineLifecycle: "ACTIVE",
      tagLifecycle: "ACTIVE",
      hasSample: false,
    })).toBe("noDataYet");
    expect(deriveTelemetryFeedState({
      siteLifecycle: "ACTIVE",
      lineLifecycle: "ACTIVE",
      tagLifecycle: "ACTIVE",
      hasSample: true,
      quality: "UNCERTAIN",
    })).toBe("uncertain");
    expect(deriveTelemetryFeedState({
      siteLifecycle: "ACTIVE",
      lineLifecycle: "ACTIVE",
      tagLifecycle: "ACTIVE",
      hasSample: true,
      quality: "BAD",
    })).toBe("bad");
  });
});
