import { describe, expect, it } from "vitest";
import {
  buildAuditQuery,
  buildCommandBody,
  isCommandStatus,
  nextAuditPageOnFilterChange,
  parseCommandError,
  patchAuditRow,
} from "@/lib/commands";
import { validateTagBounds } from "@/lib/registryBounds";
import {
  classifyStreamFailure,
  nextBackoffMs,
  shouldScheduleRetry,
} from "@/lib/streamSession";
import {
  canSubmitCommand,
  emptyTargetDraft,
  isTargetReady,
  resolveTargetValue,
} from "@/lib/targetValue";
import { mergeRows } from "@/lib/telemetryState";
import type {
  CommandAuditResponse,
  CommandUpdateEvent,
  LatestTagState,
  TagMapping,
} from "@/lib/types";

function tag(partial: Partial<TagMapping> = {}): TagMapping {
  return {
    id: "1",
    siteId: "INDORE",
    lineId: "LINE-01",
    tagCode: "VALVE_101_POS",
    description: "Inlet valve",
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
    ...partial,
  };
}

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

describe("SF-M18 row merge writable bounds", () => {
  it("carries writable, tagId, dataType, and bounds onto TelemetryRow", () => {
    const rows = mergeRows(
      [tag({ id: "tag-9", minValue: 5, maxValue: 90, writable: true })],
      {
        VALVE_101_POS: sample({
          tagCode: "VALVE_101_POS",
          value: 0,
          observedDataType: "INT",
          nodeWritable: false,
        }),
      },
    );
    const row = rows[0];
    expect(row.tagId).toBe("tag-9");
    expect(row.writable).toBe(true);
    expect(row.dataType).toBe("FLOAT");
    expect(row.observedDataType).toBe("INT");
    expect(row.nodeWritable).toBe(false);
    expect(row.minValue).toBe(5);
    expect(row.maxValue).toBe(90);
  });
});

describe("SF-M18 command body and errors", () => {
  it("trims command body fields", () => {
    expect(
      buildCommandBody({
        siteId: " INDORE ",
        lineId: " LINE-01 ",
        tagId: "uuid",
        targetValue: 0,
        requestedBy: " op@x.com ",
        reason: " open ",
      }),
    ).toEqual({
      siteId: "INDORE",
      lineId: "LINE-01",
      tagId: "uuid",
      targetValue: 0,
      requestedBy: "op@x.com",
      reason: "open",
    });
  });

  it("parses structured command errors", () => {
    const error = parseCommandError({
      code: "TARGET_OUT_OF_RANGE",
      message: "out of range",
      commandId: "c1",
      fieldErrors: [{ field: "targetValue", message: "too high" }],
    });
    expect(error.code).toBe("TARGET_OUT_OF_RANGE");
    expect(error.fieldErrors[0].field).toBe("targetValue");
  });

  it("allow-lists command statuses", () => {
    expect(isCommandStatus("VALIDATED_DISPATCHED")).toBe(true);
    expect(isCommandStatus("PENDING")).toBe(false);
    expect(isCommandStatus("REJECTED")).toBe(false);
  });
});

describe("SF-M18 target readiness", () => {
  it("accepts 0, false, and empty string without truthiness traps", () => {
    expect(isTargetReady({ kind: "FLOAT", text: "0" })).toBe(true);
    expect(isTargetReady({ kind: "INT", text: "0" })).toBe(true);
    expect(isTargetReady({ kind: "BOOLEAN", value: false })).toBe(true);
    expect(isTargetReady({ kind: "STRING", text: "" })).toBe(true);
    expect(resolveTargetValue({ kind: "FLOAT", text: "0" })).toBe(0);
    expect(resolveTargetValue({ kind: "BOOLEAN", value: false })).toBe(false);
  });

  it("rejects blank numbers and unset boolean", () => {
    expect(isTargetReady({ kind: "FLOAT", text: "" })).toBe(false);
    expect(isTargetReady({ kind: "INT", text: "  " })).toBe(false);
    expect(isTargetReady(emptyTargetDraft("BOOLEAN"))).toBe(false);
    expect(
      canSubmitCommand({
        draft: { kind: "FLOAT", text: "0" },
        requestedBy: "op",
        reason: "note",
        confirmed: true,
      }),
    ).toBe(true);
    expect(
      canSubmitCommand({
        draft: { kind: "FLOAT", text: "0" },
        requestedBy: "",
        reason: "note",
        confirmed: true,
      }),
    ).toBe(false);
  });
});

describe("SF-M18 audit query and patches", () => {
  it("builds query and resets page on filter change", () => {
    expect(buildAuditQuery({ siteId: "INDORE", page: 2, size: 50 })).toContain(
      "page=2",
    );
    expect(
      nextAuditPageOnFilterChange(
        { siteId: "INDORE", status: "ACKED", page: 3 },
        { siteId: "INDORE", status: "FAILED_EDGE", page: 3 },
      ),
    ).toBe(0);
    expect(
      nextAuditPageOnFilterChange(
        { siteId: "INDORE", page: 2 },
        { siteId: "INDORE", page: 2 },
      ),
    ).toBe(2);
  });

  it("patches visible rows and drops status-filter mismatches", () => {
    const rows: CommandAuditResponse[] = [
      {
        commandId: "c1",
        siteId: "INDORE",
        lineId: "LINE-01",
        tagCode: "VALVE_101_POS",
        requestedValue: "50",
        previousValue: null,
        requestedBy: "op",
        reason: "test",
        status: "VALIDATED_DISPATCHED",
        rejectionReason: null,
        appliedValue: null,
        createdAt: "2026-11-20T10:00:00.000Z",
        ackedAt: null,
      },
      {
        commandId: "c2",
        siteId: "INDORE",
        lineId: "LINE-01",
        tagCode: "REACTOR_TEMP_C",
        requestedValue: "80",
        previousValue: null,
        requestedBy: "op",
        reason: "test",
        status: "VALIDATED_DISPATCHED",
        rejectionReason: null,
        appliedValue: null,
        createdAt: "2026-11-20T10:01:00.000Z",
        ackedAt: null,
      },
    ];
    const ack: CommandUpdateEvent = {
      commandId: "c1",
      siteId: "INDORE",
      lineId: "LINE-01",
      tagCode: "VALVE_101_POS",
      status: "ACKED",
      appliedValue: 50,
      errorMessage: null,
      executedAt: "2026-11-20T10:00:01.000Z",
    };
    const patched = patchAuditRow(rows, ack, null);
    expect(patched[0].status).toBe("ACKED");
    expect(patched[0].appliedValue).toBe("50");
    expect(patched[0].ackedAt).toBe("2026-11-20T10:00:01.000Z");

    const ignored = patchAuditRow(rows, { ...ack, commandId: "missing" }, null);
    expect(ignored).toEqual(rows);

    const dropped = patchAuditRow(rows, ack, "VALIDATED_DISPATCHED");
    expect(dropped.map((row) => row.commandId)).toEqual(["c2"]);
  });
});

describe("SF-M18 stream backoff and capacity", () => {
  it("uses stepped backoff and pauses while hidden", () => {
    expect(nextBackoffMs(0)).toBe(3000);
    expect(nextBackoffMs(1)).toBe(6000);
    expect(nextBackoffMs(2)).toBe(12000);
    expect(nextBackoffMs(9)).toBe(30000);
    expect(shouldScheduleRetry("visible")).toBe(true);
    expect(shouldScheduleRetry("hidden")).toBe(false);
  });

  it("maps STREAM_AT_CAPACITY to at-capacity", () => {
    expect(
      classifyStreamFailure(503, { code: "STREAM_AT_CAPACITY", message: "full" }),
    ).toBe("at-capacity");
    expect(classifyStreamFailure(502, null)).toBe("reconnecting");
  });
});

describe("SF-M18 registry bounds", () => {
  it("mirrors TagBoundsValidator nesting rules", () => {
    expect(
      validateTagBounds({
        minValue: 10,
        maxValue: 5,
        deadband: 0,
        warningLow: null,
        warningHigh: null,
        criticalLow: null,
        criticalHigh: null,
      }),
    ).toEqual([
      {
        field: "minValue",
        message: "minValue must be less than or equal to maxValue",
      },
    ]);
    expect(
      validateTagBounds({
        minValue: 0,
        maxValue: 100,
        deadband: -1,
        warningLow: 20,
        warningHigh: 80,
        criticalLow: 25,
        criticalHigh: 90,
      }).map((issue) => issue.field),
    ).toEqual(["deadband", "criticalLow"]);
  });
});
