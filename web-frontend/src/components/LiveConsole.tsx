"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { AlarmBanner } from "@/components/AlarmBanner";
import {
  ConsoleHeader,
  ConsoleHeaderFallback,
} from "@/components/ConsoleHeader";
import { TelemetryTable } from "@/components/TelemetryTable";
import { WriteCommandModal } from "@/components/WriteCommandModal";
import { useTelemetryStream } from "@/hooks/useTelemetryStream";
import {
  mergeCatalog,
  resolveSelection,
  SITE_CATALOG,
  type DiscoveredPair,
} from "@/lib/sites";
import type { TelemetryRow } from "@/lib/types";

export function LiveConsole() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const [writeRow, setWriteRow] = useState<TelemetryRow | null>(null);

  const rawSite = searchParams.get("siteId");
  const rawLine = searchParams.get("lineId");
  const selection = resolveSelection(rawSite, rawLine);

  const stream = useTelemetryStream(selection.siteId, selection.lineId);

  const discovered = useMemo<DiscoveredPair[]>(() => {
    return stream.registry.map((tag) => ({
      siteId: tag.siteId,
      lineId: tag.lineId,
    }));
  }, [stream.registry]);

  const catalog = useMemo(
    () => mergeCatalog(SITE_CATALOG, discovered),
    [discovered],
  );

  useEffect(() => {
    if (rawSite === selection.siteId && rawLine === selection.lineId) {
      return;
    }
    const params = new URLSearchParams(searchParams.toString());
    params.set("siteId", selection.siteId);
    params.set("lineId", selection.lineId);
    router.replace(`/?${params.toString()}`);
  }, [rawSite, rawLine, selection.siteId, selection.lineId, router, searchParams]);

  const replaceSelection = (siteId: string, lineId: string) => {
    const next = resolveSelection(siteId, lineId, catalog);
    const params = new URLSearchParams();
    params.set("siteId", next.siteId);
    params.set("lineId", next.lineId);
    router.replace(`/?${params.toString()}`);
  };

  const liveWriteRow =
    writeRow == null
      ? null
      : (stream.rows.find((row) => row.tagCode === writeRow.tagCode) ?? writeRow);

  return (
    <div className="flex flex-col gap-4">
      <ConsoleHeader
        title="Live Telemetry & Alarm Console"
        subtitle={
          <>
            {selection.site.name} · {selection.line.name}
          </>
        }
        catalog={catalog}
        siteId={selection.siteId}
        lineId={selection.lineId}
        onSiteChange={(siteId) => replaceSelection(siteId, selection.lineId)}
        onLineChange={(lineId) => replaceSelection(selection.siteId, lineId)}
        status={stream.status}
      />

      <AlarmBanner alarms={stream.alarms} />
      <TelemetryTable rows={stream.rows} onWrite={setWriteRow} />

      <WriteCommandModal
        open={writeRow != null}
        row={writeRow}
        siteId={selection.siteId}
        lineId={selection.lineId}
        liveRow={liveWriteRow}
        lastCommandEvent={stream.lastCommandEvent}
        onOpenChange={(open) => {
          if (!open) {
            setWriteRow(null);
          }
        }}
      />
    </div>
  );
}

export function LiveConsoleFallback() {
  return (
    <div className="flex flex-col gap-4">
      <ConsoleHeaderFallback title="Live Telemetry & Alarm Console" />
      <div className="rounded-lg border border-slate-800 bg-slate-900 px-4 py-8 text-center text-sm text-slate-400">
        Connecting to control plane…
      </div>
    </div>
  );
}
