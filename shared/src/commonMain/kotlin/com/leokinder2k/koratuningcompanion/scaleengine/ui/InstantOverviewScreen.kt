package com.leokinder2k.koratuningcompanion.scaleengine.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leokinder2k.koratuningcompanion.generated.resources.Res
import com.leokinder2k.koratuningcompanion.generated.resources.*
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.Pitch
import com.leokinder2k.koratuningcompanion.livetuner.audio.MetronomeClickPlayer
import com.leokinder2k.koratuningcompanion.livetuner.audio.MetronomeSoundOption
import com.leokinder2k.koratuningcompanion.livetuner.audio.PluckedStringPlayer
import com.leokinder2k.koratuningcompanion.livetuner.model.TunerTargetMatcher
import com.leokinder2k.koratuningcompanion.scaleengine.chords.ChordDefinition
import com.leokinder2k.koratuningcompanion.scaleengine.chords.ChordMatch
import com.leokinder2k.koratuningcompanion.scaleengine.chords.ChordPlanner
import com.leokinder2k.koratuningcompanion.scaleengine.chords.ChordQuality
import com.leokinder2k.koratuningcompanion.scaleengine.model.LeverState
import com.leokinder2k.koratuningcompanion.scaleengine.model.PegCorrectStringResult
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType
import com.leokinder2k.koratuningcompanion.scaleengine.model.StringSide
import com.leokinder2k.koratuningcompanion.ui.theme.KoraClosedLeverColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraCombinedChangesColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraDetunedColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraLeverClosedStringColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraOpenLeverColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private enum class OverviewViewMode { DIAGRAM, TABLE, CHORDS, EXERCISE }
private enum class ChordPlaybackMode { BLOCK, BROKEN, SPREAD }
private enum class ExerciseChoiceMode { PRESET, RANDOM }

private val StickyDiagramHeight = 360.dp
private const val KORA_DIAGRAM_PDF_ASSET_NAME = "Kora_x22.pdf"

private data class MetronomeTimeSignature(val numerator: Int, val denominator: Int) {
    val label: String = "$numerator/$denominator"
}

private data class DiagramStringSegment(
    val row: PegCorrectStringResult,
    val peg: Offset,
    val bridge: Offset
)

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun InstantOverviewScreen(
    uiState: ScaleCalculationUiState,
    onScaleTypeSelected: (ScaleType) -> Unit,
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val showLeverInfo = uiState.result.request.instrumentProfile.tuningMode == KoraTuningMode.LEVERED
    val pagerState = rememberPagerState(
        initialPage = OverviewViewMode.entries.indexOf(OverviewViewMode.DIAGRAM),
        pageCount = { OverviewViewMode.entries.size }
    )
    val viewMode = OverviewViewMode.entries.getOrElse(pagerState.currentPage) { OverviewViewMode.DIAGRAM }
    var diagramZoom by rememberSaveable { mutableStateOf(1f) }
    var isDiagramLocked by rememberSaveable { mutableStateOf(false) }
    var playingStringNumbers by remember(
        uiState.rootNote, uiState.scaleType, uiState.result.request.instrumentProfile.stringCount
    ) { mutableStateOf(emptySet<Int>()) }
    var selectedChordRoot by rememberSaveable(uiState.rootNote) { mutableStateOf(uiState.rootNote) }
    var selectedChordQuality by rememberSaveable { mutableStateOf(ChordQuality.MAJOR) }
    var chordPlaybackMode by rememberSaveable { mutableStateOf(ChordPlaybackMode.BLOCK) }
    var chordVoicingNoteCount by rememberSaveable { mutableStateOf(4) }
    var circleExerciseStartRoot by rememberSaveable { mutableStateOf(NoteName.C) }
    var circleExerciseStepOffset by rememberSaveable { mutableStateOf(0) }
    var exerciseChoiceMode by rememberSaveable { mutableStateOf(ExerciseChoiceMode.PRESET) }
    var isTimedExerciseRunning by rememberSaveable { mutableStateOf(false) }
    var timedExerciseStepOffset by rememberSaveable { mutableStateOf(0) }
    var timedExerciseChordIntervalBeats by rememberSaveable { mutableStateOf(1) }
    var timedExerciseTargetRoot by remember { mutableStateOf<NoteName?>(null) }
    var timedExerciseChoiceRoots by remember { mutableStateOf(emptyList<NoteName>()) }
    var timedExerciseDueTick by remember { mutableStateOf(0L) }
    var metronomeBpm by rememberSaveable { mutableStateOf(96) }
    var metronomeTimeSignature by remember {
        mutableStateOf(METRONOME_TIME_SIGNATURES.first { it.label == "4/4" })
    }
    var metronomeSound by rememberSaveable { mutableStateOf(MetronomeSoundOption.WOOD_SOFT) }
    var metronomeVolumePercent by rememberSaveable { mutableStateOf(120f) }
    var isMetronomeRunning by rememberSaveable { mutableStateOf(false) }
    var metronomeCurrentBeat by remember { mutableStateOf(0) }
    var metronomeTick by remember { mutableStateOf(0L) }
    var metronomeEnabledBeats by remember { mutableStateOf(setOf(1, 3)) }
    var toneVolumeDb by rememberSaveable { mutableStateOf(70f) }
    var showNoteLabels by rememberSaveable { mutableStateOf(true) }
    var selectedChordToneOffsets by remember(selectedChordQuality) {
        mutableStateOf(selectedChordQuality.tones.map { it.semitoneOffset }.toSet())
    }

    val pitchShiftByString = remember { mutableStateMapOf<Int, Int>() }
    val baseRows = uiState.result.pegCorrectTable
    val rows = baseRows.map { row ->
        val shift = (pitchShiftByString[row.stringNumber] ?: 0).coerceIn(-1, 1)
        if (shift == 0) row else row.copy(selectedPitch = row.selectedPitch.plusSemitones(shift))
    }
    val scrollState = rememberScrollState()
    val tonePlayer = remember { PluckedStringPlayer() }
    val metronomePlayer = remember { MetronomeClickPlayer() }
    val coroutineScope = rememberCoroutineScope()
    val playGenerationByString = remember { mutableStateMapOf<Int, Int>() }

    LaunchedEffect(isMuted) {
        if (isMuted) {
            tonePlayer.stopAll()
            metronomePlayer.stopAll()
            playingStringNumbers = emptySet()
            playGenerationByString.clear()
            isMetronomeRunning = false
        }
    }
    LaunchedEffect(uiState.rootNote, uiState.scaleType, uiState.result.request.instrumentProfile.stringCount) {
        playingStringNumbers = emptySet()
        playGenerationByString.clear()
    }
    LaunchedEffect(uiState.result.request.instrumentProfile) { pitchShiftByString.clear() }
    LaunchedEffect(metronomeTimeSignature) {
        val trimmed = metronomeEnabledBeats.filter { it in 1..metronomeTimeSignature.numerator }.toSet()
        metronomeEnabledBeats = if (trimmed.isEmpty()) setOf(1) else trimmed
    }
    LaunchedEffect(isMetronomeRunning, metronomeBpm, metronomeTimeSignature,
        metronomeEnabledBeats, metronomeSound, metronomeVolumePercent) {
        if (!isMetronomeRunning) {
            metronomePlayer.stopAll(); metronomeCurrentBeat = 0; metronomeTick = 0L
            return@LaunchedEffect
        }
        var beat = 0
        val stepDelayMs = (60_000.0 / metronomeBpm.toDouble()).roundToInt().coerceAtLeast(50).toLong()
        while (isActive && isMetronomeRunning) {
            beat = if (beat >= metronomeTimeSignature.numerator) 1 else beat + 1
            metronomeCurrentBeat = beat
            metronomeTick += 1L
            if (beat in metronomeEnabledBeats && !isMuted) {
                metronomePlayer.play(sound = metronomeSound, accent = beat == 1, volumeScale = metronomeVolumePercent / 100f)
            }
            delay(stepDelayMs)
        }
    }
    LaunchedEffect(toneVolumeDb) { tonePlayer.setVolumeDb(toneVolumeDb.toDouble()) }
    DisposableEffect(Unit) { onDispose { tonePlayer.release(); metronomePlayer.release() } }

    val selectedChordDefinition = remember(selectedChordRoot, selectedChordQuality) {
        ChordDefinition(root = selectedChordRoot, quality = selectedChordQuality)
    }
    val selectedChordMatch = remember(rows, selectedChordDefinition) {
        ChordPlanner.analyze(rows = rows, definition = selectedChordDefinition)
    }
    val suggestedNonDetunedChord = remember(rows, selectedChordDefinition, selectedChordMatch.usesDetunedStrings) {
        if (!selectedChordMatch.usesDetunedStrings) null
        else ChordPlanner.suggestClosestWithoutDetune(rows = rows, desired = selectedChordDefinition)
    }
    val bestChordMatches = remember(rows) { ChordPlanner.bestMatches(rows = rows, limit = 10) }
    val selectedChordStrings = remember(rows, selectedChordDefinition, selectedChordToneOffsets, chordVoicingNoteCount) {
        ChordPlanner.chooseChordStrings(
            rows = rows, definition = selectedChordDefinition,
            toneOffsetsToInclude = selectedChordToneOffsets, maxNotes = chordVoicingNoteCount
        )
    }

    fun playString(stringNumber: Int, pitch: Pitch, centsOffset: Double) {
        if (isMuted) return
        val frequency = TunerTargetMatcher.pitchToFrequencyHz(pitch = pitch, centsOffset = centsOffset)
        tonePlayer.play(stringNumber = stringNumber, frequencyHz = frequency * 2.0)
        playingStringNumbers = playingStringNumbers + stringNumber
        val playGeneration = (playGenerationByString[stringNumber] ?: 0) + 1
        playGenerationByString[stringNumber] = playGeneration
        coroutineScope.launch {
            delay(PLUCK_VISUAL_HOLD_MS)
            if (playGenerationByString[stringNumber] == playGeneration) {
                playingStringNumbers = playingStringNumbers - stringNumber
            }
        }
    }
    fun playRow(row: PegCorrectStringResult) = playString(row.stringNumber, row.selectedPitch, row.selectedIntonationCents)
    fun stopRow(row: PegCorrectStringResult) {
        tonePlayer.stop(row.stringNumber)
        playingStringNumbers = playingStringNumbers - row.stringNumber
        playGenerationByString.remove(row.stringNumber)
    }
    val toggleRow: (PegCorrectStringResult) -> Unit = { row ->
        if (row.stringNumber in playingStringNumbers) stopRow(row) else playRow(row)
    }
    val sharpenRow: (PegCorrectStringResult) -> Unit = { row ->
        val sn = row.stringNumber
        val cur = (pitchShiftByString[sn] ?: 0).coerceIn(-1, 1)
        val next = if (cur == 1) 0 else 1
        if (next == 0) pitchShiftByString.remove(sn) else pitchShiftByString[sn] = next
        playString(sn, row.selectedPitch.plusSemitones(-cur).plusSemitones(next), row.selectedIntonationCents)
    }
    val flattenRow: (PegCorrectStringResult) -> Unit = { row ->
        val sn = row.stringNumber
        val cur = (pitchShiftByString[sn] ?: 0).coerceIn(-1, 1)
        val next = if (cur == -1) 0 else -1
        if (next == 0) pitchShiftByString.remove(sn) else pitchShiftByString[sn] = next
        playString(sn, row.selectedPitch.plusSemitones(-cur).plusSemitones(next), row.selectedIntonationCents)
    }
    val playChord: (List<PegCorrectStringResult>, ChordPlaybackMode) -> Unit = { chordRows, mode ->
        val limitedRows = chordRows.take(4)
        when (mode) {
            ChordPlaybackMode.BLOCK -> limitedRows.forEach { playRow(it) }
            ChordPlaybackMode.BROKEN -> coroutineScope.launch {
                limitedRows.forEachIndexed { index, row -> playRow(row); if (index != limitedRows.lastIndex) delay(BROKEN_CHORD_STEP_MS) }
            }
            ChordPlaybackMode.SPREAD -> coroutineScope.launch {
                val spread = spreadOrder(limitedRows)
                spread.forEachIndexed { index, row -> playRow(row); if (index != spread.lastIndex) delay(SPREAD_CHORD_STEP_MS) }
            }
        }
    }
    val stopAllPlayback: () -> Unit = {
        tonePlayer.stopAll(); playingStringNumbers = emptySet(); playGenerationByString.clear()
    }
    val runCircleExerciseStep: () -> Unit = {
        val startIndex = circleOfFifthsIndexFor(circleExerciseStartRoot)
        val root = CIRCLE_OF_FIFTHS_ORDER[(startIndex + circleExerciseStepOffset) % CIRCLE_OF_FIFTHS_ORDER.size]
        selectedChordRoot = root
        val exerciseRows = ChordPlanner.chooseChordStrings(
            rows = rows, definition = ChordDefinition(root = root, quality = selectedChordQuality),
            toneOffsetsToInclude = selectedChordToneOffsets, maxNotes = chordVoicingNoteCount
        )
        playChord(exerciseRows, chordPlaybackMode)
        circleExerciseStepOffset = (circleExerciseStepOffset + 1) % CIRCLE_OF_FIFTHS_ORDER.size
    }
    val scheduleTimedExercisePrompt: (Long) -> Unit = { currentTick ->
        val nextRoot = when (exerciseChoiceMode) {
            ExerciseChoiceMode.PRESET -> {
                val startIndex = circleOfFifthsIndexFor(circleExerciseStartRoot)
                val root = CIRCLE_OF_FIFTHS_ORDER[(startIndex + timedExerciseStepOffset) % CIRCLE_OF_FIFTHS_ORDER.size]
                timedExerciseStepOffset = (timedExerciseStepOffset + 1) % CIRCLE_OF_FIFTHS_ORDER.size
                root
            }
            ExerciseChoiceMode.RANDOM -> CIRCLE_OF_FIFTHS_ORDER.random()
        }
        timedExerciseTargetRoot = nextRoot
        timedExerciseChoiceRoots = buildExerciseChoices(nextRoot, size = 4)
        timedExerciseDueTick = currentTick + timedExerciseChordIntervalBeats.coerceIn(1, 4)
    }

    LaunchedEffect(isTimedExerciseRunning, exerciseChoiceMode, circleExerciseStartRoot, metronomeTimeSignature) {
        if (!isTimedExerciseRunning) {
            timedExerciseTargetRoot = null; timedExerciseChoiceRoots = emptyList(); timedExerciseDueTick = 0L
            return@LaunchedEffect
        }
        if (exerciseChoiceMode == ExerciseChoiceMode.PRESET) timedExerciseStepOffset = 0
        scheduleTimedExercisePrompt(metronomeTick)
    }
    LaunchedEffect(metronomeTick, isTimedExerciseRunning, timedExerciseTargetRoot, timedExerciseDueTick,
        timedExerciseChordIntervalBeats, selectedChordQuality, selectedChordToneOffsets, chordVoicingNoteCount, chordPlaybackMode, rows) {
        if (!isTimedExerciseRunning || metronomeTick == 0L) return@LaunchedEffect
        if (timedExerciseTargetRoot == null) { scheduleTimedExercisePrompt(metronomeTick); return@LaunchedEffect }
        if (metronomeTick < timedExerciseDueTick) return@LaunchedEffect
        val targetRoot = requireNotNull(timedExerciseTargetRoot)
        selectedChordRoot = targetRoot
        val exerciseRows = ChordPlanner.chooseChordStrings(
            rows = rows, definition = ChordDefinition(root = targetRoot, quality = selectedChordQuality),
            toneOffsetsToInclude = selectedChordToneOffsets, maxNotes = chordVoicingNoteCount
        )
        playChord(exerciseRows, chordPlaybackMode)
        scheduleTimedExercisePrompt(metronomeTick)
    }

    val timedExerciseDueBeatInBar = if (timedExerciseDueTick <= 0L) 1
        else (((timedExerciseDueTick - 1L) % metronomeTimeSignature.numerator.toLong()) + 1L).toInt()
    val timedExerciseBeatsUntilDue = (timedExerciseDueTick - metronomeTick).coerceAtLeast(0L).toInt()
    val onStringTouched: (PegCorrectStringResult) -> Unit = toggleRow
    val isScreenScrollEnabled = !(viewMode == OverviewViewMode.DIAGRAM && (diagramZoom > 1f || isDiagramLocked))

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.title_instant_overview)) },
                actions = {
                    IconButton(onClick = onToggleMute) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = if (isMuted) MaterialTheme.colorScheme.error
                                   else androidx.compose.ui.graphics.Color.Unspecified
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(state = scrollState, enabled = isScreenScrollEnabled)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = uiState.profileStatus, style = MaterialTheme.typography.bodyMedium)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(Res.string.overview_reference_volume_label), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = "${toneVolumeDb.roundToInt()} dB", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(value = toneVolumeDb, onValueChange = { toneVolumeDb = it }, valueRange = 30f..100f)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(Res.string.overview_note_letters_label), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(checked = showNoteLabels, onCheckedChange = { showNoteLabels = it })
                    }
                }
            }

            OverviewSelectionControls(
                rootNote = uiState.rootNote,
                scaleType = uiState.scaleType,
                onScaleTypeSelected = onScaleTypeSelected
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = viewMode == OverviewViewMode.DIAGRAM,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(OverviewViewMode.entries.indexOf(OverviewViewMode.DIAGRAM)) } },
                    label = { Text(stringResource(Res.string.overview_view_diagram)) })
                FilterChip(selected = viewMode == OverviewViewMode.TABLE,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(OverviewViewMode.entries.indexOf(OverviewViewMode.TABLE)) } },
                    label = { Text(stringResource(Res.string.overview_view_table)) })
                FilterChip(selected = viewMode == OverviewViewMode.CHORDS,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(OverviewViewMode.entries.indexOf(OverviewViewMode.CHORDS)) } },
                    label = { Text(stringResource(Res.string.overview_view_chords)) })
                FilterChip(selected = viewMode == OverviewViewMode.EXERCISE,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(OverviewViewMode.entries.indexOf(OverviewViewMode.EXERCISE)) } },
                    label = { Text(stringResource(Res.string.overview_view_exercise)) })
            }

            Box(modifier = Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterStart) {
                val showStopButton = playingStringNumbers.isNotEmpty() && viewMode != OverviewViewMode.DIAGRAM
                if (showStopButton) {
                    OutlinedButton(onClick = stopAllPlayback) {
                        Text(stringResource(Res.string.overview_action_stop_all_tones))
                    }
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth(), userScrollEnabled = false) { page ->
                when (OverviewViewMode.entries[page]) {
                    OverviewViewMode.DIAGRAM -> DiagramOverview(
                        rows = rows, pitchShiftByString = pitchShiftByString,
                        playingStringNumbers = playingStringNumbers, onStringTouched = toggleRow,
                        onStringSharpened = sharpenRow, onStringFlattened = flattenRow,
                        onPlayAllStrings = { rows.forEach { playRow(it) } },
                        onStopAllStrings = stopAllPlayback, showLeverInfo = showLeverInfo,
                        diagramZoom = diagramZoom, onDiagramZoomChanged = { diagramZoom = it },
                        isDiagramLocked = isDiagramLocked, onDiagramLockedChanged = { isDiagramLocked = it },
                        showNoteLetters = showNoteLabels
                    )
                    OverviewViewMode.TABLE -> TableOverview(
                        rows = rows, pitchShiftByString = pitchShiftByString,
                        playingStringNumbers = playingStringNumbers, onStringTouched = onStringTouched,
                        showLeverInfo = showLeverInfo
                    )
                    OverviewViewMode.CHORDS -> ChordOverview(
                        rows = rows, pitchShiftByString = pitchShiftByString,
                        selectedRoot = selectedChordRoot, onRootSelected = { selectedChordRoot = it },
                        selectedQuality = selectedChordQuality, onQualitySelected = { selectedChordQuality = it },
                        selectedMatch = selectedChordMatch, selectedChordStrings = selectedChordStrings,
                        selectedToneOffsets = selectedChordToneOffsets,
                        onToneOffsetToggled = { offset ->
                            selectedChordToneOffsets = if (offset in selectedChordToneOffsets) {
                                val next = selectedChordToneOffsets - offset
                                if (next.isEmpty()) selectedChordToneOffsets else next
                            } else selectedChordToneOffsets + offset
                        },
                        playbackMode = chordPlaybackMode, onPlaybackModeSelected = { chordPlaybackMode = it },
                        chordVoicingNoteCount = chordVoicingNoteCount,
                        onChordVoicingNoteCountChanged = { chordVoicingNoteCount = it.coerceIn(1, 4) },
                        onPlaySelectedChord = { playChord(selectedChordStrings, chordPlaybackMode) },
                        showLeverInfo = showLeverInfo,
                        circleExerciseStartRoot = circleExerciseStartRoot,
                        onCircleExerciseStartRootChanged = { circleExerciseStartRoot = it; circleExerciseStepOffset = 0 },
                        nextCircleExerciseRoot = CIRCLE_OF_FIFTHS_ORDER[
                            (circleOfFifthsIndexFor(circleExerciseStartRoot) + circleExerciseStepOffset) % CIRCLE_OF_FIFTHS_ORDER.size
                        ],
                        onRunCircleExerciseStep = runCircleExerciseStep,
                        exerciseChoiceMode = exerciseChoiceMode, onExerciseChoiceModeSelected = { exerciseChoiceMode = it },
                        isTimedExerciseRunning = isTimedExerciseRunning,
                        onTimedExerciseRunningChanged = { running ->
                            isTimedExerciseRunning = running
                            if (running && !isMetronomeRunning) isMetronomeRunning = true
                        },
                        timedExerciseChordIntervalBeats = timedExerciseChordIntervalBeats,
                        onTimedExerciseChordIntervalBeatsChanged = { timedExerciseChordIntervalBeats = it.coerceIn(1, 4) },
                        timedExerciseTargetRoot = timedExerciseTargetRoot,
                        timedExerciseChoiceRoots = timedExerciseChoiceRoots,
                        timedExerciseDueBeat = timedExerciseDueBeatInBar,
                        timedExerciseBeatsUntilDue = timedExerciseBeatsUntilDue,
                        metronomeBpm = metronomeBpm, onMetronomeBpmChanged = { metronomeBpm = it.coerceIn(40, 250) },
                        metronomeTimeSignature = metronomeTimeSignature, onMetronomeTimeSignatureChanged = { metronomeTimeSignature = it },
                        metronomeEnabledBeats = metronomeEnabledBeats,
                        onMetronomeBeatToggled = { beat ->
                            metronomeEnabledBeats = if (beat in metronomeEnabledBeats) {
                                val next = metronomeEnabledBeats - beat
                                if (next.isEmpty()) metronomeEnabledBeats else next
                            } else metronomeEnabledBeats + beat
                        },
                        metronomeSound = metronomeSound, onMetronomeSoundChanged = { metronomeSound = it },
                        isMetronomeRunning = isMetronomeRunning, onMetronomeRunningChanged = { isMetronomeRunning = it },
                        metronomeCurrentBeat = metronomeCurrentBeat,
                        suggestedNonDetunedChord = suggestedNonDetunedChord, bestMatches = bestChordMatches,
                        onChordSelected = { definition ->
                            selectedChordRoot = definition.root; selectedChordQuality = definition.quality; circleExerciseStepOffset = 0
                        },
                        playingStringNumbers = playingStringNumbers, onStringTouched = onStringTouched
                    )
                    OverviewViewMode.EXERCISE -> ChordExerciseOverview(
                        selectedQuality = selectedChordQuality,
                        circleExerciseStartRoot = circleExerciseStartRoot,
                        onCircleExerciseStartRootChanged = { circleExerciseStartRoot = it; circleExerciseStepOffset = 0 },
                        nextCircleExerciseRoot = CIRCLE_OF_FIFTHS_ORDER[
                            (circleOfFifthsIndexFor(circleExerciseStartRoot) + circleExerciseStepOffset) % CIRCLE_OF_FIFTHS_ORDER.size
                        ],
                        onRunCircleExerciseStep = runCircleExerciseStep,
                        exerciseChoiceMode = exerciseChoiceMode, onExerciseChoiceModeSelected = { exerciseChoiceMode = it },
                        isTimedExerciseRunning = isTimedExerciseRunning,
                        onTimedExerciseRunningChanged = { running ->
                            isTimedExerciseRunning = running
                            if (running && !isMetronomeRunning) isMetronomeRunning = true
                        },
                        timedExerciseChordIntervalBeats = timedExerciseChordIntervalBeats,
                        onTimedExerciseChordIntervalBeatsChanged = { timedExerciseChordIntervalBeats = it.coerceIn(1, 4) },
                        timedExerciseTargetRoot = timedExerciseTargetRoot,
                        timedExerciseChoiceRoots = timedExerciseChoiceRoots,
                        timedExerciseDueBeat = timedExerciseDueBeatInBar,
                        timedExerciseBeatsUntilDue = timedExerciseBeatsUntilDue,
                        onRootSelected = { selectedChordRoot = it },
                        metronomeBpm = metronomeBpm, onMetronomeBpmChanged = { metronomeBpm = it.coerceIn(40, 250) },
                        metronomeTimeSignature = metronomeTimeSignature, onMetronomeTimeSignatureChanged = { metronomeTimeSignature = it },
                        metronomeEnabledBeats = metronomeEnabledBeats,
                        onMetronomeBeatToggled = { beat ->
                            metronomeEnabledBeats = if (beat in metronomeEnabledBeats) {
                                val next = metronomeEnabledBeats - beat
                                if (next.isEmpty()) metronomeEnabledBeats else next
                            } else metronomeEnabledBeats + beat
                        },
                        metronomeSound = metronomeSound, onMetronomeSoundChanged = { metronomeSound = it },
                        metronomeVolumePercent = metronomeVolumePercent,
                        onMetronomeVolumePercentChanged = { metronomeVolumePercent = it.coerceIn(30f, 180f) },
                        isMetronomeRunning = isMetronomeRunning, onMetronomeRunningChanged = { isMetronomeRunning = it },
                        metronomeCurrentBeat = metronomeCurrentBeat
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewSelectionControls(
    rootNote: NoteName,
    scaleType: ScaleType,
    onScaleTypeSelected: (ScaleType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(Res.string.scale_root_note_label) + ": ${rootNote.symbol}", style = MaterialTheme.typography.titleMedium)
        Text(text = stringResource(Res.string.scale_type_label), style = MaterialTheme.typography.titleMedium)
        ScaleTypeDropdownMenus(selectedScaleType = scaleType, onScaleTypeSelected = onScaleTypeSelected)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DiagramOverview(
    rows: List<PegCorrectStringResult>,
    pitchShiftByString: Map<Int, Int>,
    playingStringNumbers: Set<Int>,
    onStringTouched: (PegCorrectStringResult) -> Unit,
    onStringSharpened: (PegCorrectStringResult) -> Unit,
    onStringFlattened: (PegCorrectStringResult) -> Unit,
    onPlayAllStrings: () -> Unit,
    onStopAllStrings: () -> Unit,
    showLeverInfo: Boolean,
    diagramZoom: Float,
    onDiagramZoomChanged: (Float) -> Unit,
    isDiagramLocked: Boolean,
    onDiagramLockedChanged: (Boolean) -> Unit,
    showNoteLetters: Boolean
) {
    val left = rows.filter { it.role.side == StringSide.LEFT }.sortedBy { it.role.positionFromLow }
    val right = rows.filter { it.role.side == StringSide.RIGHT }.sortedBy { it.role.positionFromLow }
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val maxTapDistancePx = with(density) { 20.dp.toPx() }
    var diagramSize by remember { mutableStateOf(IntSize.Zero) }
    // rememberUpdatedState ensures the pointerInput coroutine always calls the latest
    // onStringTouched lambda even when pointerInput keys haven't changed (e.g. after
    // playingStringNumbers updates on the first tap).
    val currentOnStringTouched by rememberUpdatedState(onStringTouched)
    val textMeasurer = rememberTextMeasurer()

    val infiniteTransition = rememberInfiniteTransition(label = "string_vibration")
    val rawVibrationPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 280, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vibration_phase"
    )
    val vibrationPhase = if (playingStringNumbers.isNotEmpty()) rawVibrationPhase else 0f

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = stringResource(Res.string.overview_diagram_title), style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.overview_diagram_zoom_label, (diagramZoom * 100f).toInt().toString()),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = { onDiagramZoomChanged((diagramZoom - 0.2f).coerceAtLeast(1f)) }, enabled = diagramZoom > 1f) { Text("-") }
                    OutlinedButton(onClick = { onDiagramZoomChanged((diagramZoom + 0.2f).coerceAtMost(3f)) }, enabled = diagramZoom < 3f) { Text("+") }
                    OutlinedButton(onClick = { onDiagramZoomChanged(1f) }, enabled = diagramZoom != 1f) { Text(stringResource(Res.string.action_reset)) }
                    OutlinedButton(
                        onClick = { onDiagramLockedChanged(!isDiagramLocked) },
                        border = if (isDiagramLocked) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Text(stringResource(if (isDiagramLocked) Res.string.overview_diagram_locked else Res.string.overview_diagram_free))
                    }
                }
                OutlinedButton(onClick = onPlayAllStrings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.overview_action_play_all_strings))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(StickyDiagramHeight)
                        .clipToBounds()
                        .onSizeChanged { size -> diagramSize = size }
                        .pointerInput(left, right, diagramZoom, diagramSize) {
                            awaitEachGesture {
                                val activeStringByPointer = mutableMapOf<Long, Int>()
                                var event = awaitPointerEvent()
                                while (true) {
                                    event.changes.forEach { change ->
                                        if (!change.pressed) { activeStringByPointer.remove(change.id.value); return@forEach }
                                        val hit = resolveDiagramHit(
                                            tapOffset = change.position, diagramSize = diagramSize,
                                            zoom = diagramZoom, leftRows = left, rightRows = right, maxDistancePx = maxTapDistancePx
                                        )
                                        val newString = hit?.stringNumber
                                        val prevString = activeStringByPointer[change.id.value]
                                        if (newString != null && newString != prevString) {
                                            activeStringByPointer[change.id.value] = newString
                                            currentOnStringTouched(hit)
                                        } else if (newString == null) {
                                            activeStringByPointer.remove(change.id.value)
                                        }
                                    }
                                    if (event.changes.none { it.pressed }) break
                                    event = awaitPointerEvent()
                                }
                            }
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = diagramZoom, scaleY = diagramZoom)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawKoraBody(colors)
                            drawKoraDiagram(
                                leftRows = left, rightRows = right, colors = colors,
                                activeStringNumbers = playingStringNumbers, showLeverInfo = showLeverInfo,
                                noteLabelsVisible = showNoteLetters, pitchShiftByString = pitchShiftByString,
                                vibrationPhase = vibrationPhase, textMeasurer = textMeasurer
                            )
                        }
                    }
                }

                DiagramLegend(showLeverInfo = showLeverInfo)
                TouchStringRows(
                    leftRows = left, rightRows = right, pitchShiftByString = pitchShiftByString,
                    playingStringNumbers = playingStringNumbers, onStringTouched = onStringTouched,
                    onStringSharpened = onStringSharpened, onStringFlattened = onStringFlattened
                )
            }
        }
        if (playingStringNumbers.isNotEmpty()) {
            OutlinedButton(
                onClick = onStopAllStrings,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) { Text(stringResource(Res.string.overview_action_stop_all_tones)) }
        }
    }
}

private fun DrawScope.drawKoraBody(colors: ColorScheme) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return
    val bg0 = colors.background
    val isDark = (bg0.red * 0.299f + bg0.green * 0.587f + bg0.blue * 0.114f) < 0.5f
    val bg = if (isDark) Color(0xFF0F0A05) else Color(0xFFF5F0E8)
    val woodDark = Color(0xFF3B2010); val woodMid = Color(0xFF6B4520); val woodLight = Color(0xFF9B6840)
    val skinIvory = Color(0xFFF2E8D0)
    val nCX = w * 0.500f; val nHW = w * 0.052f; val nL = nCX - nHW; val nR = nCX + nHW
    val nSD = w * 0.018f; val nBY = h * 0.600f
    val gX = w * 0.500f; val gY = h * 0.730f; val gRX = w * 0.400f; val gRY = h * 0.245f
    val bY = h * 0.600f
    val aBodyThick = w * 0.048f; val aHighlight = w * 0.014f
    drawRect(color = bg)
    for ((attachX, tipX) in listOf(w * 0.200f to w * 0.018f, w * 0.800f to w * 0.982f)) {
        val attachY = h * 0.622f; val tipY = h * 0.638f
        drawLine(Color.Black.copy(alpha = 0.25f), Offset(attachX, attachY + aBodyThick * 0.32f), Offset(tipX, tipY + aBodyThick * 0.32f), aBodyThick, StrokeCap.Round)
        drawLine(woodMid, Offset(attachX, attachY), Offset(tipX, tipY), aBodyThick, StrokeCap.Round)
        drawLine(woodDark.copy(alpha = 0.60f), Offset(attachX, attachY + aBodyThick * 0.18f), Offset(tipX, tipY + aBodyThick * 0.18f), aBodyThick * 0.52f, StrokeCap.Round)
        drawLine(woodLight.copy(alpha = 0.65f), Offset(attachX, attachY - aBodyThick * 0.20f), Offset(tipX, tipY - aBodyThick * 0.20f), aHighlight, StrokeCap.Round)
    }
    val nTopHW = nHW * 0.82f; val nTopSD = nSD * 0.82f
    drawPath(Path().apply { moveTo(nCX + nHW, nBY); lineTo(nCX + nHW + nSD, nBY); lineTo(nCX + nTopHW + nTopSD, 0f); lineTo(nCX + nTopHW, 0f); close() }, woodDark)
    drawPath(Path().apply { moveTo(nCX - nHW, nBY); lineTo(nCX + nHW, nBY); lineTo(nCX + nTopHW, 0f); lineTo(nCX - nTopHW, 0f); close() }, woodMid)
    drawPath(Path().apply {
        val hBotHW = nHW * 0.28f; val hTopHW = nTopHW * 0.28f
        moveTo(nCX - hBotHW, nBY); lineTo(nCX + hBotHW, nBY); lineTo(nCX + hTopHW, 0f); lineTo(nCX - hTopHW, 0f); close()
    }, woodLight.copy(alpha = 0.42f))
    repeat(7) { i ->
        val frac = (i + 1) / 8f; val y = nBY * frac; val hw = lerp(nTopHW, nHW, frac)
        drawLine(woodDark.copy(alpha = 0.15f), Offset(nCX - hw + 1f, y), Offset(nCX + hw, y), 1f)
    }
    drawRect(woodDark, topLeft = Offset(nCX - nTopHW - 2f, 0f), size = Size(nTopHW * 2f + nTopSD + 4f, h * 0.015f))
    drawOval(Color.Black.copy(alpha = 0.28f), topLeft = Offset(gX - gRX + 8f, gY - gRY + 14f), size = Size(gRX * 2f, gRY * 2f))
    drawOval(woodMid, topLeft = Offset(gX - gRX, gY - gRY), size = Size(gRX * 2f, gRY * 2f))
    val sp = w * 0.018f
    drawOval(skinIvory, topLeft = Offset(gX - gRX + sp, gY - gRY + sp), size = Size((gRX - sp) * 2f, (gRY - sp) * 2f))
    drawOval(woodDark.copy(alpha = 0.12f), topLeft = Offset(gX - gRX + sp, gY), size = Size((gRX - sp) * 2f, gRY - sp))
    drawOval(Color.White.copy(alpha = 0.13f), topLeft = Offset(gX - gRX * 0.65f, gY - gRY * 0.72f), size = Size(gRX * 1.22f, gRY * 1.0f))
    val bridgeShadow = Offset(w * 0.006f, h * 0.008f)
    val bridgeBarWidth = w * 0.48f; val bridgeBarHeight = h * 0.028f
    val bridgeBarLeft = nCX - (bridgeBarWidth * 0.5f); val bridgeBarTop = bY + h * 0.018f
    val bridgeBarRadius = CornerRadius(w * 0.010f, w * 0.010f)
    drawRoundRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset(bridgeBarLeft + bridgeShadow.x, bridgeBarTop + bridgeShadow.y), size = Size(bridgeBarWidth, bridgeBarHeight), cornerRadius = bridgeBarRadius)
    drawRoundRect(woodMid, topLeft = Offset(bridgeBarLeft, bridgeBarTop), size = Size(bridgeBarWidth, bridgeBarHeight), cornerRadius = bridgeBarRadius)
    drawRoundRect(woodDark.copy(alpha = 0.38f), topLeft = Offset(bridgeBarLeft, bridgeBarTop + bridgeBarHeight * 0.50f), size = Size(bridgeBarWidth, bridgeBarHeight * 0.50f), cornerRadius = bridgeBarRadius)
    drawRoundRect(woodLight.copy(alpha = 0.32f), topLeft = Offset(bridgeBarLeft + bridgeBarWidth * 0.05f, bridgeBarTop + bridgeBarHeight * 0.10f), size = Size(bridgeBarWidth * 0.68f, bridgeBarHeight * 0.18f), cornerRadius = bridgeBarRadius)
    val bridgeStemWidth = w * 0.026f; val bridgeStemHeight = h * 0.112f
    val bridgeStemLeft = nCX - (bridgeStemWidth * 0.5f); val bridgeStemTop = bridgeBarTop - h * 0.020f
    val bridgeStemRadius = CornerRadius(w * 0.008f, w * 0.008f)
    drawRoundRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset(bridgeStemLeft + bridgeShadow.x, bridgeStemTop + bridgeShadow.y), size = Size(bridgeStemWidth, bridgeStemHeight), cornerRadius = bridgeStemRadius)
    drawRoundRect(woodDark, topLeft = Offset(bridgeStemLeft, bridgeStemTop), size = Size(bridgeStemWidth, bridgeStemHeight), cornerRadius = bridgeStemRadius)
    drawRoundRect(woodLight.copy(alpha = 0.28f), topLeft = Offset(bridgeStemLeft + bridgeStemWidth * 0.22f, bridgeStemTop + h * 0.006f), size = Size(bridgeStemWidth * 0.22f, bridgeStemHeight - h * 0.012f), cornerRadius = bridgeStemRadius)
    drawLine(woodDark.copy(alpha = 0.65f), start = Offset(bridgeBarLeft + bridgeBarWidth * 0.10f, bridgeBarTop + bridgeBarHeight * 0.50f), end = Offset(bridgeBarLeft + bridgeBarWidth * 0.90f, bridgeBarTop + bridgeBarHeight * 0.50f), strokeWidth = h * 0.0045f, cap = StrokeCap.Round)
}

private fun DrawScope.drawKoraDiagram(
    leftRows: List<PegCorrectStringResult>,
    rightRows: List<PegCorrectStringResult>,
    colors: ColorScheme,
    activeStringNumbers: Set<Int>,
    showLeverInfo: Boolean,
    noteLabelsVisible: Boolean,
    pitchShiftByString: Map<Int, Int>,
    vibrationPhase: Float = 0f,
    textMeasurer: TextMeasurer? = null
) {
    if (size.width <= 0f || size.height <= 0f) return
    val bridgeCenterY = size.height * 0.60f
    val bridgeTop = bridgeCenterY - (size.height * 0.17f)
    val bridgeBottom = bridgeCenterY + (size.height * 0.17f)
    drawStringSet(leftRows, true, bridgeTop, bridgeBottom, colors, activeStringNumbers, showLeverInfo, noteLabelsVisible, pitchShiftByString, vibrationPhase, textMeasurer)
    drawStringSet(rightRows, false, bridgeTop, bridgeBottom, colors, activeStringNumbers, showLeverInfo, noteLabelsVisible, pitchShiftByString, vibrationPhase, textMeasurer)
}

private fun DrawScope.drawStringSet(
    rows: List<PegCorrectStringResult>,
    isLeft: Boolean,
    bridgeTop: Float,
    bridgeBottom: Float,
    colors: ColorScheme,
    activeStringNumbers: Set<Int>,
    showLeverInfo: Boolean,
    noteLabelsVisible: Boolean,
    pitchShiftByString: Map<Int, Int>,
    vibrationPhase: Float = 0f,
    textMeasurer: TextMeasurer? = null
) {
    if (rows.isEmpty()) return
    val width = size.width
    val pegTopY = size.height * 0.06f; val pegBottomY = size.height * 0.28f
    val pegX = if (isLeft) width * 0.42f else width * 0.58f
    val bridgeBassX = if (isLeft) width * 0.08f else width * 0.92f
    val bridgeTrebleX = if (isLeft) width * 0.42f else width * 0.58f
    val bridgeY = (bridgeTop + bridgeBottom) / 2f
    val maxIndex = rows.lastIndex.coerceAtLeast(1)

    (rows.indices.reversed()).forEach { index ->
        val row = rows[index]
        val ratio = index.toFloat() / maxIndex.toFloat()
        val pegY = lerp(pegTopY, pegBottomY, ratio)
        val bridgeX = lerp(bridgeBassX, bridgeTrebleX, ratio)
        val closedLever = row.selectedLeverState == LeverState.CLOSED
        val leverColor = if (showLeverInfo) {
            when {
                closedLever && row.pegRetuneRequired -> KoraCombinedChangesColor  // purple: both
                row.pegRetuneRequired                -> KoraDetunedColor           // blue: peg only
                closedLever                          -> KoraLeverClosedStringColor // amber: lever only
                else                                 -> KoraOpenLeverColor         // green: no action
            }
        } else colors.outline
        val isActive = row.stringNumber in activeStringNumbers
        val baseStrokeWidth = if (row.pegRetuneRequired || closedLever) width * 0.0058f else width * 0.004f
        val depthThickness = (1.55f - (0.75f * ratio)).coerceIn(0.80f, 1.55f)
        val depthAlpha = 0.60f + 0.40f * (1f - ratio)
        val strokeWidth = (if (isActive) baseStrokeWidth * 1.8f else baseStrokeWidth) * depthThickness
        val stringColor = if (isActive) colors.primary.copy(alpha = depthAlpha) else leverColor.copy(alpha = depthAlpha)
        val midX = (pegX + bridgeX) * 0.5f; val midY = (pegY + bridgeY) * 0.5f
        val staticBendX = if (isLeft) -width * 0.025f else width * 0.025f

        if (isActive && vibrationPhase != 0f) {
            val dx = bridgeX - pegX; val dy = bridgeY - pegY
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val perpX = -dy / len; val perpY = dx / len
            val amplitude = width * 0.009f * (1.25f - ratio * 0.5f)
            val offset = sin(vibrationPhase + ratio * PI.toFloat()) * amplitude
            val path = Path().apply {
                moveTo(pegX, pegY)
                quadraticTo(midX + staticBendX + perpX * offset, midY + perpY * offset, bridgeX, bridgeY)
            }
            drawPath(path, color = stringColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        } else {
            drawPath(Path().apply { moveTo(pegX, pegY); quadraticTo(midX + staticBendX, midY, bridgeX, bridgeY) }, color = stringColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }

        val pegDotColor = when {
            closedLever && row.pegRetuneRequired -> KoraCombinedChangesColor
            row.pegRetuneRequired                -> KoraDetunedColor
            closedLever                          -> KoraLeverClosedStringColor
            else                                 -> colors.outline
        }
        drawCircle(color = pegDotColor.copy(alpha = depthAlpha), radius = if (row.pegRetuneRequired || closedLever) width * 0.010f else width * 0.007f, center = Offset(pegX, pegY))
        drawCircle(color = stringColor, radius = if (isActive) width * 0.0082f else width * 0.006f, center = Offset(bridgeX, bridgeY))

        if (noteLabelsVisible && textMeasurer != null) {
            val semitoneShift = (pitchShiftByString[row.stringNumber] ?: 0).coerceIn(-1, 1)
            val labelX = if (isLeft) pegX + (width * 0.032f) else pegX - (width * 0.032f)
            val labelText = displayPitchLabel(effectivePitch = row.selectedPitch, semitoneShift = semitoneShift, includeOctave = false)
            val textSizeSp = (width * 0.022f / density).sp
            val textResult = textMeasurer.measure(labelText, TextStyle(color = colors.onBackground, fontSize = textSizeSp))
            val textX = if (isLeft) labelX else labelX - textResult.size.width
            val textY = pegY + (width * 0.010f) - textResult.size.height * 0.75f
            drawText(textResult, topLeft = Offset(textX, textY))
        }
    }
}

private fun buildDiagramStringSegments(leftRows: List<PegCorrectStringResult>, rightRows: List<PegCorrectStringResult>, size: Size): List<DiagramStringSegment> {
    val bridgeCenterY = size.height * 0.60f
    val bridgeTop = bridgeCenterY - (size.height * 0.17f); val bridgeBottom = bridgeCenterY + (size.height * 0.17f)
    return buildSideStringSegments(leftRows, true, size, bridgeTop, bridgeBottom) + buildSideStringSegments(rightRows, false, size, bridgeTop, bridgeBottom)
}

private fun buildSideStringSegments(rows: List<PegCorrectStringResult>, isLeft: Boolean, size: Size, bridgeTop: Float, bridgeBottom: Float): List<DiagramStringSegment> {
    if (rows.isEmpty()) return emptyList()
    val width = size.width; val pegTopY = size.height * 0.06f; val pegBottomY = size.height * 0.28f
    val pegX = if (isLeft) width * 0.42f else width * 0.58f
    val bridgeBassX = if (isLeft) width * 0.08f else width * 0.92f; val bridgeTrebleX = if (isLeft) width * 0.42f else width * 0.58f
    val bridgeY = (bridgeTop + bridgeBottom) / 2f; val maxIndex = rows.lastIndex.coerceAtLeast(1)
    return rows.mapIndexed { index, row ->
        val ratio = index.toFloat() / maxIndex.toFloat()
        DiagramStringSegment(row = row, peg = Offset(x = pegX, y = lerp(pegTopY, pegBottomY, ratio)), bridge = Offset(x = lerp(bridgeBassX, bridgeTrebleX, ratio), y = bridgeY))
    }
}

private fun findTappedString(tapOffset: Offset, segments: List<DiagramStringSegment>, maxDistancePx: Float): PegCorrectStringResult? {
    val nearest = segments.minByOrNull { distancePointToSegment(tapOffset, it.peg, it.bridge) } ?: return null
    return if (distancePointToSegment(tapOffset, nearest.peg, nearest.bridge) <= maxDistancePx) nearest.row else null
}

private fun resolveDiagramHit(tapOffset: Offset, diagramSize: IntSize, zoom: Float, leftRows: List<PegCorrectStringResult>, rightRows: List<PegCorrectStringResult>, maxDistancePx: Float): PegCorrectStringResult? {
    if (diagramSize.width == 0 || diagramSize.height == 0) return null
    val size = Size(diagramSize.width.toFloat(), diagramSize.height.toFloat())
    return findTappedString(unscaleTapOffset(tapOffset, size, zoom), buildDiagramStringSegments(leftRows, rightRows, size), maxDistancePx)
}

private suspend fun PointerInputScope.consumeAllTouches() {
    awaitEachGesture {
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

private fun distancePointToSegment(point: Offset, segmentStart: Offset, segmentEnd: Offset): Float {
    val dx = segmentEnd.x - segmentStart.x; val dy = segmentEnd.y - segmentStart.y
    val segLenSq = dx * dx + dy * dy
    if (segLenSq <= 0f) { val px = point.x - segmentStart.x; val py = point.y - segmentStart.y; return sqrt(px * px + py * py) }
    val t = (((point.x - segmentStart.x) * dx + (point.y - segmentStart.y) * dy) / segLenSq).coerceIn(0f, 1f)
    val cx = segmentStart.x + t * dx; val cy = segmentStart.y + t * dy
    return sqrt((point.x - cx) * (point.x - cx) + (point.y - cy) * (point.y - cy))
}

private fun unscaleTapOffset(tapOffset: Offset, size: Size, zoom: Float): Offset {
    if (zoom <= 1f) return tapOffset
    val cx = size.width * 0.5f; val cy = size.height * 0.5f
    return Offset(cx + (tapOffset.x - cx) / zoom, cy + (tapOffset.y - cy) / zoom)
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

@Composable
private fun DiagramLegend(showLeverInfo: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showLeverInfo) {
            LegendItem(color = KoraOpenLeverColor, text = stringResource(Res.string.overview_legend_open_lever))
            LegendItem(color = KoraLeverClosedStringColor, text = stringResource(Res.string.overview_legend_closed_lever))
        }
        LegendItem(color = KoraDetunedColor, text = stringResource(Res.string.overview_legend_peg_retune_required))
        if (showLeverInfo) {
            LegendItem(color = KoraCombinedChangesColor, text = stringResource(Res.string.overview_legend_both_lever_and_peg))
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TouchStringRows(
    leftRows: List<PegCorrectStringResult>,
    rightRows: List<PegCorrectStringResult>,
    pitchShiftByString: Map<Int, Int>,
    playingStringNumbers: Set<Int>,
    onStringTouched: (PegCorrectStringResult) -> Unit,
    onStringSharpened: (PegCorrectStringResult) -> Unit,
    onStringFlattened: (PegCorrectStringResult) -> Unit
) {
    Text(text = stringResource(Res.string.overview_diagram_tap_to_hear), style = MaterialTheme.typography.bodySmall)
    Text(text = stringResource(Res.string.instrument_tuning_assistant_left_side), style = MaterialTheme.typography.labelMedium)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = leftRows, key = { it.stringNumber }) { row ->
            val shift = (pitchShiftByString[row.stringNumber] ?: 0).coerceIn(-1, 1)
            PitchActionChip(
                isActive = row.stringNumber in playingStringNumbers,
                text = "${row.role.asLabel()} ${displayPitchLabel(row.selectedPitch, shift, false)}",
                onClick = { onStringTouched(row) }, onDoubleClick = { onStringSharpened(row) }, onLongPress = { onStringFlattened(row) }
            )
        }
    }
    Text(text = stringResource(Res.string.instrument_tuning_assistant_right_side), style = MaterialTheme.typography.labelMedium)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = rightRows, key = { it.stringNumber }) { row ->
            val shift = (pitchShiftByString[row.stringNumber] ?: 0).coerceIn(-1, 1)
            PitchActionChip(
                isActive = row.stringNumber in playingStringNumbers,
                text = "${row.role.asLabel()} ${displayPitchLabel(row.selectedPitch, shift, false)}",
                onClick = { onStringTouched(row) }, onDoubleClick = { onStringSharpened(row) }, onLongPress = { onStringFlattened(row) }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PitchActionChip(isActive: Boolean, text: String, onClick: () -> Unit, onDoubleClick: () -> Unit, onLongPress: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(containerColor = if (isActive) colors.secondaryContainer else colors.surface),
        border = BorderStroke(1.dp, if (isActive) colors.primary else colors.outlineVariant)
    ) {
        Text(text = text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ChordOverview(
    rows: List<PegCorrectStringResult>,
    pitchShiftByString: Map<Int, Int>,
    selectedRoot: NoteName,
    onRootSelected: (NoteName) -> Unit,
    selectedQuality: ChordQuality,
    onQualitySelected: (ChordQuality) -> Unit,
    selectedMatch: ChordMatch,
    selectedChordStrings: List<PegCorrectStringResult>,
    selectedToneOffsets: Set<Int>,
    onToneOffsetToggled: (Int) -> Unit,
    playbackMode: ChordPlaybackMode,
    onPlaybackModeSelected: (ChordPlaybackMode) -> Unit,
    chordVoicingNoteCount: Int,
    onChordVoicingNoteCountChanged: (Int) -> Unit,
    onPlaySelectedChord: () -> Unit,
    showLeverInfo: Boolean,
    circleExerciseStartRoot: NoteName,
    onCircleExerciseStartRootChanged: (NoteName) -> Unit,
    nextCircleExerciseRoot: NoteName,
    onRunCircleExerciseStep: () -> Unit,
    exerciseChoiceMode: ExerciseChoiceMode,
    onExerciseChoiceModeSelected: (ExerciseChoiceMode) -> Unit,
    isTimedExerciseRunning: Boolean,
    onTimedExerciseRunningChanged: (Boolean) -> Unit,
    timedExerciseChordIntervalBeats: Int,
    onTimedExerciseChordIntervalBeatsChanged: (Int) -> Unit,
    timedExerciseTargetRoot: NoteName?,
    timedExerciseChoiceRoots: List<NoteName>,
    timedExerciseDueBeat: Int,
    timedExerciseBeatsUntilDue: Int,
    metronomeBpm: Int,
    onMetronomeBpmChanged: (Int) -> Unit,
    metronomeTimeSignature: MetronomeTimeSignature,
    onMetronomeTimeSignatureChanged: (MetronomeTimeSignature) -> Unit,
    metronomeEnabledBeats: Set<Int>,
    onMetronomeBeatToggled: (Int) -> Unit,
    metronomeSound: MetronomeSoundOption,
    onMetronomeSoundChanged: (MetronomeSoundOption) -> Unit,
    isMetronomeRunning: Boolean,
    onMetronomeRunningChanged: (Boolean) -> Unit,
    metronomeCurrentBeat: Int,
    suggestedNonDetunedChord: ChordMatch?,
    bestMatches: List<ChordMatch>,
    onChordSelected: (ChordDefinition) -> Unit,
    playingStringNumbers: Set<Int>,
    onStringTouched: (PegCorrectStringResult) -> Unit
) {
    var isQualityMenuExpanded by remember { mutableStateOf(false) }
    val leftRows = rows.filter { it.role.side == StringSide.LEFT }.sortedBy { it.role.positionFromLow }
    val rightRows = rows.filter { it.role.side == StringSide.RIGHT }.sortedBy { it.role.positionFromLow }
    val rowCount = maxOf(leftRows.size, rightRows.size)
    val selectedChordStringNumbers = selectedChordStrings.map { it.stringNumber }.toSet()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(Res.string.overview_chords_title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(Res.string.overview_chords_subtitle), style = MaterialTheme.typography.bodySmall)
            Text(text = stringResource(Res.string.overview_chords_root_label), style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(NoteName.entries) { note -> FilterChip(selected = note == selectedRoot, onClick = { onRootSelected(note) }, label = { Text(note.symbol) }) }
            }
            Text(text = stringResource(Res.string.overview_chords_quality_label), style = MaterialTheme.typography.labelMedium)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { isQualityMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(chordQualityLabel(selectedQuality)) }
                DropdownMenu(expanded = isQualityMenuExpanded, onDismissRequest = { isQualityMenuExpanded = false }) {
                    ChordQuality.entries.forEach { quality ->
                        DropdownMenuItem(text = { Text(chordQualityLabel(quality)) }, onClick = { isQualityMenuExpanded = false; onQualitySelected(quality) })
                    }
                }
            }
            Text(text = stringResource(Res.string.overview_chords_notes_line, selectedMatch.definition.chordNotes.joinToString(" ") { it.symbol }), style = MaterialTheme.typography.bodySmall)
            Text(text = stringResource(Res.string.overview_chords_positions_label), style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = selectedQuality.tones, key = { it.semitoneOffset }) { tone ->
                    FilterChip(selected = tone.semitoneOffset in selectedToneOffsets, onClick = { onToneOffsetToggled(tone.semitoneOffset) }, label = { Text(tone.label) })
                }
            }
            Text(
                text = if (selectedMatch.missingNotes.isEmpty()) stringResource(Res.string.overview_chords_coverage_complete)
                       else stringResource(Res.string.overview_chords_coverage_missing, selectedMatch.missingNotes.joinToString(" ") { it.symbol }),
                style = MaterialTheme.typography.bodySmall
            )
            Text(text = stringResource(Res.string.overview_chords_strings_to_play_line, selectedChordStrings.size, 4), style = MaterialTheme.typography.bodySmall)
            Text(text = stringResource(Res.string.overview_chords_pool_line, selectedMatch.playedStringNumbers.size, selectedMatch.openPlayCount, selectedMatch.closedPlayCount, selectedMatch.detunedPlayCount), style = MaterialTheme.typography.bodySmall)
            Text(text = stringResource(Res.string.overview_chords_playback_label), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChordPlaybackMode.entries.forEach { mode -> FilterChip(selected = mode == playbackMode, onClick = { onPlaybackModeSelected(mode) }, label = { Text(chordPlaybackModeLabel(mode)) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3, 4).forEach { count ->
                    FilterChip(selected = count == chordVoicingNoteCount, onClick = { onChordVoicingNoteCountChanged(count) }, label = { Text(stringResource(Res.string.overview_chords_notes_count_chip, count)) })
                }
            }
            OutlinedButton(onClick = onPlaySelectedChord, enabled = selectedChordStrings.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.overview_chords_action_sound_chord))
            }
            if (selectedMatch.usesDetunedStrings) {
                val stableChordLabel = suggestedNonDetunedChord?.definition?.let { chordDefinitionLabel(it) } ?: stringResource(Res.string.value_none_found)
                Text(text = stringResource(Res.string.overview_chords_detuned_warning, stableChordLabel), style = MaterialTheme.typography.bodySmall, color = KoraDetunedColor)
                if (suggestedNonDetunedChord != null) {
                    OutlinedButton(onClick = { onChordSelected(suggestedNonDetunedChord.definition) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.overview_chords_action_use_stable_alternative))
                    }
                }
            }
            Text(text = stringResource(Res.string.overview_chords_exercise_note), style = MaterialTheme.typography.bodySmall)
            Text(text = stringResource(Res.string.overview_chords_diagram_label), style = MaterialTheme.typography.labelMedium)
            ChordPictorialKoraDiagram(rows = rows, chordStringNumbers = selectedChordStringNumbers, playingStringNumbers = playingStringNumbers, onStringTouched = onStringTouched, showLeverInfo = showLeverInfo)
            SideColumnHeader()
            repeat(rowCount) { index ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val lr = leftRows.getOrNull(index)
                    if (lr != null) ChordSideCell(lr, pitchShiftByString, lr.stringNumber in selectedChordStringNumbers, lr.stringNumber in playingStringNumbers, onStringTouched, showLeverInfo, Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f))
                    val rr = rightRows.getOrNull(index)
                    if (rr != null) ChordSideCell(rr, pitchShiftByString, rr.stringNumber in selectedChordStringNumbers, rr.stringNumber in playingStringNumbers, onStringTouched, showLeverInfo, Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(Res.string.overview_chords_closest_title), style = MaterialTheme.typography.titleSmall)
            bestMatches.forEach { match ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = chordDefinitionLabel(match.definition), style = MaterialTheme.typography.bodyMedium)
                        Text(text = stringResource(Res.string.overview_chords_match_notes_play_line, match.definition.chordNotes.joinToString(" ") { it.symbol }, match.playedStringNumbers.size), style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = if (match.usesDetunedStrings) stringResource(Res.string.overview_chords_match_includes_detuned) else stringResource(Res.string.overview_chords_match_no_detuned),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (match.usesDetunedStrings) KoraDetunedColor else MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(onClick = { onChordSelected(match.definition) }) { Text(stringResource(Res.string.overview_chords_action_use)) }
                }
            }
        }
    }
}

@Composable
private fun ChordExerciseOverview(
    selectedQuality: ChordQuality,
    circleExerciseStartRoot: NoteName,
    onCircleExerciseStartRootChanged: (NoteName) -> Unit,
    nextCircleExerciseRoot: NoteName,
    onRunCircleExerciseStep: () -> Unit,
    exerciseChoiceMode: ExerciseChoiceMode,
    onExerciseChoiceModeSelected: (ExerciseChoiceMode) -> Unit,
    isTimedExerciseRunning: Boolean,
    onTimedExerciseRunningChanged: (Boolean) -> Unit,
    timedExerciseChordIntervalBeats: Int,
    onTimedExerciseChordIntervalBeatsChanged: (Int) -> Unit,
    timedExerciseTargetRoot: NoteName?,
    timedExerciseChoiceRoots: List<NoteName>,
    timedExerciseDueBeat: Int,
    timedExerciseBeatsUntilDue: Int,
    onRootSelected: (NoteName) -> Unit,
    metronomeBpm: Int,
    onMetronomeBpmChanged: (Int) -> Unit,
    metronomeTimeSignature: MetronomeTimeSignature,
    onMetronomeTimeSignatureChanged: (MetronomeTimeSignature) -> Unit,
    metronomeEnabledBeats: Set<Int>,
    onMetronomeBeatToggled: (Int) -> Unit,
    metronomeSound: MetronomeSoundOption,
    onMetronomeSoundChanged: (MetronomeSoundOption) -> Unit,
    metronomeVolumePercent: Float,
    onMetronomeVolumePercentChanged: (Float) -> Unit,
    isMetronomeRunning: Boolean,
    onMetronomeRunningChanged: (Boolean) -> Unit,
    metronomeCurrentBeat: Int
) {
    var isTimeSignatureMenuExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(Res.string.overview_exercise_title), style = MaterialTheme.typography.titleSmall)
            Text(text = stringResource(Res.string.overview_exercise_order_note), style = MaterialTheme.typography.bodySmall)
            Text(text = stringResource(Res.string.overview_exercise_start_key_label), style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CIRCLE_OF_FIFTHS_ORDER) { note ->
                    FilterChip(selected = note == circleExerciseStartRoot, onClick = { onCircleExerciseStartRootChanged(note) }, label = { Text(circleOfFifthsLabel(note)) })
                }
            }
            Text(text = stringResource(Res.string.overview_exercise_choice_mode_label), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseChoiceMode.entries.forEach { mode ->
                    FilterChip(selected = mode == exerciseChoiceMode, onClick = { onExerciseChoiceModeSelected(mode) }, label = { Text(exerciseChoiceModeLabel(mode)) })
                }
            }
            CircleOfFifthsExerciseDiagram(startRoot = circleExerciseStartRoot, nextRoot = timedExerciseTargetRoot ?: nextCircleExerciseRoot, choiceRoots = timedExerciseChoiceRoots)
            if (timedExerciseTargetRoot != null) {
                Text(
                    text = pluralStringResource(Res.plurals.overview_exercise_suggested_next, timedExerciseBeatsUntilDue, circleOfFifthsLabel(timedExerciseTargetRoot), chordQualityLabel(selectedQuality), timedExerciseBeatsUntilDue, timedExerciseDueBeat),
                    style = MaterialTheme.typography.bodySmall
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(timedExerciseChoiceRoots) { choiceRoot ->
                        FilterChip(
                            selected = choiceRoot == timedExerciseTargetRoot, onClick = { onRootSelected(choiceRoot) },
                            label = { Text(stringResource(Res.string.overview_exercise_choice_item, circleOfFifthsLabel(choiceRoot), chordQualityLabel(selectedQuality))) }
                        )
                    }
                }
            }
            Text(text = stringResource(Res.string.overview_exercise_next_step, circleOfFifthsLabel(nextCircleExerciseRoot), chordQualityLabel(selectedQuality)), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRunCircleExerciseStep, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.overview_exercise_action_next_fifth)) }
            Text(text = stringResource(Res.string.overview_exercise_timed_interval_label), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3, 4).forEach { beats ->
                    FilterChip(
                        selected = beats == timedExerciseChordIntervalBeats, onClick = { onTimedExerciseChordIntervalBeatsChanged(beats) },
                        label = { Text(pluralStringResource(Res.plurals.overview_exercise_interval_chip, beats, beats)) }
                    )
                }
            }
            OutlinedButton(onClick = { onTimedExerciseRunningChanged(!isTimedExerciseRunning) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (isTimedExerciseRunning) stringResource(Res.string.overview_exercise_action_stop_timed) else stringResource(Res.string.overview_exercise_action_start_timed))
            }
            Text(text = stringResource(Res.string.overview_exercise_timed_note), style = MaterialTheme.typography.bodySmall)
            Text(text = stringResource(Res.string.overview_metronome_title), style = MaterialTheme.typography.labelMedium)
            Text(text = stringResource(Res.string.overview_metronome_bpm_line, metronomeBpm), style = MaterialTheme.typography.bodySmall)
            Slider(value = metronomeBpm.toFloat(), onValueChange = { onMetronomeBpmChanged(it.roundToInt()) }, valueRange = 40f..250f)
            Text(text = stringResource(Res.string.overview_metronome_time_signature_label), style = MaterialTheme.typography.labelMedium)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { isTimeSignatureMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(metronomeTimeSignature.label) }
                DropdownMenu(expanded = isTimeSignatureMenuExpanded, onDismissRequest = { isTimeSignatureMenuExpanded = false }) {
                    METRONOME_TIME_SIGNATURES.forEach { sig ->
                        DropdownMenuItem(text = { Text(sig.label) }, onClick = { isTimeSignatureMenuExpanded = false; onMetronomeTimeSignatureChanged(sig) })
                    }
                }
            }
            Text(text = stringResource(Res.string.overview_metronome_beats_to_click_label), style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((1..metronomeTimeSignature.numerator).toList()) { beat ->
                    FilterChip(selected = beat in metronomeEnabledBeats, onClick = { onMetronomeBeatToggled(beat) },
                        label = { Text(if (beat == metronomeCurrentBeat) "$beat *" else beat.toString()) })
                }
            }
            Text(text = stringResource(Res.string.overview_metronome_sound_label), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetronomeSoundOption.entries.forEach { sound ->
                    FilterChip(selected = sound == metronomeSound, onClick = { onMetronomeSoundChanged(sound) }, label = { Text(metronomeSoundOptionLabel(sound)) })
                }
            }
            Text(text = stringResource(Res.string.overview_metronome_volume_label, metronomeVolumePercent.roundToInt()), style = MaterialTheme.typography.labelMedium)
            Slider(value = metronomeVolumePercent, onValueChange = onMetronomeVolumePercentChanged, valueRange = 30f..180f)
            OutlinedButton(onClick = { onMetronomeRunningChanged(!isMetronomeRunning) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (isMetronomeRunning) stringResource(Res.string.overview_metronome_action_stop) else stringResource(Res.string.overview_metronome_action_start))
            }
        }
    }
}

@Composable
private fun CircleOfFifthsExerciseDiagram(startRoot: NoteName, nextRoot: NoteName, choiceRoots: List<NoteName>) {
    val colorScheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = stringResource(Res.string.overview_exercise_diagram_title), style = MaterialTheme.typography.bodySmall)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .sizeIn(maxWidth = 380.dp, maxHeight = 380.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(startRoot, nextRoot, choiceRoots) { consumeAllTouches() }
            ) {
                if (size.minDimension <= 0f) return@Canvas
                val center = Offset(size.width * 0.5f, size.height * 0.5f)
                val ringRadius = size.minDimension * 0.38f
                drawCircle(color = colorScheme.outlineVariant, radius = ringRadius, center = center, style = Stroke(width = size.minDimension * 0.006f))

                CIRCLE_OF_FIFTHS_ORDER.forEachIndexed { index, note ->
                    val angleRadians = (-90.0 + (index * 30.0)) * PI / 180.0
                    val markerCenter = Offset(center.x + cos(angleRadians).toFloat() * ringRadius, center.y + sin(angleRadians).toFloat() * ringRadius)
                    val isStart = note == startRoot; val isNext = note == nextRoot; val isChoice = note in choiceRoots
                    val markerColor = when { isNext -> KoraClosedLeverColor; isStart -> KoraOpenLeverColor; isChoice -> colorScheme.tertiary; else -> colorScheme.surfaceVariant }
                    val markerRadius = when { isNext -> size.minDimension * 0.056f; isStart -> size.minDimension * 0.048f; else -> size.minDimension * 0.042f }
                    drawCircle(color = markerColor, radius = markerRadius, center = markerCenter)
                    if (isNext || isStart) {
                        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = markerRadius + size.minDimension * 0.006f, center = markerCenter, style = Stroke(width = size.minDimension * 0.006f))
                    }
                    val textColor = if (isNext || isStart) Color.White else colorScheme.onSurface
                    val textResult = textMeasurer.measure(circleOfFifthsLabel(note), TextStyle(color = textColor, fontSize = 11.sp, textAlign = TextAlign.Center))
                    drawText(textResult, topLeft = Offset(markerCenter.x - textResult.size.width / 2f, markerCenter.y - textResult.size.height / 2f))
                }
            }
            } // end Box
            Text(text = stringResource(Res.string.overview_exercise_diagram_legend), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ChordPictorialKoraDiagram(
    rows: List<PegCorrectStringResult>,
    chordStringNumbers: Set<Int>,
    playingStringNumbers: Set<Int>,
    onStringTouched: (PegCorrectStringResult) -> Unit,
    showLeverInfo: Boolean
) {
    val leftRows = rows.filter { it.role.side == StringSide.LEFT }.sortedBy { it.role.positionFromLow }
    val rightRows = rows.filter { it.role.side == StringSide.RIGHT }.sortedBy { it.role.positionFromLow }
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val maxTapDistancePx = with(density) { 20.dp.toPx() }
    var diagramSize by remember { mutableStateOf(IntSize.Zero) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = stringResource(Res.string.overview_chords_diagram_title), style = MaterialTheme.typography.bodySmall)
            Box(
                modifier = Modifier.fillMaxWidth().height(StickyDiagramHeight).clipToBounds()
                    .onSizeChanged { size -> diagramSize = size }
                    .pointerInput(leftRows, rightRows, diagramSize) {
                        detectTapGestures { tapOffset ->
                            resolveDiagramHit(tapOffset, diagramSize, 1f, leftRows, rightRows, maxTapDistancePx)?.let(onStringTouched)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawKoraBody(colors)
                    drawKoraDiagram(leftRows = leftRows, rightRows = rightRows, colors = colors, activeStringNumbers = playingStringNumbers, showLeverInfo = showLeverInfo, noteLabelsVisible = false, pitchShiftByString = emptyMap())
                    drawChordMarkers(segments = buildDiagramStringSegments(leftRows, rightRows, size), chordStringNumbers = chordStringNumbers)
                }
            }
        }
    }
}

private fun DrawScope.drawChordMarkers(segments: List<DiagramStringSegment>, chordStringNumbers: Set<Int>) {
    val markerRadius = size.width * 0.019f; val markerOutline = size.width * 0.0045f; val markerHalo = markerRadius + size.width * 0.004f
    segments.forEach { segment ->
        if (segment.row.stringNumber !in chordStringNumbers) return@forEach
        val center = Offset((segment.peg.x + segment.bridge.x) * 0.5f, (segment.peg.y + segment.bridge.y) * 0.5f)
        drawCircle(Color.White.copy(alpha = 0.85f), markerHalo, center)
        drawCircle(KoraClosedLeverColor, markerRadius, center)
        drawCircle(Color.White.copy(alpha = 0.95f), markerRadius, center, style = Stroke(width = markerOutline))
    }
}

@Composable
private fun ChordSideCell(row: PegCorrectStringResult, pitchShiftByString: Map<Int, Int>, shouldPlay: Boolean, isSounding: Boolean, onStringTouched: (PegCorrectStringResult) -> Unit, showLeverInfo: Boolean, modifier: Modifier = Modifier) {
    val baseColor = when {
        row.selectedLeverState == LeverState.CLOSED && row.pegRetuneRequired -> KoraCombinedChangesColor.copy(alpha = 0.24f)
        row.pegRetuneRequired -> KoraDetunedColor.copy(alpha = 0.24f)
        !showLeverInfo -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        row.selectedLeverState == LeverState.CLOSED -> KoraLeverClosedStringColor.copy(alpha = 0.22f)
        else -> KoraOpenLeverColor.copy(alpha = 0.22f)
    }
    Card(
        modifier = modifier.clickable { onStringTouched(row) },
        colors = CardDefaults.cardColors(containerColor = if (isSounding) MaterialTheme.colorScheme.secondaryContainer else baseColor),
        border = BorderStroke(1.dp, if (shouldPlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = buildString {
                val shift = (pitchShiftByString[row.stringNumber] ?: 0).coerceIn(-1, 1)
                append(if (shouldPlay) "X " else "- ")
                append("${row.role.asLabel()} (S${row.stringNumber})\n")
                append(displayPitchLabel(row.selectedPitch, shift, true))
                if (showLeverInfo) append("  ${row.selectedLeverState.name}")
                if (row.pegRetuneRequired) append("  ${stringResource(Res.string.value_detuned)}")
            },
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(12.dp).background(color = color, shape = MaterialTheme.shapes.small))
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TableOverview(rows: List<PegCorrectStringResult>, pitchShiftByString: Map<Int, Int>, playingStringNumbers: Set<Int>, onStringTouched: (PegCorrectStringResult) -> Unit, showLeverInfo: Boolean) {
    val leftRows = rows.filter { it.role.side == StringSide.LEFT }.sortedBy { it.role.positionFromLow }
    val rightRows = rows.filter { it.role.side == StringSide.RIGHT }.sortedBy { it.role.positionFromLow }
    val rowCount = maxOf(leftRows.size, rightRows.size)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(Res.string.overview_table_title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(Res.string.scale_engine_two_column_note), style = MaterialTheme.typography.bodySmall)
            SideColumnHeader()
            repeat(rowCount) { index ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val lr = leftRows.getOrNull(index)
                    if (lr != null) SideCell(formatOverviewRow(lr, showLeverInfo, pitchShiftByString), lr.stringNumber in playingStringNumbers, { onStringTouched(lr) }, Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f))
                    val rr = rightRows.getOrNull(index)
                    if (rr != null) SideCell(formatOverviewRow(rr, showLeverInfo, pitchShiftByString), rr.stringNumber in playingStringNumbers, { onStringTouched(rr) }, Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SideCell(text: String, isActive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Text(text = text, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun formatOverviewRow(row: PegCorrectStringResult, showLeverInfo: Boolean, pitchShiftByString: Map<Int, Int>): String {
    val pegIndicator = if (row.pegRetuneRequired) signed(row.pegRetuneSemitones) else "0"
    val shift = (pitchShiftByString[row.stringNumber] ?: 0).coerceIn(-1, 1)
    val pitchLabel = displayPitchLabel(row.selectedPitch, shift, true)
    return buildString {
        append("${row.role.asLabel()} (S${row.stringNumber})\n")
        if (showLeverInfo) append(stringResource(Res.string.scale_engine_peg_row_target_lever, pitchLabel, row.selectedLeverState.name))
        else append(stringResource(Res.string.scale_engine_peg_tuning_row_target, pitchLabel))
        append("\n")
        append(stringResource(Res.string.scale_engine_row_int_peg, signed(row.selectedIntonationCents), pegIndicator))
    }
}

private fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()

private fun displayPitchLabel(effectivePitch: Pitch, semitoneShift: Int, includeOctave: Boolean): String {
    val shift = semitoneShift.coerceIn(-1, 1)
    val basePitch = effectivePitch.plusSemitones(-shift)
    val baseSymbol = basePitch.note.symbol
    val shiftedSymbol = when (shift) {
        1 -> "${baseSymbol}#"
        -1 -> if (baseSymbol.endsWith("#")) baseSymbol.dropLast(1) else "${baseSymbol}b"
        else -> baseSymbol
    }
    return if (includeOctave) "${shiftedSymbol}${basePitch.octave}" else shiftedSymbol
}

private const val PLUCK_VISUAL_HOLD_MS = 760L
private const val BROKEN_CHORD_STEP_MS = 140L
private const val SPREAD_CHORD_STEP_MS = 120L

private val METRONOME_TIME_SIGNATURES = listOf(
    MetronomeTimeSignature(2, 4), MetronomeTimeSignature(3, 4), MetronomeTimeSignature(4, 4),
    MetronomeTimeSignature(6, 8), MetronomeTimeSignature(9, 9), MetronomeTimeSignature(12, 8)
)

private val CIRCLE_OF_FIFTHS_ORDER = listOf(
    NoteName.C, NoteName.F, NoteName.A_SHARP, NoteName.D_SHARP, NoteName.G_SHARP,
    NoteName.C_SHARP, NoteName.F_SHARP, NoteName.B, NoteName.E, NoteName.A, NoteName.D, NoteName.G
)

private fun spreadOrder(rows: List<PegCorrectStringResult>): List<PegCorrectStringResult> {
    if (rows.size <= 2) return rows
    val ordered = rows.sortedBy { (it.selectedPitch.octave * 12) + it.selectedPitch.note.semitone }.toMutableList()
    val spread = mutableListOf<PegCorrectStringResult>()
    while (ordered.isNotEmpty()) { spread += ordered.removeAt(0); if (ordered.isNotEmpty()) spread += ordered.removeAt(ordered.lastIndex) }
    return spread
}

private fun circleOfFifthsIndexFor(note: NoteName): Int { val i = CIRCLE_OF_FIFTHS_ORDER.indexOf(note); return if (i >= 0) i else 0 }

private fun circleOfFifthsLabel(note: NoteName): String = when (note) {
    NoteName.A_SHARP -> "Bb"; NoteName.D_SHARP -> "Eb"; NoteName.G_SHARP -> "Ab"
    NoteName.C_SHARP -> "Db"; NoteName.F_SHARP -> "F# / Gb"; else -> note.symbol
}

private fun buildExerciseChoices(targetRoot: NoteName, size: Int): List<NoteName> {
    val desired = size.coerceAtLeast(1)
    val distractors = CIRCLE_OF_FIFTHS_ORDER.filter { it != targetRoot }.shuffled().take((desired - 1).coerceAtLeast(0))
    return (distractors + targetRoot).shuffled().take(desired)
}

@Composable
private fun chordQualityLabel(quality: ChordQuality): String = when (quality) {
    ChordQuality.MAJOR -> stringResource(Res.string.chord_quality_major)
    ChordQuality.MINOR -> stringResource(Res.string.chord_quality_minor)
    ChordQuality.DIMINISHED -> stringResource(Res.string.chord_quality_diminished)
    ChordQuality.HALF_DIMINISHED -> stringResource(Res.string.chord_quality_half_diminished)
    ChordQuality.SUS2 -> stringResource(Res.string.chord_quality_sus2)
    ChordQuality.SUS4 -> stringResource(Res.string.chord_quality_sus4)
    ChordQuality.DOMINANT7 -> stringResource(Res.string.chord_quality_dominant7)
    ChordQuality.MAJOR7 -> stringResource(Res.string.chord_quality_major7)
    ChordQuality.MINOR7 -> stringResource(Res.string.chord_quality_minor7)
}

@Composable
private fun chordDefinitionLabel(definition: ChordDefinition): String = "${definition.root.symbol} ${chordQualityLabel(definition.quality)}"

@Composable
private fun metronomeSoundOptionLabel(sound: MetronomeSoundOption): String = when (sound) {
    MetronomeSoundOption.WOOD_SOFT -> stringResource(Res.string.metronome_sound_soft_wood)
    MetronomeSoundOption.WOOD_BLOCK -> stringResource(Res.string.metronome_sound_wood_block)
    MetronomeSoundOption.WOOD_CLICK -> stringResource(Res.string.metronome_sound_bright_click)
}

@Composable
private fun chordPlaybackModeLabel(mode: ChordPlaybackMode): String = when (mode) {
    ChordPlaybackMode.BLOCK -> stringResource(Res.string.overview_chords_playback_block)
    ChordPlaybackMode.BROKEN -> stringResource(Res.string.overview_chords_playback_broken)
    ChordPlaybackMode.SPREAD -> stringResource(Res.string.overview_chords_playback_spread)
}

@Composable
private fun exerciseChoiceModeLabel(mode: ExerciseChoiceMode): String = when (mode) {
    ExerciseChoiceMode.PRESET -> stringResource(Res.string.overview_exercise_choice_preset)
    ExerciseChoiceMode.RANDOM -> stringResource(Res.string.overview_exercise_choice_random)
}
