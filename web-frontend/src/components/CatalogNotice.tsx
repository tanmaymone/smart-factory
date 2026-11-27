"use client";

import Link from "next/link";
import { consoleMountState } from "@/lib/catalogSelection";
import { Button } from "@/components/ui/button";

export function CatalogNotice({
  catalogLoading,
  catalogError,
  hasValidLineSelection,
  siteId,
  onRetry,
}: {
  catalogLoading: boolean;
  catalogError: boolean;
  hasValidLineSelection: boolean;
  siteId?: string;
  onRetry: () => void;
}) {
  const mount = consoleMountState({ catalogLoading, catalogError, hasValidLineSelection });
  if (mount === "body") return null;
  if (mount === "loading") {
    return <p className="text-sm text-slate-400">Loading plant catalog…</p>;
  }
  if (mount === "error") {
    return (
      <div className="flex items-center justify-between gap-3 rounded-lg border border-rose-500/40 bg-slate-900 px-4 py-3 text-sm text-rose-300">
        <span>The plant catalog could not be loaded.</span>
        <Button size="sm" variant="secondary" onClick={onRetry}>Retry</Button>
      </div>
    );
  }
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900 px-4 py-6 text-sm text-slate-300">
      No lines configured.
      <Link className="ml-2 text-cyan-400" href={`/onboarding?siteId=${encodeURIComponent(siteId ?? "")}`}>
        Open Onboarding
      </Link>
    </div>
  );
}
