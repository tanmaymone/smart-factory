"use client";

import { useEffect, useMemo, useState } from "react";
import { CommandStatusPanel } from "@/components/write/CommandStatusPanel";
import { TargetValueField } from "@/components/write/TargetValueField";
import {
  WriteSnapshotPanel,
  type WriteSnapshot,
} from "@/components/write/WriteSnapshotPanel";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { OPERATOR_STORAGE_KEY, submitCommand } from "@/lib/commands";
import {
  canSubmitCommand,
  emptyTargetDraft,
  resolveTargetValue,
  type TargetDraft,
} from "@/lib/targetValue";
import type {
  CommandErrorResponse,
  CommandUpdateEvent,
  TelemetryRow,
} from "@/lib/types";

export function WriteCommandModal({
  open,
  row,
  siteId,
  lineId,
  liveRow,
  lastCommandEvent,
  onOpenChange,
}: {
  open: boolean;
  row: TelemetryRow | null;
  siteId: string;
  lineId: string;
  liveRow: TelemetryRow | null;
  lastCommandEvent: CommandUpdateEvent | null;
  onOpenChange: (open: boolean) => void;
}) {
  const [snapshot, setSnapshot] = useState<WriteSnapshot | null>(null);
  const [draft, setDraft] = useState<TargetDraft>(emptyTargetDraft(null));
  const [requestedBy, setRequestedBy] = useState("");
  const [reason, setReason] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [phase, setPhase] = useState<"idle" | "submitting" | "accepted" | "rejected">(
    "idle",
  );
  const [commandId, setCommandId] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<CommandErrorResponse | null>(null);

  useEffect(() => {
    if (!open || !row) {
      return;
    }
    setSnapshot({
      value: row.value,
      quality: row.quality,
      minValue: row.minValue,
      maxValue: row.maxValue,
      unit: row.unit,
    });
    setDraft(emptyTargetDraft(row.dataType));
    setReason("");
    setConfirmed(false);
    setPhase("idle");
    setCommandId(null);
    setStatus(null);
    setError(null);
    try {
      setRequestedBy(sessionStorage.getItem(OPERATOR_STORAGE_KEY) ?? "");
    } catch {
      setRequestedBy("");
    }
  }, [open, row]);

  useEffect(() => {
    if (!lastCommandEvent || !commandId) {
      return;
    }
    if (lastCommandEvent.commandId !== commandId) {
      return;
    }
    setStatus(lastCommandEvent.status);
    if (
      lastCommandEvent.status === "FAILED_EDGE" ||
      lastCommandEvent.status === "REJECTED_VALIDATION" ||
      lastCommandEvent.status === "REJECTED_INTERLOCK"
    ) {
      setError({
        code: lastCommandEvent.status,
        message: lastCommandEvent.errorMessage ?? lastCommandEvent.status,
        commandId,
        fieldErrors: [],
      });
    }
  }, [lastCommandEvent, commandId]);

  const liveDrift = useMemo(() => {
    if (!snapshot || !liveRow) {
      return null;
    }
    const changed =
      liveRow.value !== snapshot.value ||
      liveRow.quality !== snapshot.quality ||
      liveRow.minValue !== snapshot.minValue ||
      liveRow.maxValue !== snapshot.maxValue;
    return changed ? liveRow : null;
  }, [snapshot, liveRow]);

  const ready = canSubmitCommand({ draft, requestedBy, reason, confirmed });

  const onSubmit = async () => {
    if (!row?.tagId || !ready || phase === "submitting") {
      return;
    }
    setPhase("submitting");
    setStatus("Submitting");
    setError(null);
    try {
      sessionStorage.setItem(OPERATOR_STORAGE_KEY, requestedBy.trim());
    } catch {
      // ignore
    }
    const result = await submitCommand({
      siteId,
      lineId,
      tagId: row.tagId,
      targetValue: resolveTargetValue(draft),
      requestedBy,
      reason,
    });
    if (result.ok) {
      setPhase("accepted");
      setCommandId(result.response.commandId);
      setStatus(result.response.status);
      return;
    }
    setPhase("rejected");
    setCommandId(result.error.commandId);
    setStatus(result.error.code);
    setError(result.error);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Governed write</DialogTitle>
          <DialogDescription>
            Confirm setpoint for{" "}
            <span className="mono font-mono text-slate-200">{row?.tagCode}</span>
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3 text-sm">
          <WriteSnapshotPanel snapshot={snapshot} liveDrift={liveDrift} />
          <TargetValueField
            draft={draft}
            onChange={setDraft}
            disabled={phase === "submitting"}
          />
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="requested-by">Operator</Label>
            <Input
              id="requested-by"
              value={requestedBy}
              onChange={(event) => setRequestedBy(event.target.value)}
              disabled={phase === "submitting"}
              placeholder="operator@factory.com"
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="reason">Logbook reason</Label>
            <Input
              id="reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              disabled={phase === "submitting"}
              placeholder="Why is this write required?"
            />
          </div>
          <label className="flex items-start gap-2 text-slate-300">
            <Checkbox
              checked={confirmed}
              onCheckedChange={(value) => setConfirmed(value === true)}
              disabled={phase === "submitting"}
              className="mt-0.5"
            />
            <span>
              I confirm this write is within process limits and PLC interlocks remain
              authoritative.
            </span>
          </label>
          <CommandStatusPanel
            phase={phase}
            commandId={commandId}
            status={status}
            error={error}
          />
        </div>

        <DialogFooter>
          <Button variant="secondary" onClick={() => onOpenChange(false)}>
            Close
          </Button>
          <Button
            onClick={() => void onSubmit()}
            disabled={!ready || phase === "submitting"}
          >
            {phase === "submitting" ? "Submitting…" : "Submit command"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
