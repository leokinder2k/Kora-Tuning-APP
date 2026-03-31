/**
 * omrPipeline.js — Optical Music Recognition (OMR) pipeline API for Kora Notation.
 *
 * OMR requires a platform-specific backend (e.g., Audiveris, a cloud OCR service,
 * or a bundled ML model) that is not available in a pure JS runtime. This module
 * defines the engine-level API surface and provides wrapper functions that accept
 * an injected backend function, keeping the engine portable and deterministic.
 *
 * Usage (platform-specific wrapper):
 *
 *   import { runOmrPipeline } from "./omrPipeline.js";
 *   import { audiverisBackend } from "./platform/audiveris.js"; // platform-specific
 *
 *   const result = runOmrPipeline({
 *     imageBytes,
 *     ocrBackendFn: audiverisBackend,
 *   });
 *   if (result.status === "success") {
 *     const draft = result.simplifiedScoreDraft; // SimplifiedScore (best-effort)
 *   }
 *
 * Without an ocrBackendFn, all calls return { status: "unsupported" }.
 *
 * The backend function signature:
 *   ({ imageBytes: Uint8Array, staffLineHints: object|null })
 *     => { status: "success" | "failed", data?: object, reason?: string }
 *
 * The returned `data` should be a partial SimplifiedScore that the engine can
 * validate and clean up before presenting to the user as a best-effort draft.
 */

// Reason codes for non-success results.
export const OmrReason = Object.freeze({
  BACKEND_REQUIRED: "BACKEND_REQUIRED",      // No ocrBackendFn provided
  IMAGE_INVALID: "IMAGE_INVALID",            // imageBytes is missing or empty
  RECOGNITION_FAILED: "RECOGNITION_FAILED", // Backend returned error or threw
});

/**
 * Run the OMR pipeline on a raw image.
 *
 * @param {object} params
 * @param {Uint8Array|null} params.imageBytes
 *   Raw image bytes (PNG, JPEG, or other raster format).
 * @param {object|null} [params.staffLineHints]
 *   Optional manual staff-line position hints (for user-adjusted crops/rotations).
 * @param {Function|null} [params.ocrBackendFn]
 *   Platform-provided OCR function:
 *     ({ imageBytes: Uint8Array, staffLineHints: object|null })
 *       => { status: "success"|"failed", data?: object, reason?: string }
 *
 * @returns {{ status: "success"|"unsupported"|"failed", simplifiedScoreDraft?: object, reason?: string }}
 */
export function runOmrPipeline({ imageBytes, staffLineHints = null, ocrBackendFn = null }) {
  if (!imageBytes || !(imageBytes instanceof Uint8Array) || imageBytes.length === 0) {
    return { status: "failed", reason: OmrReason.IMAGE_INVALID };
  }

  if (typeof ocrBackendFn !== "function") {
    return { status: "unsupported", reason: OmrReason.BACKEND_REQUIRED };
  }

  let backendResult;
  try {
    backendResult = ocrBackendFn({ imageBytes, staffLineHints });
  } catch (err) {
    return { status: "failed", reason: OmrReason.RECOGNITION_FAILED, error: String(err) };
  }

  if (!backendResult || backendResult.status !== "success") {
    return {
      status: "failed",
      reason: OmrReason.RECOGNITION_FAILED,
      error: backendResult?.reason ?? null,
    };
  }

  return {
    status: "success",
    simplifiedScoreDraft: backendResult.data ?? null,
  };
}
