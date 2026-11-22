import { connection } from "next/server";

const BACKEND_ORIGIN = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";

const FORWARD_REQUEST_HEADERS = ["accept", "last-event-id"] as const;

export async function GET(request: Request) {
  await connection();

  const url = new URL(request.url);
  const upstreamUrl = new URL("/api/stream/telemetry", BACKEND_ORIGIN);
  url.searchParams.forEach((value, key) => {
    upstreamUrl.searchParams.set(key, value);
  });

  const headers = new Headers();
  for (const name of FORWARD_REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value) {
      headers.set(name, value);
    }
  }
  headers.set("Accept", "text/event-stream");

  let upstream: Response;
  try {
    upstream = await fetch(upstreamUrl, {
      headers,
      signal: request.signal,
      cache: "no-store",
    });
  } catch (error) {
    if (request.signal.aborted) {
      return new Response(null, { status: 499 });
    }
    const message = error instanceof Error ? error.message : "Upstream fetch failed";
    return new Response(message, { status: 502 });
  }

  if (!upstream.ok || !upstream.body) {
    const body = upstream.body ? upstream.body : null;
    return new Response(body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: {
        "Content-Type": upstream.headers.get("Content-Type") ?? "text/plain",
        "Cache-Control": "no-cache, no-transform",
      },
    });
  }

  const reader = upstream.body.getReader();
  const stream = new ReadableStream<Uint8Array>({
    async start(controller) {
      const onAbort = () => {
        void reader.cancel().catch(() => undefined);
        try {
          controller.close();
        } catch {
          // already closed
        }
      };
      request.signal.addEventListener("abort", onAbort);

      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) {
            break;
          }
          if (value) {
            controller.enqueue(value);
          }
        }
        controller.close();
      } catch (error) {
        try {
          await reader.cancel();
        } catch {
          // ignore
        }
        controller.error(error);
      } finally {
        request.signal.removeEventListener("abort", onAbort);
      }
    },
    cancel() {
      return reader.cancel().catch(() => undefined);
    },
  });

  return new Response(stream, {
    status: 200,
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
      "X-Accel-Buffering": "no",
    },
  });
}
