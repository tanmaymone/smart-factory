import type {
  RegistryErrorResponse,
  TagMapping,
  TagMappingRequest,
} from "@/lib/types";

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
  const response = await fetch(`/api/registry/tags?${qs}`, {
    signal,
    cache: "no-store",
  });
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
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal,
    cache: "no-store",
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
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal,
    cache: "no-store",
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
    signal,
    cache: "no-store",
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
