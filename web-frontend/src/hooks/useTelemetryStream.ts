"use client";

import { useEffect, useRef, useState } from "react";
import { isCommandStatus } from "@/lib/commands";
import {
  activeAlarms,
  applyAlarmUpdate,
  applyTagUpdate,
  emptyStore,
  mergeRows,
  replaceSnapshot,
  type TelemetryStore,
} from "@/lib/telemetryState";
import { connectTelemetryStream } from "@/lib/streamSession";
import type {
  CommandUpdateEvent,
  ConnectionStatus,
  LatestTagState,
  TagMapping,
  TelemetryRow,
} from "@/lib/types";

let connectionCounter = 0;
let snapshotCounter = 0;

function nextConnectionId(): number {
  connectionCounter += 1;
  return connectionCounter;
}

function nextSnapshotId(): number {
  snapshotCounter += 1;
  return snapshotCounter;
}

async function fetchJson<T>(url: string, signal: AbortSignal): Promise<T> {
  const response = await fetch(url, { signal, cache: "no-store" });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return (await response.json()) as T;
}

export type TelemetryStreamResult = {
  status: ConnectionStatus;
  registry: TagMapping[];
  rows: TelemetryRow[];
  alarms: LatestTagState[];
  generation: number;
  lastCommandEvent: CommandUpdateEvent | null;
};

export function useTelemetryStream(
  siteId: string,
  lineId: string,
): TelemetryStreamResult {
  const [status, setStatus] = useState<ConnectionStatus>("connecting");
  const [registry, setRegistry] = useState<TagMapping[]>([]);
  const [store, setStore] = useState<TelemetryStore>(() => emptyStore(siteId, lineId));
  const [generation, setGeneration] = useState(0);
  const [lastCommandEvent, setLastCommandEvent] =
    useState<CommandUpdateEvent | null>(null);
  const connectionRef = useRef(0);
  const snapshotRef = useRef(0);

  useEffect(() => {
    const connectionId = nextConnectionId();
    connectionRef.current = connectionId;
    setGeneration(connectionId);
    setStatus("connecting");
    setStore(emptyStore(siteId, lineId));
    setRegistry([]);
    setLastCommandEvent(null);

    const controller = new AbortController();
    const isActiveConnection = () => connectionRef.current === connectionId;

    const loadSnapshot = async (signal: AbortSignal) => {
      const snapshotId = nextSnapshotId();
      snapshotRef.current = snapshotId;
      const params = new URLSearchParams({ siteId, lineId });
      const [latest, alarms, tags] = await Promise.all([
        fetchJson<LatestTagState[]>(`/api/telemetry/latest?${params}`, signal),
        fetchJson<LatestTagState[]>(`/api/telemetry/alarms?${params}`, signal),
        fetchJson<TagMapping[]>(`/api/registry/tags?${params}`, signal),
      ]);
      if (!isActiveConnection() || snapshotRef.current !== snapshotId) {
        return;
      }
      setRegistry(tags);
      setStore((prev) => {
        const next = replaceSnapshot(siteId, lineId, latest, alarms);
        let merged = next;
        for (const live of Object.values(prev.tags)) {
          if (live.siteId !== siteId || live.lineId !== lineId) {
            continue;
          }
          merged = applyTagUpdate(merged, live);
        }
        for (const live of Object.values(prev.alarms)) {
          if (live.siteId !== siteId || live.lineId !== lineId) {
            continue;
          }
          merged = applyAlarmUpdate(merged, live);
        }
        return merged;
      });
    };

    let session: ReturnType<typeof connectTelemetryStream> | null = null;
    let cancelled = false;

    void (async () => {
      try {
        await loadSnapshot(controller.signal);
      } catch (error) {
        if (controller.signal.aborted || !isActiveConnection()) {
          return;
        }
        console.warn("initial snapshot failed", error);
      }
      if (cancelled || !isActiveConnection() || controller.signal.aborted) {
        return;
      }

      session = connectTelemetryStream(siteId, lineId, {
        onStatus: (next) => {
          if (!isActiveConnection()) {
            return;
          }
          setStatus(next);
          if (next === "live") {
            void loadSnapshot(controller.signal).catch((error: unknown) => {
              if (controller.signal.aborted || !isActiveConnection()) {
                return;
              }
              console.warn("snapshot refetch failed", error);
            });
          }
        },
        onTag: (data) => {
          if (!isActiveConnection()) {
            return;
          }
          try {
            const sample = JSON.parse(data) as LatestTagState;
            if (sample.siteId !== siteId || sample.lineId !== lineId) {
              return;
            }
            setStore((prev) => applyTagUpdate(prev, sample));
          } catch {
            // ignore
          }
        },
        onAlarm: (data) => {
          if (!isActiveConnection()) {
            return;
          }
          try {
            const sample = JSON.parse(data) as LatestTagState;
            if (sample.siteId !== siteId || sample.lineId !== lineId) {
              return;
            }
            setStore((prev) => applyAlarmUpdate(prev, sample));
          } catch {
            // ignore
          }
        },
        onCommand: (event) => {
          if (!isActiveConnection()) {
            return;
          }
          if (!isCommandStatus(event.status)) {
            return;
          }
          setLastCommandEvent(event);
        },
      });
    })();

    return () => {
      cancelled = true;
      controller.abort();
      session?.abort();
    };
  }, [siteId, lineId]);

  return {
    status,
    registry,
    rows: mergeRows(registry, store.tags),
    alarms: activeAlarms(store),
    generation,
    lastCommandEvent,
  };
}
