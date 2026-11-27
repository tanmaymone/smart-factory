import { Badge } from "@/components/ui/badge";
import type { RegistryLifecycle } from "@/lib/types";

export function LifecycleBadge({ lifecycle }: { lifecycle?: RegistryLifecycle }) {
  const variant = lifecycle === "ACTIVE" ? "emerald" : lifecycle === "VERIFIED" ? "amber" : "slate";
  return <Badge variant={variant}>{lifecycle ?? "DRAFT"}</Badge>;
}
