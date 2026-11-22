"use client";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { commandStatusVariant } from "@/lib/commandStatusUi";
import { formatTimestamp } from "@/lib/format";
import type { CommandAuditResponse } from "@/lib/types";

export function AuditLogTable({
  rows,
  errorCode,
  errorMessage,
}: {
  rows: CommandAuditResponse[];
  errorCode?: string | null;
  errorMessage?: string | null;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Command Audit Logbook</CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        {errorCode ? (
          <div className="border-b border-rose-500/30 bg-rose-500/10 px-4 py-3 text-sm text-rose-300">
            <span className="mono font-mono tabular-nums">{errorCode}</span>
            {errorMessage ? ` · ${errorMessage}` : null}
          </div>
        ) : null}
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Timestamp</TableHead>
              <TableHead>Site / Line</TableHead>
              <TableHead>Tag</TableHead>
              <TableHead>Requested</TableHead>
              <TableHead>Actor</TableHead>
              <TableHead>Outcome</TableHead>
              <TableHead>ACK</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="py-8 text-center text-slate-400">
                  No audit entries for this filter.
                </TableCell>
              </TableRow>
            ) : (
              rows.map((row) => (
                <TableRow key={row.commandId}>
                  <TableCell className="mono font-mono tabular-nums text-slate-300">
                    {formatTimestamp(row.createdAt)}
                  </TableCell>
                  <TableCell className="mono font-mono tabular-nums text-slate-300">
                    {row.siteId}/{row.lineId}
                  </TableCell>
                  <TableCell className="mono font-mono tabular-nums text-slate-100">
                    {row.tagCode}
                  </TableCell>
                  <TableCell className="mono font-mono tabular-nums text-cyan-300">
                    {row.requestedValue}
                  </TableCell>
                  <TableCell className="text-slate-300">{row.requestedBy}</TableCell>
                  <TableCell>
                    <div className="flex flex-col gap-1">
                      <Badge variant={commandStatusVariant(row.status)}>
                        {row.status}
                      </Badge>
                      {row.rejectionReason ? (
                        <span className="text-xs text-rose-300/90">
                          {row.rejectionReason}
                        </span>
                      ) : null}
                    </div>
                  </TableCell>
                  <TableCell className="mono font-mono tabular-nums text-slate-300">
                    <div>{row.appliedValue ?? "—"}</div>
                    <div className="text-xs text-slate-500">
                      {formatTimestamp(row.ackedAt)}
                    </div>
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
