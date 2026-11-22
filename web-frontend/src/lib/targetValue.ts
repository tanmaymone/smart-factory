import type { TagDataType } from "@/lib/types";

/** Unset boolean select uses null; false is an explicit ready value. */
export type TargetDraft =
  | { kind: "FLOAT" | "INT"; text: string }
  | { kind: "BOOLEAN"; value: boolean | null }
  | { kind: "STRING"; text: string };

export function emptyTargetDraft(dataType: TagDataType | null): TargetDraft {
  if (dataType === "BOOLEAN") {
    return { kind: "BOOLEAN", value: null };
  }
  if (dataType === "STRING") {
    return { kind: "STRING", text: "" };
  }
  if (dataType === "INT") {
    return { kind: "INT", text: "" };
  }
  return { kind: "FLOAT", text: "" };
}

export function isTargetReady(draft: TargetDraft): boolean {
  if (draft.kind === "BOOLEAN") {
    return draft.value === true || draft.value === false;
  }
  if (draft.kind === "STRING") {
    return true;
  }
  const trimmed = draft.text.trim();
  if (trimmed === "") {
    return false;
  }
  const n = Number(trimmed);
  if (!Number.isFinite(n)) {
    return false;
  }
  if (draft.kind === "INT") {
    return Number.isInteger(n);
  }
  return true;
}

export function resolveTargetValue(draft: TargetDraft): unknown {
  if (draft.kind === "BOOLEAN") {
    return draft.value;
  }
  if (draft.kind === "STRING") {
    return draft.text;
  }
  return Number(draft.text.trim());
}

export function canSubmitCommand(input: {
  draft: TargetDraft;
  requestedBy: string;
  reason: string;
  confirmed: boolean;
}): boolean {
  return (
    isTargetReady(input.draft) &&
    input.requestedBy.trim().length > 0 &&
    input.reason.trim().length > 0 &&
    input.confirmed
  );
}
