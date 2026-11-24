export type AlarmSeverity = "NONE" | "WARNING" | "CRITICAL";
export type AlarmDirection = "LOW" | "HIGH" | null;
export type Quality = "GOOD" | "BAD" | "UNCERTAIN" | string;

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
