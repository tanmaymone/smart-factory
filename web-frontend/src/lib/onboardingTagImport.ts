import type { TagMappingRequest } from "@/lib/types";

export function parseTagImport(
  raw: string,
  context: { siteId: string; lineId: string; endpointUrl: string },
): { drafts: TagMappingRequest[]; error: string | null } {
  const text = raw.trim();
  if (!text) return { drafts: [], error: "Paste JSON or CSV first." };
  if (text.startsWith("{") || text.startsWith("[")) {
    try {
      const parsed: unknown = JSON.parse(text);
      const rows = Array.isArray(parsed)
        ? parsed
        : parsed != null && typeof parsed === "object" && Array.isArray((parsed as { tags?: unknown }).tags)
          ? (parsed as { tags: unknown[] }).tags
          : null;
      if (!rows) return { drafts: [], error: "JSON must be an array or {tags:[]}." };
      return { drafts: rows.map((row) => coerceRow(row, context)), error: null };
    } catch {
      return { drafts: [], error: "JSON could not be parsed." };
    }
  }
  const [header, ...lines] = text.split(/\r?\n/).filter((line) => line.trim());
  if (!header) return { drafts: [], error: "CSV needs a header row." };
  const columns = header.split(",").map((cell) => cell.trim());
  const drafts = lines.map((line) => {
    const cells = line.split(",");
    const record: Record<string, string> = {};
    columns.forEach((column, index) => {
      record[column] = (cells[index] ?? "").trim();
    });
    return coerceRow(record, context);
  });
  return { drafts, error: null };
}

function coerceRow(
  row: unknown,
  context: { siteId: string; lineId: string; endpointUrl: string },
): TagMappingRequest {
  const source = row != null && typeof row === "object" ? (row as Record<string, unknown>) : {};
  const text = (key: string) => (typeof source[key] === "string" ? source[key] : "");
  return {
    siteId: context.siteId,
    lineId: context.lineId,
    tagCode: String(text("tagCode")).trim(),
    description: String(text("description")),
    protocol: "OPC_UA",
    endpointUrl: context.endpointUrl.trim(),
    externalAddress: String(text("externalAddress")).trim(),
    dataType: "FLOAT",
    unit: text("unit") || null,
    writable: source.writable === true || source.writable === "true",
    minValue: null,
    maxValue: null,
    deadband: Number(source.deadband ?? 0) || 0,
    warningLow: null,
    warningHigh: null,
    criticalLow: null,
    criticalHigh: null,
    interlockRuleKey: null,
    lifecycle: "DRAFT",
  };
}
