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

  it("sends the setup token on mutations only when it is non-blank", async () => {
    vi.stubEnv("NEXT_PUBLIC_BOOTSTRAP_TOKEN", "secret");
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 201 }));
    vi.stubGlobal("fetch", fetchMock);
    await createSite({ siteCode: "SITE", name: "Site", timezone: "UTC" });
    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>;
    expect(headers["X-Setup-Token"]).toBe("secret");

    vi.stubEnv("NEXT_PUBLIC_BOOTSTRAP_TOKEN", "  ");
    fetchMock.mockResolvedValueOnce(new Response("[]", { status: 200 }));
    await listSites();
    const getHeaders = fetchMock.mock.calls[1][1];
    expect(getHeaders?.headers).toBeUndefined();
  });

  it("posts the connection probe with setup headers", async () => {
    vi.stubEnv("NEXT_PUBLIC_BOOTSTRAP_TOKEN", "");
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      endpointUrl: "opc.tcp://h:4840",
      securityMode: "None",
      reachable: true,
      error: null,
      tags: [],
    }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await postOnboardingProbe({ endpointUrl: "opc.tcp://h:4840", securityMode: "None", nodeIds: [] });
    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>;
    expect(headers["X-Setup-Token"]).toBeUndefined();
  });
});
