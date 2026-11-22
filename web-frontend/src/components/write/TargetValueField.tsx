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
import type { TargetDraft } from "@/lib/targetValue";

export function TargetValueField({
  draft,
  onChange,
  disabled,
}: {
  draft: TargetDraft;
  onChange: (next: TargetDraft) => void;
  disabled?: boolean;
}) {
  if (draft.kind === "BOOLEAN") {
    const selectValue =
      draft.value === true ? "true" : draft.value === false ? "false" : "";
    return (
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="target-value">Target value</Label>
        <Select
          value={selectValue || undefined}
          onValueChange={(value) =>
            onChange({ kind: "BOOLEAN", value: value === "true" })
          }
          disabled={disabled}
        >
          <SelectTrigger id="target-value" className="mono font-mono tabular-nums">
            <SelectValue placeholder="Select true or false" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="true">true</SelectItem>
            <SelectItem value="false">false</SelectItem>
          </SelectContent>
        </Select>
      </div>
    );
  }

  if (draft.kind === "STRING") {
    return (
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="target-value">Target value</Label>
        <Input
          id="target-value"
          className="mono font-mono"
          value={draft.text}
          onChange={(event) =>
            onChange({ kind: "STRING", text: event.target.value })
          }
          disabled={disabled}
        />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor="target-value">
        Target value ({draft.kind === "INT" ? "integer" : "number"})
      </Label>
      <Input
        id="target-value"
        inputMode="decimal"
        className="mono font-mono tabular-nums"
        value={draft.text}
        onChange={(event) =>
          onChange({ kind: draft.kind, text: event.target.value })
        }
        disabled={disabled}
      />
    </div>
  );
}
