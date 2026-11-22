"use client";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { Protocol } from "@/lib/types";

const PROTOCOLS: Protocol[] = ["OPC_UA", "S7", "MODBUS_TCP", "BACNET_IP"];

export function RegistryTagFilters({
  protocol,
  writableFilter,
  onProtocolChange,
  onWritableChange,
  onCreate,
}: {
  protocol: string;
  writableFilter: "all" | "true" | "false";
  onProtocolChange: (protocol: string) => void;
  onWritableChange: (value: "all" | "true" | "false") => void;
  onCreate: () => void;
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3 rounded-lg border border-slate-800 bg-slate-900/80 px-4 py-3">
      <div className="flex flex-wrap gap-3">
        <div className="flex min-w-[10rem] flex-col gap-1.5">
          <Label>Protocol</Label>
          <Select
            value={protocol || "ALL"}
            onValueChange={(value) => onProtocolChange(value === "ALL" ? "" : value)}
          >
            <SelectTrigger>
              <SelectValue placeholder="All protocols" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All protocols</SelectItem>
              {PROTOCOLS.map((item) => (
                <SelectItem key={item} value={item}>
                  {item}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex min-w-[10rem] flex-col gap-1.5">
          <Label>Writable</Label>
          <Select
            value={writableFilter}
            onValueChange={(value) =>
              onWritableChange(value as "all" | "true" | "false")
            }
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All</SelectItem>
              <SelectItem value="true">Writable</SelectItem>
              <SelectItem value="false">Read-only</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
      <Button onClick={onCreate}>New tag</Button>
    </div>
  );
}
