import type { AuthErrorResponse, AuthUser, LoginRequest } from "@/lib/types";

function parseAuthError(payload: unknown): AuthErrorResponse {
  if (payload == null || typeof payload !== "object") {
    return { code: "UNKNOWN", message: "Authentication failed", fieldErrors: [] };
  }
  const body = payload as Partial<AuthErrorResponse>;
  return {
    code: typeof body.code === "string" ? body.code : "UNKNOWN",
    message: typeof body.message === "string" ? body.message : "Authentication failed",
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

export function sanitizeNextPath(next: string | null): string {
  if (next == null || next === "") {
    return "/";
  }
  if (!next.startsWith("/") || next.startsWith("//")) {
    return "/";
  }
  return next;
}

export async function fetchCurrentUser(signal?: AbortSignal): Promise<
  | { ok: true; user: AuthUser }
  | { ok: false; status: number; error: AuthErrorResponse }
> {
  const response = await fetch("/api/auth/me", {
    credentials: "same-origin",
    cache: "no-store",
    signal,
  });
  const payload: unknown = await response.json().catch(() => null);
  if (response.ok) {
    return { ok: true, user: payload as AuthUser };
  }
  return { ok: false, status: response.status, error: parseAuthError(payload) };
}

export async function loginWithPassword(
  body: LoginRequest,
  signal?: AbortSignal,
): Promise<
  | { ok: true; user: AuthUser }
  | { ok: false; status: number; error: AuthErrorResponse }
> {
  const response = await fetch("/api/auth/login", {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    cache: "no-store",
    signal,
  });
  const payload: unknown = await response.json().catch(() => null);
  if (response.ok) {
    return { ok: true, user: payload as AuthUser };
  }
  return { ok: false, status: response.status, error: parseAuthError(payload) };
}

export async function logoutSession(signal?: AbortSignal): Promise<void> {
  await fetch("/api/auth/logout", {
    method: "POST",
    credentials: "same-origin",
    cache: "no-store",
    signal,
  });
}