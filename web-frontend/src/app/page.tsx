import { Suspense } from "react";
import { LiveConsole, LiveConsoleFallback } from "@/components/LiveConsole";

export default function HomePage() {
  return (
    <main className="dot-grid relative min-h-screen">
      <div className="relative mx-auto flex w-full max-w-7xl flex-col gap-4 px-4 py-6 sm:px-6">
        <Suspense fallback={<LiveConsoleFallback />}>
          <LiveConsole />
        </Suspense>
      </div>
    </main>
  );
}
