import type { ProbeSubject } from "@/lib/onboardingProbe";
import type { SecurityMode, TagDiagnostic, TagMappingRequest } from "@/lib/types";

export type WizardPersisted = {
  step: 1 | 2 | 3 | 4;
  siteId: string;
  lineId: string;
  siteName: string;
  siteTimezone: string;
  lineName: string;
  endpointUrl: string;
  securityMode: SecurityMode;
  drafts: TagMappingRequest[];
  savedTagCodes: string[];
  step3Dirty: boolean;
};

export type WizardMemory = {
  wizardGeneration: number;
  smokeGeneration: number;
  probeRunId: number;
  step3EditRevision: number;
  siteNameEditRevision: number;
  siteTimezoneEditRevision: number;
  lineNameEditRevision: number;
  siteNameDirty: boolean;
  siteTimezoneDirty: boolean;
  lineNameDirty: boolean;
  step2Passed: boolean;
  probeDiagnostics: Record<string, TagDiagnostic>;
  probeSubject: Record<string, ProbeSubject>;
  preActivationProbePassed: boolean;
  activatedTagCodes: string[];
};

export type WizardState = WizardPersisted & WizardMemory;

const STORAGE_PREFIX = "sf-onboarding-wizard-v1";

export function storageKey(siteId: string, lineId: string): string {
  return `${STORAGE_PREFIX}:${siteId}:${lineId}`;
}

export function emptyWizard(siteId: string, lineId: string): WizardState {
  return {
    step: 1,
    siteId,
    lineId,
    siteName: "",
    siteTimezone: "Asia/Kolkata",
    lineName: "",
    endpointUrl: "opc.tcp://localhost:53530/OPCUA/SimulationServer",
    securityMode: "None",
    drafts: [],
    savedTagCodes: [],
    step3Dirty: false,
    wizardGeneration: 1,
    smokeGeneration: 1,
    probeRunId: 0,
    step3EditRevision: 0,
    siteNameEditRevision: 0,
    siteTimezoneEditRevision: 0,
    lineNameEditRevision: 0,
    siteNameDirty: false,
    siteTimezoneDirty: false,
    lineNameDirty: false,
    step2Passed: false,
    probeDiagnostics: {},
    probeSubject: {},
    preActivationProbePassed: false,
    activatedTagCodes: [],
  };
}

export function serializeWizard(state: WizardState): WizardPersisted {
  return {
    step: state.step,
    siteId: state.siteId,
    lineId: state.lineId,
    siteName: state.siteName,
    siteTimezone: state.siteTimezone,
    lineName: state.lineName,
    endpointUrl: state.endpointUrl,
    securityMode: state.securityMode,
    drafts: state.drafts,
    savedTagCodes: state.savedTagCodes,
    step3Dirty: state.step3Dirty,
  };
}

export function restoreWizard(raw: Partial<WizardPersisted> | null, fallback: WizardState): WizardState {
  if (!raw) return fallback;
  const drafts = raw.drafts ?? [];
  const step3Dirty = raw.step3Dirty == null ? drafts.length > 0 : raw.step3Dirty;
  const storedStep = raw.step ?? 1;
  const step: WizardPersisted["step"] = storedStep >= 2 ? 2 : 1;
  return {
    ...emptyWizard(raw.siteId ?? fallback.siteId, raw.lineId ?? fallback.lineId),
    ...raw,
    drafts,
    savedTagCodes: raw.savedTagCodes ?? [],
    step3Dirty,
    step,
    step2Passed: false,
    probeDiagnostics: {},
    probeSubject: {},
    preActivationProbePassed: false,
  };
}

export function advanceAfterStep2(state: WizardState): WizardState {
  if (!state.step2Passed) return state;
  if (state.savedTagCodes.length > 0 && !state.step3Dirty) {
    return { ...state, step: 4 };
  }
  return { ...state, step: state.drafts.length > 0 ? 3 : 2 };
}

export function invalidateFromStep1(state: WizardState, siteId: string, lineId: string): WizardState {
  const next = emptyWizard(siteId, lineId);
  return {
    ...next,
    wizardGeneration: state.wizardGeneration + 1,
    smokeGeneration: state.smokeGeneration + 1,
    probeRunId: state.probeRunId + 1,
  };
}

export function invalidateFromStep2(
  state: WizardState,
  patch: { endpointUrl?: string; securityMode?: SecurityMode },
): WizardState {
  const endpointChanged = patch.endpointUrl != null && patch.endpointUrl.trim() !== state.endpointUrl;
  return {
    ...state,
    endpointUrl: patch.endpointUrl?.trim() ?? state.endpointUrl,
    securityMode: patch.securityMode ?? state.securityMode,
    wizardGeneration: state.wizardGeneration + 1,
    probeRunId: state.probeRunId + 1,
    step2Passed: false,
    probeDiagnostics: {},
    probeSubject: {},
    preActivationProbePassed: false,
    step3Dirty: endpointChanged && (state.savedTagCodes.length > 0 || state.drafts.length > 0) ? true : state.step3Dirty,
  };
}

export function bumpStep3Edit(state: WizardState): WizardState {
  return { ...state, step3EditRevision: state.step3EditRevision + 1, step3Dirty: true };
}

export function bumpFieldEdit(
  state: WizardState,
  field: "siteName" | "siteTimezone" | "lineName",
  value: string,
): WizardState {
  if (field === "siteName") {
    return { ...state, siteName: value, siteNameDirty: true, siteNameEditRevision: state.siteNameEditRevision + 1 };
  }
  if (field === "siteTimezone") {
    return { ...state, siteTimezone: value, siteTimezoneDirty: true, siteTimezoneEditRevision: state.siteTimezoneEditRevision + 1 };
  }
  return { ...state, lineName: value, lineNameDirty: true, lineNameEditRevision: state.lineNameEditRevision + 1 };
}

export function applyMetadataField(
  state: WizardState,
  field: "siteName" | "siteTimezone" | "lineName",
  value: string,
  captured: { generation: number; revision: number },
): WizardState {
  if (state.wizardGeneration !== captured.generation) return state;
  if (field === "siteName" && state.siteNameEditRevision === captured.revision && !state.siteNameDirty) {
    return { ...state, siteName: value };
  }
  if (field === "siteTimezone" && state.siteTimezoneEditRevision === captured.revision && !state.siteTimezoneDirty) {
    return { ...state, siteTimezone: value };
  }
  if (field === "lineName" && state.lineNameEditRevision === captured.revision && !state.lineNameDirty) {
    return { ...state, lineName: value };
  }
  return state;
}

export function classifyCreateStatus(status: number): "created" | "collision" | "error" {
  if (status === 201) return "created";
  if (status === 200) return "collision";
  return "error";
}

export function applyCreateSuccess(
  state: WizardState,
  which: "site" | "line",
  captured: { generation: number; siteNameRevision: number; timezoneRevision: number; lineRevision: number },
): WizardState {
  if (state.wizardGeneration !== captured.generation) return state;
  if (which === "site") {
    return {
      ...state,
      siteNameDirty: state.siteNameEditRevision === captured.siteNameRevision ? false : state.siteNameDirty,
      siteTimezoneDirty: state.siteTimezoneEditRevision === captured.timezoneRevision ? false : state.siteTimezoneDirty,
    };
  }
  return {
    ...state,
    lineNameDirty: state.lineNameEditRevision === captured.lineRevision ? false : state.lineNameDirty,
  };
}

export function applySaveSuccess(
  state: WizardState,
  savedTagCodes: string[],
  captured: { generation: number; step3EditRevision: number },
): WizardState {
  if (state.wizardGeneration !== captured.generation || state.step3EditRevision !== captured.step3EditRevision) {
    return state;
  }
  return { ...state, savedTagCodes, step3Dirty: false };
}

export function beginProbe(state: WizardState): WizardState {
  return {
    ...state,
    probeRunId: state.probeRunId + 1,
    step2Passed: false,
    probeDiagnostics: {},
    probeSubject: {},
    preActivationProbePassed: false,
  };
}

export function commitProbe(
  state: WizardState,
  captured: { probeRunId: number; generation: number; step3EditRevision: number },
  diagnostics: WizardState["probeDiagnostics"],
  subjects: WizardState["probeSubject"],
  step2: boolean,
): WizardState {
  if (
    state.probeRunId !== captured.probeRunId ||
    state.wizardGeneration !== captured.generation ||
    state.step3EditRevision !== captured.step3EditRevision
  ) {
    return state;
  }
  return {
    ...state,
    step2Passed: step2 ? true : state.step2Passed,
    probeDiagnostics: diagnostics,
    probeSubject: subjects,
    preActivationProbePassed: !step2 && Object.keys(subjects).length > 0,
  };
}
