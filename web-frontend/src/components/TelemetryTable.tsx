"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { TelemetryQualityBadge } from "@/components/TelemetryQualityBadge";
import { TelemetryValueCell } from "@/components/TelemetryValueCell";
import { formatDeadband, formatTimestamp } from "@/lib/format";
import type { RegistryLifecycle, TelemetryRow } from "@/lib/types";

function severityVariant(severity: string): "emerald" | "amber" | "rose" | "slate" {
  if (severity === "CRITICAL") return "rose";
  if (severity === "WARNING") return "amber";
  if (severity === "NONE") return "slate";
  return "slate";
}

export function TelemetryTable({
  rows,
  onWrite,
  siteLifecycle,
  lineLifecycle,
  isWriteEnabled,
}: {
  rows: TelemetryRow[];
  onWrite?: (row: TelemetryRow) => void;
  siteLifecycle?: RegistryLifecycle;
  lineLifecycle?: RegistryLifecycle;
  isWriteEnabled?: (row: TelemetryRow) => boolean;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Live Telemetry</CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Tag</TableHead>
              <TableHead>Value</TableHead>
              <TableHead>Timestamp</TableHead>
              <TableHead>Quality</TableHead>
              <TableHead>Deadband</TableHead>
              <TableHead>Severity</TableHead>
              <TableHead className="w-[5.5rem]">Write</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="py-8 text-center text-slate-400">
                  No tags for this site and line yet.
                </TableCell>
              </TableRow>
            ) : (
              rows.map((row) => (
                <TableRow key={row.tagCode}>
                  <TableCell>
                    <div className="flex flex-col gap-0.5">
                      <span className="mono font-mono text-sm tabular-nums text-slate-100">
                        {row.tagCode}
                      </span>
                      <span className="text-xs text-slate-400">
                        {row.description || (row.registered ? "" : "Unregistered")}
                      </span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <TelemetryValueCell
                      value={row.value}
                      unit={row.unit}
                      hasSample={row.hasSample}
                    />
                  </TableCell>
                  <TableCell className="mono font-mono tabular-nums text-slate-300">
                    {formatTimestamp(row.timestamp)}
                  </TableCell>
                  <TableCell>
                    <TelemetryQualityBadge
                      row={row}
                      siteLifecycle={siteLifecycle}
                      lineLifecycle={lineLifecycle}
                    />
                  </TableCell>
                  <TableCell className="mono font-mono tabular-nums text-slate-300">
                    {formatDeadband(row.deadband)}
                  </TableCell>
                  <TableCell>
                    <Badge variant={severityVariant(row.severity)}>{row.severity}</Badge>
                  </TableCell>
                  <TableCell>
                    {row.registered && row.writable && (isWriteEnabled?.(row) ?? true) ? (
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => onWrite?.(row)}
                      >
                        Write
                      </Button>
                    ) : (
                      <span className="text-slate-600">—</span>
                    )}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
