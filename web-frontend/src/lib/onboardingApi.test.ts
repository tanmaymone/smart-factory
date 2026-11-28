import { afterEach, describe, expect, it, vi } from "vitest";
import { isStep2Pass, parseProbeError, postOnboardingProbe } from "@/lib/onboardingApi";
import { createSite, listSites } from "@/lib/registryApi";

describe("SF-M35 onboarding API", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it("branches 403 FORBIDDEN from PROBE_DISABLED and reads 422 field errors", () => {
    expect(parseProbeError({ code: "FORBIDDEN", message: "no" }, 403).code).toBe("FORBIDDEN");
    expect(parseProbeError({ code: "PROBE_DISABLED", message: "off" }, 403).code).toBe("PROBE_DISABLED");
    expect(parseProbeError({ code: "VALIDATION", fieldErrors: [{ field: "endpointUrl", code: "ENDPOINT_URL_INVALID", message: "bad" }] }, 422).code).toBe("ENDPOINT_URL_INVALID");
    expect(isStep2Pass({ endpointUrl: "opc.tcp://h:4840", securityMode: "None", reachable: true, error: "namespace", tags: [] })).toBe(false);
  });

  it("uses same-origin credentials without bootstrap headers", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 201 }));
    vi.stubGlobal("fetch", fetchMock);
    await createSite({ siteCode: "SITE", name: "Site", timezone: "UTC" });
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.credentials).toBe("same-origin");
    const headers = init.headers as Record<string, string>;
    expect(headers["X-Setup-Token"]).toBeUndefined();
    expect(headers["X-Actor-Id"]).toBeUndefined();

    fetchMock.mockResolvedValueOnce(new Response("[]", { status: 200 }));
    await listSites();
    const listInit = fetchMock.mock.calls[1][1] as RequestInit;
    expect(listInit.credentials).toBe("same-origin");
  });

  it("posts the connection probe with session credentials only", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      endpointUrl: "opc.tcp://h:4840",
      securityMode: "None",
      reachable: true,
      error: null,
      tags: [],
    }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await postOnboardingProbe({ endpointUrl: "opc.tcp://h:4840", securityMode: "None", nodeIds: [] });
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.credentials).toBe("same-origin");
    const headers = init.headers as Record<string, string>;
    expect(headers["X-Setup-Token"]).toBeUndefined();
    expect(headers["X-Actor-Id"]).toBeUndefined();
  });
});