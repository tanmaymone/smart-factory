import type {
  CommandAcceptedResponse,
  CommandAuditResponse,
  CommandErrorResponse,
  CommandRequest,
  CommandStatus,
  CommandUpdateEvent,
} from "@/lib/types";
import { COMMAND_STATUSES } from "@/lib/types";

export function isCommandStatus(value: string): value is CommandStatus {
  return (COMMAND_STATUSES as readonly string[]).includes(value);
}

export function buildCommandBody(request: CommandRequest): CommandRequest {
  return {
    siteId: request.siteId.trim(),
    lineId: request.lineId.trim(),
    tagId: request.tagId,
    targetValue: request.targetValue,
    reason: request.reason.trim(),
  };
}

export function parseCommandError(payload: unknown): CommandErrorResponse {
  if (payload == null || typeof payload !== "object") {
    return {
      code: "UNKNOWN",
      message: "Command request failed",
      commandId: null,
      fieldErrors: [],
    };
  }
  const body = payload as Partial<CommandErrorResponse>;
  return {
    code: typeof body.code === "string" ? body.code : "UNKNOWN",
    message:
      typeof body.message === "string" ? body.message : "Command request failed",
    commandId: typeof body.commandId === "string" ? body.commandId : null,
    fieldErrors: Array.isArray(body.fieldErrors)
      ? body.fieldErrors.filter(
          (item): item is { field: string; message: string } =>
            item != null &&
            typeof item === "object" &&
            typeof (item as { field?: unknown }).field === "string" &&
            typeof (item as { message?: unknown }).message === "string",
        )
      : [],
  };
}

export async function submitCommand(
  request: CommandRequest,
  signal?: AbortSignal,
): Promise<
  | { ok: true; response: CommandAcceptedResponse }
  | { ok: false; status: number; error: CommandErrorResponse }
> {
  const body = buildCommandBody(request);
  const response = await fetch("/api/commands", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "same-origin",
    body: JSON.stringify(body),
    signal,
    cache: "no-store",
  });
  const payload: unknown = await response.json().catch(() => null);
  if (response.status === 202) {
    return { ok: true, response: payload as CommandAcceptedResponse };
  }
  return {
    ok: false,
    status: response.status,
    error: parseCommandError(payload),
  };
}

export type AuditQuery = {
  siteId?: string;
  lineId?: string;
  tagCode?: string;
  status?: string;
  page?: number;
  size?: number;
};

export function buildAuditQuery(query: AuditQuery): string {
  const params = new URLSearchParams();
  if (query.siteId) params.set("siteId", query.siteId);
  if (query.lineId) params.set("lineId", query.lineId);
  if (query.tagCode) params.set("tagCode", query.tagCode);
  if (query.status) params.set("status", query.status);
  const page = query.page ?? 0;
  params.set("page", String(Math.max(0, page)));
  params.set("size", String(query.size ?? 50));
  return params.toString();
}

export function nextAuditPageOnFilterChange(
  prev: AuditQuery,
  next: AuditQuery,
): number {
  const filterKeys: (keyof AuditQuery)[] = [
    "siteId",
    "lineId",
    "tagCode",
    "status",
  ];
  for (const key of filterKeys) {
    if ((prev[key] ?? "") !== (next[key] ?? "")) {
      return 0;
    }
  }
  return next.page ?? prev.page ?? 0;
}

export function patchAuditRow(
  rows: CommandAuditResponse[],
  event: CommandUpdateEvent,
  statusFilter: string | null,
): CommandAuditResponse[] {
  if (!isCommandStatus(event.status)) {
    return rows;
  }
  const index = rows.findIndex((row) => row.commandId === event.commandId);
  if (index < 0) {
    return rows;
  }
  if (statusFilter && event.status !== statusFilter) {
    return rows.filter((row) => row.commandId !== event.commandId);
  }
  const applied =
    event.appliedValue == null ? null : String(event.appliedValue);
  const next: CommandAuditResponse = {
    ...rows[index],
    status: event.status,
    rejectionReason: event.errorMessage ?? rows[index].rejectionReason,
    appliedValue: applied ?? rows[index].appliedValue,
    ackedAt:
      event.status === "ACKED" || event.status === "FAILED_EDGE"
        ? (event.executedAt ?? rows[index].ackedAt)
        : rows[index].ackedAt,
  };
  const copy = rows.slice();
  copy[index] = next;
  return copy;
}

export async function fetchCommandAudit(
  query: AuditQuery,
  signal?: AbortSignal,
): Promise<CommandAuditResponse[]> {
  const qs = buildAuditQuery(query);
  const response = await fetch(`/api/commands/audit?${qs}`, {
    credentials: "same-origin",
    signal,
    cache: "no-store",
  });
  if (!response.ok) {
    const payload: unknown = await response.json().catch(() => null);
    const error = parseCommandError(payload);
    throw Object.assign(new Error(error.message), {
      code: error.code,
      status: response.status,
    });
  }
  return (await response.json()) as CommandAuditResponse[];
}