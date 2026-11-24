import type {
  AlarmSeverity,
  LatestTagState,
  TagMapping,
  TelemetryRow,
} from "@/lib/types";

export type TelemetryStore = {
  siteId: string;
  lineId: string;
  tags: Record<string, LatestTagState>;
  alarms: Record<string, LatestTagState>;
};

export function emptyStore(siteId: string, lineId: string): TelemetryStore {
  return { siteId, lineId, tags: {}, alarms: {} };
}

export function parseTimestamp(value: string | null | undefined): number | null {
  if (value == null || value === "") {
    return null;
  }
  const ms = Date.parse(value);
  return Number.isNaN(ms) ? null : ms;
}

/** Incoming wins when equal or newer; unparseable never overwrites a parsed sample. */
export function isNewerOrEqual(
  incoming: string | null | undefined,
  existing: string | null | undefined,
): boolean {
  const next = parseTimestamp(incoming);
  const prev = parseTimestamp(existing);
  if (next == null && prev != null) {
    return false;
  }
  if (next == null && prev == null) {
    return true;
  }
  if (next != null && prev == null) {
    return true;
  }
  return next! >= prev!;
}

export function hasSampleValue(value: unknown): boolean {
  return value !== null && value !== undefined;
}

function upsertTag(
  tags: Record<string, LatestTagState>,
  sample: LatestTagState,
): Record<string, LatestTagState> {
  const existing = tags[sample.tagCode];
  if (existing && !isNewerOrEqual(sample.timestamp, existing.timestamp)) {
    return tags;
  }
  return { ...tags, [sample.tagCode]: sample };
}

function applyAlarm(
  alarms: Record<string, LatestTagState>,
  sample: LatestTagState,
): Record<string, LatestTagState> {
  if (sample.severity === "NONE") {
    if (!(sample.tagCode in alarms)) {
      return alarms;
    }
    const next = { ...alarms };
    delete next[sample.tagCode];
    return next;
  }
  const existing = alarms[sample.tagCode];
  if (
    existing &&
    !isNewerOrEqual(sample.alarmTimestamp ?? sample.timestamp, existing.alarmTimestamp ?? existing.timestamp)
  ) {
    return alarms;
  }
  return { ...alarms, [sample.tagCode]: sample };
}

export function applyTagUpdate(
  store: TelemetryStore,
  sample: LatestTagState,
): TelemetryStore {
  if (sample.siteId !== store.siteId || sample.lineId !== store.lineId) {
    return store;
  }
  return {
    ...store,
    tags: upsertTag(store.tags, sample),
    alarms: applyAlarm(store.alarms, sample),
  };
}

export function applyAlarmUpdate(
  store: TelemetryStore,
  sample: LatestTagState,
): TelemetryStore {
  if (sample.siteId !== store.siteId || sample.lineId !== store.lineId) {
    return store;
  }
  return {
    ...store,
    tags: upsertTag(store.tags, sample),
    alarms: applyAlarm(store.alarms, sample),
  };
}

export function replaceSnapshot(
  siteId: string,
  lineId: string,
  latest: LatestTagState[],
  alarms: LatestTagState[],
): TelemetryStore {
  const tags: Record<string, LatestTagState> = {};
  for (const sample of latest) {
    if (sample.siteId !== siteId || sample.lineId !== lineId) {
      continue;
    }
    const existing = tags[sample.tagCode];
    if (!existing || isNewerOrEqual(sample.timestamp, existing.timestamp)) {
      tags[sample.tagCode] = sample;
    }
  }

  const alarmMap: Record<string, LatestTagState> = {};
  for (const sample of alarms) {
    if (sample.siteId !== siteId || sample.lineId !== lineId) {
      continue;
    }
    if (sample.severity === "NONE") {
      continue;
    }
    alarmMap[sample.tagCode] = sample;
  }

  return { siteId, lineId, tags, alarms: alarmMap };
}

export function mergeRows(
  registry: TagMapping[],
  live: Record<string, LatestTagState>,
): TelemetryRow[] {
  const rows: TelemetryRow[] = [];
  const seen = new Set<string>();

  for (const tag of registry) {
    seen.add(tag.tagCode);
    const sample = live[tag.tagCode];
    rows.push(toRow(tag.tagCode, tag, sample));
  }

  for (const sample of Object.values(live)) {
    if (seen.has(sample.tagCode)) {
      continue;
    }
    rows.push(toRow(sample.tagCode, null, sample));
  }

  rows.sort((a, b) => a.tagCode.localeCompare(b.tagCode));
  return rows;
}

function toRow(
  tagCode: string,
  tag: TagMapping | null,
  sample: LatestTagState | undefined,
): TelemetryRow {
  const value = sample?.value;
  return {
    tagId: tag?.id ?? null,
    tagCode,
    description: tag?.description ?? "",
    unit: tag?.unit ?? "",
    deadband: tag != null ? tag.deadband : null,
    registered: tag != null,
    writable: tag?.writable ?? false,
    dataType: tag?.dataType ?? null,
    minValue: tag?.minValue ?? null,
    maxValue: tag?.maxValue ?? null,
    value,
    quality: sample?.quality ?? null,
    timestamp: sample?.timestamp ?? null,
    severity: (sample?.severity ?? "NONE") as AlarmSeverity,
    direction: sample?.direction ?? null,
    alarmFrozen: sample?.alarmFrozen ?? false,
    alarmValue: sample?.alarmValue ?? null,
    alarmTimestamp: sample?.alarmTimestamp ?? null,
    observedDataType: sample?.observedDataType ?? null,
    nodeWritable: sample?.nodeWritable ?? null,
    hasSample: hasSampleValue(value),
  };
}

export function activeAlarms(store: TelemetryStore): LatestTagState[] {
  return Object.values(store.alarms)
    .filter((alarm) => alarm.severity !== "NONE")
    .sort((a, b) => a.tagCode.localeCompare(b.tagCode));
}
