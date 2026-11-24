import { describe, expect, it } from "vitest";
import { writeBlock } from "@/lib/writeNodeGuard";

describe("SF-NODE write preflight", () => {
  it("allows a FLOAT sample on a writable Float or Double node", () => {
    expect(writeBlock({ dataType: "FLOAT", observedDataType: "FLOAT", nodeWritable: true })).toBeNull();
    expect(writeBlock({ dataType: "FLOAT", observedDataType: null, nodeWritable: null })).toBeNull();
  });

  it("blocks FLOAT on an integer node before submit", () => {
    expect(writeBlock({ dataType: "FLOAT", observedDataType: "INT", nodeWritable: true })).toEqual({
      code: "NODE_TYPE_MISMATCH",
      message: "FLOAT requires Float or Double node datatype",
    });
  });

  it("blocks a read-only node when the datatype already matches", () => {
    expect(writeBlock({ dataType: "FLOAT", observedDataType: "FLOAT", nodeWritable: false })).toEqual({
      code: "NODE_NOT_WRITABLE",
      message: "OPC UA node is not writable",
    });
  });
});
