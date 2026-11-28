"use client";

import { useEffect, useMemo, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  CHECKLIST_CATEGORY_ORDER,
  checklistCategoryLabel,
  fetchPreCutoverChecklist,
} from "@/lib/onboardingCatalogApi";
import type { ChecklistCategory, PreCutoverChecklistItem } from "@/lib/types";

const SIGN_OFF_KEY = "sf-m36-checklist-signoff";

function signOffStorageKey(siteId: string, lineId: string): string {
  return `${SIGN_OFF_KEY}:${siteId}:${lineId}`;
}

export function PreCutoverChecklistCard({
  siteId,
  lineId,
}: {
  siteId: string;
  lineId: string;
}) {
  const [open, setOpen] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [items, setItems] = useState<PreCutoverChecklistItem[]>([]);
  const [signed, setSigned] = useState<Record<string, boolean>>({});

  useEffect(() => {
    const raw = sessionStorage.getItem(signOffStorageKey(siteId, lineId));
    setSigned(raw ? (JSON.parse(raw) as Record<string, boolean>) : {});
  }, [siteId, lineId]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    void fetchPreCutoverChecklist()
      .then((body) => {
        if (cancelled) return;
        setItems(body.items);
        setError(null);
      })
      .catch(() => {
        if (cancelled) return;
        setError("Could not load the pre-cutover checklist.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const grouped = useMemo(() => {
    const map = new Map<ChecklistCategory, PreCutoverChecklistItem[]>();
    for (const category of CHECKLIST_CATEGORY_ORDER) {
      map.set(category, []);
    }
    for (const item of items) {
      const bucket = map.get(item.category) ?? [];
      bucket.push(item);
      map.set(item.category, bucket);
    }
    return map;
  }, [items]);

  const toggleSignOff = (id: string, checked: boolean) => {
    setSigned((current) => {
      const next = { ...current, [id]: checked };
      sessionStorage.setItem(signOffStorageKey(siteId, lineId), JSON.stringify(next));
      return next;
    });
  };

  return (
    <section className="rounded-lg border border-slate-800 bg-slate-900/60 p-4">
      <div className="flex items-center justify-between gap-3">
        <ChecklistCardTitle open={open} />
        <Button type="button" variant="secondary" size="sm" onClick={() => setOpen((value) => !value)}>
          {open ? "Collapse" : "Expand"}
        </Button>
      </div>
      {open ? (
        <div className="mt-4 flex flex-col gap-4">
          {loading ? <p className="text-sm text-slate-400">Loading checklist…</p> : null}
          {error ? <p className="text-sm text-rose-400">{error}</p> : null}
          {!loading && !error
            ? CHECKLIST_CATEGORY_ORDER.map((category) => {
                const rows = grouped.get(category) ?? [];
                if (rows.length === 0) return null;
                return (
                  <ChecklistDomainSection
                    key={category}
                    label={checklistCategoryLabel(category)}
                    rows={rows}
                    signed={signed}
                    onToggle={toggleSignOff}
                  />
                );
              })
            : null}
        </div>
      ) : null}
    </section>
  );
}

function ChecklistCardTitle({ open }: { open: boolean }) {
  return (
    <div>
      <h2 className="text-sm font-semibold text-slate-100">Pre-cutover checklist</h2>
      <p className="text-xs text-slate-500">
        {open ? "Five domains — runnable M3.6 checks vs. M4.x cutover gates" : "Collapsed"}
      </p>
    </div>
  );
}

function ChecklistDomainSection({
  label,
  rows,
  signed,
  onToggle,
}: {
  label: string;
  rows: PreCutoverChecklistItem[];
  signed: Record<string, boolean>;
  onToggle: (id: string, checked: boolean) => void;
}) {
  return (
    <div className="flex flex-col gap-2">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-400">{label}</p>
      <ul className="flex flex-col gap-3">
        {rows.map((item) => (
          <li
            key={item.id}
            className="rounded-md border border-slate-800 bg-slate-950/50 p-3"
          >
            <div className="flex flex-wrap items-start gap-2">
              <Checkbox
                id={`chk-${item.id}`}
                checked={Boolean(signed[item.id])}
                onCheckedChange={(value) => onToggle(item.id, value === true)}
              />
              <div className="min-w-0 flex-1">
                <label htmlFor={`chk-${item.id}`} className="text-sm text-slate-100">
                  {item.title}
                </label>
                <p className="mt-1 text-xs text-slate-500">{item.description}</p>
              </div>
              <div className="flex flex-wrap gap-1">
                {item.runnableInCurrentRelease ? (
                  <Badge className="border-emerald-500/40 bg-emerald-500/10 text-emerald-400">
                    Runnable M3.6
                  </Badge>
                ) : (
                  <Badge className="border-amber-500/40 bg-amber-500/10 text-amber-400">
                    {item.milestoneGate ?? "M4 gate"}
                  </Badge>
                )}
                {item.severity === "CUTOVER_BLOCKER" ? (
                  <Badge className="border-rose-500/40 bg-rose-500/10 text-rose-400">Cutover blocker</Badge>
                ) : null}
              </div>
            </div>
            {item.verificationCommand ? (
              <pre className="mono mt-2 overflow-x-auto rounded border border-slate-800 bg-slate-950 p-2 font-mono text-[11px] text-cyan-400">
                {item.verificationCommand}
              </pre>
            ) : null}
            <p className="mt-2 text-xs text-slate-500">
              <span className="text-slate-400">Dev:</span> {item.currentDevPosture}
            </p>
          </li>
        ))}
      </ul>
    </div>
  );
}