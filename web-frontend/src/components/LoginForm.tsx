"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { loginWithPassword, sanitizeNextPath } from "@/lib/authApi";
import { useAuth } from "@/components/AuthProvider";

export function LoginForm() {
  const [actorId, setActorId] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const searchParams = useSearchParams();
  const router = useRouter();
  const { refresh } = useAuth();

  const onSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (busy) {
      return;
    }
    setBusy(true);
    setError(null);
    const result = await loginWithPassword({ actorId: actorId.trim(), password });
    setBusy(false);
    if (!result.ok) {
      setError(result.error.message);
      return;
    }
    await refresh();
    router.replace(sanitizeNextPath(searchParams.get("next")));
  };

  return (
    <form onSubmit={(event) => void onSubmit(event)} className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="actor-id">User ID</Label>
        <Input
          id="actor-id"
          value={actorId}
          onChange={(event) => setActorId(event.target.value)}
          autoComplete="username"
          disabled={busy}
        />
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="password">Password</Label>
        <Input
          id="password"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="current-password"
          disabled={busy}
        />
      </div>
      {error ? <p className="text-sm text-rose-400">{error}</p> : null}
      <Button type="submit" disabled={busy || !actorId.trim() || !password}>
        {busy ? "Signing in…" : "Sign in"}
      </Button>
    </form>
  );
}