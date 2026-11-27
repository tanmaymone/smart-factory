import { Badge } from "@/components/ui/badge";
import { deriveTelemetryFeedState, feedStateLabel, type FeedState } from "@/lib/telemetryDisplay";
import type { RegistryLifecycle, TelemetryRow } from "@/lib/types";

const VARIANT: Record<FeedState, "slate" | "emerald" | "amber" | "rose"> = {
  pendingActivation: "slate",
  noDataYet: "slate",
  live: "emerald",
  uncertain: "amber",
  bad: "rose",
};

export function TelemetryQualityBadge({
  row,
  siteLifecycle,
  lineLifecycle,
}: {
  row: TelemetryRow;
  siteLifecycle?: RegistryLifecycle;
  lineLifecycle?: RegistryLifecycle;
}) {
  const state = deriveTelemetryFeedState({
    siteLifecycle,
    lineLifecycle,
    tagLifecycle: row.lifecycle,
    hasSample: row.hasSample,
    quality: row.quality,
  });
  return <Badge variant={VARIANT[state]}>{feedStateLabel(state)}</Badge>;
}
