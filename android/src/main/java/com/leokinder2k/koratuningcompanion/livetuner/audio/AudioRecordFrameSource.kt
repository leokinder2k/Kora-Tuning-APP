package com.leokinder2k.koratuningcompanion.livetuner.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.max

class AudioRecordFrameSource : AudioFrameSource {

    @SuppressLint("MissingPermission")
    override fun frames(sampleRate: Int, frameSize: Int): Flow<ShortArray> = callbackFlow {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize <= 0) {
            close(IllegalStateException("Unable to determine audio buffer size."))
            return@callbackFlow
        }

        val bufferSize = max(minBufferSize, frameSize * 2)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            close(IllegalStateException("AudioRecord failed to initialize."))
            return@callbackFlow
        }

        val readerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val readBuffer = ShortArray(frameSize)
        val readerJob = readerScope.launch {
            audioRecord.startRecording()
            while (isActive) {
                val readCount = audioRecord.read(readBuffer, 0, readBuffer.size)
                if (readCount > 0) {
                    trySend(readBuffer.copyOf(readCount))
                } else if (readCount == AudioRecord.ERROR_BAD_VALUE ||
                    readCount == AudioRecord.ERROR_INVALID_OPERATION
                ) {
                    close(IllegalStateException("AudioRecord read failed: $readCount"))
                    break
                }
            }
        }

        awaitClose {
            readerJob.cancel()
            runCatching {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
            }
            audioRecord.release()
        }
    }
}

