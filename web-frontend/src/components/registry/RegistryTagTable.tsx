"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { LifecycleBadge } from "@/components/onboarding/LifecycleBadge";
import type { TagMapping } from "@/lib/types";

export function RegistryTagTable({
  tags,
  onEdit,
  onDelete,
  errorCode,
  errorMessage,
}: {
  tags: TagMapping[];
  onEdit: (tag: TagMapping) => void;
  onDelete: (tag: TagMapping) => Promise<boolean>;
  errorCode?: string | null;
  errorMessage?: string | null;
}) {
  const [pending, setPending] = useState<TagMapping | null>(null);
  const [busy, setBusy] = useState(false);

  const confirmDelete = async () => {
    if (!pending || busy) {
      return;
    }
    setBusy(true);
    const removed = await onDelete(pending);
    setBusy(false);
    if (removed) {
      setPending(null);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Tag Registry</CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        {errorCode ? (
          <div className="border-b border-rose-500/30 bg-rose-500/10 px-4 py-3 text-sm text-rose-300">
            <span className="mono font-mono tabular-nums">{errorCode}</span>
            {errorMessage ? ` · ${errorMessage}` : null}
          </div>
        ) : null}
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Tag</TableHead>
              <TableHead>Protocol</TableHead>
              <TableHead>Address</TableHead>
              <TableHead>Bounds</TableHead>
              <TableHead>Deadband</TableHead>
              <TableHead>Writable</TableHead>
              <TableHead className="w-[9.5rem]" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {tags.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="py-8 text-center text-slate-400">
                  No tag mappings for this site and line.
                </TableCell>
              </TableRow>
            ) : (
              tags.map((tag) => (
                <TableRow key={tag.id}>
                  <TableCell>
                    <div className="flex flex-col gap-0.5">
                      <span className="mono font-mono text-sm tabular-nums text-slate-100">
                        {tag.tagCode}
                      </span>
                      <span className="text-xs text-slate-400">{tag.description}</span>
                      <LifecycleBadge lifecycle={tag.lifecycle} />
                    </div>
                  </TableCell>
                  <TableCell className="mono font-mono text-slate-300">
                    {tag.protocol}
                  </TableCell>
                  <TableCell className="mono font-mono text-xs text-slate-300">
                    {tag.externalAddress}
                  </TableCell>
                  <TableCell className="mono font-mono tabular-nums text-slate-300">
                    {tag.minValue ?? "—"} … {tag.maxValue ?? "—"}
                  </TableCell>
                  <TableCell className="mono font-mono tabular-nums text-slate-300">
                    {tag.deadband}
                  </TableCell>
                  <TableCell>
                    <Badge variant={tag.writable ? "emerald" : "slate"}>
                      {tag.writable ? "writable" : "read-only"}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex justify-end gap-2">
                      <Button size="sm" variant="secondary" onClick={() => onEdit(tag)}>
                        Edit
                      </Button>
                      <Button
                        size="sm"
                        variant="danger"
                        onClick={() => setPending(tag)}
                      >
                        Delete
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </CardContent>
      <Dialog
        open={pending != null}
        onOpenChange={(open) => {
          if (!open && !busy) {
            setPending(null);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete tag</DialogTitle>
            <DialogDescription>
              Remove{" "}
              <span className="mono font-mono tabular-nums text-slate-200">
                {pending?.tagCode}
              </span>{" "}
              from the registry. Stored history and the command audit log stay.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="secondary"
              disabled={busy}
              onClick={() => setPending(null)}
            >
              Cancel
            </Button>
            <Button variant="danger" disabled={busy} onClick={() => void confirmDelete()}>
              {busy ? "Deleting…" : "Delete"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
