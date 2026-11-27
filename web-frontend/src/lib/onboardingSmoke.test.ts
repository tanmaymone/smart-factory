import { describe, expect, it } from "vitest";
import { mergeSmokeLatest, mergeSmokeSample } from "@/lib/onboardingSmoke";
import { emptyStore } from "@/lib/telemetryState";
import type { LatestTagState } from "@/lib/types";

function sample(tagCode: string, timestamp: string): LatestTagState {
  return {
    siteId: "SITE-TEST",
    lineId: "REACTOR-01",
    tagCode,
    value: 1,
    quality: "GOOD",
    timestamp,
    severity: "NONE",
    direction: null,
    alarmFrozen: false,
    alarmValue: null,
    alarmTimestamp: null,
  };
}

describe("SF-M35 onboarding smoke", () => {
  it("does not let an empty latest response wipe a TAG_UPDATE", () => {
    const captured = { generation: 1, siteId: "SITE-TEST", lineId: "REACTOR-01" };
    const withSample = mergeSmokeSample(emptyStore("SITE-TEST", "REACTOR-01"), sample("VALVE", "2026-01-02T00:00:00Z"), captured, 1);
    const merged = mergeSmokeLatest(withSample, [], captured, 1);
    expect(merged.tags.VALVE?.value).toBe(1);
  });

  it("drops a late result after the generation moves", () => {
    const captured = { generation: 1, siteId: "SITE-TEST", lineId: "REACTOR-01" };
    const store = emptyStore("SITE-TEST", "REACTOR-01");
    expect(mergeSmokeSample(store, sample("VALVE", "2026-01-02T00:00:00Z"), captured, 2)).toBe(store);
  });
});
