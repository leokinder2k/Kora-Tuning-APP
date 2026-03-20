package com.leokinder2k.koratuningcompanion.notation.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

// Plucked string (Karplus-Strong) synthesizer producing kora-like tones

private const val SAMPLE_RATE = 44100
private const val CHANNELS = 1

private fun writeWavHeader(out: ByteArrayOutputStream, numSamples: Int) {
    val dataBytes = numSamples * 2  // 16-bit PCM
    val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
    buffer.putInt(36 + dataBytes)
    buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
    buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
    buffer.putInt(16)            // chunk size
    buffer.putShort(1)           // PCM format
    buffer.putShort(CHANNELS.toShort())
    buffer.putInt(SAMPLE_RATE)
    buffer.putInt(SAMPLE_RATE * 2)  // byte rate
    buffer.putShort(2)           // block align
    buffer.putShort(16)          // bits per sample
    buffer.put("data".toByteArray(Charsets.US_ASCII))
    buffer.putInt(dataBytes)
    out.write(buffer.array())
}

/**
 * Karplus-Strong plucked string simulation.
 * Returns numSamples of 16-bit PCM samples added into [buffer].
 */
private fun pluckString(
    buffer: FloatArray,
    startSample: Int,
    midiPitch: Int,
    durationSamples: Int,
    amplitude: Float,
) {
    if (midiPitch < 0 || midiPitch > 127) return
    val freq = 440.0 * 2.0.pow((midiPitch - 69) / 12.0)
    val periodSamples = (SAMPLE_RATE / freq).roundToInt().coerceIn(2, 4000)

    // Fill delay line with random noise burst (the "pluck")
    val delayLine = FloatArray(periodSamples) { (Math.random().toFloat() * 2f - 1f) * amplitude }

    val decay = 0.9985f   // controls string sustain (close to 1 = longer ring)
    val endSample = minOf(startSample + durationSamples + SAMPLE_RATE / 2, buffer.size)

    var idx = 0
    for (s in startSample until endSample) {
        val out = delayLine[idx % periodSamples]
        // Low-pass filter (average two adjacent samples) — produces plucked timbre
        val next = (idx + 1) % periodSamples
        delayLine[idx % periodSamples] = decay * (out + delayLine[next]) * 0.5f
        idx++
        buffer[s] += out
    }
}

private fun midiToFrequency(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

private fun ticksToSeconds(ticks: Int, ppq: Int, tempoMap: List<TempoInfo>): Double {
    if (ticks <= 0) return 0.0
    var elapsed = 0.0
    var prevTick = 0
    var prevBpm = 120.0
    for (entry in tempoMap) {
        if (entry.tick >= ticks) break
        elapsed += (entry.tick - prevTick) * 60.0 / (prevBpm * ppq)
        prevTick = entry.tick
        prevBpm = entry.bpm
    }
    elapsed += (ticks - prevTick) * 60.0 / (prevBpm * ppq)
    return elapsed
}

/** Synthesize a WAV file from note events using Karplus-Strong plucked string. */
fun synthesizeWav(
    notes: List<NoteEvent>,
    ppq: Int,
    tempoMap: List<TempoInfo>,
): ByteArray {
    if (notes.isEmpty()) return synthesizeSilence(1.0)

    val endTick = notes.maxOf { it.tick + it.durationTicks }
    val totalSeconds = ticksToSeconds(endTick, ppq, tempoMap) + 2.0  // tail
    val totalSamples = (totalSeconds * SAMPLE_RATE).toInt().coerceAtMost(SAMPLE_RATE * 300)

    val buffer = FloatArray(totalSamples)

    for (note in notes) {
        val startSec = ticksToSeconds(note.tick, ppq, tempoMap)
        val durSec = ticksToSeconds(note.durationTicks, ppq, tempoMap)
        val startSample = (startSec * SAMPLE_RATE).toInt().coerceIn(0, totalSamples - 1)
        val durSamples = (durSec * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val amplitude = ((note.velocity ?: 80) / 127f) * 0.3f
        pluckString(buffer, startSample, note.pitchMidi, durSamples, amplitude)
    }

    // Normalize to prevent clipping
    val peak = buffer.maxOfOrNull { abs(it) } ?: 1f
    val scale = if (peak > 0.9f) 0.9f / peak else 1f

    val out = ByteArrayOutputStream()
    writeWavHeader(out, totalSamples)
    val pcmBuf = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (s in buffer) {
        val v = (s * scale * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        pcmBuf.putShort(v.toShort())
    }
    out.write(pcmBuf.array())
    return out.toByteArray()
}

fun synthesizeSilence(durationSeconds: Double = 1.0): ByteArray {
    val samples = (durationSeconds * SAMPLE_RATE).toInt()
    val out = ByteArrayOutputStream()
    writeWavHeader(out, samples)
    out.write(ByteArray(samples * 2))
    return out.toByteArray()
}

fun exportKoraPerformanceToWavBytes(score: SimplifiedScore, mappedEvents: List<MappedEvent>): ByteArray {
    // Use original pitches (mapped events have the same ticks as original note events)
    val playableNotes = mappedEvents
        .filter { !it.omit }
        .map { me ->
            NoteEvent(
                eventId = me.sourceEventId,
                tick = me.tick,
                durationTicks = me.durationTicks,
                pitchMidi = me.pitchMidi,
                velocity = 80,
            )
        }
    return synthesizeWav(playableNotes, score.ppq, score.tempoMap)
}

fun exportSimplifiedScoreToWavBytes(score: SimplifiedScore): ByteArray =
    synthesizeWav(score.noteEvents, score.ppq, score.tempoMap)
