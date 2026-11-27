"use client";

import { useEffect, useState } from "react";
import {
  ConsoleHeader,
  ConsoleHeaderFallback,
} from "@/components/ConsoleHeader";
import { RegistryScopeLabelsCard } from "@/components/registry/RegistryScopeLabelsCard";
import { RegistryTagFilters } from "@/components/registry/RegistryTagFilters";
import { RegistryTagForm } from "@/components/registry/RegistryTagForm";
import { RegistryTagTable } from "@/components/registry/RegistryTagTable";
import { CatalogNotice } from "@/components/CatalogNotice";
import { useSiteLineSelection } from "@/hooks/useSiteLineSelection";
import { consoleMountState } from "@/lib/catalogSelection";
import { resolveEntityLabels } from "@/lib/entityLabels";
import {
  createRegistryTag,
  deleteRegistryTag,
  listRegistryTags,
  updateRegistryTag,
} from "@/lib/registryApi";
import type {
  RegistryErrorResponse,
  TagMapping,
  TagMappingRequest,
} from "@/lib/types";

export function RegistryConsole() {
  const {
    selection,
    catalog,
    replaceSelection,
    catalogLoading,
    catalogError,
    hasValidLineSelection,
    refreshCatalog,
  } = useSiteLineSelection();
  const [tags, setTags] = useState<TagMapping[]>([]);
  const [protocol, setProtocol] = useState("");
  const [writableFilter, setWritableFilter] = useState<"all" | "true" | "false">(
    "all",
  );
  const [listError, setListError] = useState<RegistryErrorResponse | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<"create" | "edit">("create");
  const [editing, setEditing] = useState<TagMapping | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<RegistryErrorResponse | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  const scopeLabels = resolveEntityLabels(selection.site);

  useEffect(() => {
    if (catalogLoading || catalogError || !hasValidLineSelection) return;
    const controller = new AbortController();
    void listRegistryTags(
      {
        siteId: selection.siteId,
        lineId: selection.lineId,
        protocol: protocol || undefined,
        writable:
          writableFilter === "all" ? undefined : writableFilter === "true",
      },
      controller.signal,
    )
      .then((data) => {
        setTags(data);
        setListError(null);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        const err = error as RegistryErrorResponse;
        setTags([]);
        setListError({
          code: err.code ?? "REGISTRY_FETCH_FAILED",
          message: err.message ?? "Failed to load tags",
          fieldErrors: err.fieldErrors ?? [],
        });
      });
    return () => controller.abort();
  }, [selection.siteId, selection.lineId, protocol, writableFilter, reloadToken, catalogLoading, catalogError, hasValidLineSelection]);

  const openCreate = () => {
    setFormMode("create");
    setEditing(null);
    setFormError(null);
    setFormOpen(true);
  };

  const openEdit = (tag: TagMapping) => {
    setFormMode("edit");
    setEditing(tag);
    setFormError(null);
    setFormOpen(true);
  };

  const onSubmit = async (body: TagMappingRequest) => {
    setSubmitting(true);
    setFormError(null);
    const result =
      formMode === "create"
        ? await createRegistryTag(body)
        : await updateRegistryTag(editing!.id, {
            ...body,
            siteId: selection.siteId,
            lineId: selection.lineId,
          });
    setSubmitting(false);
    if (!result.ok) {
      setFormError(result.error);
      return;
    }
    setFormOpen(false);
    setReloadToken((token) => token + 1);
  };

  const onDelete = async (tag: TagMapping) => {
    try {
      const result = await deleteRegistryTag(tag.id);
      if (!result.ok) {
        setListError(result.error);
        return false;
      }
    } catch {
      setListError({
        code: "REGISTRY_DELETE_FAILED",
        message: "Failed to delete tag",
        fieldErrors: [],
      });
      return false;
    }
    if (editing?.id === tag.id) {
      setFormOpen(false);
      setEditing(null);
    }
    setListError(null);
    setReloadToken((token) => token + 1);
    return true;
  };

  return (
    <div className="flex flex-col gap-4">
      <ConsoleHeader
        title="Tag Registry"
        subtitle={
          <>
            {selection.site.name} · {selection.line.name}
          </>
        }
        catalog={catalog}
        siteId={selection.siteId}
        lineId={selection.lineId}
        onSiteChange={(siteId) => replaceSelection(siteId, selection.lineId)}
        onLineChange={(lineId) => replaceSelection(selection.siteId, lineId)}
      />
      <CatalogNotice
        catalogLoading={catalogLoading}
        catalogError={catalogError}
        hasValidLineSelection={hasValidLineSelection}
        siteId={selection.siteId}
        onRetry={() => void refreshCatalog()}
      />
      {consoleMountState({ catalogLoading, catalogError, hasValidLineSelection }) === "body" ? (
      <>
      <RegistryScopeLabelsCard
        siteId={selection.siteId}
        siteLabel={scopeLabels.siteLabel}
        lineLabel={scopeLabels.lineLabel}
        onSaved={refreshCatalog}
      />
      <RegistryTagFilters
        protocol={protocol}
        writableFilter={writableFilter}
        onProtocolChange={setProtocol}
        onWritableChange={setWritableFilter}
        onCreate={openCreate}
      />

      <RegistryTagTable
        tags={tags}
        onEdit={openEdit}
        onDelete={onDelete}
        errorCode={listError?.code}
        errorMessage={listError?.message}
      />

      <RegistryTagForm
        open={formOpen}
        mode={formMode}
        siteId={selection.siteId}
        lineId={selection.lineId}
        initial={editing}
        submitting={submitting}
        error={formError}
        onOpenChange={setFormOpen}
        onSubmit={(body) => void onSubmit(body)}
      />
      </>
      ) : null}
    </div>
  );
}

export function RegistryConsoleFallback() {
  return (
    <div className="flex flex-col gap-4">
      <ConsoleHeaderFallback title="Tag Registry" />
      <div className="rounded-lg border border-slate-800 bg-slate-900 px-4 py-8 text-center text-sm text-slate-400">
        Loading registry…
      </div>
    </div>
  );
}
