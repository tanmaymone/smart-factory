import { describe, expect, it } from "vitest";
import { isValidRegistryNaturalKey } from "@/lib/registryNaturalKey";

describe("SF-M35 registry natural key", () => {
  it("accepts the edge site id shape and rejects path breakers", () => {
    expect(isValidRegistryNaturalKey("site-test")).toBe(true);
    expect(isValidRegistryNaturalKey(" SITE-TEST ")).toBe(true);
    expect(isValidRegistryNaturalKey("bad/site")).toBe(false);
    expect(isValidRegistryNaturalKey("bad site")).toBe(false);
    expect(isValidRegistryNaturalKey("bad+site")).toBe(false);
    expect(isValidRegistryNaturalKey("a".repeat(65))).toBe(false);
  });
});
