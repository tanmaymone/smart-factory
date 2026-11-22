import { hasSampleValue } from "@/lib/telemetryState";

const TIME_ZONE = "Asia/Kolkata";

export function formatTimestamp(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  const ms = Date.parse(value);
  if (Number.isNaN(ms)) {
    return "—";
  }
  return new Intl.DateTimeFormat("en-IN", {
    timeZone: TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(new Date(ms));
}

export function formatValue(value: unknown, unit: string): string {
  if (!hasSampleValue(value)) {
    return "";
  }
  let text: string;
  if (typeof value === "number") {
    text = Number.isInteger(value) ? String(value) : value.toFixed(2);
  } else if (typeof value === "boolean") {
    text = value ? "true" : "false";
  } else {
    text = String(value);
  }
  return unit ? `${text} ${unit}` : text;
}

export function formatDeadband(deadband: number | null): string {
  if (deadband == null) {
    return "—";
  }
  return String(deadband);
}
