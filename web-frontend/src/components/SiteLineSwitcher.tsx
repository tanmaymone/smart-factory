"use client";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { SiteInfo } from "@/lib/sites";

type Props = {
  catalog: SiteInfo[];
  siteId: string;
  lineId: string;
  onSiteChange: (siteId: string) => void;
  onLineChange: (lineId: string) => void;
};

export function SiteLineSwitcher({
  catalog,
  siteId,
  lineId,
  onSiteChange,
  onLineChange,
}: Props) {
  const site = catalog.find((entry) => entry.id === siteId) ?? catalog[0];
  const lines = site?.lines ?? [];

  return (
    <div className="flex flex-wrap items-end gap-3">
      <label className="flex min-w-[10rem] flex-col gap-1">
        <span className="text-xs uppercase tracking-wide text-slate-400">Site</span>
        <Select value={siteId} onValueChange={onSiteChange}>
          <SelectTrigger aria-label="Site" className="w-[12rem]">
            <SelectValue placeholder="Site" />
          </SelectTrigger>
          <SelectContent>
            {catalog.map((entry) => (
              <SelectItem key={entry.id} value={entry.id}>
                {entry.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </label>

      <label className="flex min-w-[10rem] flex-col gap-1">
        <span className="text-xs uppercase tracking-wide text-slate-400">Line</span>
        <Select value={lineId} onValueChange={onLineChange}>
          <SelectTrigger aria-label="Line" className="w-[12rem]">
            <SelectValue placeholder="Line" />
          </SelectTrigger>
          <SelectContent>
            {lines.map((entry) => (
              <SelectItem key={entry.id} value={entry.id}>
                {entry.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </label>
    </div>
  );
}
