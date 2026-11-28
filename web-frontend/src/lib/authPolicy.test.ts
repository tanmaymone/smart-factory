import { describe, expect, it } from "vitest";
import {
  canManageRegistry,
  canReadConsole,
  canRunOnboardingProbe,
  canWriteCommands,
} from "@/lib/authPolicy";

describe("SF-M42 auth policy", () => {
  it("gates roles for console, commands, registry, and probe", () => {
    expect(canWriteCommands("VIEWER")).toBe(false);
    expect(canWriteCommands("OPERATOR")).toBe(true);
    expect(canManageRegistry("OPERATOR")).toBe(false);
    expect(canManageRegistry("ONBOARDING_ADMIN")).toBe(true);
    expect(canRunOnboardingProbe("SUPERVISOR")).toBe(false);
    expect(canRunOnboardingProbe("ONBOARDING_ADMIN")).toBe(true);
    expect(canReadConsole("VIEWER")).toBe(true);
    expect(canReadConsole(null)).toBe(false);
  });
});