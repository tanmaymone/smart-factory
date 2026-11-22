"use client";

import { Badge } from "@/components/ui/badge";
import type { ConnectionStatus } from "@/lib/types";
import { cn } from "@/lib/utils";

const LABELS: Record<ConnectionStatus, string> = {
  connecting: "Connecting",
  live: "Live",
  reconnecting: "Reconnecting",
  disconnected: "Disconnected",
  "at-capacity": "At capacity",
};

const VARIANTS: Record<ConnectionStatus, "emerald" | "amber" | "rose" | "slate"> = {
  connecting: "slate",
  live: "emerald",
  reconnecting: "amber",
  disconnected: "rose",
  "at-capacity": "rose",
};

const GLOW: Record<ConnectionStatus, string> = {
  connecting: "",
  live: "glow-green",
  reconnecting: "glow-amber",
  disconnected: "glow-red",
  "at-capacity": "glow-red",
};

export function ConnectionHealth({ status }: { status: ConnectionStatus }) {
  return (
    <Badge
      variant={VARIANTS[status]}
      className={cn("gap-1.5 font-medium", GLOW[status])}
      data-testid="connection-health"
      data-status={status}
    >
      <span
        className={cn(
          "h-1.5 w-1.5 rounded-full",
          status === "live" && "bg-emerald-400",
          status === "reconnecting" && "bg-amber-400",
          status === "disconnected" && "bg-rose-400",
          status === "connecting" && "bg-slate-400",
          status === "at-capacity" && "bg-rose-400",
        )}
      />
      {LABELS[status]}
    </Badge>
  );
}
