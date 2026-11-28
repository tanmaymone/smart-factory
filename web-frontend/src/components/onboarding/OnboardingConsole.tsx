"use client";

import { ConsoleHeader, ConsoleHeaderFallback } from "@/components/ConsoleHeader";
import { OnboardingWizard } from "@/components/onboarding/OnboardingWizard";
import { PreCutoverChecklistCard } from "@/components/onboarding/PreCutoverChecklistCard";
import { useSiteLineSelection } from "@/hooks/useSiteLineSelection";

export function OnboardingConsole() {
  const catalog = useSiteLineSelection();
  return (
    <div className="flex flex-col gap-4">
      <ConsoleHeader
        title="Plant Onboarding"
        subtitle={
          <>
            {catalog.selection.site?.name ?? "No site selected"} ·{" "}
            {catalog.selection.line?.name ?? "No line selected"}
          </>
        }
        catalog={catalog.catalog}
        siteId={catalog.selection.siteId}
        lineId={catalog.selection.lineId}
        onSiteChange={(siteId) => catalog.replaceSelection(siteId, catalog.selection.lineId)}
        onLineChange={(lineId) => catalog.replaceSelection(catalog.selection.siteId, lineId)}
      />
      <OnboardingWizard
        siteId={catalog.selection.siteId}
        lineId={catalog.selection.lineId}
        sites={catalog.sites}
        refreshCatalog={catalog.refreshCatalog}
        catalogError={catalog.catalogError}
        replaceSelection={catalog.replaceSelection}
      />
      <PreCutoverChecklistCard
        siteId={catalog.selection.siteId}
        lineId={catalog.selection.lineId}
      />
    </div>
  );
}

export function OnboardingConsoleFallback() {
  return (
    <div className="flex flex-col gap-4">
      <ConsoleHeaderFallback title="Plant Onboarding" />
      <p className="text-sm text-slate-400">Loading onboarding…</p>
    </div>
  );
}
