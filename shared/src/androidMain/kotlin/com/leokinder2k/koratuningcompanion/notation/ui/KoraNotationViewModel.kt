package com.leokinder2k.koratuningcompanion.notation.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leokinder2k.koratuningcompanion.notation.engine.KoraEngineBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.zip.ZipInputStream
import android.util.Base64

data class NotationResult(
    val pdfBase64: String,
    val koraAudioBase64: String,
    val simplifiedAudioBase64: String,
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
)

sealed class NotationUiState {
    object Idle : NotationUiState()
    object Loading : NotationUiState()
    data class Success(val result: NotationResult) : NotationUiState()
    data class Error(val message: String) : NotationUiState()
}

class KoraNotationViewModel(
    private val appContext: Context,
    val bridge: KoraEngineBridge,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotationUiState>(NotationUiState.Idle)
    val uiState: StateFlow<NotationUiState> = _uiState.asStateFlow()

    private var currentScore: String? = null
    private var currentInstrumentType: String = "KORA_21"
    private var currentTitle: String = "Untitled"
    private var currentSourceKind: String = "MUSICXML"

    fun processFile(context: Context, uri: Uri, instrumentType: String) {
        viewModelScope.launch {
            _uiState.value = NotationUiState.Loading
            try {
                val fileName = uri.lastPathSegment ?: "file"
                val ext = fileName.substringAfterLast('.', "").lowercase()
                val paramsJson = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    when (ext) {
                        "mid", "midi" -> {
                            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            val title = fileName.substringBeforeLast('.')
                            """{"kind":"midi","dataBase64":"$b64","instrumentType":"$instrumentType","title":"${title.replace("\"","'")}"}"""
                        }
                        "mxl" -> {
                            val xmlText = extractXmlFromMxl(bytes)
                            val title = fileName.substringBeforeLast('.')
                            val escaped = xmlText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                            """{"kind":"xml","xmlText":"$escaped","instrumentType":"$instrumentType","title":"${title.replace("\"","'")}"}"""
                        }
                        "pdf" -> {
                            val xmlText = extractXmlFromPdf(bytes)
                            val title = fileName.substringBeforeLast('.')
                            val escaped = xmlText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                            """{"kind":"xml","xmlText":"$escaped","instrumentType":"$instrumentType","title":"${title.replace("\"","'")}"}"""
                        }
                        else -> {
                            val xmlText = bytes.decodeToString()
                            val title = fileName.substringBeforeLast('.')
                            val escaped = xmlText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                            """{"kind":"xml","xmlText":"$escaped","instrumentType":"$instrumentType","title":"${title.replace("\"","'")}"}"""
                        }
                    }
                }
                val resultJson = bridge.process(paramsJson)
                val result = parseResult(resultJson)
                currentScore = result.scoreJson
                currentInstrumentType = result.instrumentType
                currentTitle = result.title
                currentSourceKind = result.sourceKind
                _uiState.value = NotationUiState.Success(result)
            } catch (e: Exception) {
                _uiState.value = NotationUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun applyTransposeSemitone(semitones: Int) = applyEdit(
        """{"type":"TRANSPOSE_SEMITONE","semitones":$semitones}"""
    )

    fun applyTransposeDiatonic(steps: Int) = applyEdit(
        """{"type":"TRANSPOSE_DIATONIC","steps":$steps}"""
    )

    private fun applyEdit(editJson: String) {
        val score = currentScore ?: return
        viewModelScope.launch {
            _uiState.value = NotationUiState.Loading
            try {
                val paramsJson = """{"score":$score,"edit":$editJson,"instrumentType":"$currentInstrumentType","title":"${currentTitle.replace("\"","'")}","sourceKind":"$currentSourceKind"}"""
                val resultJson = bridge.edit(paramsJson)
                val result = parseResult(resultJson)
                currentScore = result.scoreJson
                currentInstrumentType = result.instrumentType
                currentTitle = result.title
                currentSourceKind = result.sourceKind
                _uiState.value = NotationUiState.Success(result)
            } catch (e: Exception) {
                _uiState.value = NotationUiState.Error(e.message ?: "Edit failed")
            }
        }
    }

    private fun parseResult(json: String): NotationResult {
        val obj = JSONObject(json)
        val meta = obj.optJSONObject("metadata") ?: JSONObject()
        return NotationResult(
            pdfBase64 = obj.optString("pdfBase64"),
            koraAudioBase64 = obj.optString("koraAudioBase64"),
            simplifiedAudioBase64 = obj.optString("simplifiedAudioBase64"),
            koraMidiBase64 = obj.optString("koraMidiBase64"),
            simplifiedMidiBase64 = obj.optString("simplifiedMidiBase64"),
            title = meta.optString("title", "Untitled"),
            instrumentType = meta.optString("instrumentType", "KORA_21"),
            sourceKind = meta.optString("sourceKind", "MUSICXML"),
            difficulty = meta.optDouble("difficulty", 0.0),
            tuningName = meta.optString("tuningName", "F tuning"),
            retunePlanJson = obj.optJSONObject("retunePlan")?.toString() ?: "{}",
            scoreJson = obj.optJSONObject("score")?.toString() ?: "{}",
            timelineJson = obj.optJSONObject("timeline")?.toString() ?: "{}",
            ppq = obj.optInt("ppq", 960),
            tempoMapJson = obj.optJSONArray("tempoMap")?.toString() ?: "[]",
        )
    }

    /**
     * Scans PDF bytes for an embedded MusicXML stream.
     * Many notation apps (MuseScore, Finale, Sibelius) embed MusicXML as a
     * PDF stream starting with `<?xml`. Throws if none is found.
     */
    private fun extractXmlFromPdf(bytes: ByteArray): String {
        // Decode as Latin-1 so binary bytes survive round-trip without corruption.
        val text = String(bytes, Charsets.ISO_8859_1)
        val xmlStart = text.indexOf("<?xml")
        if (xmlStart >= 0) {
            // Grab up to endstream marker or 2 MB, whichever comes first.
            val endStream = text.indexOf("endstream", xmlStart).takeIf { it > xmlStart }
                ?: (xmlStart + 2_097_152).coerceAtMost(text.length)
            val candidate = text.substring(xmlStart, endStream).trimEnd()
            // Must look like MusicXML (contains score-partwise or score-timewise root).
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
            val entries = mutableMapOf<String, ByteArray>()
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
            val containerBytes = entries["META-INF/container.xml"]
            var rootXmlPath: String? = null
            if (containerBytes != null) {
                val m = Regex("""<rootfile\b[^>]*\bfull-path="([^"]+)"""").find(containerBytes.decodeToString())
                rootXmlPath = m?.groupValues?.get(1)
            }
            if (rootXmlPath != null && entries.containsKey(rootXmlPath)) {
                return entries[rootXmlPath]!!.decodeToString()
            }
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
                    bridge = KoraEngineBridge(context.applicationContext),
                )
            }
        }
    }
}
