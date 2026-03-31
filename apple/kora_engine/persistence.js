// Browser stub — node:fs not available. Persistence is handled by the Android layer.

function sortKeysDeep(value) {
  if (Array.isArray(value)) return value.map(sortKeysDeep);
  if (value === null || value === undefined) return value;
  if (typeof value !== "object") return value;
  if (ArrayBuffer.isView(value)) return value;
  const out = {};
  for (const k of Object.keys(value).sort()) out[k] = sortKeysDeep(value[k]);
  return out;
}

export function stableJsonStringify(value) {
  return JSON.stringify(sortKeysDeep(value), null, 2) + "\n";
}

export async function writeJsonFile() { throw new Error("writeJsonFile not supported in browser mode"); }
export async function readJsonFile() { throw new Error("readJsonFile not supported in browser mode"); }
export async function savePieceFolder() { throw new Error("savePieceFolder not supported in browser mode"); }
export async function loadPieceFolder() { throw new Error("loadPieceFolder not supported in browser mode"); }
export function buildLibraryIndexEntry() { throw new Error("buildLibraryIndexEntry not supported in browser mode"); }
export async function indexLibraryFolder() { return []; }
export async function saveLibraryIndex() {}
export async function loadLibraryIndex() { return { version: 1, updatedAt: null, entries: [] }; }
