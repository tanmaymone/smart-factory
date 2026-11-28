export type AlarmSeverity = "NONE" | "WARNING" | "CRITICAL";
export type AlarmDirection = "LOW" | "HIGH" | null;
export type Quality = "GOOD" | "BAD" | "UNCERTAIN" | string;
export type ProbeQuality = "GOOD" | "BAD" | "UNREACHABLE";
export type RegistryLifecycle = "DRAFT" | "VERIFIED" | "ACTIVE";
export type SiteStatus = "ONLINE" | "DEGRADED" | "OFFLINE";
export type SecurityMode = "None" | "SignAndEncrypt";

export type Protocol = "OPC_UA" | "S7" | "MODBUS_TCP" | "BACNET_IP";
export type TagDataType = "FLOAT" | "INT" | "BOOLEAN" | "STRING";

export type CommandStatus =
  | "VALIDATED_DISPATCHED"
  | "REJECTED_VALIDATION"
  | "REJECTED_INTERLOCK"
  | "ACKED"
  | "FAILED_EDGE"
  | "TIMEOUT";

export const COMMAND_STATUSES: readonly CommandStatus[] = [
  "VALIDATED_DISPATCHED",
  "REJECTED_VALIDATION",
  "REJECTED_INTERLOCK",
  "ACKED",
  "FAILED_EDGE",
  "TIMEOUT",
] as const;

export type LatestTagState = {
  siteId: string;
  lineId: string;
  tagCode: string;
  value: unknown;
  quality: Quality | null;
  timestamp: string | null;
  severity: AlarmSeverity;
  direction: AlarmDirection;
  alarmFrozen: boolean;
  alarmValue: unknown;
  alarmTimestamp: string | null;
  observedDataType?: string | null;
  nodeWritable?: boolean | null;
};

export type TagMapping = {
  id: string;
  siteId: string;
  lineId: string;
  tagCode: string;
  description: string;
  protocol: Protocol;
  endpointUrl: string;
  externalAddress: string;
  dataType: TagDataType;
  unit: string | null;
  writable: boolean;
  minValue: number | null;
  maxValue: number | null;
  deadband: number;
  warningLow: number | null;
  warningHigh: number | null;
  criticalLow: number | null;
  criticalHigh: number | null;
  interlockRuleKey: string | null;
  lifecycle?: RegistryLifecycle;
};

export type TagMappingRequest = {
  siteId: string;
  lineId: string;
  tagCode: string;
  description: string;
  protocol: Protocol;
  endpointUrl: string;
  externalAddress: string;
  dataType: TagDataType;
  unit: string | null;
  writable: boolean;
  minValue: number | null;
  maxValue: number | null;
  deadband: number;
  warningLow: number | null;
  warningHigh: number | null;
  criticalLow: number | null;
  criticalHigh: number | null;
  interlockRuleKey: string | null;
  lifecycle?: RegistryLifecycle;
};

export type SiteResponse = {
  siteCode: string;
  name: string;
  timezone: string;
  status?: SiteStatus;
  lifecycle: RegistryLifecycle;
  siteLabel?: string;
  lineLabel?: string;
};

export type LineResponse = {
  siteCode: string;
  lineCode: string;
  name: string;
  lifecycle: RegistryLifecycle;
};

export type SiteRequest = {
  siteCode: string;
  name: string;
  timezone: string;
  lifecycle?: RegistryLifecycle;
  siteLabel?: string;
  lineLabel?: string;
};

export type SiteUpdateRequest = {
  name: string;
  timezone: string;
  lifecycle: RegistryLifecycle;
  siteLabel?: string;
  lineLabel?: string;
};

export type LineRequest = {
  siteCode: string;
  lineCode: string;
  name: string;
  lifecycle?: RegistryLifecycle;
};

export type LineUpdateRequest = {
  name: string;
  lifecycle: RegistryLifecycle;
};

export type TagBatchRowError = { field: string; code: string; message: string };

export type TagBatchRowResult = {
  index: number;
  tagCode: string;
  outcome: "CREATED" | "UPDATED" | "FAILED";
  id?: string | null;
  errors: TagBatchRowError[];
};

export type TagBatchResponse = {
  received: number;
  created: number;
  updated: number;
  failed: number;
  rows: TagBatchRowResult[];
};

export type OnboardingProbeRequest = {
  securityMode: SecurityMode;
  endpointUrl: string;
  nodeIds?: string[];
};

export type TagDiagnostic = {
  nodeId: string;
  reachability?: string;
  value?: unknown;
  dataType?: string | null;
  quality: ProbeQuality;
  statusName?: string | null;
  error?: string | null;
};

export type OnboardingProbeResponse = {
  endpointUrl: string;
  securityMode: string;
  reachable: boolean;
  error: string | null;
  tags: TagDiagnostic[];
};

export type ConnectionStatus =
  | "connecting"
  | "live"
  | "reconnecting"
  | "disconnected"
  | "at-capacity";

export type TelemetryRow = {
  tagId: string | null;
  tagCode: string;
  description: string;
  unit: string;
  deadband: number | null;
  registered: boolean;
  writable: boolean;
  dataType: TagDataType | null;
  minValue: number | null;
  maxValue: number | null;
  value: unknown;
  quality: Quality | null;
  timestamp: string | null;
  severity: AlarmSeverity;
  direction: AlarmDirection;
  alarmFrozen: boolean;
  alarmValue: unknown;
  alarmTimestamp: string | null;
  observedDataType: string | null;
  nodeWritable: boolean | null;
  hasSample: boolean;
  lifecycle?: RegistryLifecycle;
};

export type CommandRequest = {
  siteId: string;
  lineId: string;
  tagId: string;
  targetValue: unknown;
  requestedBy: string;
  reason: string;
};

export type CommandAcceptedResponse = {
  commandId: string;
  status: string;
};

export type CommandFieldError = {
  field: string;
  message: string;
};

export type CommandErrorResponse = {
  code: string;
  message: string;
  commandId: string | null;
  fieldErrors: CommandFieldError[];
};

export type CommandAuditResponse = {
  commandId: string;
  siteId: string;
  lineId: string;
  tagCode: string;
  requestedValue: string;
  previousValue: string | null;
  requestedBy: string;
  reason: string;
  status: string;
  rejectionReason: string | null;
  appliedValue: string | null;
  createdAt: string;
  ackedAt: string | null;
};

export type CommandUpdateEvent = {
  commandId: string;
  siteId: string;
  lineId: string;
  tagCode: string;
  status: string;
  appliedValue: unknown;
  errorMessage: string | null;
  executedAt: string | null;
};

export type RegistryFieldError = {
  field: string;
  message: string;
};

export type RegistryErrorResponse = {
  code: string;
  message: string;
  fieldErrors: RegistryFieldError[];
};

export type ChecklistCategory =
  | "OPC_UA_SECURITY"
  | "MQTT_BROKER_SECURITY"
  | "HEARTBEAT_AND_STALENESS"
  | "TIMESCALEDB_RETENTION"
  | "EDGE_PROVISIONING";

export type EquipmentTemplateSummary = {
  templateKey: string;
  displayName: string;
  equipmentType: string;
  description: string;
  defaultLineId: string;
  prosysReady: boolean;
  tagCount: number;
  supportedFormats: string[];
  downloadUrls: { json: string; csv: string };
};

export type EquipmentTemplateCatalogResponse = {
  schemaVersion: number;
  templates: EquipmentTemplateSummary[];
};

export type EquipmentTemplateJsonPayload = {
  tags: TagMappingRequest[];
};

export type PreCutoverChecklistItem = {
  id: string;
  category: ChecklistCategory;
  title: string;
  description: string;
  verificationMethod: string;
  verificationCommand: string;
  passCriteria: string;
  severity: "CUTOVER_BLOCKER" | "DEV_ADVISORY";
  runnableInCurrentRelease: boolean;
  milestoneGate: string | null;
  currentDevPosture: string;
  productionRequirement: string;
};

export type PreCutoverChecklistResponse = {
  schemaVersion: number;
  releaseScope: string;
  playbookPath: string;
  summary: {
    total: number;
    runnableNow: number;
    deferredMilestoneGates: number;
    cutoverBlockers: number;
  };
  items: PreCutoverChecklistItem[];
};
