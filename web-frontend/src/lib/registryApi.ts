import { encodeRegistryPathSegment } from "@/lib/registryNormalize";
import type {
  LineRequest,
  LineResponse,
  LineUpdateRequest,
  RegistryErrorResponse,
  SiteRequest,
  SiteResponse,
  SiteUpdateRequest,
  TagBatchResponse,
  TagMapping,
  TagMappingRequest,
} from "@/lib/types";

function jsonHeaders(): HeadersInit {
  return { "Content-Type": "application/json" };
}

const fetchInit: RequestInit = { credentials: "same-origin", cache: "no-store" };

export function parseRegistryError(payload: unknown): RegistryErrorResponse {
  if (payload == null || typeof payload !== "object") {
    return { code: "UNKNOWN", message: "Registry request failed", fieldErrors: [] };
  }
  const body = payload as Partial<RegistryErrorResponse>;
  return {
    code: typeof body.code === "string" ? body.code : "UNKNOWN",
    message:
      typeof body.message === "string" ? body.message : "Registry request failed",
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

export type RegistryListQuery = {
  siteId?: string;
  lineId?: string;
  protocol?: string;
  writable?: boolean;
};

export function buildRegistryListQuery(query: RegistryListQuery): string {
  const params = new URLSearchParams();
  if (query.siteId) params.set("siteId", query.siteId);
  if (query.lineId) params.set("lineId", query.lineId);
  if (query.protocol) params.set("protocol", query.protocol);
  if (query.writable != null) params.set("writable", String(query.writable));
  return params.toString();
}

export async function listRegistryTags(
  query: RegistryListQuery,
  signal?: AbortSignal,
): Promise<TagMapping[]> {
  const qs = buildRegistryListQuery(query);
  const response = await fetch(`/api/registry/tags?${qs}`, { ...fetchInit, signal });
  if (!response.ok) {
    throw Object.assign(new Error("Failed to load tags"), {
      ...parseRegistryError(await response.json().catch(() => null)),
      status: response.status,
    });
  }
  return (await response.json()) as TagMapping[];
}

export async function createRegistryTag(
  body: TagMappingRequest,
  signal?: AbortSignal,
): Promise<
  | { ok: true; tag: TagMapping }
  | { ok: false; status: number; error: RegistryErrorResponse }
> {
  const response = await fetch("/api/registry/tags", {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify(body),
    ...fetchInit,
    signal,
  });
  const payload: unknown = await response.json().catch(() => null);
  if (response.status === 201) {
    return { ok: true, tag: payload as TagMapping };
  }
  return {
    ok: false,
    status: response.status,
    error: parseRegistryError(payload),
  };
}

export async function updateRegistryTag(
  id: string,
  body: TagMappingRequest,
  signal?: AbortSignal,
): Promise<
  | { ok: true; tag: TagMapping }
  | { ok: false; status: number; error: RegistryErrorResponse }
> {
  const response = await fetch(`/api/registry/tags/${id}`, {
    method: "PUT",
    headers: jsonHeaders(),
    body: JSON.stringify(body),
    ...fetchInit,
    signal,
  });
  const payload: unknown = await response.json().catch(() => null);
  if (response.ok) {
    return { ok: true, tag: payload as TagMapping };
  }
  return {
    ok: false,
    status: response.status,
    error: parseRegistryError(payload),
  };
}

export async function deleteRegistryTag(
  id: string,
  signal?: AbortSignal,
): Promise<
  { ok: true } | { ok: false; status: number; error: RegistryErrorResponse }
> {
  const response = await fetch(`/api/registry/tags/${id}`, {
    method: "DELETE",
    headers: jsonHeaders(),
    ...fetchInit,
    signal,
  });
  if (response.status === 204) {
    return { ok: true };
  }
  const payload: unknown = await response.json().catch(() => null);
  return {
    ok: false,
    status: response.status,
    error: parseRegistryError(payload),
  };
}

async function readJson(response: Response): Promise<unknown> {
  return response.json().catch(() => null);
}

export async function listSites(signal?: AbortSignal): Promise<SiteResponse[]> {
  const response = await fetch("/api/registry/sites", { ...fetchInit, signal });
  if (!response.ok) {
    throw Object.assign(new Error("Failed to load sites"), {
      ...parseRegistryError(await readJson(response)),
      status: response.status,
    });
  }
  return (await response.json()) as SiteResponse[];
}

export async function listLines(
  siteCode?: string,
  signal?: AbortSignal,
): Promise<LineResponse[]> {
  const query = siteCode ? `?siteCode=${encodeURIComponent(siteCode)}` : "";
  const response = await fetch(`/api/registry/lines${query}`, { ...fetchInit, signal });
  if (!response.ok) {
    throw Object.assign(new Error("Failed to load lines"), {
      ...parseRegistryError(await readJson(response)),
      status: response.status,
    });
  }
  return (await response.json()) as LineResponse[];
}

export async function getSite(
  siteCode: string,
  signal?: AbortSignal,
): Promise<{ status: number; site: SiteResponse | null; error: RegistryErrorResponse | null }> {
  const response = await fetch(
    `/api/registry/sites/${encodeRegistryPathSegment(siteCode)}`,
    { ...fetchInit, signal },
  );
  const payload = await readJson(response);
  if (response.ok) {
    return { status: response.status, site: payload as SiteResponse, error: null };
  }
  return { status: response.status, site: null, error: parseRegistryError(payload) };
}

export async function getLine(
  siteCode: string,
  lineCode: string,
  signal?: AbortSignal,
): Promise<{ status: number; line: LineResponse | null; error: RegistryErrorResponse | null }> {
  const response = await fetch(
    `/api/registry/lines/${encodeRegistryPathSegment(siteCode)}/${encodeRegistryPathSegment(lineCode)}`,
    { ...fetchInit, signal },
  );
  const payload = await readJson(response);
  if (response.ok) {
    return { status: response.status, line: payload as LineResponse, error: null };
  }
  return { status: response.status, line: null, error: parseRegistryError(payload) };
}

export async function createSite(
  body: SiteRequest,
  signal?: AbortSignal,
): Promise<
  | { ok: true; created: boolean; site: SiteResponse }
  | { ok: false; status: number; error: RegistryErrorResponse }
> {
  const response = await fetch("/api/registry/sites", {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify(body),
    ...fetchInit,
    signal,
  });
  const payload = await readJson(response);
  if (response.status === 201 || response.status === 200) {
    return { ok: true, created: response.status === 201, site: payload as SiteResponse };
  }
  return { ok: false, status: response.status, error: parseRegistryError(payload) };
}

export async function createLine(
  body: LineRequest,
  signal?: AbortSignal,
): Promise<
  | { ok: true; created: boolean; line: LineResponse }
  | { ok: false; status: number; error: RegistryErrorResponse }
> {
  const response = await fetch("/api/registry/lines", {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify(body),
    ...fetchInit,
    signal,
  });
  const payload = await readJson(response);
  if (response.status === 201 || response.status === 200) {
    return { ok: true, created: response.status === 201, line: payload as LineResponse };
  }
  return { ok: false, status: response.status, error: parseRegistryError(payload) };
}

export async function updateSite(
  siteCode: string,
  body: SiteUpdateRequest,
  signal?: AbortSignal,
): Promise<
  | { ok: true; site: SiteResponse }
  | { ok: false; status: number; error: RegistryErrorResponse }
> {
  const response = await fetch(`/api/registry/sites/${encodeRegistryPathSegment(siteCode)}`, {
    method: "PUT",
    headers: jsonHeaders(),
    body: JSON.stringify(body),
    ...fetchInit,
    signal,
  });
  const payload = await readJson(response);
  if (response.ok) {
    return { ok: true, site: payload as SiteResponse };
  }
  return { ok: false, status: response.status, error: parseRegistryError(payload) };
}

export async function updateLine(
  siteCode: string,
  lineCode: string,
  body: LineUpdateRequest,
  signal?: AbortSignal,
): Promise<
  | { ok: true; line: LineResponse }
  | { ok: false; status: number; error: RegistryErrorResponse }
> {
  const path = `/api/registry/lines/${encodeRegistryPathSegment(siteCode)}/${encodeRegistryPathSegment(lineCode)}`;
  const response = await fetch(path, {
    method: "PUT",
    headers: jsonHeaders(),
    body: JSON.stringify(body),
    ...fetchInit,
    signal,
  });
  const payload = await readJson(response);
  if (response.ok) {
    return { ok: true, line: payload as LineResponse };
  }
  return { ok: false, status: response.status, error: parseRegistryError(payload) };
}

export async function importTagsBatch(
  rows: TagMappingRequest[],
  signal?: AbortSignal,
): Promise<
  | { ok: true; body: TagBatchResponse }
  | { ok: false; status: number; body: TagBatchResponse | null; error: RegistryErrorResponse }
> {
  const response = await fetch("/api/registry/tags/batch", {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify(rows),
    ...fetchInit,
    signal,
  });
  const payload = await readJson(response);
  if (!response.ok) {
    return {
      ok: false,
      status: response.status,
      body: null,
      error: parseRegistryError(payload),
    };
  }
  const body = payload as TagBatchResponse;
  if ((body.failed ?? 0) > 0) {
    return {
      ok: false,
      status: response.status,
      body,
      error: {
        code: "BATCH_ROW_FAILED",
        message: "One or more tag rows failed",
        fieldErrors: [],
      },
    };
  }
  return { ok: true, body };
}