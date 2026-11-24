import type { TagDataType } from "@/lib/types";

export type WriteBlock = {
  code: "NODE_TYPE_MISMATCH" | "NODE_NOT_WRITABLE";
  message: string;
};

type GuardRow = {
  dataType: TagDataType | null;
  observedDataType?: string | null;
  nodeWritable?: boolean | null;
};

/** Same reasons as central NodeWriteGuard / edge CommandValueCoercer. */
export function writeBlock(row: GuardRow | null | undefined): WriteBlock | null {
  if (!row?.dataType) {
    return null;
  }
  const observed = row.observedDataType?.trim().toUpperCase() ?? "";
  if (observed && observed !== row.dataType) {
    return { code: "NODE_TYPE_MISMATCH", message: mismatch(row.dataType) };
  }
  if (row.nodeWritable === false) {
    return { code: "NODE_NOT_WRITABLE", message: "OPC UA node is not writable" };
  }
  return null;
}

function mismatch(dataType: TagDataType): string {
  if (dataType === "FLOAT") {
    return "FLOAT requires Float or Double node datatype";
  }
  if (dataType === "INT") {
    return "INT requires an integer node datatype";
  }
  if (dataType === "BOOLEAN") {
    return "BOOLEAN requires Boolean node datatype";
  }
  return "STRING requires String node datatype";
}
