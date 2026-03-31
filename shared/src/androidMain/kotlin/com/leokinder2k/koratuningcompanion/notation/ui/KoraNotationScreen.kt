package com.leokinder2k.koratuningcompanion.notation.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import com.leokinder2k.koratuningcompanion.generated.resources.Res
import com.leokinder2k.koratuningcompanion.generated.resources.*
import org.json.JSONObject
import android.util.Base64
import kotlin.math.min

@Composable
fun KoraNotationAndroidContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: KoraNotationViewModel = viewModel(factory = KoraNotationViewModel.factory(context))
    // Keep the WebView alive in composition
    AndroidView(factory = { vm.bridge.webView }, modifier = Modifier.size(0.dp))
    KoraNotationScreenContent(vm = vm, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KoraNotationScreenContent(vm: KoraNotationViewModel, modifier: Modifier = Modifier) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedInstrument by remember { mutableStateOf("KORA_21") }
    var selectedFileName by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileName = uri.lastPathSegment ?: "file"
            vm.processFile(context, uri, selectedInstrument)
        }
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
                if (selectedFileName.isNotEmpty()) {
                    Text(
                        text = selectedFileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { filePicker.launch("*/*") }) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.notation_pick_file))
                    }
                }
            }
        }

        when (val state = uiState) {
            is NotationUiState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(Res.string.notation_processing), style = MaterialTheme.typography.bodyMedium)
                }
            }
            is NotationUiState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.message,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            is NotationUiState.Success -> {
                val exportState by vm.exportState.collectAsStateWithLifecycle()
                ResultSection(
                    result = state.result,
                    context = context,
                    exportState = exportState,
                    onTransposeSemitone = vm::applyTransposeSemitone,
                    onTransposeDiatonic = vm::applyTransposeDiatonic,
                    onRequestAudio = vm::requestAudioExport,
                    onRequestPdf = vm::requestPdfExport,
                    onClearExport = vm::clearExportState,
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun ResultSection(
    result: NotationResult,
    context: Context,
    exportState: ExportState,
    onTransposeSemitone: (Int) -> Unit,
    onTransposeDiatonic: (Int) -> Unit,
    onRequestAudio: () -> Unit,
    onRequestPdf: () -> Unit,
    onClearExport: () -> Unit,
) {
    // React to completed exports
    LaunchedEffect(exportState) {
        when (exportState) {
            is ExportState.AudioReady -> {
                // played by the play buttons below; state stays until next import
            }
            is ExportState.PdfReady -> {
                shareBase64(context, exportState.pdfBase64, "${result.title}.pdf", "application/pdf")
                onClearExport()
            }
            else -> {}
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(result.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "${result.instrumentType.replace("_", " ")}  ·  ${result.tuningName}  ·  ${result.sourceKind}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val diffPct = (result.difficulty * 100).toInt()
            Text(
                stringResource(Res.string.notation_difficulty, diffPct),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    KoraDiagramCard(instrumentType = result.instrumentType)

    val retunePlan = remember(result.retunePlanJson) {
        runCatching { JSONObject(result.retunePlanJson) }.getOrNull()
    }
    if (retunePlan != null) {
        val instructions = retunePlan.optJSONArray("barInstructions")
        if (instructions != null && instructions.length() > 0) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.notation_retune_title), style = MaterialTheme.typography.titleMedium)
                    for (i in 0 until min(instructions.length(), 10)) {
                        val inst = instructions.getJSONObject(i)
                        val delta = inst.optInt("deltaSemitones")
                        val arrow = if (delta > 0) "↑" else "↓"
                        Text(
                            "Bar ${inst.optInt("measureNumber")}: ${inst.optString("stringId")} $arrow",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (instructions.length() > 10) {
                        Text("…and ${instructions.length() - 10} more", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.notation_transpose_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onTransposeSemitone(-1) }) { Text("−1 semitone") }
                OutlinedButton(onClick = { onTransposeSemitone(1) }) { Text("+1 semitone") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onTransposeDiatonic(-1) }) { Text("−1 step") }
                OutlinedButton(onClick = { onTransposeDiatonic(1) }) { Text("+1 step") }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.notation_export_title), style = MaterialTheme.typography.titleMedium)

            val exportBusy = exportState is ExportState.LoadingAudio || exportState is ExportState.LoadingPdf
            val audioReady = exportState as? ExportState.AudioReady

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRequestPdf,
                    enabled = !exportBusy,
                ) {
                    if (exportState is ExportState.LoadingPdf) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("PDF")
                }
                Button(
                    onClick = { shareBase64(context, result.koraMidiBase64, "${result.title}_kora.mid", "audio/midi") },
                    enabled = !exportBusy,
                ) {
                    Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("MIDI")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (audioReady != null) playBase64Wav(context, audioReady.koraBase64)
                        else onRequestAudio()
                    },
                    enabled = !exportBusy,
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
                    onClick = {
                        if (audioReady != null) playBase64Wav(context, audioReady.simplifiedBase64)
                        else onRequestAudio()
                    },
                    enabled = !exportBusy,
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
                    color = MaterialTheme.colorScheme.error,
                )
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

private fun shareBase64(context: Context, base64: String, fileName: String, mimeType: String) {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    val file = java.io.File(context.cacheDir, fileName)
    file.writeBytes(bytes)
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private var activePlayer: MediaPlayer? = null

private fun playBase64Wav(context: Context, base64: String) {
    activePlayer?.release()
    activePlayer = null
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    val file = java.io.File(context.cacheDir, "_kora_preview.wav")
    file.writeBytes(bytes)
    activePlayer = MediaPlayer().apply {
        setDataSource(file.absolutePath)
        prepare()
        start()
        setOnCompletionListener { release(); activePlayer = null }
    }
}
