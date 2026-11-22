import { describe, expect, it } from "vitest";
import { formatValue } from "@/lib/format";
import {
  applyAlarmUpdate,
  applyTagUpdate,
  emptyStore,
  hasSampleValue,
  isNewerOrEqual,
  mergeRows,
  replaceSnapshot,
  type TelemetryStore,
} from "@/lib/telemetryState";
import type { LatestTagState, TagMapping } from "@/lib/types";

function sample(partial: Partial<LatestTagState> & Pick<LatestTagState, "tagCode">): LatestTagState {
  return {
    siteId: "INDORE",
    lineId: "LINE-01",
    value: 10,
    quality: "GOOD",
    timestamp: "2026-11-20T10:00:00.000Z",
    severity: "NONE",
    direction: null,
    alarmFrozen: false,
    alarmValue: null,
    alarmTimestamp: null,
    ...partial,
  };
}

describe("SF-M17 telemetry state", () => {
  it("rejects older Date.parse timestamps and accepts equal timestamps", () => {
    expect(
      isNewerOrEqual("2026-11-20T10:00:01.000Z", "2026-11-20T10:00:00.000Z"),
    ).toBe(true);
    expect(
      isNewerOrEqual("2026-11-20T10:00:00.000Z", "2026-11-20T10:00:01.000Z"),
    ).toBe(false);
    expect(
      isNewerOrEqual("2026-11-20T10:00:00.000Z", "2026-11-20T10:00:00.000Z"),
    ).toBe(true);
  });

  it("does not let an unparseable timestamp replace a parsed sample", () => {
    let store = emptyStore("INDORE", "LINE-01");
    store = applyTagUpdate(
      store,
      sample({ tagCode: "REACTOR_TEMP_C", value: 80, timestamp: "2026-11-20T10:00:00.000Z" }),
    );
    store = applyTagUpdate(
      store,
      sample({ tagCode: "REACTOR_TEMP_C", value: 99, timestamp: "not-a-date" }),
    );
    expect(store.tags.REACTOR_TEMP_C.value).toBe(80);
  });

  it("clears alarms when severity is NONE", () => {
    let store = emptyStore("INDORE", "LINE-01");
    store = applyAlarmUpdate(
      store,
      sample({
        tagCode: "REACTOR_TEMP_C",
        severity: "WARNING",
        direction: "HIGH",
        alarmValue: 75,
        alarmTimestamp: "2026-11-20T10:00:00.000Z",
      }),
    );
    expect(Object.keys(store.alarms)).toEqual(["REACTOR_TEMP_C"]);
    store = applyAlarmUpdate(
      store,
      sample({ tagCode: "REACTOR_TEMP_C", severity: "NONE" }),
    );
    expect(store.alarms).toEqual({});
  });

  it("snapshot replace drops tags from another line", () => {
    const store = replaceSnapshot(
      "INDORE",
      "LINE-02",
      [
        sample({ siteId: "INDORE", lineId: "LINE-01", tagCode: "VALVE_101_POS", value: 40 }),
        sample({ siteId: "INDORE", lineId: "LINE-02", tagCode: "VALVE_201_POS", value: 55 }),
      ],
      [
        sample({
          siteId: "INDORE",
          lineId: "LINE-01",
          tagCode: "REACTOR_TEMP_C",
          severity: "CRITICAL",
        }),
      ],
    );
    expect(Object.keys(store.tags)).toEqual(["VALVE_201_POS"]);
    expect(store.alarms).toEqual({});
  });

  it("ignores a stale LINE-01 snapshot after generation moves to LINE-02", () => {
    let activeGeneration = 1;
    let store: TelemetryStore = replaceSnapshot(
      "INDORE",
      "LINE-01",
      [sample({ tagCode: "VALVE_101_POS", value: 12 })],
      [
        sample({
          tagCode: "REACTOR_TEMP_C",
          severity: "WARNING",
          direction: "HIGH",
          alarmValue: 72,
        }),
      ],
    );

    activeGeneration = 2;
    store = emptyStore("INDORE", "LINE-02");

    const staleGeneration = 1;
    const staleSnapshot = replaceSnapshot(
      "INDORE",
      "LINE-01",
      [sample({ tagCode: "VALVE_101_POS", value: 99 })],
      [
        sample({
          tagCode: "REACTOR_TEMP_C",
          severity: "CRITICAL",
          direction: "HIGH",
          alarmValue: 90,
        }),
      ],
    );

    if (staleGeneration === activeGeneration) {
      store = staleSnapshot;
    }

    expect(store.siteId).toBe("INDORE");
    expect(store.lineId).toBe("LINE-02");
    expect(store.tags).toEqual({});
    expect(store.alarms).toEqual({});
    expect(Object.keys(staleSnapshot.tags)).toContain("VALVE_101_POS");
  });

  it("treats 0 as a present value and null as missing", () => {
    expect(hasSampleValue(0)).toBe(true);
    expect(hasSampleValue(null)).toBe(false);
    expect(hasSampleValue(undefined)).toBe(false);
    expect(formatValue(0, "%")).toBe("0 %");
    expect(formatValue(null, "%")).toBe("");
  });

  it("merges registry metadata with unregistered live tags", () => {
    const registry: TagMapping[] = [
      {
        id: "1",
        siteId: "INDORE",
        lineId: "LINE-01",
        tagCode: "VALVE_101_POS",
        description: "Inlet valve position",
        protocol: "OPC_UA",
        endpointUrl: "opc.tcp://localhost:53530/OPCUA/SimulationServer",
        externalAddress: "ns=3;i=1001",
        dataType: "FLOAT",
        unit: "%",
        writable: true,
        minValue: 0,
        maxValue: 100,
        deadband: 0.5,
        warningLow: null,
        warningHigh: null,
        criticalLow: null,
        criticalHigh: null,
        interlockRuleKey: null,
      },
    ];
    const live = {
      VALVE_101_POS: sample({ tagCode: "VALVE_101_POS", value: 0 }),
      ORPHAN_TAG: sample({ tagCode: "ORPHAN_TAG", value: 1 }),
    };
    const rows = mergeRows(registry, live);
    expect(rows).toHaveLength(2);

    const valve = rows.find((row) => row.tagCode === "VALVE_101_POS")!;
    expect(valve.unit).toBe("%");
    expect(valve.deadband).toBe(0.5);
    expect(valve.description).toBe("Inlet valve position");
    expect(valve.hasSample).toBe(true);
    expect(formatValue(valve.value, valve.unit)).toBe("0 %");

    const orphan = rows.find((row) => row.tagCode === "ORPHAN_TAG")!;
    expect(orphan.registered).toBe(false);
    expect(orphan.unit).toBe("");
    expect(orphan.deadband).toBeNull();
    expect(orphan.description).toBe("");
  });
});
