import type { ActorRole } from "@/lib/types";

export function canWriteCommands(role: ActorRole | null | undefined): boolean {
  return role === "OPERATOR" || role === "SUPERVISOR";
}

export function canManageRegistry(role: ActorRole | null | undefined): boolean {
  return role === "ONBOARDING_ADMIN";
}

export function canRunOnboardingProbe(role: ActorRole | null | undefined): boolean {
  return role === "ONBOARDING_ADMIN";
}

export function canReadConsole(role: ActorRole | null | undefined): boolean {
  return (
    role === "VIEWER" ||
    role === "OPERATOR" ||
    role === "SUPERVISOR" ||
    role === "ONBOARDING_ADMIN"
  );
}