package com.leokinder2k.koratuningcompanion.notation.engine

// ── Score event types ────────────────────────────────────────────────────────

data class NoteEvent(
    val eventId: String?,
    val type: String = "NOTE",
    val tick: Int,
    val durationTicks: Int,
    val pitchMidi: Int,
    val velocity: Int? = null,
    val role: String? = null,      // MELODY | HARMONY | BASS
    val staff: String? = null,     // UPPER | LOWER
    val tieStart: Boolean = false,
    val tieStop: Boolean = false,
    val lyric: String? = null,
    val chordSymbol: String? = null,
    val sourceEventId: String? = null,
    val melodyHint: Boolean = false,
    val voice: Int? = null,
    val partId: String? = null,
    val trackIndex: Int = 0,
)

data class RestEvent(
    val eventId: String?,
    val type: String = "REST",
    val tick: Int,
    val durationTicks: Int,
    val staff: String? = null,
    val sourceEventId: String? = null,
)

data class ChordSymbolEvent(
    val eventId: String?,
    val type: String = "CHORD_SYMBOL",
    val tick: Int,
    val text: String,
)

// ── Score metadata ────────────────────────────────────────────────────────────

data class MeasureInfo(
    val measureNumber: Int,
    val startTick: Int,
    val lengthTicks: Int,
    val timeSigNum: Int = 4,
    val timeSigDen: Int = 4,
)

data class KeySignatureInfo(
    val tick: Int,
    val fifths: Int,
    val mode: String = "MAJOR",   // MAJOR | MINOR
)

data class TempoInfo(
    val tick: Int,
    val bpm: Double,
)

data class TimeSignatureInfo(
    val tick: Int,
    val num: Int,
    val den: Int,
)

// ── Simplified score (the unified intermediate representation) ────────────────

data class SimplifiedScore(
    val ppq: Int = 960,
    val noteEvents: List<NoteEvent>,
    val restEvents: List<RestEvent>,
    val chordSymbols: List<ChordSymbolEvent> = emptyList(),
    val measures: List<MeasureInfo>,
    val keySignatures: List<KeySignatureInfo>,
    val tempoMap: List<TempoInfo>,
    val timeSignatures: List<TimeSignatureInfo>,
    val sourceKind: String = "MUSICXML",
)

// ── Mapped event (kora string assignment result) ─────────────────────────────

enum class NoteRole { MELODY, HARMONY, BASS }
enum class AccidentalSuggestion { NONE, SHARP, FLAT }

data class MappedEvent(
    val sourceEventId: String?,
    val tick: Int,
    val durationTicks: Int,
    val measureNumber: Int,
    val role: NoteRole,
    val pitchMidi: Int,
    val stringId: String?,
    val digitLine: String?,
    val renderedNumber: Int?,
    val accidentalSuggestion: AccidentalSuggestion,
    val omit: Boolean,
)

// ── Retune plan ──────────────────────────────────────────────────────────────

data class BarInstruction(
    val measureNumber: Int,
    val appliesFromMeasureNumber: Int,
    val stringId: String,
    val deltaSemitones: Int,
)

data class RetunePlan(
    val perStringNetChange: Map<String, Int>,
    val barInstructions: List<BarInstruction>,
)

// ── Pipeline output ──────────────────────────────────────────────────────────

data class KoraMappingResult(
    val events: List<MappedEvent>,
    val retunePlan: RetunePlan,
)
