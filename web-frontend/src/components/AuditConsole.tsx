"use client";

import { useEffect, useState } from "react";
import { AuditFilters } from "@/components/audit/AuditFilters";
import { AuditLogTable } from "@/components/audit/AuditLogTable";
import {
  ConsoleHeader,
  ConsoleHeaderFallback,
} from "@/components/ConsoleHeader";
import { Button } from "@/components/ui/button";
import {
  fetchCommandAudit,
  isCommandStatus,
  nextAuditPageOnFilterChange,
  patchAuditRow,
} from "@/lib/commands";
import { CatalogNotice } from "@/components/CatalogNotice";
import { useSiteLineSelection } from "@/hooks/useSiteLineSelection";
import { consoleMountState } from "@/lib/catalogSelection";
import { connectTelemetryStream } from "@/lib/streamSession";
import type { CommandAuditResponse, ConnectionStatus } from "@/lib/types";

const PAGE_SIZE = 50;

export function AuditConsole() {
  const {
    selection,
    catalog,
    replaceSelection,
    catalogLoading,
    catalogError,
    hasValidLineSelection,
    refreshCatalog,
  } = useSiteLineSelection();
  const [tagCode, setTagCode] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<CommandAuditResponse[]>([]);
  const [errorCode, setErrorCode] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [streamStatus, setStreamStatus] =
    useState<ConnectionStatus>("connecting");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (catalogLoading || catalogError || !hasValidLineSelection) return;
    const query = {
      siteId: selection.siteId,
      lineId: selection.lineId,
      tagCode: tagCode.trim() || undefined,
      status: statusFilter || undefined,
      page,
      size: PAGE_SIZE,
    };
    const controller = new AbortController();
    setLoading(true);
    void fetchCommandAudit(query, controller.signal)
      .then((data) => {
        setRows(data);
        setErrorCode(null);
        setErrorMessage(null);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        const err = error as { code?: string; message?: string };
        setRows([]);
        setErrorCode(err.code ?? "AUDIT_FETCH_FAILED");
        setErrorMessage(err.message ?? "Failed to load audit log");
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [selection.siteId, selection.lineId, tagCode, statusFilter, page, catalogLoading, catalogError, hasValidLineSelection]);

  useEffect(() => {
    if (catalogLoading || catalogError || !hasValidLineSelection) return;
    const session = connectTelemetryStream(
      selection.siteId,
      selection.lineId,
      {
        onStatus: setStreamStatus,
        onCommand: (event) => {
          if (!isCommandStatus(event.status)) {
            return;
          }
          setRows((prev) =>
            patchAuditRow(prev, event, statusFilter || null),
          );
        },
      },
    );
    return () => session.abort();
  }, [selection.siteId, selection.lineId, statusFilter, catalogLoading, catalogError, hasValidLineSelection]);

  const applyFilter = (nextTag: string, nextStatus: string) => {
    const nextPage = nextAuditPageOnFilterChange(
      {
        siteId: selection.siteId,
        lineId: selection.lineId,
        tagCode: tagCode.trim() || undefined,
        status: statusFilter || undefined,
        page,
      },
      {
        siteId: selection.siteId,
        lineId: selection.lineId,
        tagCode: nextTag.trim() || undefined,
        status: nextStatus || undefined,
        page,
      },
    );
    setTagCode(nextTag);
    setStatusFilter(nextStatus);
    setPage(nextPage);
  };

  return (
    <div className="flex flex-col gap-4">
      <ConsoleHeader
        title="Command Audit Logbook"
        subtitle={
          <>
            {selection.site.name} · {selection.line.name}
            {loading ? " · Loading…" : null}
          </>
        }
        catalog={catalog}
        siteId={selection.siteId}
        lineId={selection.lineId}
        onSiteChange={(siteId) => {
          setPage(0);
          replaceSelection(siteId, selection.lineId);
        }}
        onLineChange={(lineId) => {
          setPage(0);
          replaceSelection(selection.siteId, lineId);
        }}
        status={streamStatus}
      />
      <CatalogNotice
        catalogLoading={catalogLoading}
        catalogError={catalogError}
        hasValidLineSelection={hasValidLineSelection}
        siteId={selection.siteId}
        onRetry={() => void refreshCatalog()}
      />
      {consoleMountState({ catalogLoading, catalogError, hasValidLineSelection }) === "body" ? (
      <>
      <AuditFilters
        tagCode={tagCode}
        status={statusFilter}
        onTagCodeChange={(value) => applyFilter(value, statusFilter)}
        onStatusChange={(value) => applyFilter(tagCode, value)}
      />

      <AuditLogTable
        rows={rows}
        errorCode={errorCode}
        errorMessage={errorMessage}
      />

      <div className="flex items-center justify-between gap-2">
        <p className="mono font-mono text-xs tabular-nums text-slate-500">
          Page {page} · size {PAGE_SIZE}
        </p>
        <div className="flex gap-2">
          <Button
            variant="secondary"
            size="sm"
            disabled={page <= 0}
            onClick={() => setPage((prev) => Math.max(0, prev - 1))}
          >
            Previous
          </Button>
          <Button
            variant="secondary"
            size="sm"
            disabled={rows.length < PAGE_SIZE}
            onClick={() => setPage((prev) => prev + 1)}
          >
            Next
          </Button>
        </div>
      </div>
      </>
      ) : null}
    </div>
  );
}

export function AuditConsoleFallback() {
  return (
    <div className="flex flex-col gap-4">
      <ConsoleHeaderFallback title="Command Audit Logbook" />
      <div className="rounded-lg border border-slate-800 bg-slate-900 px-4 py-8 text-center text-sm text-slate-400">
        Loading audit log…
      </div>
    </div>
  );
}
