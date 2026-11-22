"use client";

import { NumberField } from "@/components/registry/NumberField";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { Protocol, TagDataType, TagMappingRequest } from "@/lib/types";

const PROTOCOLS: Protocol[] = ["OPC_UA", "S7", "MODBUS_TCP", "BACNET_IP"];
const DATA_TYPES: TagDataType[] = ["FLOAT", "INT", "BOOLEAN", "STRING"];

export function RegistryTagFormFields({
  form,
  submitting,
  onPatch,
}: {
  form: TagMappingRequest;
  submitting: boolean;
  onPatch: <K extends keyof TagMappingRequest>(
    key: K,
    value: TagMappingRequest[K],
  ) => void;
}) {
  return (
    <div className="grid max-h-[60vh] gap-3 overflow-y-auto pr-1 sm:grid-cols-2">
      <div className="flex flex-col gap-1.5 sm:col-span-2">
        <Label htmlFor="tag-code">Tag code</Label>
        <Input
          id="tag-code"
          className="mono font-mono"
          value={form.tagCode}
          onChange={(e) => onPatch("tagCode", e.target.value)}
          disabled={submitting}
        />
      </div>
      <div className="flex flex-col gap-1.5 sm:col-span-2">
        <Label htmlFor="description">Description</Label>
        <Input
          id="description"
          value={form.description}
          onChange={(e) => onPatch("description", e.target.value)}
          disabled={submitting}
        />
      </div>
      <div className="flex flex-col gap-1.5">
        <Label>Protocol</Label>
        <Select
          value={form.protocol}
          onValueChange={(v) => onPatch("protocol", v as Protocol)}
          disabled={submitting}
        >
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {PROTOCOLS.map((item) => (
              <SelectItem key={item} value={item}>
                {item}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="flex flex-col gap-1.5">
        <Label>Data type</Label>
        <Select
          value={form.dataType}
          onValueChange={(v) => onPatch("dataType", v as TagDataType)}
          disabled={submitting}
        >
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {DATA_TYPES.map((item) => (
              <SelectItem key={item} value={item}>
                {item}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="flex flex-col gap-1.5 sm:col-span-2">
        <Label htmlFor="endpoint">Endpoint URL</Label>
        <Input
          id="endpoint"
          className="mono font-mono text-xs"
          value={form.endpointUrl}
          onChange={(e) => onPatch("endpointUrl", e.target.value)}
          disabled={submitting}
        />
      </div>
      <div className="flex flex-col gap-1.5 sm:col-span-2">
        <Label htmlFor="address">External address</Label>
        <Input
          id="address"
          className="mono font-mono"
          value={form.externalAddress}
          onChange={(e) => onPatch("externalAddress", e.target.value)}
          disabled={submitting}
        />
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="unit">Unit</Label>
        <Input
          id="unit"
          value={form.unit ?? ""}
          onChange={(e) => onPatch("unit", e.target.value)}
          disabled={submitting}
        />
      </div>
      <div className="flex items-center gap-2 pt-6">
        <Checkbox
          id="writable"
          checked={form.writable}
          onCheckedChange={(v) => onPatch("writable", v === true)}
          disabled={submitting}
        />
        <Label htmlFor="writable" className="normal-case tracking-normal">
          Writable
        </Label>
      </div>
      <NumberField
        id="min"
        label="Min value"
        value={form.minValue}
        onChange={(v) => onPatch("minValue", v)}
        disabled={submitting}
      />
      <NumberField
        id="max"
        label="Max value"
        value={form.maxValue}
        onChange={(v) => onPatch("maxValue", v)}
        disabled={submitting}
      />
      <NumberField
        id="deadband"
        label="Deadband"
        value={form.deadband}
        onChange={(v) => onPatch("deadband", v ?? 0)}
        disabled={submitting}
      />
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="interlock">Interlock rule key</Label>
        <Input
          id="interlock"
          className="mono font-mono"
          value={form.interlockRuleKey ?? ""}
          onChange={(e) => onPatch("interlockRuleKey", e.target.value)}
          disabled={submitting}
        />
      </div>
      <NumberField
        id="warn-low"
        label="Warning low"
        value={form.warningLow}
        onChange={(v) => onPatch("warningLow", v)}
        disabled={submitting}
      />
      <NumberField
        id="warn-high"
        label="Warning high"
        value={form.warningHigh}
        onChange={(v) => onPatch("warningHigh", v)}
        disabled={submitting}
      />
      <NumberField
        id="crit-low"
        label="Critical low"
        value={form.criticalLow}
        onChange={(v) => onPatch("criticalLow", v)}
        disabled={submitting}
      />
      <NumberField
        id="crit-high"
        label="Critical high"
        value={form.criticalHigh}
        onChange={(v) => onPatch("criticalHigh", v)}
        disabled={submitting}
      />
    </div>
  );
}
