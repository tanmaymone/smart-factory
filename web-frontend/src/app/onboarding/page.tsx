import { Suspense } from "react";
import { OnboardingConsole, OnboardingConsoleFallback } from "@/components/onboarding/OnboardingConsole";

export default function OnboardingPage() {
  return (
    <main className="dot-grid relative min-h-screen">
      <div className="relative mx-auto flex w-full max-w-7xl flex-col gap-4 px-4 py-6 sm:px-6">
        <Suspense fallback={<OnboardingConsoleFallback />}>
          <OnboardingConsole />
        </Suspense>
      </div>
    </main>
  );
}
