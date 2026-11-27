import { stepOneTitle, type ResolvedEntityLabels } from "@/lib/entityLabels";
import { cn } from "@/lib/utils";

const TAIL_LABELS = ["Endpoint", "Tags", "Smoke"] as const;

export function OnboardingStepper({
  step,
  disabled,
  entityLabels,
}: {
  step: 1 | 2 | 3 | 4;
  disabled?: boolean;
  entityLabels: ResolvedEntityLabels;
}) {
  const labels = [stepOneTitle(entityLabels), ...TAIL_LABELS];

  return (
    <ol className="flex flex-wrap gap-2">
      {labels.map((label, index) => {
        const number = (index + 1) as 1 | 2 | 3 | 4;
        const active = number === step;
        return (
          <li
            key={label}
            className={cn(
              "rounded-md border px-3 py-1 text-xs",
              active ? "border-cyan-500/50 text-cyan-300" : "border-slate-800 text-slate-500",
              disabled && "opacity-60",
            )}
          >
            {number}. {label}
          </li>
        );
      })}
    </ol>
  );
}
