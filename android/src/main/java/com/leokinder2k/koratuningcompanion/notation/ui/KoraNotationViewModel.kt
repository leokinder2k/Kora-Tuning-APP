package com.leokinder2k.koratuningcompanion.notation.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leokinder2k.koratuningcompanion.notation.engine.KoraNativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.zip.ZipInputStream
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.util.Base64
import kotlin.math.pow

data class NotationResult(
    val koraMidiBase64: String,
    val simplifiedMidiBase64: String,
    val title: String,
    val instrumentType: String,
    val sourceKind: String,
    val difficulty: Double,
    val tuningName: String,
    val retunePlanJson: String,
    val scoreJson: String,
    val timelineJson: String,
    val ppq: Int,
    val tempoMapJson: String,
    val keyFifths: Int,      // current key signature (updates after transpose)
    val keyMode: String,     // "MAJOR" or "MINOR"
)

sealed class NotationUiState {
    object Idle : NotationUiState()
    object Loading : NotationUiState()
    data class Success(val result: NotationResult) : NotationUiState()
    data class Error(val message: String) : NotationUiState()
}

sealed class ExportState {
    object Idle : ExportState()
    object LoadingAudio : ExportState()
    object LoadingPdf : ExportState()
    data class AudioReady(val koraBase64: String, val simplifiedBase64: String) : ExportState()
    data class PdfReady(val pdfBase64: String) : ExportState()
    data class Error(val message: String) : ExportState()
}

class KoraNotationViewModel(
    private val appContext: Context,
    private val engine: KoraNativeEngine = KoraNativeEngine(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotationUiState>(NotationUiState.Idle)
    val uiState: StateFlow<NotationUiState> = _uiState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _transposeOffset = MutableStateFlow(0)
    val transposeOffset: StateFlow<Int> = _transposeOffset.asStateFlow()

    private val _originalKeyFifths = MutableStateFlow(0)
    val originalKeyFifths: StateFlow<Int> = _originalKeyFifths.asStateFlow()

    private val _originalKeyMode = MutableStateFlow("MAJOR")
    val originalKeyMode: StateFlow<String> = _originalKeyMode.asStateFlow()

    private var currentScore: String? = null
    private var currentInstrumentType: String = "KORA_21"
    private var currentTitle: String = "Untitled"
    private var currentSourceKind: String = "MUSICXML"
    private var currentDifficulty: Double = 0.0
    // Populated lazily when audio is first generated; used for pitch preview.
    private var currentKoraAudioBase64: String = ""

    private var previewPlayer: MediaPlayer? = null

    fun stopPreview() {
        previewPlayer?.release()
        previewPlayer = null
    }

    fun previewTransposeDelta(deltaFromCurrent: Int) {
        val audio = currentKoraAudioBase64
        if (audio.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                previewPlayer?.release()
                val bytes = Base64.decode(audio, Base64.NO_WRAP)
                val file = java.io.File(appContext.cacheDir, "_preview_pitch.wav")
                file.writeBytes(bytes)
                previewPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    val pitchFactor = 2f.pow(deltaFromCurrent / 12f)
                    playbackParams = PlaybackParams().setPitch(pitchFactor)
                    start()
                    setOnCompletionListener { release(); previewPlayer = null }
                }
            } catch (_: Exception) {}
        }
    }

    fun processFile(context: Context, uri: Uri, instrumentType: String) {
        viewModelScope.launch {
            _uiState.value = NotationUiState.Loading
            try {
                // Resolve the real display name and MIME type from the content resolver.
                // uri.lastPathSegment is unreliable for content:// URIs (returns "audio:1234" etc.)
                val (fileName, mimeType) = withContext(Dispatchers.IO) {
                    var name = "file"
                    var mime = context.contentResolver.getType(uri) ?: ""
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            name = cursor.getString(0) ?: "file"
                        }
                    }
                    name to mime
                }
                val ext = when {
                    mimeType.contains("midi") || mimeType.contains("mid") -> "mid"
                    mimeType.contains("zip") || mimeType.contains("mxl") -> "mxl"
                    mimeType.contains("pdf") -> "pdf"
                    else -> fileName.substringAfterLast('.', "").lowercase()
                }
                val paramsJson = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    val title = fileName.substringBeforeLast('.')
                    when (ext) {
                        "mid", "midi" -> {
                            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            JSONObject().apply {
                                put("kind", "midi")
                                put("dataBase64", b64)
                                put("instrumentType", instrumentType)
                                put("title", title)
                            }.toString()
                        }
                        "mxl" -> {
                            val xmlText = extractXmlFromMxl(bytes)
                            JSONObject().apply {
                                put("kind", "xml")
                                put("xmlText", xmlText)
                                put("instrumentType", instrumentType)
                                put("title", title)
                            }.toString()
                        }
                        "pdf" -> {
                            val xmlText = extractXmlFromPdf(bytes)
                            JSONObject().apply {
                                put("kind", "xml")
                                put("xmlText", xmlText)
                                put("instrumentType", instrumentType)
                                put("title", title)
                            }.toString()
                        }
                        else -> {
                            val xmlText = bytes.decodeToString()
                            JSONObject().apply {
                                put("kind", "xml")
                                put("xmlText", xmlText)
                                put("instrumentType", instrumentType)
                                put("title", title)
                            }.toString()
                        }
                    }
                }
                val resultJson = withContext(Dispatchers.Default) { engine.process(paramsJson) }
                val result = parseResult(resultJson)
                currentScore = result.scoreJson
                currentInstrumentType = result.instrumentType
                currentTitle = result.title
                currentSourceKind = result.sourceKind
                currentDifficulty = result.difficulty
                currentKoraAudioBase64 = ""
                _transposeOffset.value = 0
                _originalKeyFifths.value = result.keyFifths
                _originalKeyMode.value = result.keyMode
                _exportState.value = ExportState.Idle
                _uiState.value = NotationUiState.Success(result)
            } catch (e: Exception) {
                _uiState.value = NotationUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun applyTransposeSemitone(semitones: Int) {
        applyEdit("""{"type":"TRANSPOSE_SEMITONE","semitones":$semitones}""", transposeChange = semitones)
    }

    fun resetTranspose() {
        val offset = _transposeOffset.value
        if (offset != 0) applyTransposeSemitone(-offset)
    }

    private fun applyEdit(editJson: String, transposeChange: Int = 0) {
        val score = currentScore ?: return
        viewModelScope.launch {
            _uiState.value = NotationUiState.Loading
            try {
                val paramsJson = JSONObject().apply {
                    put("score", JSONObject(score))
                    put("edit", JSONObject(editJson))
                    put("instrumentType", currentInstrumentType)
                    put("title", currentTitle)
                    put("sourceKind", currentSourceKind)
                }.toString()
                val resultJson = withContext(Dispatchers.Default) { engine.edit(paramsJson) }
                val result = parseResult(resultJson)
                currentScore = result.scoreJson
                currentInstrumentType = result.instrumentType
                currentTitle = result.title
                currentSourceKind = result.sourceKind
                currentDifficulty = result.difficulty
                currentKoraAudioBase64 = ""
                // Update offset only when the edit actually succeeded
                _transposeOffset.value += transposeChange
                _exportState.value = ExportState.Idle
                _uiState.value = NotationUiState.Success(result)
            } catch (e: Exception) {
                _uiState.value = NotationUiState.Error(e.message ?: "Edit failed")
            }
        }
    }

    fun requestAudioExport() {
        val score = currentScore ?: return
        viewModelScope.launch {
            _exportState.value = ExportState.LoadingAudio
            try {
                val paramsJson = JSONObject().apply {
                    put("score", JSONObject(score))
                    put("instrumentType", currentInstrumentType)
                }.toString()
                val resultJson = withContext(Dispatchers.Default) { engine.exportAudio(paramsJson) }
                val obj = JSONObject(resultJson)
                val koraB64 = obj.optString("koraAudioBase64")
                currentKoraAudioBase64 = koraB64
                _exportState.value = ExportState.AudioReady(
                    koraBase64 = koraB64,
                    simplifiedBase64 = obj.optString("simplifiedAudioBase64"),
                )
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Audio export failed")
            }
        }
    }

    fun requestPdfExport() {
        val score = currentScore ?: return
        viewModelScope.launch {
            _exportState.value = ExportState.LoadingPdf
            try {
                val paramsJson = JSONObject().apply {
                    put("score", JSONObject(score))
                    put("instrumentType", currentInstrumentType)
                    put("title", currentTitle)
                    put("difficulty", currentDifficulty)
                }.toString()
                val resultJson = withContext(Dispatchers.Default) { engine.exportPdf(paramsJson) }
                val obj = JSONObject(resultJson)
                _exportState.value = ExportState.PdfReady(pdfBase64 = obj.optString("pdfBase64"))
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "PDF export failed")
            }
        }
    }

    fun clearExportState() {
        _exportState.value = ExportState.Idle
    }

    private fun parseResult(json: String): NotationResult {
        val obj = JSONObject(json)
        val meta = obj.optJSONObject("metadata") ?: JSONObject()
        val scoreObj = obj.optJSONObject("score")
        val firstKs = scoreObj?.optJSONArray("keySignatures")?.optJSONObject(0)
        return NotationResult(
            koraMidiBase64 = obj.optString("koraMidiBase64"),
            simplifiedMidiBase64 = obj.optString("simplifiedMidiBase64"),
            title = meta.optString("title", "Untitled"),
            instrumentType = meta.optString("instrumentType", "KORA_21"),
            sourceKind = meta.optString("sourceKind", "MUSICXML"),
            difficulty = meta.optDouble("difficulty", 0.0),
            tuningName = meta.optString("tuningName", "F tuning"),
            retunePlanJson = obj.optJSONObject("retunePlan")?.toString() ?: "{}",
            scoreJson = scoreObj?.toString() ?: "{}",
            timelineJson = obj.optJSONObject("timeline")?.toString() ?: "{}",
            ppq = obj.optInt("ppq", 960),
            tempoMapJson = obj.optJSONArray("tempoMap")?.toString() ?: "[]",
            keyFifths = firstKs?.optInt("fifths", 0) ?: 0,
            keyMode = firstKs?.optString("mode") ?: "MAJOR",
        )
    }

    private fun extractXmlFromPdf(bytes: ByteArray): String {
        val text = String(bytes, Charsets.ISO_8859_1)
        val xmlStart = text.indexOf("<?xml")
        if (xmlStart >= 0) {
            val endStream = text.indexOf("endstream", xmlStart).takeIf { it > xmlStart }
                ?: (xmlStart + 2_097_152).coerceAtMost(text.length)
            val candidate = text.substring(xmlStart, endStream).trimEnd()
            if (candidate.contains("score-partwise", ignoreCase = true) ||
                candidate.contains("score-timewise", ignoreCase = true)) {
                return candidate
            }
        }
        throw Exception(
            "No embedded MusicXML found in this PDF.\n" +
            "Export your score as .musicxml, .mxl, or .mid and import that file instead."
        )
    }

    private fun extractXmlFromMxl(bytes: ByteArray): String {
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            var rootXmlPath: String? = null
            val entries = mutableMapOf<String, ByteArray>()
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
            // Read container.xml to find rootfile
            val containerBytes = entries["META-INF/container.xml"]
            if (containerBytes != null) {
                val containerXml = containerBytes.decodeToString()
                val m = Regex("""<rootfile\b[^>]*\bfull-path="([^"]+)"""").find(containerXml)
                rootXmlPath = m?.groupValues?.get(1)
            }
            if (rootXmlPath != null && entries.containsKey(rootXmlPath)) {
                return entries[rootXmlPath]!!.decodeToString()
            }
            // Fallback: first .xml entry that isn't container.xml
            return entries.entries
                .firstOrNull { it.key.endsWith(".xml") && it.key != "META-INF/container.xml" }
                ?.value?.decodeToString()
                ?: throw Exception("No score XML found in MXL file")
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                KoraNotationViewModel(
                    appContext = context.applicationContext,
                    engine = KoraNativeEngine(),
                )
            }
        }
    }
}
