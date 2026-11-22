export function commandStatusVariant(
  status: string,
): "cyan" | "emerald" | "amber" | "rose" | "slate" {
  if (status === "VALIDATED_DISPATCHED") {
    return "cyan";
  }
  if (status === "ACKED") {
    return "emerald";
  }
  if (status === "TIMEOUT") {
    return "amber";
  }
  if (
    status === "REJECTED_VALIDATION" ||
    status === "REJECTED_INTERLOCK" ||
    status === "FAILED_EDGE"
  ) {
    return "rose";
  }
  return "slate";
}
