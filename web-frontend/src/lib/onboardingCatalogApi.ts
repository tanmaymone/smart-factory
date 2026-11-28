import type {
  EquipmentTemplateCatalogResponse,
  EquipmentTemplateJsonPayload,
  PreCutoverChecklistResponse,
  ChecklistCategory,
} from "@/lib/types";

export type TemplateDownloadQuery = {
  siteId?: string;
  lineId?: string;
  endpointUrl?: string;
  format?: "json" | "csv";
  download?: boolean;
};

export function templateDownloadUrl(
  templateKey: string,
  query: TemplateDownloadQuery = {},
): string {
  const params = new URLSearchParams();
  params.set("format", query.format ?? "json");
  if (query.siteId) params.set("siteId", query.siteId);
  if (query.lineId) params.set("lineId", query.lineId);
  if (query.endpointUrl) params.set("endpointUrl", query.endpointUrl);
  if (query.download !== false) params.set("download", "true");
  return `/api/onboarding/templates/${encodeURIComponent(templateKey)}?${params.toString()}`;
}

export async function fetchEquipmentTemplateCatalog(
  signal?: AbortSignal,
): Promise<EquipmentTemplateCatalogResponse> {
  const response = await fetch("/api/onboarding/templates", { cache: "no-store", signal });
  if (!response.ok) {
    throw new Error(`Template catalog failed (${response.status})`);
  }
  return (await response.json()) as EquipmentTemplateCatalogResponse;
}

export async function fetchEquipmentTemplateJson(
  templateKey: string,
  query: Omit<TemplateDownloadQuery, "format" | "download"> = {},
  signal?: AbortSignal,
): Promise<EquipmentTemplateJsonPayload> {
  const url = templateDownloadUrl(templateKey, { ...query, format: "json", download: false });
  const response = await fetch(url, { cache: "no-store", signal });
  if (!response.ok) {
    throw new Error(`Template ${templateKey} failed (${response.status})`);
  }
  return (await response.json()) as EquipmentTemplateJsonPayload;
}

export async function fetchPreCutoverChecklist(
  category?: ChecklistCategory,
  signal?: AbortSignal,
): Promise<PreCutoverChecklistResponse> {
  const suffix = category ? `?category=${encodeURIComponent(category)}` : "";
  const response = await fetch(`/api/onboarding/checklist${suffix}`, { cache: "no-store", signal });
  if (!response.ok) {
    throw new Error(`Checklist failed (${response.status})`);
  }
  return (await response.json()) as PreCutoverChecklistResponse;
}

export const CHECKLIST_CATEGORY_ORDER: ChecklistCategory[] = [
  "OPC_UA_SECURITY",
  "MQTT_BROKER_SECURITY",
  "HEARTBEAT_AND_STALENESS",
  "TIMESCALEDB_RETENTION",
  "EDGE_PROVISIONING",
];

export function checklistCategoryLabel(category: ChecklistCategory): string {
  return category
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}