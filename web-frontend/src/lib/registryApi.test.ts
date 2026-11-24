import { afterEach, describe, expect, it, vi } from "vitest";
import { deleteRegistryTag } from "@/lib/registryApi";

describe("SF-M1.3 deleteRegistryTag", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("treats 204 as success and sends DELETE", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(deleteRegistryTag("tag-1")).resolves.toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/registry/tags/tag-1",
      expect.objectContaining({
        method: "DELETE",
        headers: expect.any(Object),
      }),
    );
  });

  it("returns TAG_NOT_FOUND from a 404 body", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: "TAG_NOT_FOUND",
            message: "Tag mapping not found",
            fieldErrors: [],
          }),
          { status: 404, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );

    const result = await deleteRegistryTag("missing");
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.status).toBe(404);
      expect(result.error.code).toBe("TAG_NOT_FOUND");
    }
  });
});
