import type { RegistryLifecycle } from "@/lib/types";

export function isRegistryLive(parts: {
  siteLifecycle?: RegistryLifecycle | null;
  lineLifecycle?: RegistryLifecycle | null;
  tagLifecycle?: RegistryLifecycle | null;
}): boolean {
  return (
    parts.siteLifecycle === "ACTIVE" &&
    parts.lineLifecycle === "ACTIVE" &&
    parts.tagLifecycle === "ACTIVE"
  );
}
