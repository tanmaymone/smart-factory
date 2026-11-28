"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { AlarmBanner } from "@/components/AlarmBanner";
import { CatalogNotice } from "@/components/CatalogNotice";
import {
  ConsoleHeader,
  ConsoleHeaderFallback,
} from "@/components/ConsoleHeader";
import { TelemetryTable } from "@/components/TelemetryTable";
import { WriteCommandModal } from "@/components/WriteCommandModal";
import { useTelemetryStream } from "@/hooks/useTelemetryStream";
import { useSiteLineSelection } from "@/hooks/useSiteLineSelection";
import { useAuth } from "@/components/AuthProvider";
import { canWriteCommands } from "@/lib/authPolicy";
import { consoleMountState } from "@/lib/catalogSelection";
import { isRegistryLive } from "@/lib/registryLiveness";
import type { TelemetryRow } from "@/lib/types";

export function LiveConsole() {
  const {
    selection,
    catalog,
    sites,
    replaceSelection,
    catalogLoading,
    catalogError,
    hasValidLineSelection,
    refreshCatalog,
  } = useSiteLineSelection();
  const mount = consoleMountState({ catalogLoading, catalogError, hasValidLineSelection });
  const site = sites.find((entry) => entry.id === selection.siteId);
  const line = site?.lines.find((entry) => entry.id === selection.lineId);
  const parentsActive = site?.lifecycle === "ACTIVE" && line?.lifecycle === "ACTIVE";

  return (
    <div className="flex flex-col gap-4">
      <ConsoleHeader
        title="Live Telemetry & Alarm Console"
        subtitle={
          <>
            {selection.site?.name ?? "—"} · {selection.line?.name ?? "—"}
          </>
        }
        catalog={catalog}
        siteId={selection.siteId}
        lineId={selection.lineId}
        onSiteChange={(siteId) => replaceSelection(siteId, selection.lineId)}
        onLineChange={(lineId) => replaceSelection(selection.siteId, lineId)}
      />
      <CatalogNotice
        catalogLoading={catalogLoading}
        catalogError={catalogError}
        hasValidLineSelection={hasValidLineSelection}
        siteId={selection.siteId}
        onRetry={() => void refreshCatalog()}
      />
      {mount === "body" && !parentsActive ? (
        <p className="rounded-lg border border-slate-800 bg-slate-900 px-4 py-6 text-sm text-slate-300">
          Pending Activation.
          <Link className="ml-2 text-cyan-400" href={`/onboarding?siteId=${selection.siteId}&lineId=${selection.lineId}`}>
            Open Onboarding
          </Link>
        </p>
      ) : null}
      {mount === "body" && parentsActive && site && line ? (
        <LiveConsoleBody
          siteId={selection.siteId}
          lineId={selection.lineId}
          siteLifecycle={site.lifecycle}
          lineLifecycle={line.lifecycle}
        />
      ) : null}
    </div>
  );
}

function LiveConsoleBody({
  siteId,
  lineId,
  siteLifecycle,
  lineLifecycle,
}: {
  siteId: string;
  lineId: string;
  siteLifecycle?: "DRAFT" | "VERIFIED" | "ACTIVE";
  lineLifecycle?: "DRAFT" | "VERIFIED" | "ACTIVE";
}) {
  const [writeRow, setWriteRow] = useState<TelemetryRow | null>(null);
  const { state: authState } = useAuth();
  const stream = useTelemetryStream(siteId, lineId);
  const writesAllowed =
    authState.status === "authenticated" && canWriteCommands(authState.user.role);
  const rows = useMemo(
    () => stream.rows.filter((row) => isRegistryLive({
      siteLifecycle,
      lineLifecycle,
      tagLifecycle: row.lifecycle,
    })),
    [stream.rows, siteLifecycle, lineLifecycle],
  );
  const hidden = stream.rows.length - rows.length;
  const liveWriteRow = writeRow == null ? null : (stream.rows.find((row) => row.tagCode === writeRow.tagCode) ?? writeRow);
  return (
    <>
      {hidden > 0 ? <p className="text-xs text-slate-400">{hidden} tags not activated</p> : null}
      <AlarmBanner alarms={stream.alarms} />
      <TelemetryTable
        rows={rows}
        siteLifecycle={siteLifecycle}
        lineLifecycle={lineLifecycle}
        isWriteEnabled={(row) =>
          writesAllowed &&
          isRegistryLive({ siteLifecycle, lineLifecycle, tagLifecycle: row.lifecycle })}
        onWrite={writesAllowed ? setWriteRow : undefined}
      />
      <WriteCommandModal
        open={writeRow != null}
        row={writeRow}
        siteId={siteId}
        lineId={lineId}
        liveRow={liveWriteRow}
        lastCommandEvent={stream.lastCommandEvent}
        onOpenChange={(open) => { if (!open) setWriteRow(null); }}
      />
    </>
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
