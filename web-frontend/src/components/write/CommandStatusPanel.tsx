"use client";

import { Badge } from "@/components/ui/badge";
import type { CommandErrorResponse } from "@/lib/types";
import { cn } from "@/lib/utils";

function statusVariant(
  status: string,
): "cyan" | "emerald" | "amber" | "rose" | "slate" {
  if (status === "VALIDATED_DISPATCHED" || status === "Submitting") {
    return "cyan";
  }
  if (status === "ACKED") {
    return "emerald";
  }
  if (status === "TIMEOUT") {
    return "amber";
  }
  if (
    status === "REJECTED_VALIDATION" ||
    status === "REJECTED_INTERLOCK" ||
    status === "FAILED_EDGE"
  ) {
    return "rose";
  }
  return "slate";
}

export function CommandStatusPanel({
  phase,
  commandId,
  status,
  error,
  blockReason,
}: {
  phase: "idle" | "submitting" | "accepted" | "rejected";
  commandId: string | null;
  status: string | null;
  error: CommandErrorResponse | null;
  blockReason?: { code: string; message: string } | null;
}) {
  if (phase === "idle") {
    if (!blockReason) {
      return null;
    }
    return (
      <div className="rounded-md border border-rose-500/40 bg-rose-500/10 px-3 py-2 text-sm">
        <p className="font-medium text-rose-300">
          <span className="mono font-mono tabular-nums">{blockReason.code}</span>
          {" · "}
          {blockReason.message}
        </p>
      </div>
    );
  }

  return (
    <div
      className={cn(
        "rounded-md border px-3 py-2 text-sm",
        phase === "rejected"
          ? "border-rose-500/40 bg-rose-500/10"
          : "border-slate-700 bg-slate-950/60",
      )}
    >
      <div className="flex flex-wrap items-center gap-2">
        {status ? (
          <Badge variant={statusVariant(status)} className="mono font-mono">
            {status}
          </Badge>
        ) : null}
        {commandId ? (
          <span className="mono font-mono text-xs tabular-nums text-slate-400">
            {commandId}
          </span>
        ) : null}
      </div>
      {error ? (
        <div className="mt-2 space-y-1">
          <p className="font-medium text-rose-300">
            <span className="mono font-mono tabular-nums">{error.code}</span>
            {" · "}
            {error.message}
          </p>
          {error.fieldErrors.length > 0 ? (
            <ul className="list-inside list-disc text-xs text-rose-200/90">
              {error.fieldErrors.map((item) => (
                <li key={`${item.field}:${item.message}`}>
                  <span className="mono font-mono">{item.field}</span>: {item.message}
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
