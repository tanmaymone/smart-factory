import type { CommandUpdateEvent, ConnectionStatus } from "@/lib/types";

export type StreamEventName =
  | "TAG_UPDATE"
  | "ALARM_EVENT"
  | "COMMAND_UPDATE"
  | "connected"
  | "ping";

export type StreamHandlers = {
  onStatus: (status: ConnectionStatus) => void;
  onTag?: (data: string) => void;
  onAlarm?: (data: string) => void;
  onCommand?: (event: CommandUpdateEvent) => void;
};

export type StreamSession = {
  abort: () => void;
};

const BACKOFF_MS = [3000, 6000, 12000, 30000] as const;

export function nextBackoffMs(attempt: number): number {
  const index = Math.min(Math.max(0, attempt), BACKOFF_MS.length - 1);
  return BACKOFF_MS[index];
}

export function shouldScheduleRetry(visibilityState: DocumentVisibilityState): boolean {
  return visibilityState !== "hidden";
}

export function classifyStreamFailure(
  status: number,
  body: unknown,
): ConnectionStatus {
  if (status === 503) {
    const code =
      body != null &&
      typeof body === "object" &&
      typeof (body as { code?: unknown }).code === "string"
        ? (body as { code: string }).code
        : null;
    if (code === "STREAM_AT_CAPACITY") {
      return "at-capacity";
    }
  }
  return "reconnecting";
}

function parseSseChunk(
  buffer: string,
): { events: { event: string; data: string }[]; rest: string } {
  const events: { event: string; data: string }[] = [];
  let rest = buffer;
  let sep: number;
  while ((sep = rest.search(/\r?\n\r?\n/)) >= 0) {
    const block = rest.slice(0, sep);
    rest = rest.slice(sep).replace(/^\r?\n\r?\n/, "");
    if (!block.trim() || block.startsWith(":")) {
      continue;
    }
    let eventName = "message";
    const dataLines: string[] = [];
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith("event:")) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        dataLines.push(line.slice(5).trimStart());
      }
    }
    if (dataLines.length > 0) {
      events.push({ event: eventName, data: dataLines.join("\n") });
    }
  }
  return { events, rest };
}

function dispatchFrame(
  eventName: string,
  data: string,
  handlers: StreamHandlers,
): void {
  if (eventName === "TAG_UPDATE") {
    handlers.onTag?.(data);
    return;
  }
  if (eventName === "ALARM_EVENT") {
    handlers.onAlarm?.(data);
    return;
  }
  if (eventName === "COMMAND_UPDATE") {
    try {
      const parsed = JSON.parse(data) as CommandUpdateEvent;
      handlers.onCommand?.(parsed);
    } catch {
      // ignore malformed
    }
  }
}

/**
 * One fetch-based SSE session with capacity-aware backoff and pause-while-hidden.
 * Callers must abort on unmount; only one retry timer is scheduled at a time.
 */
export function connectTelemetryStream(
  siteId: string,
  lineId: string,
  handlers: StreamHandlers,
): StreamSession {
  let aborted = false;
  let attempt = 0;
  let retryTimer: ReturnType<typeof setTimeout> | null = null;
  let controller: AbortController | null = null;

  const clearRetry = () => {
    if (retryTimer != null) {
      clearTimeout(retryTimer);
      retryTimer = null;
    }
  };

  const scheduleRetry = (status: ConnectionStatus) => {
    clearRetry();
    if (aborted) {
      return;
    }
    handlers.onStatus(status);
    if (!shouldScheduleRetry(document.visibilityState)) {
      return;
    }
    const delay = nextBackoffMs(attempt);
    attempt += 1;
    retryTimer = setTimeout(() => {
      retryTimer = null;
      void open();
    }, delay);
  };

  const open = async () => {
    if (aborted) {
      return;
    }
    clearRetry();
    if (controller) {
      controller.abort();
    }
    controller = new AbortController();
    handlers.onStatus(attempt === 0 ? "connecting" : "reconnecting");

    const params = new URLSearchParams({ siteId, lineId });
    try {
      const response = await fetch(`/api/stream/telemetry?${params}`, {
        headers: { Accept: "text/event-stream" },
        signal: controller.signal,
        cache: "no-store",
      });

      if (!response.ok || !response.body) {
        let body: unknown = null;
        try {
          body = await response.json();
        } catch {
          body = null;
        }
        if (aborted) {
          return;
        }
        scheduleRetry(classifyStreamFailure(response.status, body));
        return;
      }

      attempt = 0;
      handlers.onStatus("live");

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (!aborted) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        const parsed = parseSseChunk(buffer);
        buffer = parsed.rest;
        for (const frame of parsed.events) {
          dispatchFrame(frame.event, frame.data, handlers);
        }
      }

      if (!aborted) {
        scheduleRetry("reconnecting");
      }
    } catch (error) {
      if (aborted || (error instanceof DOMException && error.name === "AbortError")) {
        return;
      }
      scheduleRetry("reconnecting");
    }
  };

  const onVisibility = () => {
    if (aborted) {
      return;
    }
    if (document.visibilityState === "hidden") {
      clearRetry();
      if (controller) {
        controller.abort();
        controller = null;
      }
      handlers.onStatus("disconnected");
      return;
    }
    attempt = 0;
    void open();
  };

  document.addEventListener("visibilitychange", onVisibility);
  void open();

  return {
    abort: () => {
      aborted = true;
      clearRetry();
      document.removeEventListener("visibilitychange", onVisibility);
      if (controller) {
        controller.abort();
        controller = null;
      }
    },
  };
}
