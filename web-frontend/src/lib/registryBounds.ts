import type { TagMappingRequest } from "@/lib/types";

export type BoundsIssue = { field: string; message: string };

/** Mirrors TagBoundsValidator on the backend. */
export function validateTagBounds(
  request: Pick<
    TagMappingRequest,
    | "minValue"
    | "maxValue"
    | "deadband"
    | "warningLow"
    | "warningHigh"
    | "criticalLow"
    | "criticalHigh"
  >,
): BoundsIssue[] {
  const issues: BoundsIssue[] = [];
  if (request.deadband < 0) {
    issues.push({ field: "deadband", message: "deadband must be >= 0" });
  }
  if (
    request.minValue != null &&
    request.maxValue != null &&
    request.minValue > request.maxValue
  ) {
    issues.push({
      field: "minValue",
      message: "minValue must be less than or equal to maxValue",
    });
  }
  if (
    request.warningLow != null &&
    request.warningHigh != null &&
    request.warningLow > request.warningHigh
  ) {
    issues.push({
      field: "warningLow",
      message: "warningLow must be less than or equal to warningHigh",
    });
  }
  if (
    request.criticalLow != null &&
    request.criticalHigh != null &&
    request.criticalLow > request.criticalHigh
  ) {
    issues.push({
      field: "criticalLow",
      message: "criticalLow must be less than or equal to criticalHigh",
    });
  }
  if (
    request.criticalLow != null &&
    request.warningLow != null &&
    request.criticalLow > request.warningLow
  ) {
    issues.push({
      field: "criticalLow",
      message: "criticalLow must be less than or equal to warningLow",
    });
  }
  if (
    request.warningHigh != null &&
    request.criticalHigh != null &&
    request.warningHigh > request.criticalHigh
  ) {
    issues.push({
      field: "warningHigh",
      message: "warningHigh must be less than or equal to criticalHigh",
    });
  }
  return issues;
}
