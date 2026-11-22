"use client";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatTimestamp, formatValue } from "@/lib/format";
import type { LatestTagState } from "@/lib/types";
import { cn } from "@/lib/utils";

export function AlarmBanner({ alarms }: { alarms: LatestTagState[] }) {
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>Active Alarms</CardTitle>
        <Badge variant={alarms.length > 0 ? "rose" : "slate"}>{alarms.length}</Badge>
      </CardHeader>
      <CardContent className="space-y-2">
        {alarms.length === 0 ? (
          <p className="text-sm text-slate-400">No active alarms for this site and line.</p>
        ) : (
          alarms.map((alarm) => {
            const critical = alarm.severity === "CRITICAL";
            return (
              <div
                key={alarm.tagCode}
                className={cn(
                  "flex flex-wrap items-center justify-between gap-2 rounded-md border border-slate-800 bg-slate-950/60 px-3 py-2",
                  critical && "glow-red border-rose-500/40",
                )}
              >
                <div className="flex flex-wrap items-center gap-2">
                  <span className="mono font-mono text-sm tabular-nums text-slate-100">
                    {alarm.tagCode}
                  </span>
                  <Badge variant={critical ? "rose" : "amber"}>{alarm.severity}</Badge>
                  {alarm.direction ? (
                    <Badge variant="slate">{alarm.direction}</Badge>
                  ) : null}
                  {alarm.alarmFrozen ? <Badge variant="amber">Frozen</Badge> : null}
                </div>
                <div className="flex flex-wrap items-center gap-3 text-sm text-slate-400">
                  <span className="mono font-mono tabular-nums text-cyan-400">
                    {formatValue(alarm.alarmValue ?? alarm.value, "")}
                  </span>
                  <span className="mono font-mono tabular-nums">
                    {formatTimestamp(alarm.alarmTimestamp ?? alarm.timestamp)}
                  </span>
                </div>
              </div>
            );
          })
        )}
      </CardContent>
    </Card>
  );
}
