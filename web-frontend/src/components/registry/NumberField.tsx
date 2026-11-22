"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

function parseOptionalNumber(text: string): number | null {
  const trimmed = text.trim();
  if (trimmed === "") {
    return null;
  }
  const n = Number(trimmed);
  return Number.isFinite(n) ? n : null;
}

export function NumberField({
  id,
  label,
  value,
  onChange,
  disabled,
}: {
  id: string;
  label: string;
  value: number | null;
  onChange: (value: number | null) => void;
  disabled?: boolean;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        className="mono font-mono tabular-nums"
        value={value == null ? "" : String(value)}
        onChange={(event) => onChange(parseOptionalNumber(event.target.value))}
        disabled={disabled}
      />
    </div>
  );
}
