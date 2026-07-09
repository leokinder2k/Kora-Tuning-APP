package com.leokinder2k.koratuningcompanion.synth

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

@Composable
fun SynthRoute(
    modifier: Modifier = Modifier,
    viewModel: SynthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SynthScreen(
        uiState = uiState,
        onRefreshMidi = viewModel::refreshMidi,
        onVolumeChange = viewModel::setVolume,
        onBassSplitChange = viewModel::setBassSplit,
        onPadLayerChange = viewModel::setPadLayer,
        onSplitNoteChange = viewModel::setSplitNote,
        onOctaveShiftChange = viewModel::setOctaveShift,
        onNoteOn = viewModel::noteOn,
        onNoteOff = viewModel::noteOff,
        onPad = viewModel::playPad,
        onAllNotesOff = viewModel::allNotesOff,
        onToggleRecording = viewModel::toggleRecording,
        onToggleLoop = viewModel::toggleLoopPlayback,
        onMetronomeEnabledChange = viewModel::setMetronomeEnabled,
        onMetronomeBpmChange = viewModel::setMetronomeBpm,
        onSaveRecording = viewModel::saveRecording,
        onLoadSoundFont = viewModel::loadSoundFont,
        onUseBuiltInSound = viewModel::useBuiltInSound,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SynthScreen(
    uiState: SynthUiState,
    onRefreshMidi: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBassSplitChange: (Boolean) -> Unit,
    onPadLayerChange: (Boolean) -> Unit,
    onSplitNoteChange: (Int) -> Unit,
    onOctaveShiftChange: (Int) -> Unit,
    onNoteOn: (Int, Float) -> Unit,
    onNoteOff: (Int) -> Unit,
    onPad: (Int, PadQuality) -> Unit,
    onAllNotesOff: () -> Unit,
    onToggleRecording: () -> Unit,
    onToggleLoop: () -> Unit,
    onMetronomeEnabledChange: (Boolean) -> Unit,
    onMetronomeBpmChange: (Float) -> Unit,
    onSaveRecording: (android.net.Uri) -> Unit,
    onLoadSoundFont: (android.net.Uri) -> Unit,
    onUseBuiltInSound: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundFontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onLoadSoundFont(uri)
        }
    }
    val midiSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/midi")) { uri ->
        if (uri != null) onSaveRecording(uri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeaderPanel(uiState = uiState, onRefreshMidi = onRefreshMidi)

        ControlPanel(
            uiState = uiState,
            onVolumeChange = onVolumeChange,
            onBassSplitChange = onBassSplitChange,
            onPadLayerChange = onPadLayerChange,
            onSplitNoteChange = onSplitNoteChange,
            onOctaveShiftChange = onOctaveShiftChange,
            onAllNotesOff = onAllNotesOff
        )

        RecorderPanel(
            uiState = uiState,
            onToggleRecording = onToggleRecording,
            onToggleLoop = onToggleLoop,
            onMetronomeEnabledChange = onMetronomeEnabledChange,
            onMetronomeBpmChange = onMetronomeBpmChange,
            onSave = {
                midiSaver.launch("kora-synth-loop.mid")
            }
        )

        SoundFontPanel(
            uiState = uiState,
            onLoad = {
                soundFontPicker.launch(arrayOf("*/*"))
            },
            onUseBuiltInSound = onUseBuiltInSound
        )

        ChordPads(onPad = onPad)

        TouchKeyboard(
            octaveShift = uiState.octaveShift,
            splitNote = uiState.splitNote,
            bassSplitEnabled = uiState.bassSplitEnabled,
            onNoteOn = onNoteOn,
            onNoteOff = onNoteOff
        )
    }
}

@Composable
private fun HeaderPanel(
    uiState: SynthUiState,
    onRefreshMidi: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Piano, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Controller Synth", style = MaterialTheme.typography.titleLarge)
                    Text(
                        uiState.midiStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(onClick = onRefreshMidi) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("MIDI")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onRefreshMidi,
                    label = { Text(uiState.connectedMidiDevice ?: "No MIDI controller") }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("Last ${uiState.lastNote}  ${velocityPercent(uiState.lastVelocity)}") }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("${inputDeviceCount(uiState)} input(s)") }
                )
                AssistChip(
                    onClick = {},
                    label = { Text(uiState.midiInputMode) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("MIDI ${uiState.midiEventCount} ev ${uiState.midiByteCount} B") }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("Idle ${midiIdleLabel(uiState.midiIdleMs)}") }
                )
                if (uiState.midiReconnectCount > 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Reconn ${uiState.midiReconnectCount}") }
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(if (uiState.sustainEnabled) "Pedal on" else "Pedal off") }
                )
                AssistChip(
                    onClick = onRefreshMidi,
                    label = {
                        Text(
                            text = usbStatusLabel(uiState.visibleUsbDevices),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 260.dp)
                        )
                    }
                )
            }
            if (
                uiState.connectedMidiDevice == null &&
                uiState.availableMidiDevices.isEmpty() &&
                uiState.visibleUsbDevices.isNotEmpty() &&
                uiState.visibleUsbDevices.none { it.isSupportedMidi }
            ) {
                Text(
                    text = "A-49 is visible, but no usable MIDI endpoint is exposed. Check Generic USB mode and the OTG/powered hub.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlPanel(
    uiState: SynthUiState,
    onVolumeChange: (Float) -> Unit,
    onBassSplitChange: (Boolean) -> Unit,
    onPadLayerChange: (Boolean) -> Unit,
    onSplitNoteChange: (Int) -> Unit,
    onOctaveShiftChange: (Int) -> Unit,
    onAllNotesOff: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Performance", style = MaterialTheme.typography.titleMedium)
            LabeledSlider(
                label = "Volume",
                valueText = "${(uiState.volume * 100).toInt()}%",
                value = uiState.volume,
                onValueChange = onVolumeChange
            )
            LabeledSlider(
                label = "Split",
                valueText = midiNoteName(uiState.splitNote),
                value = (uiState.splitNote - 36) / 36f,
                onValueChange = { onSplitNoteChange(36 + (it * 36).toInt()) }
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip("Bass split", uiState.bassSplitEnabled, onBassSplitChange)
                ToggleChip("Pad layer", uiState.padLayerEnabled, onPadLayerChange)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Octave", style = MaterialTheme.typography.labelLarge)
                (-2..2).forEach { shift ->
                    FilterChip(
                        selected = uiState.octaveShift == shift,
                        onClick = { onOctaveShiftChange(shift) },
                        label = { Text(if (shift == 0) "0" else "%+d".format(shift)) }
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onAllNotesOff) {
                    Text("Stop")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecorderPanel(
    uiState: SynthUiState,
    onToggleRecording: () -> Unit,
    onToggleLoop: () -> Unit,
    onMetronomeEnabledChange: (Boolean) -> Unit,
    onMetronomeBpmChange: (Float) -> Unit,
    onSave: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recorder", style = MaterialTheme.typography.titleMedium)
                    Text(
                        uiState.recordingStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(recordingLengthLabel(uiState)) }
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onToggleRecording,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (uiState.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (uiState.isRecording) "Stop rec" else "Record")
                }
                OutlinedButton(
                    onClick = onToggleLoop,
                    enabled = uiState.recordedEventCount > 0 && !uiState.isRecording
                ) {
                    Icon(
                        imageVector = if (uiState.isLooping) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (uiState.isLooping) "Stop loop" else "Loop")
                }
                OutlinedButton(
                    onClick = onSave,
                    enabled = uiState.recordedEventCount > 0 && !uiState.isRecording
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save MIDI")
                }
                ToggleChip("Click", uiState.metronomeEnabled, onMetronomeEnabledChange)
            }
            LabeledSlider(
                label = "Tempo",
                valueText = "${uiState.metronomeBpm} bpm",
                value = ((uiState.metronomeBpm - MinBpm) / (MaxBpm - MinBpm).toFloat()).coerceIn(0f, 1f),
                onValueChange = { value ->
                    onMetronomeBpmChange(MinBpm + value * (MaxBpm - MinBpm))
                }
            )
        }
    }
}

@Composable
private fun SoundFontPanel(
    uiState: SynthUiState,
    onLoad: () -> Unit,
    onUseBuiltInSound: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(uiState.soundFontName ?: "Built-in Grand", fontWeight = FontWeight.SemiBold)
                Text(
                    uiState.soundFontStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(onClick = onUseBuiltInSound) {
                Text("Built-in")
            }
            Button(onClick = onLoad) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("SF2")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChordPads(
    onPad: (Int, PadQuality) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Pads", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PadButton("C maj", 48, PadQuality.Major, onPad)
                PadButton("F maj", 53, PadQuality.Major, onPad)
                PadButton("G 7", 55, PadQuality.Seven, onPad)
                PadButton("A min", 57, PadQuality.Minor, onPad)
                PadButton("D sus", 50, PadQuality.Sus, onPad)
                PadButton("E min", 52, PadQuality.Minor, onPad)
            }
        }
    }
}

@Composable
private fun PadButton(
    label: String,
    root: Int,
    quality: PadQuality,
    onPad: (Int, PadQuality) -> Unit
) {
    Button(
        onClick = { onPad(root, quality) },
        modifier = Modifier
            .height(44.dp)
            .widthIn(min = 84.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
    ) {
        Text(label, maxLines = 1)
    }
}

@Composable
private fun TouchKeyboard(
    octaveShift: Int,
    splitNote: Int,
    bassSplitEnabled: Boolean,
    onNoteOn: (Int, Float) -> Unit,
    onNoteOff: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    val notes = (48..77).toList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Keyboard", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            notes.forEach { note ->
                PianoTouchKey(
                    note = note,
                    displayedNote = note + octaveShift * 12,
                    splitNote = splitNote,
                    bassSplitEnabled = bassSplitEnabled,
                    onNoteOn = onNoteOn,
                    onNoteOff = onNoteOff
                )
            }
        }
    }
}

@Composable
private fun PianoTouchKey(
    note: Int,
    displayedNote: Int,
    splitNote: Int,
    bassSplitEnabled: Boolean,
    onNoteOn: (Int, Float) -> Unit,
    onNoteOff: (Int) -> Unit
) {
    val isBlack = note.mod(12) in setOf(1, 3, 6, 8, 10)
    val isBass = bassSplitEnabled && displayedNote < splitNote
    val container = when {
        isBass -> MaterialTheme.colorScheme.tertiaryContainer
        isBlack -> MaterialTheme.colorScheme.inverseSurface
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        isBass -> MaterialTheme.colorScheme.onTertiaryContainer
        isBlack -> MaterialTheme.colorScheme.inverseOnSurface
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .width(if (isBlack) 42.dp else 50.dp)
            .height(if (isBlack) 124.dp else 152.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .noteTouch(note, onNoteOn, onNoteOff)
            .padding(6.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = midiNoteName(displayedNote),
            color = content,
            fontSize = 12.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun ToggleChip(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(28.dp)
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value.coerceIn(0f, 1f), onValueChange = onValueChange)
    }
}

private fun Modifier.noteTouch(
    note: Int,
    onNoteOn: (Int, Float) -> Unit,
    onNoteOff: (Int) -> Unit
): Modifier = pointerInput(note) {
    awaitTouch(note, onNoteOn, onNoteOff)
}

private suspend fun PointerInputScope.awaitTouch(
    note: Int,
    onNoteOn: (Int, Float) -> Unit,
    onNoteOff: (Int) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val height = size.height.toFloat().coerceAtLeast(1f)
        val velocity = (0.44f + ((height - down.position.y.coerceIn(0f, height)) / height) * 0.56f)
            .coerceIn(0.25f, 1f)
        onNoteOn(note, velocity)
        waitForUpOrCancellation()
        onNoteOff(note)
    }
}

private fun velocityPercent(velocity: Float): String = "${(velocity.coerceIn(0f, 1f) * 100).toInt()}%"

private fun recordingLengthLabel(uiState: SynthUiState): String {
    val seconds = uiState.recordedDurationMs / 1000f
    val duration = "${(seconds * 10f).roundToInt() / 10f}s"
    return "${uiState.recordedEventCount} events • $duration"
}

private fun inputDeviceCount(uiState: SynthUiState): Int {
    val directUsbInput = if (
        uiState.connectedMidiDevice != null &&
        uiState.availableMidiDevices.isEmpty() &&
        uiState.visibleUsbDevices.any { it.isSupportedMidi }
    ) {
        1
    } else {
        0
    }
    return uiState.availableMidiDevices.size + directUsbInput
}

private fun usbStatusLabel(devices: List<UsbDeviceSummary>): String {
    val first = devices.firstOrNull() ?: return "USB: none"
    val id = "%04X:%04X".format(first.vendorId, first.productId)
    val midiTag = first.midiTag?.let { " $it" }.orEmpty()
    return "USB: ${first.name}$midiTag $id"
}

private fun midiIdleLabel(idleMs: Long?): String {
    return idleMs?.let { "${(it / 1000f * 10f).roundToInt() / 10f}s" } ?: "--"
}

private const val MinBpm = 60
private const val MaxBpm = 200
