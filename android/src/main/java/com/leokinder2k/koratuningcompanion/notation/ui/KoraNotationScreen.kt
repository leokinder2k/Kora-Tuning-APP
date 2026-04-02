package com.leokinder2k.koratuningcompanion.notation.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.instrumentconfig.data.DataStoreInstrumentConfigRepository
import com.leokinder2k.koratuningcompanion.notation.engine.KoraInstrumentType
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun KoraNotationRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: KoraNotationViewModel = viewModel(factory = KoraNotationViewModel.factory(context))
    KoraNotationScreen(vm = vm, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KoraNotationScreen(vm: KoraNotationViewModel, modifier: Modifier = Modifier) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val transposeOffset by vm.transposeOffset.collectAsStateWithLifecycle()
    val originalKeyFifths by vm.originalKeyFifths.collectAsStateWithLifecycle()
    val originalKeyMode by vm.originalKeyMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val instrumentRepository = remember(context) { DataStoreInstrumentConfigRepository.create(context) }
    val savedProfile by instrumentRepository.instrumentProfile.collectAsStateWithLifecycle(initialValue = null)
    val savedInstrumentType = when (savedProfile?.stringCount) {
        22 -> "KORA_22"
        else -> "KORA_21"
    }
    var selectedInstrument by rememberSaveable(savedInstrumentType) { mutableStateOf(savedInstrumentType) }
    var selectedFileName by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // Resolve display name from content resolver (lastPathSegment is unreliable)
            var displayName = uri.lastPathSegment ?: "file"
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) displayName = cursor.getString(0) ?: displayName
            }
            selectedFileName = displayName
            vm.processFile(context, uri, selectedInstrument)
        }
    }

    val darkCard = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
    val onDark = Color.White
    val onDarkMuted = Color(0xFFAAAAAA)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Upload Card ──────────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth(), colors = darkCard) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.notation_upload_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = onDark
                )
                Text(
                    text = stringResource(R.string.notation_upload_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = onDarkMuted
                )

                // Instrument selector
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
                        Text(stringResource(R.string.notation_pick_file))
                    }
                }
            }
        }

        // ── Status ───────────────────────────────────────────────────────────
        when (val state = uiState) {
            is NotationUiState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = onDark)
                    Text(stringResource(R.string.notation_processing), style = MaterialTheme.typography.bodyMedium, color = onDark)
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
                    transposeOffset = transposeOffset,
                    originalKeyFifths = originalKeyFifths,
                    originalKeyMode = originalKeyMode,
                    exportState = exportState,
                    context = context,
                    onTransposeSemitone = vm::applyTransposeSemitone,
                    onResetTranspose = vm::resetTranspose,
                    onPreviewTranspose = vm::previewTransposeDelta,
                    onStopPreview = vm::stopPreview,
                    onRequestAudio = vm::requestAudioExport,
                    onRequestPdf = vm::requestPdfExport,
                    onClearExport = vm::clearExportState,
                )
            }
            else -> {}
        }
    }
}

private fun fifthsToKeyName(fifths: Int, mode: String): String {
    val note = when (fifths.coerceIn(-7, 7)) {
        -7 -> "Cb"; -6 -> "Gb"; -5 -> "Db"; -4 -> "Ab"; -3 -> "Eb"; -2 -> "Bb"; -1 -> "F"
         0 -> "C";   1 -> "G";   2 -> "D";   3 -> "A";   4 -> "E";   5 -> "B";   6 -> "F#"
        else -> "C#"
    }
    return if (mode.equals("MINOR", ignoreCase = true)) "$note minor" else "$note major"
}

/** Replicates JS transposeKeySignatureFifths — finds the nearest valid key-sig fifths value
 *  whose tonic pitch class matches [originalFifths] tonic shifted by [semitones]. */
private fun transposeFifths(originalFifths: Int, semitones: Int): Int {
    val fifthsToTonic = mapOf(
        -7 to 11, -6 to 6, -5 to 1, -4 to 8, -3 to 3, -2 to 10, -1 to 5,
         0 to 0,   1 to 7,  2 to 2,  3 to 9,  4 to 4,  5 to 11,  6 to 6,  7 to 1
    )
    val tonicPc = fifthsToTonic[originalFifths.coerceIn(-7, 7)] ?: 0
    val targetPc = ((tonicPc + semitones) % 12 + 12) % 12
    val candidates = fifthsToTonic.entries.filter { it.value == targetPc }.map { it.key }
    if (candidates.isEmpty()) return originalFifths
    return candidates.minWithOrNull(
        compareBy({ kotlin.math.abs(it - originalFifths) }, { kotlin.math.abs(it) })
    ) ?: originalFifths
}

@Composable
private fun ResultSection(
    result: NotationResult,
    transposeOffset: Int,
    originalKeyFifths: Int,
    originalKeyMode: String,
    exportState: ExportState,
    context: Context,
    onTransposeSemitone: (Int) -> Unit,
    onResetTranspose: () -> Unit,
    onPreviewTranspose: (Int) -> Unit = {},
    onStopPreview: () -> Unit = {},
    onRequestAudio: () -> Unit = {},
    onRequestPdf: () -> Unit = {},
    onClearExport: () -> Unit = {},
) {
    // Release the module-level MediaPlayer when this composable leaves composition.
    DisposableEffect(Unit) {
        onDispose {
            activePlayer?.release()
            activePlayer = null
            activePlayerFile = null
        }
    }

    val darkCard = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
    val onDark = Color.White
    val onDarkMuted = Color(0xFFAAAAAA)
    // ── Metadata Card ────────────────────────────────────────────────────────
    Card(modifier = Modifier.fillMaxWidth(), colors = darkCard) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(result.title, style = MaterialTheme.typography.titleMedium, color = onDark)
            Text(
                "${instrumentTypeLabel(result.instrumentType)}  ·  ${result.tuningName}  ·  ${result.sourceKind}",
                style = MaterialTheme.typography.bodySmall,
                color = onDarkMuted
            )
            val diffPct = (result.difficulty * 100).toInt()
            Text(
                stringResource(R.string.notation_difficulty, diffPct),
                style = MaterialTheme.typography.bodySmall,
                color = onDarkMuted
            )
        }
    }

    // ── Kora Diagram ─────────────────────────────────────────────────────────
    KoraDiagramCard(instrumentType = result.instrumentType, cardColors = darkCard, labelColor = onDark, mutedColor = onDarkMuted)

    // ── Retune Plan ──────────────────────────────────────────────────────────
    val retunePlan = remember(result.retunePlanJson) {
        runCatching { JSONObject(result.retunePlanJson) }.getOrNull()
    }
    if (retunePlan != null) {
        val instructions = retunePlan.optJSONArray("barInstructions")
        if (instructions != null && instructions.length() > 0) {
            Card(modifier = Modifier.fillMaxWidth(), colors = darkCard) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.notation_retune_title), style = MaterialTheme.typography.titleMedium, color = onDark)
                    for (i in 0 until min(instructions.length(), 10)) {
                        val inst = instructions.getJSONObject(i)
                        val delta = inst.optInt("deltaSemitones")
                        val arrow = if (delta > 0) "↑" else "↓"
                        Text(
                            "Bar ${inst.optInt("measureNumber")}: ${inst.optString("stringId")} $arrow",
                            style = MaterialTheme.typography.bodySmall,
                            color = onDarkMuted
                        )
                    }
                    if (instructions.length() > 10) {
                        Text("…and ${instructions.length() - 10} more", style = MaterialTheme.typography.bodySmall, color = onDarkMuted)
                    }
                }
            }
        }
    }

    // ── Transpose Card ────────────────────────────────────────────────────────
    Card(modifier = Modifier.fillMaxWidth(), colors = darkCard) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.notation_transpose_title), style = MaterialTheme.typography.titleMedium, color = onDark)

            // Slider state — represents the absolute target offset from original
            var sliderTarget by remember(transposeOffset) { mutableIntStateOf(transposeOffset) }
            val delta = sliderTarget - transposeOffset

            // Live key preview: compute what key the slider position would produce
            val originalKeyLabel = fifthsToKeyName(originalKeyFifths, originalKeyMode)
            val previewKeyFifths = transposeFifths(originalKeyFifths, sliderTarget)
            val previewKeyLabel = fifthsToKeyName(previewKeyFifths, originalKeyMode)
            val previewColor = when {
                delta != 0 -> Color(0xFFFFC107)          // amber = pending change
                transposeOffset != 0 -> MaterialTheme.colorScheme.primary  // blue = applied
                else -> onDarkMuted                       // grey = no change
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(originalKeyLabel, style = MaterialTheme.typography.bodyMedium, color = onDarkMuted)
                Text("→", style = MaterialTheme.typography.bodyMedium, color = onDarkMuted)
                Text(
                    text = previewKeyLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (delta != 0) FontWeight.Bold else FontWeight.Normal,
                    color = previewColor
                )
                if (delta != 0) {
                    val sign = if (sliderTarget > 0) "+" else ""
                    Text(
                        text = "($sign$sliderTarget st)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFC107)
                    )
                }
            }

            // Debounced audio pitch preview on slider drag — stops main playback first.
            LaunchedEffect(sliderTarget) {
                delay(150L)
                val d = sliderTarget - transposeOffset
                if (d != 0) {
                    activePlayer?.release()
                    activePlayer = null
                    activePlayerFile = null
                    onPreviewTranspose(d)
                }
            }

            // Slider -12 → +12
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("-12", style = MaterialTheme.typography.labelSmall, color = onDarkMuted)
                Slider(
                    value = sliderTarget.toFloat(),
                    onValueChange = { sliderTarget = it.roundToInt() },
                    valueRange = -12f..12f,
                    steps = 23,
                    modifier = Modifier.weight(1f)
                )
                Text("+12", style = MaterialTheme.typography.labelSmall, color = onDarkMuted)
            }

            // Apply / Reset row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (transposeOffset != 0) {
                    OutlinedButton(onClick = { sliderTarget = 0; onResetTranspose() }) {
                        Text("Reset", color = onDark)
                    }
                }
                Button(
                    onClick = { if (delta != 0) onTransposeSemitone(delta) },
                    enabled = delta != 0
                ) { Text("Apply") }
            }
        }
    }

    // ── Export Card ───────────────────────────────────────────────────────────
    // Trigger PDF share when export completes
    LaunchedEffect(exportState) {
        if (exportState is ExportState.PdfReady) {
            shareBase64(context, exportState.pdfBase64, "${result.title}.pdf", "application/pdf")
            onClearExport()
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = darkCard) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.notation_export_title), style = MaterialTheme.typography.titleMedium, color = onDark)

            val exportBusy = exportState is ExportState.LoadingAudio || exportState is ExportState.LoadingPdf
            val audioReady = exportState as? ExportState.AudioReady

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRequestPdf, enabled = !exportBusy) {
                    if (exportState is ExportState.LoadingPdf) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = onDark)
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
                        if (audioReady != null) playBase64Wav(context, audioReady.koraBase64, "_kora_audio.wav", onStopPreview)
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
                    Text(stringResource(R.string.notation_play_kora))
                }
                OutlinedButton(
                    onClick = {
                        if (audioReady != null) playBase64Wav(context, audioReady.simplifiedBase64, "_original_audio.wav", onStopPreview)
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
                    Text(stringResource(R.string.notation_play_original))
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

private fun instrumentTypeLabel(instrumentType: String): String {
    return when (KoraInstrumentType.fromString(instrumentType)) {
        KoraInstrumentType.KORA_21 -> "21-string kora"
        KoraInstrumentType.KORA_22_CHROMATIC -> "22-string chromatic kora"
    }
}

@Composable
private fun KoraDiagramCard(
    instrumentType: String,
    cardColors: CardColors = CardDefaults.cardColors(),
    labelColor: Color = Color.Unspecified,
    mutedColor: Color = Color.Unspecified,
) {
    val instrument = KoraInstrumentType.fromString(instrumentType)
    val isChromatic = instrument == KoraInstrumentType.KORA_22_CHROMATIC
    val leftCount = instrument.leftCount
    val rightCount = instrument.rightCount
    val primary = MaterialTheme.colorScheme.primary
    val surface = Color(0xFF2A2A2A)   // dark gourd fill on black bg

    Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.notation_diagram_title), style = MaterialTheme.typography.titleMedium, color = labelColor)
            Text(
                stringResource(
                    if (isChromatic) R.string.notation_diagram_subtitle_22 else R.string.notation_diagram_subtitle_21,
                    leftCount,
                    rightCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_kora_diagram_base),
                    contentDescription = stringResource(R.string.notation_diagram_title),
                    modifier = Modifier.fillMaxSize()
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val bridgeY = h * 0.5f
                    val gourdR = h * 0.35f

                    drawCircle(color = surface.copy(alpha = 0.10f), radius = gourdR, center = Offset(w / 2f, bridgeY))
                    drawCircle(color = primary, radius = gourdR, center = Offset(w / 2f, bridgeY), style = Stroke(2f))
                    drawLine(primary, Offset(w * 0.3f, bridgeY), Offset(w * 0.7f, bridgeY), strokeWidth = 3f)

                    val leftSpacing = (w * 0.28f) / (leftCount + 1)
                    for (i in 1..leftCount) {
                        val x = w * 0.02f + leftSpacing * i
                        drawLine(
                            primary,
                            Offset(x, 0f),
                            Offset(w * 0.3f + leftSpacing * i * 0.1f, bridgeY),
                            strokeWidth = 1.5f
                        )
                    }

                    val rightSpacing = (w * 0.28f) / (rightCount + 1)
                    for (i in 1..rightCount) {
                        val x = w * 0.72f + rightSpacing * i
                        drawLine(
                            primary,
                            Offset(x, 0f),
                            Offset(w * 0.7f + rightSpacing * i * 0.1f, bridgeY),
                            strokeWidth = 1.5f
                        )
                    }
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
private var activePlayerFile: String? = null

private fun playBase64Wav(context: Context, base64: String, fileName: String, stopPreview: () -> Unit = {}) {
    // Toggle off if the same track is already playing.
    if (activePlayerFile == fileName && activePlayer?.isPlaying == true) {
        activePlayer?.release()
        activePlayer = null
        activePlayerFile = null
        return
    }
    try {
        stopPreview()                   // kill any pitch-preview that may be running
        activePlayer?.release()
        activePlayer = null
        activePlayerFile = null
        if (base64.isBlank()) return
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val file = java.io.File(context.cacheDir, fileName)
        file.writeBytes(bytes)
        activePlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
            setOnCompletionListener { release(); activePlayer = null; activePlayerFile = null }
        }
        activePlayerFile = fileName
    } catch (_: Exception) { /* audio unavailable */ }
}
