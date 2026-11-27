"use client";

import { useEffect, useState } from "react";
import { mergeSmokeLatest, mergeSmokeSample } from "@/lib/onboardingSmoke";
import { connectTelemetryStream } from "@/lib/streamSession";
import { emptyStore, type TelemetryStore } from "@/lib/telemetryState";
import type { LatestTagState } from "@/lib/types";

export function useOnboardingSmokeStream(
  enabled: boolean,
  siteId: string,
  lineId: string,
  generation: number,
): TelemetryStore {
  const [store, setStore] = useState<TelemetryStore>(() => emptyStore(siteId, lineId));

  useEffect(() => {
    if (!enabled) return;
    const captured = { generation, siteId, lineId };
    const controller = new AbortController();
    const session = connectTelemetryStream(siteId, lineId, {
      onStatus: () => undefined,
      onTag: (data) => {
        const sample = JSON.parse(data) as LatestTagState;
        setStore((prev) => mergeSmokeSample(prev, sample, captured, generation));
      },
    });
    const params = new URLSearchParams({ siteId, lineId });
    void fetch(`/api/telemetry/latest?${params}`, { signal: controller.signal, cache: "no-store" })
      .then((response) => (response.ok ? response.json() : []))
      .then((latest: LatestTagState[]) => {
        if (controller.signal.aborted) return;
        setStore((prev) => mergeSmokeLatest(prev, latest, captured, generation));
      })
      .catch(() => undefined);
    return () => {
      controller.abort();
      session.abort();
    };
  }, [enabled, siteId, lineId, generation]);

  return store;
}
