package com.leokinder2k.koratuningcompanion.synth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynthLatencyModeTest {
    @Test
    fun minimumModeRequestsSmallestRenderChunk() {
        assertEquals(64, SynthLatencyMode.Minimum.renderFramesFor(preferredFrames = 192))
    }

    @Test
    fun stableModeAddsMoreBufferThanLowMode() {
        val preferredFrames = 128
        val minBufferFrames = 240
        val lowFrames = SynthLatencyMode.Low.renderFramesFor(preferredFrames)
        val stableFrames = SynthLatencyMode.Stable.renderFramesFor(preferredFrames)

        val lowBuffer = SynthLatencyMode.Low.bufferFramesFor(minBufferFrames, lowFrames)
        val stableBuffer = SynthLatencyMode.Stable.bufferFramesFor(minBufferFrames, stableFrames)

        assertTrue(stableFrames > lowFrames)
        assertTrue(stableBuffer > lowBuffer)
    }

    @Test
    fun bufferNeverFallsBelowRenderChunkOrAndroidMinimum() {
        val renderFrames = SynthLatencyMode.Balanced.renderFramesFor(preferredFrames = 96)
        val bufferFrames = SynthLatencyMode.Balanced.bufferFramesFor(
            minBufferFrames = 1_024,
            renderFrames = renderFrames
        )

        assertTrue(bufferFrames >= renderFrames)
        assertTrue(bufferFrames >= 1_024)
    }
}
