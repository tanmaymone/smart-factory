"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { fetchCurrentUser, logoutSession, sanitizeNextPath } from "@/lib/authApi";
import type { AuthUser } from "@/lib/types";

type AuthState =
  | { status: "loading"; user: null }
  | { status: "authenticated"; user: AuthUser }
  | { status: "anonymous"; user: null };

type AuthContextValue = {
  state: AuthState;
  refresh: () => Promise<void>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return ctx;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: "loading", user: null });
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const router = useRouter();

  const refresh = useCallback(async () => {
    const result = await fetchCurrentUser();
    if (result.ok) {
      setState({ status: "authenticated", user: result.user });
      return;
    }
    setState({ status: "anonymous", user: null });
  }, []);

  const signOut = useCallback(async () => {
    await logoutSession();
    setState({ status: "anonymous", user: null });
    router.replace("/login");
  }, [router]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (state.status === "loading") {
      return;
    }
    if (pathname === "/login") {
      if (state.status === "authenticated") {
        const next = sanitizeNextPath(searchParams.get("next"));
        router.replace(next);
      }
      return;
    }
    if (state.status === "anonymous") {
      const next = encodeURIComponent(`${pathname}?${searchParams.toString()}`);
      router.replace(`/login?next=${next}`);
    }
  }, [state.status, pathname, router, searchParams]);

  const value = useMemo(
    () => ({
      state,
      refresh,
      signOut,
    }),
    [state, refresh, signOut],
  );

  if (state.status === "loading" && pathname !== "/login") {
    return (
      <ViewLoadingShell message="Checking session…" />
    );
  }

  if (state.status === "anonymous" && pathname !== "/login") {
    return <ViewLoadingShell message="Redirecting to sign in…" />;
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function ViewLoadingShell({ message }: { message: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-950 text-slate-400">
      <p className="text-sm">{message}</p>
    </div>
  );
}