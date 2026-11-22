"use client";

import { formatValue } from "@/lib/format";

type Props = {
  value: unknown;
  unit: string;
  hasSample: boolean;
};

/** Leaf cell so value ticks do not rerender the table chrome. */
export function TelemetryValueCell({ value, unit, hasSample }: Props) {
  if (!hasSample) {
    return <span className="mono font-mono tabular-nums text-slate-500">—</span>;
  }
  return (
    <span className="mono font-mono tabular-nums text-cyan-400">
      {formatValue(value, unit)}
    </span>
  );
}
