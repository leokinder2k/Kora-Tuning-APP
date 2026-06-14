@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.io.encoding.ExperimentalEncodingApi::class)

package com.leokinder2k.koratuningcompanion.notation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import com.leokinder2k.koratuningcompanion.generated.resources.Res
import com.leokinder2k.koratuningcompanion.generated.resources.*
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import platform.AVFAudio.AVAudioPlayer
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSData
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataWithBytes
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.memcpy
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream_s
import kotlin.io.encoding.Base64
import kotlin.math.min

private val ANDROID_BRIDGE_SHIM = """
window.AndroidBridge = {
  onReady: function() {
    window.webkit.messageHandlers.koraReady.postMessage(null);
  },
  onResult: function(callbackId, resultJson, error) {
    window.webkit.messageHandlers.koraResult.postMessage(
      JSON.stringify({ callbackId: callbackId, resultJson: resultJson, error: error })
    );
  }
};
""".trimIndent()

@Composable
actual fun KoraNotationRoute(
    modifier: Modifier,
    isMuted: Boolean
) {
    val controller = remember { IosKoraEngineController() }
    val scope = rememberCoroutineScope()

    var selectedInstrument by remember { mutableStateOf("KORA_21") }
    var selectedFileName by remember { mutableStateOf("") }
    var uiState by remember { mutableStateOf<NotationUiState>(NotationUiState.Idle) }
    var exportState by remember { mutableStateOf<ExportState>(ExportState.Idle) }

    LaunchedEffect(isMuted) {
        if (isMuted) controller.stopActivePlayback()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.notation_upload_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(Res.string.notation_upload_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("KORA_21", "KORA_22").forEach { inst ->
                        FilterChip(
                            selected = selectedInstrument == inst,
                            onClick = { selectedInstrument = inst },
                            label = { Text(if (inst == "KORA_21") "21-string" else "22-string") }
                        )
                    }
                }
                if (selectedFileName.isNotBlank()) {
                    Text(
                        text = selectedFileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.Main) {
                            uiState = NotationUiState.Loading
                            exportState = ExportState.Idle
                            runCatching {
                                controller.pickAndProcess(selectedInstrument)
                            }.onSuccess { picked ->
                                if (picked == null) {
                                    uiState = NotationUiState.Idle
                                } else {
                                    selectedFileName = picked.fileName
                                    uiState = NotationUiState.Success(picked.result)
                                }
                            }.onFailure { error ->
                                uiState = NotationUiState.Error(importErrorMessage(error))
                            }
                        }
                    },
                    enabled = uiState !is NotationUiState.Loading
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.notation_pick_file))
                }
            }
        }

        when (val state = uiState) {
            is NotationUiState.Loading -> ProcessingRow()
            is NotationUiState.Error -> ErrorCard(state.message)
            is NotationUiState.Success -> ResultSection(
                result = state.result,
                exportState = exportState,
                isMuted = isMuted,
                onTransposeSemitone = { semitones ->
                    scope.launch(Dispatchers.Main) {
                        uiState = NotationUiState.Loading
                        exportState = ExportState.Idle
                        runCatching {
                            controller.applyEdit(
                                result = state.result,
                                editJson = """{"type":"TRANSPOSE_SEMITONE","semitones":$semitones}"""
                            )
                        }.onSuccess { edited ->
                            uiState = NotationUiState.Success(edited)
                        }.onFailure {
                            uiState = NotationUiState.Error("Could not apply that edit to this score.")
                        }
                    }
                },
                onTransposeDiatonic = { steps ->
                    scope.launch(Dispatchers.Main) {
                        uiState = NotationUiState.Loading
                        exportState = ExportState.Idle
                        runCatching {
                            controller.applyEdit(
                                result = state.result,
                                editJson = """{"type":"TRANSPOSE_DIATONIC","steps":$steps}"""
                            )
                        }.onSuccess { edited ->
                            uiState = NotationUiState.Success(edited)
                        }.onFailure {
                            uiState = NotationUiState.Error("Could not apply that edit to this score.")
                        }
                    }
                },
                onShareMidi = {
                    runCatching {
                        controller.shareBase64(
                            base64 = state.result.koraMidiBase64,
                            fileName = "${state.result.title}_kora.mid",
                            mimeType = "audio/midi"
                        )
                    }.onFailure { exportState = ExportState.Error("MIDI export failed for this score.") }
                },
                onSharePdf = {
                    scope.launch(Dispatchers.Main) {
                        exportState = ExportState.LoadingPdf
                        runCatching {
                            val pdfBase64 = controller.exportPdf(state.result)
                            controller.shareBase64(
                                base64 = pdfBase64,
                                fileName = "${state.result.title}.pdf",
                                mimeType = "application/pdf"
                            )
                        }.onSuccess {
                            exportState = ExportState.Idle
                        }.onFailure {
                            exportState = ExportState.Error("PDF export failed for this score.")
                        }
                    }
                },
                onPlayKora = {
                    scope.launch(Dispatchers.Main) {
                        val audioReady = exportState as? ExportState.AudioReady
                        val audio = if (audioReady != null) {
                            audioReady
                        } else {
                            exportState = ExportState.LoadingAudio
                            runCatching { controller.exportAudio(state.result) }
                                .onSuccess { exportState = it }
                                .onFailure { exportState = ExportState.Error("Audio export failed for this score.") }
                                .getOrNull() ?: return@launch
                        }
                        controller.playBase64Wav(audio.koraBase64, isMuted)
                    }
                },
                onPlayOriginal = {
                    scope.launch(Dispatchers.Main) {
                        val audioReady = exportState as? ExportState.AudioReady
                        val audio = if (audioReady != null) {
                            audioReady
                        } else {
                            exportState = ExportState.LoadingAudio
                            runCatching { controller.exportAudio(state.result) }
                                .onSuccess { exportState = it }
                                .onFailure { exportState = ExportState.Error("Audio export failed for this score.") }
                                .getOrNull() ?: return@launch
                        }
                        controller.playBase64Wav(audio.simplifiedBase64, isMuted)
                    }
                }
            )
            NotationUiState.Idle -> {}
        }
    }

    Box(modifier = Modifier.size(0.dp)) {
        UIKitView(
            factory = { controller.buildWebView() },
            modifier = Modifier.size(1.dp),
            onRelease = { controller.stopActivePlayback() }
        )
    }
}

@Composable
private fun ProcessingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            stringResource(Res.string.notation_processing),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ResultSection(
    result: NotationResult,
    exportState: ExportState,
    isMuted: Boolean,
    onTransposeSemitone: (Int) -> Unit,
    onTransposeDiatonic: (Int) -> Unit,
    onShareMidi: () -> Unit,
    onSharePdf: () -> Unit,
    onPlayKora: () -> Unit,
    onPlayOriginal: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(result.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "${result.instrumentType.replace("_", " ")} - ${result.tuningName} - ${result.sourceKind}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(Res.string.notation_difficulty, (result.difficulty * 100).toInt()),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    KoraDiagramCard(instrumentType = result.instrumentType)
    RetunePlanCard(instructions = result.retuneInstructions)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.notation_transpose_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onTransposeSemitone(-1) }) { Text("-1 semitone") }
                OutlinedButton(onClick = { onTransposeSemitone(1) }) { Text("+1 semitone") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onTransposeDiatonic(-1) }) { Text("-1 step") }
                OutlinedButton(onClick = { onTransposeDiatonic(1) }) { Text("+1 step") }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.notation_export_title), style = MaterialTheme.typography.titleMedium)

            val exportBusy = exportState is ExportState.LoadingAudio || exportState is ExportState.LoadingPdf

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSharePdf,
                    enabled = !exportBusy
                ) {
                    if (exportState is ExportState.LoadingPdf) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("PDF")
                }
                Button(
                    onClick = onShareMidi,
                    enabled = !exportBusy
                ) {
                    Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("MIDI")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPlayKora,
                    enabled = !exportBusy && !isMuted
                ) {
                    if (exportState is ExportState.LoadingAudio) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.notation_play_kora))
                }
                OutlinedButton(
                    onClick = onPlayOriginal,
                    enabled = !exportBusy && !isMuted
                ) {
                    if (exportState is ExportState.LoadingAudio) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.notation_play_original))
                }
            }

            if (exportState is ExportState.Error) {
                Text(
                    exportState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun RetunePlanCard(instructions: List<RetuneInstruction>) {
    if (instructions.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.notation_retune_title), style = MaterialTheme.typography.titleMedium)
            instructions.take(10).forEach { instruction ->
                val arrow = if (instruction.deltaSemitones > 0) "up" else "down"
                Text(
                    "Bar ${instruction.measureNumber}: ${instruction.stringId} $arrow",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (instructions.size > 10) {
                Text("...and ${instructions.size - 10} more", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun KoraDiagramCard(instrumentType: String) {
    val isChromatic = instrumentType == "KORA_22"
    val leftCount = 11
    val rightCount = if (isChromatic) 11 else 10
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.notation_diagram_title), style = MaterialTheme.typography.titleMedium)
            Text(
                if (isChromatic) "22-string chromatic" else "21-string",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                val w = size.width
                val h = size.height
                val bridgeY = h * 0.5f
                val gourdR = h * 0.35f
                drawCircle(color = surface, radius = gourdR, center = Offset(w / 2f, bridgeY))
                drawCircle(color = primary, radius = gourdR, center = Offset(w / 2f, bridgeY), style = Stroke(2f))
                drawLine(primary, Offset(w * 0.3f, bridgeY), Offset(w * 0.7f, bridgeY), strokeWidth = 3f)
                val leftSpacing = (w * 0.28f) / (leftCount + 1)
                for (i in 1..leftCount) {
                    val x = w * 0.02f + leftSpacing * i
                    drawLine(primary, Offset(x, 0f), Offset(w * 0.3f + leftSpacing * i * 0.1f, bridgeY), strokeWidth = 1.5f)
                }
                val rightSpacing = (w * 0.28f) / (rightCount + 1)
                for (i in 1..rightCount) {
                    val x = w * 0.72f + rightSpacing * i
                    drawLine(primary, Offset(x, 0f), Offset(w * 0.7f + rightSpacing * i * 0.1f, bridgeY), strokeWidth = 1.5f)
                }
            }
        }
    }
}

private data class PickedNotationResult(
    val fileName: String,
    val result: NotationResult
)

private data class ImportedFile(
    val displayName: String,
    val extension: String,
    val bytes: ByteArray
)

private data class RetuneInstruction(
    val measureNumber: Int,
    val stringId: String,
    val deltaSemitones: Int
)

private data class NotationResult(
    val koraMidiBase64: String,
    val simplifiedMidiBase64: String,
    val title: String,
    val instrumentType: String,
    val sourceKind: String,
    val difficulty: Double,
    val tuningName: String,
    val scoreJson: String,
    val retuneInstructions: List<RetuneInstruction>
)

private sealed class NotationUiState {
    object Idle : NotationUiState()
    object Loading : NotationUiState()
    data class Success(val result: NotationResult) : NotationUiState()
    data class Error(val message: String) : NotationUiState()
}

private sealed class ExportState {
    object Idle : ExportState()
    object LoadingAudio : ExportState()
    object LoadingPdf : ExportState()
    data class AudioReady(val koraBase64: String, val simplifiedBase64: String) : ExportState()
    data class Error(val message: String) : ExportState()
}

private class UserVisibleImportException(message: String) : Exception(message)

private class IosKoraEngineController {

    private val readyDeferred = CompletableDeferred<Unit>()
    private val pendingResults = mutableMapOf<String, CompletableDeferred<Pair<String?, String?>>>()
    private var webView: WKWebView? = null
    private var nextCallId = 0
    private var readyHandler: ReadyMessageHandler? = null
    private var resultHandler: ResultMessageHandler? = null
    private var pickerDelegate: DocumentPickerDelegate? = null
    private var activePlayer: AVAudioPlayer? = null

    fun buildWebView(): WKWebView {
        webView?.let { return it }

        val controller = WKUserContentController()
        val shim = WKUserScript(
            source = ANDROID_BRIDGE_SHIM,
            injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
            forMainFrameOnly = true
        )
        controller.addUserScript(shim)

        val ready = ReadyMessageHandler { readyDeferred.complete(Unit) }
        val result = ResultMessageHandler { callbackId, resultJson, error ->
            pendingResults.remove(callbackId)?.complete(Pair(resultJson, error))
        }
        readyHandler = ready
        resultHandler = result
        controller.addScriptMessageHandler(ready, name = "koraReady")
        controller.addScriptMessageHandler(result, name = "koraResult")

        val config = WKWebViewConfiguration()
        config.userContentController = controller

        val wv = WKWebView(frame = CGRectZero.readValue(), configuration = config)
        webView = wv

        val url = platform.Foundation.NSBundle.mainBundle.URLForResource(
            name = "bridge",
            withExtension = "html",
            subdirectory = "kora_engine"
        )
        if (url != null) {
            val dir = url.URLByDeletingLastPathComponent ?: url
            wv.loadFileURL(url, allowingReadAccessToURL = dir)
        }
        return wv
    }

    suspend fun pickAndProcess(instrumentType: String): PickedNotationResult? {
        val imported = pickFile() ?: return null
        val title = imported.displayName.substringBeforeLast('.', imported.displayName)
        val paramsJson = withContext(Dispatchers.Default) {
            buildProcessParams(imported, instrumentType, title)
        }
        val resultJson = callJs("process", paramsJson)
        return PickedNotationResult(
            fileName = imported.displayName,
            result = parseResult(resultJson)
        )
    }

    suspend fun applyEdit(result: NotationResult, editJson: String): NotationResult {
        val paramsJson = buildString {
            append("{")
            append("\"score\":")
            append(result.scoreJson.ifBlank { "{}" })
            append(",\"edit\":")
            append(editJson)
            append(",\"instrumentType\":")
            append(jsonString(result.instrumentType))
            append(",\"title\":")
            append(jsonString(result.title))
            append(",\"sourceKind\":")
            append(jsonString(result.sourceKind))
            append("}")
        }
        return parseResult(callJs("edit", paramsJson))
    }

    suspend fun exportAudio(result: NotationResult): ExportState.AudioReady {
        val paramsJson = buildString {
            append("{\"score\":")
            append(result.scoreJson.ifBlank { "{}" })
            append(",\"instrumentType\":")
            append(jsonString(result.instrumentType))
            append("}")
        }
        val obj = parseJsonObject(callJs("exportAudio", paramsJson))
        return ExportState.AudioReady(
            koraBase64 = obj.stringValue("koraAudioBase64"),
            simplifiedBase64 = obj.stringValue("simplifiedAudioBase64")
        )
    }

    suspend fun exportPdf(result: NotationResult): String {
        val paramsJson = buildString {
            append("{\"score\":")
            append(result.scoreJson.ifBlank { "{}" })
            append(",\"instrumentType\":")
            append(jsonString(result.instrumentType))
            append(",\"title\":")
            append(jsonString(result.title))
            append(",\"difficulty\":")
            append(result.difficulty)
            append("}")
        }
        val obj = parseJsonObject(callJs("exportPdf", paramsJson))
        return obj.stringValue("pdfBase64")
    }

    fun shareBase64(base64: String, fileName: String, mimeType: String) {
        val bytes = Base64.Default.decode(base64)
        val safeName = safeFileName(fileName)
        val path = "${NSTemporaryDirectory().trimEnd('/')}/$safeName"
        if (!writeBytesToFile(bytes, path)) {
            throw UserVisibleImportException("Could not prepare $mimeType export.")
        }
        val url = NSURL.fileURLWithPath(path)
        val presenter = topViewController() ?: throw UserVisibleImportException("Could not open iOS share sheet.")
        val activity = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        presenter.presentViewController(activity, animated = true, completion = null)
    }

    fun playBase64Wav(base64: String, isMuted: Boolean) {
        if (isMuted) {
            stopActivePlayback()
            return
        }
        stopActivePlayback()
        val bytes = Base64.Default.decode(base64)
        val player = AVAudioPlayer(data = bytes.toNSData(), error = null)
        activePlayer = player
        player.prepareToPlay()
        player.play()
    }

    fun stopActivePlayback() {
        activePlayer?.stop()
        activePlayer = null
    }

    private suspend fun pickFile(): ImportedFile? {
        val selectedUrl = CompletableDeferred<NSURL?>()
        withContext(Dispatchers.Main) {
            val presenter = topViewController()
                ?: throw UserVisibleImportException("Could not open iOS file picker.")
            val delegate = DocumentPickerDelegate { url ->
                if (!selectedUrl.isCompleted) selectedUrl.complete(url)
                pickerDelegate = null
            }
            pickerDelegate = delegate
            val picker = UIDocumentPickerViewController(
                documentTypes = listOf("public.item"),
                inMode = UIDocumentPickerMode.UIDocumentPickerModeImport
            )
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            presenter.presentViewController(picker, animated = true, completion = null)
        }

        val url = selectedUrl.await() ?: return null
        return withContext(Dispatchers.Default) {
            val displayName = safeDisplayName(url.lastPathComponent ?: "file")
            val ext = (url.pathExtension ?: displayName.substringAfterLast('.', "")).lowercase()
            val bytes = readUrlBytes(url, MAX_IMPORT_BYTES)
            ImportedFile(displayName = displayName, extension = ext, bytes = bytes)
        }
    }

    private suspend fun callJs(fn: String, paramsJson: String): String {
        val wv = buildWebView()
        readyDeferred.await()
        val callId = "ios-${nextCallId++}"
        val deferred = CompletableDeferred<Pair<String?, String?>>()
        pendingResults[callId] = deferred

        val js = "window._koraEngine.$fn(${jsonString(paramsJson)}, ${jsonString(callId)})"
        withContext(Dispatchers.Main) {
            wv.evaluateJavaScript(js) { _, error ->
                if (error != null) {
                    pendingResults.remove(callId)?.complete(Pair(null, error.localizedDescription))
                }
            }
        }

        val (resultJson, error) = deferred.await()
        if (!error.isNullOrBlank()) throw UserVisibleImportException(error)
        return resultJson ?: "{}"
    }
}

private class DocumentPickerDelegate(
    private val onResult: (NSURL?) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        onResult(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(null)
    }
}

private class ReadyMessageHandler(
    private val onReady: () -> Unit
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        onReady()
    }
}

@OptIn(BetaInteropApi::class)
private class ResultMessageHandler(
    private val onResult: (callbackId: String, resultJson: String?, error: String?) -> Unit
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        val body = didReceiveScriptMessage.body as? String ?: return
        val map = parseJsonObject(body)
        val callbackId = map.stringValue("callbackId")
        if (callbackId.isBlank()) return
        onResult(
            callbackId,
            map.stringValue("resultJson").takeIf { it.isNotEmpty() },
            map.stringValue("error").takeIf { it.isNotEmpty() }
        )
    }
}

private fun buildProcessParams(imported: ImportedFile, instrumentType: String, title: String): String {
    return when (imported.extension) {
        "mid", "midi" -> buildString {
            append("{\"kind\":\"midi\",\"dataBase64\":")
            append(jsonString(Base64.Default.encode(imported.bytes)))
            append(",\"instrumentType\":")
            append(jsonString(instrumentType))
            append(",\"title\":")
            append(jsonString(title))
            append("}")
        }
        "mxl" -> buildXmlProcessParams(
            xmlText = extractXmlFromMxl(imported.bytes),
            instrumentType = instrumentType,
            title = title
        )
        "pdf" -> buildXmlProcessParams(
            xmlText = extractXmlFromPdf(imported.bytes),
            instrumentType = instrumentType,
            title = title
        )
        else -> buildXmlProcessParams(
            xmlText = imported.bytes.decodeToString(),
            instrumentType = instrumentType,
            title = title
        )
    }
}

private fun buildXmlProcessParams(xmlText: String, instrumentType: String, title: String): String {
    return buildString {
        append("{\"kind\":\"xml\",\"xmlText\":")
        append(jsonString(xmlText))
        append(",\"instrumentType\":")
        append(jsonString(instrumentType))
        append(",\"title\":")
        append(jsonString(title))
        append("}")
    }
}

private fun parseResult(json: String): NotationResult {
    val obj = parseJsonObject(json)
    val meta = obj.mapValue("metadata")
    val retunePlan = obj.mapValue("retunePlan")
    return NotationResult(
        koraMidiBase64 = obj.stringValue("koraMidiBase64"),
        simplifiedMidiBase64 = obj.stringValue("simplifiedMidiBase64"),
        title = meta.stringValue("title", "Untitled"),
        instrumentType = meta.stringValue("instrumentType", "KORA_21"),
        sourceKind = meta.stringValue("sourceKind", "MUSICXML"),
        difficulty = meta.doubleValue("difficulty"),
        tuningName = meta.stringValue("tuningName", "F tuning"),
        scoreJson = jsonObjectToString(obj["score"], "{}"),
        retuneInstructions = parseRetuneInstructions(retunePlan["barInstructions"])
    )
}

private fun parseRetuneInstructions(value: Any?): List<RetuneInstruction> {
    val rows = value as? List<*> ?: return emptyList()
    return rows.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        RetuneInstruction(
            measureNumber = map.intValue("measureNumber"),
            stringId = map.stringValue("stringId"),
            deltaSemitones = map.intValue("deltaSemitones")
        )
    }
}

private fun parseJsonObject(json: String): Map<*, *> {
    if (json.isBlank()) return emptyMap<Any?, Any?>()
    val data = json.encodeToByteArray().toNSData(allowEmpty = false)
    return NSJSONSerialization.JSONObjectWithData(data, 0UL, null) as? Map<*, *>
        ?: emptyMap<Any?, Any?>()
}

private fun jsonObjectToString(value: Any?, fallback: String): String {
    if (value == null || !NSJSONSerialization.isValidJSONObject(value)) return fallback
    val data = NSJSONSerialization.dataWithJSONObject(value, 0UL, null) ?: return fallback
    return data.toByteArray().decodeToString()
}

private fun Map<*, *>.stringValue(key: String, fallback: String = ""): String =
    (this[key] as? String) ?: fallback

private fun Map<*, *>.mapValue(key: String): Map<*, *> =
    this[key] as? Map<*, *> ?: emptyMap<Any?, Any?>()

private fun Map<*, *>.intValue(key: String): Int =
    when (val value = this[key]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        else -> 0
    }

private fun Map<*, *>.doubleValue(key: String): Double =
    when (val value = this[key]) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

private fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> {
                if (ch.code < 0x20) {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
    }
    append('"')
}

private fun topViewController(): UIViewController? {
    val app = UIApplication.sharedApplication
    val sceneWindow = app.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
        ?.windows
        ?.filterIsInstance<UIWindow>()
        ?.firstOrNull { it.isKeyWindow() }
    val fallbackWindow = app.windows
        .filterIsInstance<UIWindow>()
        .firstOrNull { it.isKeyWindow() }
        ?: app.keyWindow

    var controller = (sceneWindow ?: fallbackWindow)?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}

private fun readUrlBytes(url: NSURL, maxBytes: Int): ByteArray {
    val didAccess = url.startAccessingSecurityScopedResource()
    try {
        val path = url.path ?: throw UserVisibleImportException("Cannot open selected file.")
        return readBytesFromFile(path, maxBytes)
    } finally {
        if (didAccess) url.stopAccessingSecurityScopedResource()
    }
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val output = ByteArray(size)
    output.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return output
}

private fun ByteArray.toNSData(allowEmpty: Boolean = false): NSData {
    if (isEmpty() && !allowEmpty) throw UserVisibleImportException("Export data was empty.")
    return usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), length = size.convert())
    }
}

private fun readBytesFromFile(path: String, maxBytes: Int): ByteArray {
    val file = fopen(path, "rb") ?: throw UserVisibleImportException("Cannot open selected file.")
    try {
        if (fseek(file, 0, SEEK_END) != 0) throw UserVisibleImportException("Cannot read selected file.")
        val size = ftell(file)
        if (size < 0) throw UserVisibleImportException("Cannot read selected file.")
        if (size > maxBytes) throw UserVisibleImportException("Selected file is too large.")
        if (fseek(file, 0, SEEK_SET) != 0) throw UserVisibleImportException("Cannot read selected file.")

        val output = ByteArray(size.toInt())
        if (output.isEmpty()) return output
        val readCount = output.usePinned { pinned ->
            fread(pinned.addressOf(0), 1.convert(), output.size.convert(), file)
        }
        if (readCount.toLong() != output.size.toLong()) {
            throw UserVisibleImportException("Could not read the selected file.")
        }
        return output
    } finally {
        fclose(file)
    }
}

private fun writeBytesToFile(bytes: ByteArray, path: String): Boolean {
    val file = fopen(path, "wb") ?: return false
    return try {
        if (bytes.isEmpty()) {
            true
        } else {
            val written = bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
            }
            written.toLong() == bytes.size.toLong()
        }
    } finally {
        fclose(file)
    }
}

private fun safeDisplayName(name: String): String =
    name.substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("\\p{Cntrl}+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(96)
        .ifBlank { "file" }

private fun safeFileName(name: String): String =
    safeDisplayName(name)
        .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        .ifBlank { "export" }

private fun importErrorMessage(error: Throwable): String {
    return if (error is UserVisibleImportException) {
        error.message ?: "Could not process this file."
    } else {
        "Could not process this file. Use a valid MusicXML, MXL, MIDI, or supported PDF file."
    }
}

private fun extractXmlFromPdf(bytes: ByteArray): String {
    val text = decodeLatin1(bytes)
    val xmlStart = text.indexOf("<?xml")
    if (xmlStart >= 0) {
        val endStream = text.indexOf("endstream", xmlStart).takeIf { it > xmlStart }
            ?: (xmlStart + 2_097_152).coerceAtMost(text.length)
        val candidate = text.substring(xmlStart, endStream).trimEnd()
        if (candidate.contains("score-partwise", ignoreCase = true) ||
            candidate.contains("score-timewise", ignoreCase = true)
        ) {
            return candidate
        }
    }
    throw UserVisibleImportException(
        "No embedded MusicXML found in this PDF.\n" +
            "Export your score as .musicxml, .mxl, or .mid and import that file instead."
    )
}

private fun extractXmlFromMxl(bytes: ByteArray): String {
    val entries = unzipEntries(bytes)
    val containerBytes = entries["META-INF/container.xml"]
    val rootXmlPath = containerBytes
        ?.decodeToString()
        ?.let { Regex("""<rootfile\b[^>]*\bfull-path="([^"]+)"""").find(it)?.groupValues?.get(1) }

    if (rootXmlPath != null) {
        entries[rootXmlPath]?.let { return it.decodeToString() }
    }

    return entries.entries
        .firstOrNull { (name, _) -> name.endsWith(".xml") && name != "META-INF/container.xml" }
        ?.value
        ?.decodeToString()
        ?: throw UserVisibleImportException("No score XML found in MXL file.")
}

private fun unzipEntries(zipBytes: ByteArray): Map<String, ByteArray> {
    val eocdOffset = findEocdOffset(zipBytes)
    if (eocdOffset < 0) throw UserVisibleImportException("Invalid MXL file.")

    val totalEntries = readU16LE(zipBytes, eocdOffset + 10)
    if (totalEntries > MAX_MXL_ENTRIES) {
        throw UserVisibleImportException("MXL file has too many entries.")
    }
    var offset = readU32LE(zipBytes, eocdOffset + 16).toInt()
    val entries = mutableMapOf<String, ByteArray>()
    var totalUncompressedBytes = 0

    repeat(totalEntries) {
        requireZipBounds(zipBytes, offset, 46)
        if (!hasSignature(zipBytes, offset, 0x02014b50)) {
            throw UserVisibleImportException("Invalid MXL central directory.")
        }
        val compressionMethod = readU16LE(zipBytes, offset + 10)
        val compressedSize = readU32LE(zipBytes, offset + 20)
        val uncompressedSize = readU32LE(zipBytes, offset + 24)
        val fileNameLength = readU16LE(zipBytes, offset + 28)
        val extraLength = readU16LE(zipBytes, offset + 30)
        val commentLength = readU16LE(zipBytes, offset + 32)
        val localHeaderOffset = readU32LE(zipBytes, offset + 42).toInt()
        requireZipBounds(zipBytes, offset + 46, fileNameLength)
        val fileName = zipBytes.copyOfRange(offset + 46, offset + 46 + fileNameLength).decodeToString()
        offset += 46 + fileNameLength + extraLength + commentLength

        if (fileName.endsWith("/")) return@repeat
        if (uncompressedSize > MAX_MXL_ENTRY_BYTES) {
            throw UserVisibleImportException("MXL file has an oversized entry.")
        }

        requireZipBounds(zipBytes, localHeaderOffset, 30)
        if (!hasSignature(zipBytes, localHeaderOffset, 0x04034b50)) {
            throw UserVisibleImportException("Invalid MXL local file header.")
        }
        val localNameLength = readU16LE(zipBytes, localHeaderOffset + 26)
        val localExtraLength = readU16LE(zipBytes, localHeaderOffset + 28)
        val dataStart = localHeaderOffset + 30 + localNameLength + localExtraLength
        requireZipBounds(zipBytes, dataStart, compressedSize.toInt())
        val compressed = zipBytes.copyOfRange(dataStart, dataStart + compressedSize.toInt())
        val entryBytes = when (compressionMethod) {
            0 -> compressed
            8 -> inflateRaw(compressed, uncompressedSize.toInt().coerceAtMost(MAX_MXL_ENTRY_BYTES))
            else -> throw UserVisibleImportException("Unsupported MXL compression method.")
        }

        totalUncompressedBytes += entryBytes.size
        if (totalUncompressedBytes > MAX_MXL_TOTAL_BYTES) {
            throw UserVisibleImportException("MXL file is too large after extraction.")
        }
        entries[fileName] = entryBytes
    }

    return entries
}

private fun inflateRaw(compressed: ByteArray, expectedSize: Int): ByteArray = memScoped {
    if (compressed.isEmpty()) return@memScoped ByteArray(0)

    val stream = alloc<z_stream_s>()
    val streamPtr = stream.ptr
    stream.zalloc = null
    stream.zfree = null
    stream.opaque = null

    val initResult = inflateInit2(streamPtr, -15)
    if (initResult != Z_OK) {
        throw UserVisibleImportException("Could not initialize MXL decompressor.")
    }

    val output = ByteArrayBuilder(maxOf(expectedSize, DEFAULT_INFLATE_BUFFER_SIZE))
    try {
        compressed.usePinned { input ->
            stream.next_in = input.addressOf(0).reinterpret()
            stream.avail_in = compressed.size.convert()

            val buffer = ByteArray(DEFAULT_INFLATE_BUFFER_SIZE)
            while (true) {
                var produced = 0
                var result = Z_OK
                buffer.usePinned { out ->
                    stream.next_out = out.addressOf(0).reinterpret()
                    stream.avail_out = buffer.size.convert()
                    result = inflate(streamPtr, Z_NO_FLUSH)
                    produced = buffer.size - stream.avail_out.toInt()
                }
                if (produced > 0) {
                    output.append(buffer, produced, MAX_MXL_ENTRY_BYTES)
                }
                if (result == Z_STREAM_END) break
                if (result != Z_OK) {
                    throw UserVisibleImportException("Could not decompress MXL file.")
                }
                if (produced == 0 && stream.avail_in == 0u) {
                    throw UserVisibleImportException("Incomplete MXL compressed data.")
                }
            }
        }
        output.toByteArray()
    } finally {
        inflateEnd(streamPtr)
    }
}

private class ByteArrayBuilder(initialCapacity: Int) {
    private var buffer = ByteArray(initialCapacity.coerceAtLeast(1))
    private var size = 0

    fun append(source: ByteArray, count: Int, maxSize: Int) {
        if (count <= 0) return
        if (size + count > maxSize) {
            throw UserVisibleImportException("MXL file has an oversized entry.")
        }
        ensureCapacity(size + count)
        source.copyInto(buffer, destinationOffset = size, startIndex = 0, endIndex = count)
        size += count
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensureCapacity(required: Int) {
        if (required <= buffer.size) return
        var nextSize = buffer.size * 2
        while (nextSize < required) nextSize *= 2
        buffer = buffer.copyOf(nextSize)
    }
}

private fun findEocdOffset(bytes: ByteArray): Int {
    val minOffset = maxOf(0, bytes.size - 65_557)
    for (i in bytes.size - 22 downTo minOffset) {
        if (hasSignature(bytes, i, 0x06054b50)) return i
    }
    return -1
}

private fun hasSignature(bytes: ByteArray, offset: Int, signature: Int): Boolean {
    if (offset < 0 || offset + 4 > bytes.size) return false
    return readU32LE(bytes, offset).toInt() == signature
}

private fun readU16LE(bytes: ByteArray, offset: Int): Int {
    requireZipBounds(bytes, offset, 2)
    return (bytes[offset].toInt() and 0xff) or
        ((bytes[offset + 1].toInt() and 0xff) shl 8)
}

private fun readU32LE(bytes: ByteArray, offset: Int): Long {
    requireZipBounds(bytes, offset, 4)
    return ((bytes[offset].toLong() and 0xff) or
        ((bytes[offset + 1].toLong() and 0xff) shl 8) or
        ((bytes[offset + 2].toLong() and 0xff) shl 16) or
        ((bytes[offset + 3].toLong() and 0xff) shl 24)) and 0xffffffffL
}

private fun requireZipBounds(bytes: ByteArray, offset: Int, count: Int) {
    if (offset < 0 || count < 0 || offset > bytes.size - count) {
        throw UserVisibleImportException("Invalid MXL file.")
    }
}

private fun decodeLatin1(bytes: ByteArray): String {
    val builder = StringBuilder(bytes.size)
    bytes.forEach { byte -> builder.append((byte.toInt() and 0xff).toChar()) }
    return builder.toString()
}

private const val MAX_IMPORT_BYTES = 20 * 1024 * 1024
private const val MAX_MXL_ENTRY_BYTES = 5 * 1024 * 1024
private const val MAX_MXL_TOTAL_BYTES = 10 * 1024 * 1024
private const val MAX_MXL_ENTRIES = 128
private const val DEFAULT_INFLATE_BUFFER_SIZE = 8192
