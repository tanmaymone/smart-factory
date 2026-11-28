"use client";

import { useEffect, useRef, useState, type RefObject } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  fetchEquipmentTemplateCatalog,
  fetchEquipmentTemplateJson,
  templateDownloadUrl,
} from "@/lib/onboardingCatalogApi";
import type { EquipmentTemplateSummary } from "@/lib/types";
import type { TagMappingRequest } from "@/lib/types";

export function OnboardingStep3Templates({
  siteId,
  lineId,
  endpointUrl,
  disabled,
  draftCount,
  onLoadDrafts,
  onImportText,
}: {
  siteId: string;
  lineId: string;
  endpointUrl: string;
  disabled: boolean;
  draftCount: number;
  onLoadDrafts: (drafts: TagMappingRequest[]) => void;
  onImportText: (text: string) => void;
}) {
  const [templates, setTemplates] = useState<EquipmentTemplateSummary[]>([]);
  const [templateKey, setTemplateKey] = useState("reactor");
  const [busy, setBusy] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    void fetchEquipmentTemplateCatalog()
      .then((body) => {
        setTemplates(body.templates);
        if (body.templates[0]) {
          setTemplateKey(body.templates[0].templateKey);
        }
      })
      .catch(() => setTemplates([]));
  }, []);

  const selected = templates.find((row) => row.templateKey === templateKey);

  const loadJson = async () => {
    setBusy(true);
    try {
      const payload = await fetchEquipmentTemplateJson(templateKey, {
        siteId,
        lineId,
        endpointUrl,
      });
      onLoadDrafts(payload.tags);
      onImportText(JSON.stringify(payload.tags, null, 2));
    } finally {
      setBusy(false);
    }
  };

  return (
    <EquipmentTemplateBar
      templates={templates}
      templateKey={templateKey}
      disabled={disabled || busy}
      selected={selected}
      draftCount={draftCount}
      fileRef={fileRef}
      onTemplateKey={setTemplateKey}
      onLoadJson={() => void loadJson()}
      onDownloadCsv={() => {
        window.location.assign(
          templateDownloadUrl(templateKey, { siteId, lineId, endpointUrl, format: "csv" }),
        );
      }}
      onDownloadJson={() => {
        window.location.assign(
          templateDownloadUrl(templateKey, { siteId, lineId, endpointUrl, format: "json" }),
        );
      }}
      onFilePick={(text) => onImportText(text)}
    />
  );
}

function EquipmentTemplateBar(props: {
  templates: EquipmentTemplateSummary[];
  templateKey: string;
  disabled: boolean;
  selected?: EquipmentTemplateSummary;
  draftCount: number;
  fileRef: RefObject<HTMLInputElement | null>;
  onTemplateKey: (key: string) => void;
  onLoadJson: () => void;
  onDownloadCsv: () => void;
  onDownloadJson: () => void;
  onFilePick: (text: string) => void;
}) {
  return (
    <div className="flex flex-col gap-2 rounded-md border border-slate-800 bg-slate-950/50 p-3">
      <p className="text-xs uppercase tracking-wide text-slate-400">Equipment tag templates</p>
      <div className="flex flex-wrap items-center gap-2">
        <Select
          value={props.templateKey}
          disabled={props.disabled || props.templates.length === 0}
          onValueChange={props.onTemplateKey}
        >
          <SelectTrigger className="w-44">
            <SelectValue placeholder="Template" />
          </SelectTrigger>
          <SelectContent>
            {props.templates.map((row) => (
              <SelectItem key={row.templateKey} value={row.templateKey}>
                {row.displayName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button type="button" size="sm" variant="secondary" disabled={props.disabled} onClick={props.onDownloadCsv}>
          Download CSV
        </Button>
        <Button type="button" size="sm" variant="secondary" disabled={props.disabled} onClick={props.onDownloadJson}>
          Download JSON
        </Button>
        <Button type="button" size="sm" disabled={props.disabled} onClick={props.onLoadJson}>
          Load into editor
        </Button>
        <Button
          type="button"
          size="sm"
          variant="secondary"
          disabled={props.disabled}
          onClick={() => props.fileRef.current?.click()}
        >
          Upload file
        </Button>
        <input
          ref={props.fileRef}
          type="file"
          accept=".csv,.json,text/csv,application/json"
          className="hidden"
          onChange={async (event) => {
            const file = event.target.files?.[0];
            event.target.value = "";
            if (!file) return;
            props.onFilePick(await file.text());
          }}
        />
      </div>
      <div className="flex flex-wrap gap-2 font-mono text-xs text-slate-400">
        <Badge variant="slate" className="border-slate-700 text-slate-300">
          Parsed: {props.draftCount}
        </Badge>
        {props.selected ? (
          <span>
            {props.selected.tagCount} tags ·{" "}
            {props.selected.prosysReady ? "Prosys-ready" : "Generic"}
          </span>
        ) : null}
      </div>
    </div>
  );
}