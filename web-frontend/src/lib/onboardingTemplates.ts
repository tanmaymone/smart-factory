import type { TagMappingRequest } from "@/lib/types";

const NSU = "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/";

export function prosysReactorDrafts(
  siteId: string,
  lineId: string,
  endpointUrl: string,
): TagMappingRequest[] {
  const base = {
    siteId,
    lineId,
    protocol: "OPC_UA" as const,
    endpointUrl: endpointUrl.trim(),
    dataType: "FLOAT" as const,
    unit: "",
    minValue: null,
    maxValue: null,
    deadband: 0.1,
    warningLow: null,
    warningHigh: null,
    criticalLow: null,
    criticalHigh: null,
    interlockRuleKey: null,
    lifecycle: "DRAFT" as const,
  };
  return [
    {
      ...base,
      tagCode: "VALVE_101_POS",
      description: "Inlet valve position",
      externalAddress: `${NSU};i=1007`,
      writable: true,
    },
    {
      ...base,
      tagCode: "REACTOR_TEMP_C",
      description: "Reactor temperature",
      externalAddress: `${NSU};i=1002`,
      writable: false,
    },
    {
      ...base,
      tagCode: "REACTOR_TEMP_SP",
      description: "Reactor temperature setpoint",
      externalAddress: `${NSU};i=1007`,
      writable: true,
    },
  ];
}
