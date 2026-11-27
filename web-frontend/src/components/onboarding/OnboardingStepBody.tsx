"use client";

import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { parseTagImport } from "@/lib/onboardingTagImport";
import { prosysReactorDrafts } from "@/lib/onboardingTemplates";
import {
  bumpFieldEdit,
  bumpStep3Edit,
  invalidateFromStep1,
  invalidateFromStep2,
  type WizardState,
} from "@/lib/onboardingWizardState";

export function OnboardingStepBody({
  state,
  locked,
  sampleCount,
  setState,
  setNotice,
  onCreate,
  onProbe,
  onSave,
  onActivate,
}: {
  state: WizardState;
  locked: boolean;
  sampleCount: number;
  setState: (update: (current: WizardState) => WizardState) => void;
  setNotice: (message: string | null) => void;
  onCreate: (which: "site" | "line") => void;
  onProbe: () => void;
  onSave: () => void;
  onActivate: (allowBad: boolean) => void;
}) {
  if (state.step === 1) {
    return (
      <div className="grid gap-3 md:grid-cols-2">
        <Input aria-label="Site code" value={state.siteId} disabled={locked} onChange={(event) => setState((current) => invalidateFromStep1(current, event.target.value.trim(), current.lineId))} />
        <Input aria-label="Line code" value={state.lineId} disabled={locked} onChange={(event) => setState((current) => invalidateFromStep1(current, current.siteId, event.target.value.trim()))} />
        <Input aria-label="Site name" value={state.siteName} disabled={locked} onChange={(event) => setState((current) => bumpFieldEdit(current, "siteName", event.target.value))} />
        <Input aria-label="Timezone" value={state.siteTimezone} disabled={locked} onChange={(event) => setState((current) => bumpFieldEdit(current, "siteTimezone", event.target.value))} />
        <Input aria-label="Line name" value={state.lineName} disabled={locked} onChange={(event) => setState((current) => bumpFieldEdit(current, "lineName", event.target.value))} />
        <div className="flex gap-2">
          <Button disabled={locked} onClick={() => onCreate("site")}>Create site</Button>
          <Button disabled={locked} onClick={() => onCreate("line")}>Create line</Button>
        </div>
      </div>
    );
  }
  if (state.step === 2) {
    return (
      <div className="flex flex-col gap-3">
        <Input aria-label="Endpoint URL" value={state.endpointUrl} disabled={locked} onChange={(event) => setState((current) => invalidateFromStep2(current, { endpointUrl: event.target.value }))} />
        <Button disabled={locked} onClick={onProbe}>Test connection</Button>
      </div>
    );
  }
  if (state.step === 3) {
    return (
      <div className="flex flex-col gap-3">
        <Textarea aria-label="Tag import" disabled={locked} onChange={(event) => {
          const parsed = parseTagImport(event.target.value, { siteId: state.siteId, lineId: state.lineId, endpointUrl: state.endpointUrl });
          if (parsed.error) setNotice(parsed.error);
          else setState((current) => ({ ...bumpStep3Edit(current), drafts: parsed.drafts }));
        }} />
        <div className="flex gap-2">
          <Button disabled={locked} variant="secondary" onClick={() => setState((current) => ({ ...bumpStep3Edit(current), drafts: prosysReactorDrafts(current.siteId, current.lineId, current.endpointUrl) }))}>Load reactor set</Button>
          <Button disabled={locked} onClick={onSave}>Save tags</Button>
        </div>
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-3">
      <Button disabled={locked} onClick={() => onActivate(false)}>Activate</Button>
      <Button disabled={locked} variant="secondary" onClick={() => onActivate(true)}>Activate anyway</Button>
      <p className="font-mono text-xs text-slate-400">Live samples: {sampleCount}</p>
      <Link className="text-sm text-cyan-400" href={`/?siteId=${encodeURIComponent(state.siteId)}&lineId=${encodeURIComponent(state.lineId)}`}>Open Live Console</Link>
    </div>
  );
}
