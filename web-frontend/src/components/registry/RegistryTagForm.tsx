"use client";

import { useEffect, useState } from "react";
import { RegistryTagFormFields } from "@/components/registry/RegistryTagFormFields";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { validateTagBounds } from "@/lib/registryBounds";
import {
  emptyRegistryForm,
  normalizeRegistryRequest,
  tagMappingToRequest,
} from "@/lib/registryForm";
import type {
  RegistryErrorResponse,
  TagMapping,
  TagMappingRequest,
} from "@/lib/types";

export function RegistryTagForm({
  open,
  mode,
  siteId,
  lineId,
  initial,
  submitting,
  error,
  onOpenChange,
  onSubmit,
}: {
  open: boolean;
  mode: "create" | "edit";
  siteId: string;
  lineId: string;
  initial: TagMapping | null;
  submitting: boolean;
  error: RegistryErrorResponse | null;
  onOpenChange: (open: boolean) => void;
  onSubmit: (body: TagMappingRequest) => void;
}) {
  const [form, setForm] = useState<TagMappingRequest>(() =>
    emptyRegistryForm(siteId, lineId),
  );
  const [clientIssues, setClientIssues] = useState<
    { field: string; message: string }[]
  >([]);

  useEffect(() => {
    if (!open) {
      return;
    }
    setClientIssues([]);
    setForm(initial ? tagMappingToRequest(initial) : emptyRegistryForm(siteId, lineId));
  }, [open, initial, siteId, lineId]);

  const patch = <K extends keyof TagMappingRequest>(
    key: K,
    value: TagMappingRequest[K],
  ) => setForm((prev) => ({ ...prev, [key]: value }));

  const handleSubmit = () => {
    const body = normalizeRegistryRequest(
      form,
      mode === "edit" ? { siteId, lineId } : undefined,
    );
    const issues = validateTagBounds(body);
    setClientIssues(issues);
    if (issues.length > 0) {
      return;
    }
    onSubmit(body);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[min(100%-2rem,40rem)]">
        <DialogHeader>
          <DialogTitle>{mode === "create" ? "Create tag" : "Edit tag"}</DialogTitle>
          <DialogDescription>
            {mode === "edit"
              ? "siteId and lineId are immutable on update."
              : "Register a new TagMapping for this site and line."}
          </DialogDescription>
        </DialogHeader>

        <RegistryTagFormFields
          form={form}
          submitting={submitting}
          onPatch={patch}
        />

        {(clientIssues.length > 0 || error) && (
          <div className="mt-3 rounded-md border border-rose-500/40 bg-rose-500/10 px-3 py-2 text-sm text-rose-200">
            {error ? (
              <p>
                <span className="mono font-mono tabular-nums">{error.code}</span>
                {" · "}
                {error.message}
              </p>
            ) : null}
            <ul className="mt-1 list-inside list-disc text-xs">
              {[...clientIssues, ...(error?.fieldErrors ?? [])].map((item) => (
                <li key={`${item.field}:${item.message}`}>
                  <span className="mono font-mono">{item.field}</span>: {item.message}
                </li>
              ))}
            </ul>
          </div>
        )}

        <DialogFooter>
          <Button variant="secondary" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={submitting}>
            {submitting ? "Saving…" : mode === "create" ? "Create" : "Save"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
