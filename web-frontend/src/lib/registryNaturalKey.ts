/** M3.4 EDGE_SITE_ID pattern. Same rule for lineCode. */
export const REGISTRY_NATURAL_KEY = /^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/;

export function isValidRegistryNaturalKey(value: string | null | undefined): boolean {
  return REGISTRY_NATURAL_KEY.test((value ?? "").trim());
}
