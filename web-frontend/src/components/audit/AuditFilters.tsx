"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { COMMAND_STATUSES } from "@/lib/types";

export function AuditFilters({
  tagCode,
  status,
  onTagCodeChange,
  onStatusChange,
}: {
  tagCode: string;
  status: string;
  onTagCodeChange: (value: string) => void;
  onStatusChange: (value: string) => void;
}) {
  return (
    <div className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-800 bg-slate-900/80 px-4 py-3">
      <div className="flex min-w-[10rem] flex-col gap-1.5">
        <Label htmlFor="audit-tag">Tag code</Label>
        <Input
          id="audit-tag"
          className="mono font-mono"
          value={tagCode}
          onChange={(event) => onTagCodeChange(event.target.value)}
          placeholder="VALVE_101_POS"
        />
      </div>
      <div className="flex min-w-[12rem] flex-col gap-1.5">
        <Label>Status</Label>
        <Select
          value={status || "ALL"}
          onValueChange={(value) => onStatusChange(value === "ALL" ? "" : value)}
        >
          <SelectTrigger>
            <SelectValue placeholder="All statuses" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All statuses</SelectItem>
            {COMMAND_STATUSES.map((item) => (
              <SelectItem key={item} value={item}>
                {item}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}
