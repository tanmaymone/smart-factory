"use client";

import { formatValue } from "@/lib/format";
import type { TelemetryRow } from "@/lib/types";
import { cn } from "@/lib/utils";

export type WriteSnapshot = {
  value: unknown;
  quality: string | null;
  minValue: number | null;
  maxValue: number | null;
  unit: string;
};

export function WriteSnapshotPanel({
  snapshot,
  liveDrift,
}: {
  snapshot: WriteSnapshot | null;
  liveDrift: TelemetryRow | null;
}) {
  const boundsLabel =
    snapshot?.minValue != null || snapshot?.maxValue != null
      ? `${snapshot?.minValue ?? "—"} … ${snapshot?.maxValue ?? "—"}`
      : "unbounded";

  return (
    <div className="rounded-md border border-slate-800 bg-slate-950/50 px-3 py-2">
      <p className="text-xs uppercase tracking-wide text-slate-500">
        Snapshot at open
      </p>
      <p className="mono font-mono tabular-nums text-slate-100">
        {formatValue(snapshot?.value, snapshot?.unit ?? "") || "—"}
        {" · quality "}
        {snapshot?.quality ?? "—"}
      </p>
      <p className="mono font-mono tabular-nums text-slate-400">
        Bounds {boundsLabel}
      </p>
      {liveDrift ? (
        <p
          className={cn(
            "mt-1 mono font-mono tabular-nums",
            liveDrift.quality === "BAD" || liveDrift.quality === "UNCERTAIN"
              ? "text-amber-400"
              : "text-slate-300",
          )}
        >
          Live now {formatValue(liveDrift.value, liveDrift.unit) || "—"} ·{" "}
          {liveDrift.quality ?? "—"}
        </p>
      ) : null}
    </div>
  );
}
