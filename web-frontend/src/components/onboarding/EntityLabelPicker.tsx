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
import { entityLabelPatternOk } from "@/lib/entityLabels";

const CUSTOM = "__custom__";

type Props = {
  id: string;
  title: string;
  presets: readonly string[];
  value: string;
  disabled?: boolean;
  onChange: (next: string) => void;
};

export function EntityLabelPicker({ id, title, presets, value, disabled, onChange }: Props) {
  const presetSelected = (presets as readonly string[]).includes(value);
  const mode = presetSelected ? value : CUSTOM;
  const customInvalid = mode === CUSTOM && value.trim().length > 0 && !entityLabelPatternOk(value);

  return (
    <div className="flex flex-col gap-2 rounded-md border border-slate-800 bg-slate-950/40 p-3">
      <Label htmlFor={`${id}-preset`} className="text-xs uppercase tracking-wide text-slate-400">
        {title}
      </Label>
      <p className="text-xs text-slate-500">What you call this level in the control room (not the code id).</p>
      <Select
        value={mode}
        disabled={disabled}
        onValueChange={(next) => {
          if (next === CUSTOM) {
            onChange("");
            return;
          }
          onChange(next);
        }}
      >
        <SelectTrigger id={`${id}-preset`} className="w-full sm:max-w-xs">
          <SelectValue placeholder="Choose a word" />
        </SelectTrigger>
        <SelectContent>
          {presets.map((preset) => (
            <SelectItem key={preset} value={preset}>
              {preset}
            </SelectItem>
          ))}
          <SelectItem value={CUSTOM}>Custom</SelectItem>
        </SelectContent>
      </Select>
      {mode === CUSTOM ? (
        <Input
          id={`${id}-custom`}
          value={value}
          disabled={disabled}
          placeholder="Shop Floor"
          className="w-full sm:max-w-xs"
          onChange={(event) => onChange(event.target.value)}
        />
      ) : null}
      {customInvalid ? (
        <p className="text-xs text-rose-400">Use 2–24 letters; spaces or hyphens between words only.</p>
      ) : null}
    </div>
  );
}