import { setupMutationHeaders } from "@/lib/registryApi";
import type { OnboardingProbeRequest, OnboardingProbeResponse } from "@/lib/types";

export type ProbeFieldError = { field: string; message: string; code?: string };

export type ParsedProbeError = {
  status: number;
  code: string;
  message: string;
  fieldErrors: ProbeFieldError[];
};

const FIELD_CODES = [
  "ENDPOINT_URL_INVALID",
  "SECURITY_MODE_UNSUPPORTED",
  "NODE_IDS_LIMIT_EXCEEDED",
  "NODE_ID_TOO_LONG",
] as const;

export function parseProbeError(payload: unknown, status: number): ParsedProbeError {
  const body =
    payload != null && typeof payload === "object"
      ? (payload as { code?: unknown; message?: unknown; fieldErrors?: unknown })
      : {};
  const fieldErrors = Array.isArray(body.fieldErrors)
    ? body.fieldErrors.flatMap((item) => {
        if (item == null || typeof item !== "object") {
          return [];
        }
        const row = item as { field?: unknown; message?: unknown; code?: unknown };
        const code = typeof row.code === "string" ? row.code : undefined;
        return [
          {
            field: typeof row.field === "string" ? row.field : "",
            message: typeof row.message === "string" ? row.message : code ?? "invalid",
            code,
          },
        ];
      })
    : [];
  const fromField = fieldErrors.find((item) =>
    item.code != null && (FIELD_CODES as readonly string[]).includes(item.code),
  );
  const top = typeof body.code === "string" ? body.code : "";
  const code = status === 422 && fromField?.code ? fromField.code : top || "UNKNOWN";
  return {
    status,
    code,
    message: typeof body.message === "string" ? body.message : code,
    fieldErrors,
  };
}

export function probeErrorCopy(error: ParsedProbeError): string {
  if (error.status === 403 && error.code === "FORBIDDEN") {
    return "Set NEXT_PUBLIC_BOOTSTRAP_TOKEN to the same value as APP_BOOTSTRAP_TOKEN.";
  }
  if (error.status === 401) {
    return "The setup token was rejected. Check APP_BOOTSTRAP_TOKEN.";
  }
  if (error.code === "PROBE_DISABLED") {
    return "Enable app.onboarding.probe-enabled on central and the edge.";
  }
  if (error.code === "EDGE_UNREACHABLE") {
    return "The edge process did not answer. Check EDGE_BASE_URL.";
  }
  if (error.code === "PROBE_IN_PROGRESS") {
    return "A probe is already running. Retry this chunk.";
  }
  return error.message;
}

export function isStep2Pass(body: OnboardingProbeResponse | null): boolean {
  return body != null && body.reachable === true && body.error == null;
}

export async function postOnboardingProbe(
  body: OnboardingProbeRequest,
  signal?: AbortSignal,
): Promise<
  | { ok: true; body: OnboardingProbeResponse }
  | { ok: false; error: ParsedProbeError }
> {
  const response = await fetch("/api/onboarding/probe", {
    method: "POST",
    headers: setupMutationHeaders(),
    body: JSON.stringify(body),
    signal,
    cache: "no-store",
  });
  const payload: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    return { ok: false, error: parseProbeError(payload, response.status) };
  }
  return { ok: true, body: payload as OnboardingProbeResponse };
}
