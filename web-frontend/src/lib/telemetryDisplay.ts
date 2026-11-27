import { isRegistryLive } from "@/lib/registryLiveness";
import type { Quality, RegistryLifecycle } from "@/lib/types";

export type FeedState = "pendingActivation" | "noDataYet" | "live" | "uncertain" | "bad";

export function deriveTelemetryFeedState(input: {
  siteLifecycle?: RegistryLifecycle | null;
  lineLifecycle?: RegistryLifecycle | null;
  tagLifecycle?: RegistryLifecycle | null;
  hasSample: boolean;
  quality?: Quality | null;
}): FeedState {
  if (
    !isRegistryLive({
      siteLifecycle: input.siteLifecycle,
      lineLifecycle: input.lineLifecycle,
      tagLifecycle: input.tagLifecycle,
    })
  ) {
    return "pendingActivation";
  }
  if (!input.hasSample) {
    return "noDataYet";
  }
  if (input.quality === "GOOD") {
    return "live";
  }
  if (input.quality === "UNCERTAIN") {
    return "uncertain";
  }
  if (input.quality === "BAD") {
    return "bad";
  }
  return "noDataYet";
}

export function feedStateLabel(state: FeedState): string {
  if (state === "pendingActivation") return "Pending Activation";
  if (state === "noDataYet") return "No Data Yet";
  if (state === "live") return "Live";
  if (state === "uncertain") return "UNCERTAIN";
  return "BAD";
}
