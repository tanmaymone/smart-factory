import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium transition-colors",
  {
    variants: {
      variant: {
        default: "border-slate-700 bg-slate-800 text-slate-100",
        emerald: "border-emerald-500/40 bg-emerald-500/10 text-emerald-400",
        amber: "border-amber-500/40 bg-amber-500/10 text-amber-400",
        rose: "border-rose-500/40 bg-rose-500/10 text-rose-400",
        cyan: "border-cyan-500/40 bg-cyan-500/10 text-cyan-400",
        slate: "border-slate-700 bg-slate-900 text-slate-400",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  },
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />;
}
