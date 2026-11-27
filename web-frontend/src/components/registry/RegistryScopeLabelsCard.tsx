"use client";

import { useEffect, useState } from "react";
import { EntityLabelPicker } from "@/components/onboarding/EntityLabelPicker";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  entityLabelPatternOk,
  LINE_LABEL_PRESETS,
  resolveEntityLabels,
  SITE_LABEL_PRESETS,
} from "@/lib/entityLabels";
import { saveSiteEntityLabels } from "@/lib/siteEntityLabels";

type Props = {
  siteId: string;
  siteLabel: string;
  lineLabel: string;
  disabled?: boolean;
  onSaved: () => Promise<void>;
};

export function RegistryScopeLabelsCard({
  siteId,
  siteLabel,
  lineLabel,
  disabled,
  onSaved,
}: Props) {
  const [draftSite, setDraftSite] = useState(siteLabel);
  const [draftLine, setDraftLine] = useState(lineLabel);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ tone: "ok" | "err"; text: string } | null>(null);

  useEffect(() => {
    setDraftSite(siteLabel);
    setDraftLine(lineLabel);
    setNotice(null);
  }, [siteId, siteLabel, lineLabel]);

  const draftLabels = resolveEntityLabels({ siteLabel: draftSite, lineLabel: draftLine });
  const savedLabels = resolveEntityLabels({ siteLabel, lineLabel });
  const unchanged =
    draftLabels.siteLabel === savedLabels.siteLabel && draftLabels.lineLabel === savedLabels.lineLabel;
  const patternsOk = entityLabelPatternOk(draftSite) && entityLabelPatternOk(draftLine);

  const onSave = async () => {
    if (busy || disabled || unchanged || !patternsOk) return;
    setBusy(true);
    setNotice(null);
    const result = await saveSiteEntityLabels(siteId, draftSite, draftLine);
    setBusy(false);
    if (!result.ok) {
      setNotice({ tone: "err", text: result.message });
      return;
    }
    setNotice({ tone: "ok", text: "Display words saved. Switcher labels update for this site." });
    await onSaved();
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Scope settings</CardTitle>
        <p className="text-xs text-slate-500">
          Vocabulary for site <span className="font-mono text-slate-300">{siteId}</span> in the header and tables. Codes
          and MQTT paths stay <span className="font-mono">siteId</span> / <span className="font-mono">lineId</span>.
        </p>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="grid gap-3 md:grid-cols-2">
          <EntityLabelPicker
            id="reg-site-label"
            title={`${draftLabels.siteLabel} word`}
            presets={SITE_LABEL_PRESETS}
            value={draftSite}
            disabled={disabled || busy}
            onChange={setDraftSite}
          />
          <EntityLabelPicker
            id="reg-line-label"
            title={`${draftLabels.lineLabel} word`}
            presets={LINE_LABEL_PRESETS}
            value={draftLine}
            disabled={disabled || busy}
            onChange={setDraftLine}
          />
        </div>
        {notice ? (
          <p
            className={
              notice.tone === "ok"
                ? "text-xs text-emerald-400"
                : "text-xs text-rose-400"
            }
          >
            {notice.text}
          </p>
        ) : null}
        <Button className="self-start" disabled={disabled || busy || unchanged || !patternsOk} onClick={() => void onSave()}>
          Save display words
        </Button>
      </CardContent>
    </Card>
  );
}