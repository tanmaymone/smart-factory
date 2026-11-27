import { applyTagUpdate, emptyStore, type TelemetryStore } from "@/lib/telemetryState";
import type { LatestTagState } from "@/lib/types";

export function mergeSmokeSample(
  store: TelemetryStore,
  sample: LatestTagState,
  captured: { generation: number; siteId: string; lineId: string },
  currentGeneration: number,
): TelemetryStore {
  if (captured.generation !== currentGeneration) return store;
  if (sample.siteId !== captured.siteId || sample.lineId !== captured.lineId) return store;
  return applyTagUpdate(store, sample);
}

export function mergeSmokeLatest(
  store: TelemetryStore,
  latest: LatestTagState[],
  captured: { generation: number; siteId: string; lineId: string },
  currentGeneration: number,
): TelemetryStore {
  if (captured.generation !== currentGeneration) return store;
  if (latest.length === 0) return store;
  return latest.reduce(
    (next, sample) => mergeSmokeSample(next, sample, captured, currentGeneration),
    store.siteId === captured.siteId ? store : emptyStore(captured.siteId, captured.lineId),
  );
}
