package com.leokinder2k.koratuningcompanion.livetuner.model

enum class TuningFeedbackState {
    IN_TUNE,
    FLAT,
    SHARP
}

object TuningFeedbackClassifier {
    const val DEFAULT_IN_TUNE_THRESHOLD_CENTS: Double = 5.0

    fun classify(
        centsDeviation: Double,
        inTuneThresholdCents: Double = DEFAULT_IN_TUNE_THRESHOLD_CENTS
    ): TuningFeedbackState {
        require(inTuneThresholdCents > 0.0) {
            "In-tune threshold must be positive."
        }

        return when {
            centsDeviation < -inTuneThresholdCents -> TuningFeedbackState.FLAT
            centsDeviation > inTuneThresholdCents -> TuningFeedbackState.SHARP
            else -> TuningFeedbackState.IN_TUNE
        }
    }
}

