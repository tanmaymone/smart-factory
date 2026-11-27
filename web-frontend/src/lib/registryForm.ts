import type { TagMapping, TagMappingRequest } from "@/lib/types";

export function emptyRegistryForm(
  siteId: string,
  lineId: string,
): TagMappingRequest {
  return {
    siteId,
    lineId,
    tagCode: "",
    description: "",
    protocol: "OPC_UA",
    endpointUrl: "opc.tcp://localhost:53530/OPCUA/SimulationServer",
    externalAddress: "",
    dataType: "FLOAT",
    unit: "",
    writable: false,
    minValue: null,
    maxValue: null,
    deadband: 0,
    warningLow: null,
    warningHigh: null,
    criticalLow: null,
    criticalHigh: null,
    interlockRuleKey: null,
    lifecycle: undefined,
  };
}

export function tagMappingToRequest(tag: TagMapping): TagMappingRequest {
  return {
    siteId: tag.siteId,
    lineId: tag.lineId,
    tagCode: tag.tagCode,
    description: tag.description,
    protocol: tag.protocol,
    endpointUrl: tag.endpointUrl,
    externalAddress: tag.externalAddress,
    dataType: tag.dataType,
    unit: tag.unit ?? "",
    writable: tag.writable,
    minValue: tag.minValue,
    maxValue: tag.maxValue,
    deadband: tag.deadband,
    warningLow: tag.warningLow,
    warningHigh: tag.warningHigh,
    criticalLow: tag.criticalLow,
    criticalHigh: tag.criticalHigh,
    interlockRuleKey: tag.interlockRuleKey,
    lifecycle: tag.lifecycle,
  };
}

export function normalizeRegistryRequest(
  form: TagMappingRequest,
  locked?: { siteId: string; lineId: string },
): TagMappingRequest {
  return {
    ...form,
    siteId: locked?.siteId ?? form.siteId,
    lineId: locked?.lineId ?? form.lineId,
    unit: form.unit?.trim() ? form.unit.trim() : null,
    interlockRuleKey: form.interlockRuleKey?.trim()
      ? form.interlockRuleKey.trim()
      : null,
  };
}
