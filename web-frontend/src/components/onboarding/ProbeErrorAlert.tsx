import { probeErrorCopy, type ParsedProbeError } from "@/lib/onboardingApi";

export function ProbeErrorAlert({ error }: { error: ParsedProbeError | null }) {
  if (!error) return null;
  return (
    <p className="rounded-md border border-rose-500/40 bg-slate-950 px-3 py-2 text-sm text-rose-300">
      {error.code}: {probeErrorCopy(error)}
    </p>
  );
}
