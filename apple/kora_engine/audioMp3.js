/**
 * audioMp3.js — MP3 / M4A audio export API for Kora Notation.
 *
 * MP3 and M4A/AAC encoding require platform-specific codecs (e.g., LAME on
 * Android, AudioToolbox on iOS/macOS) that are not available in a pure JS
 * runtime. This module defines the engine-level API surface and provides
 * wrapper functions that accept an injected encoder function, keeping the
 * engine portable and deterministic.
 *
 * Usage (platform-specific wrapper):
 *
 *   import { exportSimplifiedScoreToMp3Bytes } from "./audioMp3.js";
 *   import { lameMp3Encode } from "./platform/lame.js"; // platform-specific
 *
 *   const result = exportSimplifiedScoreToMp3Bytes({
 *     score,
 *     encoderFn: lameMp3Encode,
 *   });
 *   // result.bytes is a Uint8Array of MP3 data, or null if unsupported.
 */

import {
  exportSimplifiedScoreToPcm16Mono,
  exportKoraPerformanceToPcm16Mono,
} from "./audioWav.js";
import { RepeatsMode } from "./scheduler.js";

// Reason codes returned when encoding is unavailable.
export const AudioExportReason = Object.freeze({
  MP3_ENCODER_REQUIRED: "MP3_ENCODER_REQUIRED",
  M4A_ENCODER_REQUIRED: "M4A_ENCODER_REQUIRED",
});

/**
 * Export result type:
 *   { bytes: Uint8Array, supported: true }          — success
 *   { bytes: null, supported: false, reason: string } — encoder not provided
 */

/**
 * Export a simplified score to MP3.
 *
 * @param {object} params
 * @param {object} params.score            SimplifiedScore
 * @param {string} [params.repeatsMode]    RepeatsMode (default LINEARIZE)
 * @param {number} [params.sampleRate]     Sample rate in Hz (default 44100)
 * @param {number} [params.kbps]           Target bitrate in kbps (default 192)
 * @param {number} [params.defaultVelocity]
 * @param {number} [params.masterGain]
 * @param {number} [params.attackSeconds]
 * @param {number} [params.releaseSeconds]
 * @param {Function|null} [params.encoderFn]
 *   Platform-provided encoder: ({ pcm: Int16Array, sampleRate: number, kbps: number }) => Uint8Array
 *
 * @returns {{ bytes: Uint8Array|null, supported: boolean, reason?: string }}
 */
export function exportSimplifiedScoreToMp3Bytes({
  score,
  repeatsMode = RepeatsMode.LINEARIZE,
  sampleRate = 44100,
  kbps = 192,
  defaultVelocity = 80,
  masterGain = 0.20,
  attackSeconds = 0.004,
  releaseSeconds = 0.060,
  encoderFn = null,
}) {
  if (typeof encoderFn !== "function") {
    return { bytes: null, supported: false, reason: AudioExportReason.MP3_ENCODER_REQUIRED };
  }

  const pcm = exportSimplifiedScoreToPcm16Mono({
    score,
    repeatsMode,
    sampleRate,
    defaultVelocity,
    masterGain,
    attackSeconds,
    releaseSeconds,
  });

  const bytes = encoderFn({ pcm, sampleRate, kbps });
  return { bytes, supported: true };
}

/**
 * Export a kora performance to MP3.
 *
 * @param {object} params
 * @param {object} params.score
 * @param {Array}  params.mappedEvents     KoraMapping events
 * @param {string} [params.repeatsMode]
 * @param {number} [params.sampleRate]
 * @param {number} [params.kbps]
 * @param {number} [params.defaultVelocity]
 * @param {number} [params.masterGain]
 * @param {number} [params.attackSeconds]
 * @param {number} [params.releaseSeconds]
 * @param {Function|null} [params.encoderFn]
 *
 * @returns {{ bytes: Uint8Array|null, supported: boolean, reason?: string }}
 */
export function exportKoraPerformanceToMp3Bytes({
  score,
  mappedEvents,
  repeatsMode = RepeatsMode.LINEARIZE,
  sampleRate = 44100,
  kbps = 192,
  defaultVelocity = 80,
  masterGain = 0.20,
  attackSeconds = 0.004,
  releaseSeconds = 0.060,
  encoderFn = null,
}) {
  if (typeof encoderFn !== "function") {
    return { bytes: null, supported: false, reason: AudioExportReason.MP3_ENCODER_REQUIRED };
  }

  const pcm = exportKoraPerformanceToPcm16Mono({
    score,
    mappedEvents,
    repeatsMode,
    sampleRate,
    defaultVelocity,
    masterGain,
    attackSeconds,
    releaseSeconds,
  });

  const bytes = encoderFn({ pcm, sampleRate, kbps });
  return { bytes, supported: true };
}

/**
 * Export a simplified score to M4A/AAC.
 * Same encoder-injection pattern; platform provides an AAC encoder function.
 *
 * @param {object} params
 * @param {Function|null} [params.encoderFn]
 *   Platform-provided encoder: ({ pcm: Int16Array, sampleRate: number, kbps: number }) => Uint8Array
 *
 * @returns {{ bytes: Uint8Array|null, supported: boolean, reason?: string }}
 */
export function exportSimplifiedScoreToM4aBytes({
  score,
  repeatsMode = RepeatsMode.LINEARIZE,
  sampleRate = 44100,
  kbps = 256,
  defaultVelocity = 80,
  masterGain = 0.20,
  attackSeconds = 0.004,
  releaseSeconds = 0.060,
  encoderFn = null,
}) {
  if (typeof encoderFn !== "function") {
    return { bytes: null, supported: false, reason: AudioExportReason.M4A_ENCODER_REQUIRED };
  }

  const pcm = exportSimplifiedScoreToPcm16Mono({
    score,
    repeatsMode,
    sampleRate,
    defaultVelocity,
    masterGain,
    attackSeconds,
    releaseSeconds,
  });

  const bytes = encoderFn({ pcm, sampleRate, kbps });
  return { bytes, supported: true };
}

/**
 * Export a kora performance to M4A/AAC.
 */
export function exportKoraPerformanceToM4aBytes({
  score,
  mappedEvents,
  repeatsMode = RepeatsMode.LINEARIZE,
  sampleRate = 44100,
  kbps = 256,
  defaultVelocity = 80,
  masterGain = 0.20,
  attackSeconds = 0.004,
  releaseSeconds = 0.060,
  encoderFn = null,
}) {
  if (typeof encoderFn !== "function") {
    return { bytes: null, supported: false, reason: AudioExportReason.M4A_ENCODER_REQUIRED };
  }

  const pcm = exportKoraPerformanceToPcm16Mono({
    score,
    mappedEvents,
    repeatsMode,
    sampleRate,
    defaultVelocity,
    masterGain,
    attackSeconds,
    releaseSeconds,
  });

  const bytes = encoderFn({ pcm, sampleRate, kbps });
  return { bytes, supported: true };
}
