import type {
  OnboardingProbeResponse,
  ProbeQuality,
  SecurityMode,
  TagDiagnostic,
} from "@/lib/types";

export type ProbeRow = {
  tagCode: string;
  protocol: string;
  externalAddress: string;
  endpointUrl: string;
};

export type ProbeSubject = {
  protocol: string;
  endpointUrl: string;
  externalAddress: string;
  securityMode: SecurityMode;
  probeRunId: number;
  wizardGeneration: number;
  quality: ProbeQuality;
};

export function collectProbeNodeIds(rows: ProbeRow[]): string[] {
  const seen = new Set<string>();
  const ids: string[] = [];
  for (const row of rows) {
    if (row.protocol !== "OPC_UA") continue;
    const address = row.externalAddress.trim();
    if (!address || seen.has(address)) continue;
    seen.add(address);
    ids.push(address);
  }
  return ids;
}

export function chunkNodeIds(ids: string[], size = 32): string[][] {
  const chunks: string[][] = [];
  for (let index = 0; index < ids.length; index += size) {
    chunks.push(ids.slice(index, index + size));
  }
  return chunks;
}

export function fanOutDiagnostics(
  rows: ProbeRow[],
  diagnostics: TagDiagnostic[],
): Record<string, TagDiagnostic> {
  const byNode = new Map<string, TagDiagnostic>();
  for (const diagnostic of diagnostics) {
    byNode.set(diagnostic.nodeId.trim(), diagnostic);
  }
  const mapped: Record<string, TagDiagnostic> = {};
  for (const row of rows) {
    const hit = byNode.get(row.externalAddress.trim());
    if (hit) mapped[row.tagCode.trim()] = hit;
  }
  return mapped;
}

export type ChunkResult =
  | { ok: true; body: OnboardingProbeResponse }
  | { ok: false; status: number; network?: boolean; body?: OnboardingProbeResponse | null };

export function foldProbeChunks(results: ChunkResult[]): {
  commit: boolean;
  diagnostics: TagDiagnostic[];
  error: string | null;
} {
  const diagnostics: TagDiagnostic[] = [];
  for (const result of results) {
    if (!result.ok || result.body.reachable !== true || result.body.error != null) {
      return { commit: false, diagnostics: [], error: result.ok ? result.body.error : `HTTP ${result.status}` };
    }
    diagnostics.push(...(result.body.tags ?? []));
  }
  return { commit: true, diagnostics, error: null };
}

export function subjectsFromRows(
  rows: ProbeRow[],
  diagnostics: Record<string, TagDiagnostic>,
  securityMode: SecurityMode,
  probeRunId: number,
  wizardGeneration: number,
): Record<string, ProbeSubject> {
  const subjects: Record<string, ProbeSubject> = {};
  for (const row of rows) {
    const code = row.tagCode.trim();
    const diagnostic = diagnostics[code];
    if (!diagnostic) continue;
    subjects[code] = {
      protocol: row.protocol,
      endpointUrl: row.endpointUrl.trim(),
      externalAddress: row.externalAddress.trim(),
      securityMode,
      probeRunId,
      wizardGeneration,
      quality: diagnostic.quality,
    };
  }
  return subjects;
}
