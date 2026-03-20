package com.leokinder2k.koratuningcompanion.notation.engine

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Native Kotlin replacement for KoraEngineBridge + JS kora_engine.
 *
 * Exposes the same [process] and [edit] API as the old WebView bridge,
 * accepting and returning the identical JSON format so that
 * KoraNotationViewModel requires zero changes.
 *
 * No WebView, no JavaScript, no network — pure Kotlin algorithms.
 */
class KoraNativeEngine {

    suspend fun process(paramsJson: String): String {
        val p = JSONObject(paramsJson)
        val kind = p.optString("kind", "xml")
        val instrumentType = KoraInstrumentType.fromString(p.optString("instrumentType", "KORA_21"))
        val title = p.optString("title", "Untitled")

        val tuning = fTuning(instrumentType)
        val tuningMidi = tuningToMidi(tuning)
        val tuningMidiSet = tuningMidi.values.toSet()

        val score: SimplifiedScore = when (kind) {
            "midi" -> {
                val bytes = Base64.decode(p.getString("dataBase64"), Base64.NO_WRAP)
                importMidiToSimplifiedScore(bytes, tuningMidi = tuningMidiSet)
            }
            else -> {
                val xmlText = p.getString("xmlText")
                importMusicXmlToSimplifiedScore(xmlText, tuningMidi = tuningMidiSet)
            }
        }

        return buildResultJson(score, instrumentType, tuning, tuningMidi, title, kind)
    }

    suspend fun edit(paramsJson: String): String {
        val p = JSONObject(paramsJson)
        val instrumentType = KoraInstrumentType.fromString(p.optString("instrumentType", "KORA_21"))
        val title = p.optString("title", "Untitled")
        val sourceKind = p.optString("sourceKind", "MUSICXML")
        val editObj = p.optJSONObject("edit") ?: JSONObject()
        val editType = editObj.optString("type", "")

        val tuning = fTuning(instrumentType)
        val tuningMidi = tuningToMidi(tuning)
        val tuningMidiSet = tuningMidi.values.toSet()

        // Reconstruct score from JSON
        var score = scoreFromJson(p.optJSONObject("score"), tuningMidiSet)

        // Apply edit
        score = when (editType) {
            "TRANSPOSE_SEMITONE" -> transposeScoreSemitone(score, editObj.optInt("semitones", 0))
            else -> score
        }

        return buildResultJson(score, instrumentType, tuning, tuningMidi, title, sourceKind)
    }

    // ── Score serialisation ───────────────────────────────────────────────────

    private fun buildResultJson(
        score: SimplifiedScore,
        instrumentType: KoraInstrumentType,
        tuning: KoraTuning,
        tuningMidi: Map<String, Int>,
        title: String,
        sourceKind: String,
    ): String {
        val mappingResult = mapSimplifiedScoreToKora(instrumentType, tuningMidi, score)
        val retunePlan = mappingResult.retunePlan
        val mappedEvents = mappingResult.events

        val pdfBytes = exportLessonToPdfBytes(
            score, mappedEvents, retunePlan,
            PdfExportMeta(
                pieceName = title,
                tuningName = tuning.name,
                exportedAtIso = Instant.now().toString(),
                difficulty = estimateDifficulty(mappedEvents),
            )
        )
        val wavKora = exportKoraPerformanceToWavBytes(score, mappedEvents)
        val wavSimpl = exportSimplifiedScoreToWavBytes(score)
        val midiKora = exportKoraPerformanceToMidiBytes(score, mappedEvents)
        val midiSimpl = exportSimplifiedScoreToMidiBytes(score)

        val difficulty = estimateDifficulty(mappedEvents)

        return JSONObject().apply {
            put("pdfBase64", Base64.encodeToString(pdfBytes, Base64.NO_WRAP))
            put("koraAudioBase64", Base64.encodeToString(wavKora, Base64.NO_WRAP))
            put("simplifiedAudioBase64", Base64.encodeToString(wavSimpl, Base64.NO_WRAP))
            put("koraMidiBase64", Base64.encodeToString(midiKora, Base64.NO_WRAP))
            put("simplifiedMidiBase64", Base64.encodeToString(midiSimpl, Base64.NO_WRAP))
            put("ppq", score.ppq)
            put("tempoMap", tempoMapToJson(score.tempoMap))
            put("timeline", JSONObject())  // placeholder — layout model not ported
            put("retunePlan", retunePlanToJson(retunePlan))
            put("score", scoreToJson(score))
            put("metadata", JSONObject().apply {
                put("title", title)
                put("instrumentType", instrumentType.name)
                put("sourceKind", sourceKind.uppercase())
                put("difficulty", difficulty)
                put("tuningName", tuning.name)
            })
        }.toString()
    }

    // ── Difficulty estimate ───────────────────────────────────────────────────

    private fun estimateDifficulty(mappedEvents: List<MappedEvent>): Double {
        if (mappedEvents.isEmpty()) return 0.0
        val omitted = mappedEvents.count { it.omit }
        val accidentals = mappedEvents.count { it.accidentalSuggestion != AccidentalSuggestion.NONE }
        val total = mappedEvents.size.toDouble()
        return ((omitted + accidentals * 0.5) / total).coerceIn(0.0, 1.0)
    }

    // ── JSON serialisation helpers ────────────────────────────────────────────

    private fun retunePlanToJson(plan: RetunePlan): JSONObject = JSONObject().apply {
        val netChange = JSONObject()
        for ((k, v) in plan.perStringNetChange) netChange.put(k, v)
        put("perStringNetChange", netChange)
        put("barInstructions", JSONArray().also { arr ->
            for (inst in plan.barInstructions) {
                arr.put(JSONObject().apply {
                    put("measureNumber", inst.measureNumber)
                    put("appliesFromMeasureNumber", inst.appliesFromMeasureNumber)
                    put("stringId", inst.stringId)
                    put("deltaSemitones", inst.deltaSemitones)
                })
            }
        })
    }

    private fun tempoMapToJson(tempoMap: List<TempoInfo>): JSONArray = JSONArray().also { arr ->
        for (t in tempoMap) {
            arr.put(JSONObject().apply {
                put("tick", t.tick)
                put("bpm", t.bpm)
            })
        }
    }

    private fun scoreToJson(score: SimplifiedScore): JSONObject = JSONObject().apply {
        put("ppq", score.ppq)
        put("keySignatures", JSONArray().also { arr ->
            for (ks in score.keySignatures) {
                arr.put(JSONObject().apply {
                    put("tick", ks.tick)
                    put("fifths", ks.fifths)
                    put("mode", ks.mode)
                })
            }
        })
        put("tempoMap", tempoMapToJson(score.tempoMap))
        put("measures", JSONArray().also { arr ->
            for (m in score.measures) {
                arr.put(JSONObject().apply {
                    put("measureNumber", m.measureNumber)
                    put("startTick", m.startTick)
                    put("lengthTicks", m.lengthTicks)
                })
            }
        })
        put("events", JSONArray().also { arr ->
            for (e in score.noteEvents) {
                arr.put(JSONObject().apply {
                    put("type", "NOTE")
                    put("eventId", e.eventId ?: "")
                    put("tick", e.tick)
                    put("durationTicks", e.durationTicks)
                    put("pitchMidi", e.pitchMidi)
                    e.velocity?.let { put("velocity", it) }
                    e.role?.let { put("role", it) }
                    e.staff?.let { put("staff", it) }
                })
            }
            for (e in score.restEvents) {
                arr.put(JSONObject().apply {
                    put("type", "REST")
                    put("eventId", e.eventId ?: "")
                    put("tick", e.tick)
                    put("durationTicks", e.durationTicks)
                })
            }
        })
    }

    // ── Score deserialisation (for edit flow) ─────────────────────────────────

    private fun scoreFromJson(obj: JSONObject?, tuningMidiSet: Set<Int>): SimplifiedScore {
        if (obj == null) return SimplifiedScore(
            noteEvents = emptyList(), restEvents = emptyList(),
            measures = listOf(MeasureInfo(1, 0, 3840)),
            keySignatures = listOf(KeySignatureInfo(0, 0)),
            tempoMap = listOf(TempoInfo(0, 120.0)),
            timeSignatures = listOf(TimeSignatureInfo(0, 4, 4)),
        )

        val ppq = obj.optInt("ppq", 960)

        val keySigs = mutableListOf<KeySignatureInfo>()
        val ksArr = obj.optJSONArray("keySignatures")
        if (ksArr != null) {
            for (i in 0 until ksArr.length()) {
                val ks = ksArr.getJSONObject(i)
                keySigs.add(KeySignatureInfo(ks.optInt("tick"), ks.optInt("fifths"), ks.optString("mode", "MAJOR")))
            }
        }
        if (keySigs.isEmpty() || keySigs[0].tick != 0) keySigs.add(0, KeySignatureInfo(0, 0))

        val tempoMap = mutableListOf<TempoInfo>()
        val tmArr = obj.optJSONArray("tempoMap")
        if (tmArr != null) {
            for (i in 0 until tmArr.length()) {
                val t = tmArr.getJSONObject(i)
                tempoMap.add(TempoInfo(t.optInt("tick"), t.optDouble("bpm", 120.0)))
            }
        }
        if (tempoMap.isEmpty() || tempoMap[0].tick != 0) tempoMap.add(0, TempoInfo(0, 120.0))

        val measures = mutableListOf<MeasureInfo>()
        val mArr = obj.optJSONArray("measures")
        if (mArr != null) {
            for (i in 0 until mArr.length()) {
                val m = mArr.getJSONObject(i)
                measures.add(MeasureInfo(m.optInt("measureNumber", i + 1), m.optInt("startTick"), m.optInt("lengthTicks", 3840)))
            }
        }
        if (measures.isEmpty()) measures.add(MeasureInfo(1, 0, 3840))

        val noteEvents = mutableListOf<NoteEvent>()
        val restEvents = mutableListOf<RestEvent>()
        val evArr = obj.optJSONArray("events")
        if (evArr != null) {
            for (i in 0 until evArr.length()) {
                val e = evArr.getJSONObject(i)
                when (e.optString("type")) {
                    "NOTE" -> noteEvents.add(NoteEvent(
                        eventId = e.optString("eventId").takeIf { it.isNotEmpty() },
                        tick = e.optInt("tick"),
                        durationTicks = e.optInt("durationTicks", 240),
                        pitchMidi = e.optInt("pitchMidi"),
                        velocity = if (e.has("velocity")) e.getInt("velocity") else null,
                        role = e.optString("role").takeIf { it.isNotEmpty() },
                        staff = e.optString("staff").takeIf { it.isNotEmpty() },
                    ))
                    "REST" -> restEvents.add(RestEvent(
                        eventId = e.optString("eventId").takeIf { it.isNotEmpty() },
                        tick = e.optInt("tick"),
                        durationTicks = e.optInt("durationTicks", 240),
                    ))
                }
            }
        }

        return SimplifiedScore(
            ppq = ppq,
            noteEvents = noteEvents,
            restEvents = restEvents,
            measures = measures,
            keySignatures = keySigs,
            tempoMap = tempoMap,
            timeSignatures = listOf(TimeSignatureInfo(0, 4, 4)),
        )
    }
}
