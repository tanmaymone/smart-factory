import { Suspense } from "react";
import Image from "next/image";
import { LoginForm } from "@/components/LoginForm";

export default function LoginPage() {
  return (
    <main className="dot-grid relative flex min-h-screen items-center justify-center px-4 py-10">
      <div className="w-full max-w-md rounded-lg border border-slate-800 bg-slate-900/95 p-6 shadow-lg">
        <div className="mb-6 flex items-center gap-3">
          <Image
            src="/smart-factory-logo.png"
            alt="Smart Factory"
            width={40}
            height={40}
            className="h-10 w-10 object-contain"
          />
          <div>
            <p className="text-xs uppercase tracking-[0.2em] text-slate-400">Smart Factory</p>
            <h1 className="text-lg font-semibold text-slate-100">Sign in</h1>
          </div>
        </div>
        <Suspense fallback={<p className="text-sm text-slate-400">Loading…</p>}>
          <LoginForm />
        </Suspense>
      </div>
    </main>
  );
}