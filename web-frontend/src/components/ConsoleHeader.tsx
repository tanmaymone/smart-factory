"use client";

import type { ReactNode } from "react";
import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ConnectionHealth } from "@/components/ConnectionHealth";
import { SiteLineSwitcher } from "@/components/SiteLineSwitcher";
import type { SiteInfo } from "@/lib/sites";
import type { ConnectionStatus } from "@/lib/types";
import { cn } from "@/lib/utils";

const NAV = [
  { href: "/", label: "Live" },
  { href: "/audit", label: "Audit" },
  { href: "/registry", label: "Registry" },
] as const;

function BrandCluster({
  title,
  subtitle,
}: {
  title: string;
  subtitle: ReactNode;
}) {
  return (
    <div className="flex shrink-0 items-center gap-3">
      <Image
        src="/smart-factory-logo.png"
        alt="Smart Factory"
        width={40}
        height={40}
        priority
        className="h-10 w-10 shrink-0 object-contain"
      />
      <div>
        <p className="text-xs uppercase tracking-[0.2em] text-slate-400">
          Smart Factory
        </p>
        <h1 className="text-lg font-semibold text-slate-100">{title}</h1>
        <p className="text-sm text-slate-400">{subtitle}</p>
      </div>
    </div>
  );
}

export function ConsoleHeader({
  title,
  subtitle,
  catalog,
  siteId,
  lineId,
  onSiteChange,
  onLineChange,
  status,
}: {
  title: string;
  subtitle: ReactNode;
  catalog: SiteInfo[];
  siteId: string;
  lineId: string;
  onSiteChange: (siteId: string) => void;
  onLineChange: (lineId: string) => void;
  status?: ConnectionStatus;
}) {
  const pathname = usePathname();
  const qs = `?siteId=${encodeURIComponent(siteId)}&lineId=${encodeURIComponent(lineId)}`;

  return (
    <header className="flex flex-wrap items-center justify-between gap-4 rounded-lg border border-slate-800 bg-slate-900/90 px-4 py-3">
      <BrandCluster title={title} subtitle={subtitle} />
      <div className="flex flex-wrap items-center gap-4">
        <nav className="flex items-center gap-1 rounded-md border border-slate-800 bg-slate-950/60 p-1">
          {NAV.map((item) => {
            const active =
              item.href === "/"
                ? pathname === "/"
                : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={`${item.href}${qs}`}
                className={cn(
                  "rounded px-2.5 py-1 text-xs font-medium uppercase tracking-wide",
                  active
                    ? "bg-slate-800 text-cyan-400"
                    : "text-slate-400 hover:text-slate-100",
                )}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
        <SiteLineSwitcher
          catalog={catalog}
          siteId={siteId}
          lineId={lineId}
          onSiteChange={onSiteChange}
          onLineChange={onLineChange}
        />
        {status ? <ConnectionHealth status={status} /> : null}
      </div>
    </header>
  );
}

export function ConsoleHeaderFallback({
  title,
  status = "connecting",
}: {
  title: string;
  status?: ConnectionStatus;
}) {
  return (
    <header className="flex flex-wrap items-center justify-between gap-4 rounded-lg border border-slate-800 bg-slate-900/90 px-4 py-3">
      <BrandCluster title={title} subtitle="Loading selection…" />
      <ConnectionHealth status={status} />
    </header>
  );
}
