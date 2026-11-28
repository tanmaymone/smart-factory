import { parseCsvRecords } from "@/lib/rfc4180Csv";
import type { Protocol, TagDataType, TagMappingRequest } from "@/lib/types";

const PROTOCOLS: Protocol[] = ["OPC_UA", "S7", "MODBUS_TCP", "BACNET_IP"];
const DATA_TYPES: TagDataType[] = ["FLOAT", "INT", "BOOLEAN", "STRING"];

export const TAG_BATCH_CSV_HEADER =
  "siteId,lineId,tagCode,description,protocol,endpointUrl,externalAddress,dataType,unit,writable,minValue,maxValue,deadband,warningLow,warningHigh,criticalLow,criticalHigh,interlockRuleKey,lifecycle";

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

  const records = parseCsvRecords(text);
  if (records.length === 0) return { drafts: [], error: "CSV needs a header row." };
  const [headerRow, ...dataRows] = records;
  const columns = headerRow.map((cell) => cell.trim());
  if (columns.length === 0) return { drafts: [], error: "CSV needs a header row." };

  const drafts = dataRows.map((cells) => {
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
  const text = (key: string) => (typeof source[key] === "string" ? source[key] : String(source[key] ?? ""));
  const protocol = parseProtocol(text("protocol"));
  const dataType = parseDataType(text("dataType"));
  return {
    siteId: String(text("siteId")).trim() || context.siteId,
    lineId: String(text("lineId")).trim() || context.lineId,
    tagCode: String(text("tagCode")).trim(),
    description: String(text("description")),
    protocol,
    endpointUrl: String(text("endpointUrl")).trim() || context.endpointUrl.trim(),
    externalAddress: String(text("externalAddress")).trim(),
    dataType,
    unit: nullableText(text("unit")),
    writable: parseBool(source.writable),
    minValue: parseOptionalNumber(source.minValue),
    maxValue: parseOptionalNumber(source.maxValue),
    deadband: parseOptionalNumber(source.deadband) ?? 0,
    warningLow: parseOptionalNumber(source.warningLow),
    warningHigh: parseOptionalNumber(source.warningHigh),
    criticalLow: parseOptionalNumber(source.criticalLow),
    criticalHigh: parseOptionalNumber(source.criticalHigh),
    interlockRuleKey: nullableText(text("interlockRuleKey")),
    lifecycle: parseLifecycle(text("lifecycle")),
  };
}

function parseProtocol(value: string): Protocol {
  const normalized = value.trim().toUpperCase();
  return (PROTOCOLS as string[]).includes(normalized) ? (normalized as Protocol) : "OPC_UA";
}

function parseDataType(value: string): TagDataType {
  const normalized = value.trim().toUpperCase();
  return (DATA_TYPES as string[]).includes(normalized) ? (normalized as TagDataType) : "FLOAT";
}

function parseLifecycle(value: string): TagMappingRequest["lifecycle"] {
  const normalized = value.trim().toUpperCase();
  if (normalized === "ACTIVE" || normalized === "VERIFIED") return normalized;
  return "DRAFT";
}

function parseBool(value: unknown): boolean {
  if (value === true) return true;
  if (value === false) return false;
  const text = String(value ?? "").trim().toLowerCase();
  return text === "true" || text === "1" || text === "yes";
}

function parseOptionalNumber(value: unknown): number | null {
  if (value == null || value === "") return null;
  if (typeof value === "number" && Number.isFinite(value)) return value;
  const text = String(value).trim();
  if (!text || text.toLowerCase() === "null") return null;
  const parsed = Number(text);
  return Number.isFinite(parsed) ? parsed : null;
}

function nullableText(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}