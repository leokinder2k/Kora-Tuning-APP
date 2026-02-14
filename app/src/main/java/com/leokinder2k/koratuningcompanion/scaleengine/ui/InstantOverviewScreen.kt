package com.leokinder2k.koratuningcompanion.scaleengine.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.KoraTuningMode
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.NoteName
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
import com.leokinder2k.koratuningcompanion.ui.theme.KoraDetunedColor
import com.leokinder2k.koratuningcompanion.ui.theme.KoraOpenLeverColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private enum class OverviewViewMode {
    DIAGRAM,
    TABLE,
    CHORDS,
    EXERCISE
}

private enum class ChordPlaybackMode {
    BLOCK,
    BROKEN,
    SPREAD
}

private enum class ExerciseChoiceMode {
    PRESET,
    RANDOM
}

private data class MetronomeTimeSignature(
    val numerator: Int,
    val denominator: Int
) {
    val label: String = "$numerator/$denominator"
}

private data class DiagramStringSegment(
    val row: PegCorrectStringResult,
    val peg: Offset,
    val bridge: Offset
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun InstantOverviewScreen(
    uiState: ScaleCalculationUiState,
    onRootNoteSelected: (NoteName) -> Unit,
    onScaleTypeSelected: (ScaleType) -> Unit,
    modifier: Modifier = Modifier
) {
    val showLeverInfo = uiState.result.request.instrumentProfile.tuningMode == KoraTuningMode.LEVERED
    var viewMode by rememberSaveable { mutableStateOf(OverviewViewMode.DIAGRAM) }
    var diagramZoom by rememberSaveable { mutableFloatStateOf(1f) }
    var playingStringNumbers by remember(
        uiState.rootNote,
        uiState.scaleType,
        uiState.result.request.instrumentProfile.stringCount
    ) {
        mutableStateOf(emptySet<Int>())
    }
    var selectedChordRoot by rememberSaveable(uiState.rootNote) { mutableStateOf(uiState.rootNote) }
    var selectedChordQuality by rememberSaveable { mutableStateOf(ChordQuality.MAJOR) }
    var chordPlaybackMode by rememberSaveable { mutableStateOf(ChordPlaybackMode.BLOCK) }
    var chordVoicingNoteCount by rememberSaveable { mutableIntStateOf(4) }
    var circleExerciseStartRoot by rememberSaveable { mutableStateOf(NoteName.C) }
    var circleExerciseStepOffset by rememberSaveable { mutableIntStateOf(0) }
    var exerciseChoiceMode by rememberSaveable { mutableStateOf(ExerciseChoiceMode.PRESET) }
    var isTimedExerciseRunning by rememberSaveable { mutableStateOf(false) }
    var timedExerciseStepOffset by rememberSaveable { mutableIntStateOf(0) }
    var timedExerciseChordIntervalBeats by rememberSaveable { mutableIntStateOf(1) }
    var timedExerciseTargetRoot by remember { mutableStateOf<NoteName?>(null) }
    var timedExerciseChoiceRoots by remember { mutableStateOf(emptyList<NoteName>()) }
    var timedExerciseDueTick by remember { mutableLongStateOf(0L) }
    var metronomeBpm by rememberSaveable { mutableIntStateOf(96) }
    var metronomeTimeSignature by remember {
        mutableStateOf(METRONOME_TIME_SIGNATURES.first { signature -> signature.label == "4/4" })
    }
    var metronomeSound by rememberSaveable { mutableStateOf(MetronomeSoundOption.WOOD_SOFT) }
    var isMetronomeRunning by rememberSaveable { mutableStateOf(false) }
    var metronomeCurrentBeat by remember { mutableIntStateOf(0) }
    var metronomeTick by remember { mutableLongStateOf(0L) }
    var metronomeEnabledBeats by remember { mutableStateOf(setOf(1, 3)) }
    var selectedChordToneOffsets by remember(selectedChordQuality) {
        mutableStateOf(selectedChordQuality.tones.map { tone -> tone.semitoneOffset }.toSet())
    }

    val rows = uiState.result.pegCorrectTable
    val scrollState = rememberScrollState()
    val tonePlayer = remember { PluckedStringPlayer() }
    val metronomePlayer = remember { MetronomeClickPlayer() }
    val coroutineScope = rememberCoroutineScope()
    val playGenerationByString = remember { mutableStateMapOf<Int, Int>() }
    val lastTapAtByString = remember { mutableStateMapOf<Int, Long>() }

    LaunchedEffect(
        uiState.rootNote,
        uiState.scaleType,
        uiState.result.request.instrumentProfile.stringCount
    ) {
        playingStringNumbers = emptySet()
        playGenerationByString.clear()
        lastTapAtByString.clear()
    }
    LaunchedEffect(metronomeTimeSignature) {
        val trimmed = metronomeEnabledBeats
            .filter { beat -> beat in 1..metronomeTimeSignature.numerator }
            .toSet()
        metronomeEnabledBeats = if (trimmed.isEmpty()) {
            setOf(1)
        } else {
            trimmed
        }
    }
    LaunchedEffect(
        isMetronomeRunning,
        metronomeBpm,
        metronomeTimeSignature,
        metronomeEnabledBeats,
        metronomeSound
    ) {
        if (!isMetronomeRunning) {
            metronomePlayer.stopAll()
            metronomeCurrentBeat = 0
            metronomeTick = 0L
            return@LaunchedEffect
        }

        var beat = 0
        val stepDelayMs = (60_000.0 / metronomeBpm.toDouble())
            .roundToInt()
            .coerceAtLeast(50)
            .toLong()
        while (isActive && isMetronomeRunning) {
            beat = if (beat >= metronomeTimeSignature.numerator) {
                1
            } else {
                beat + 1
            }
            metronomeCurrentBeat = beat
            metronomeTick += 1L
            if (beat in metronomeEnabledBeats) {
                metronomePlayer.play(
                    sound = metronomeSound,
                    accent = beat == 1
                )
            }
            delay(stepDelayMs)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tonePlayer.release()
            metronomePlayer.release()
        }
    }

    val selectedChordDefinition = remember(selectedChordRoot, selectedChordQuality) {
        ChordDefinition(
            root = selectedChordRoot,
            quality = selectedChordQuality
        )
    }
    val selectedChordMatch = remember(rows, selectedChordDefinition) {
        ChordPlanner.analyze(
            rows = rows,
            definition = selectedChordDefinition
        )
    }
    val suggestedNonDetunedChord = remember(
        rows,
        selectedChordDefinition,
        selectedChordMatch.usesDetunedStrings
    ) {
        if (!selectedChordMatch.usesDetunedStrings) {
            null
        } else {
            ChordPlanner.suggestClosestWithoutDetune(
                rows = rows,
                desired = selectedChordDefinition
            )
        }
    }
    val bestChordMatches = remember(rows) {
        ChordPlanner.bestMatches(
            rows = rows,
            limit = 10
        )
    }
    val selectedChordStrings = remember(
        rows,
        selectedChordDefinition,
        selectedChordToneOffsets,
        chordVoicingNoteCount
    ) {
        ChordPlanner.chooseChordStrings(
            rows = rows,
            definition = selectedChordDefinition,
            toneOffsetsToInclude = selectedChordToneOffsets,
            maxNotes = chordVoicingNoteCount
        )
    }

    val playStringPluck: (PegCorrectStringResult) -> Unit = { row ->
        val stringNumber = row.stringNumber
        val frequency = TunerTargetMatcher.pitchToFrequencyHz(
            pitch = row.selectedPitch,
            centsOffset = row.selectedIntonationCents
        )
        tonePlayer.play(
            stringNumber = stringNumber,
            frequencyHz = frequency
        )
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
    val playChord: (List<PegCorrectStringResult>, ChordPlaybackMode) -> Unit = { chordRows, mode ->
        val limitedRows = chordRows.take(4)
        when (mode) {
            ChordPlaybackMode.BLOCK -> {
                limitedRows.forEach { row -> playStringPluck(row) }
            }

            ChordPlaybackMode.BROKEN -> {
                coroutineScope.launch {
                    limitedRows.forEachIndexed { index, row ->
                        playStringPluck(row)
                        if (index != limitedRows.lastIndex) {
                            delay(BROKEN_CHORD_STEP_MS)
                        }
                    }
                }
            }

            ChordPlaybackMode.SPREAD -> {
                val spreadRows = spreadOrder(limitedRows)
                coroutineScope.launch {
                    spreadRows.forEachIndexed { index, row ->
                        playStringPluck(row)
                        if (index != spreadRows.lastIndex) {
                            delay(SPREAD_CHORD_STEP_MS)
                        }
                    }
                }
            }
        }
    }
    val runCircleExerciseStep: () -> Unit = {
        val startIndex = circleOfFifthsIndexFor(circleExerciseStartRoot)
        val root = CIRCLE_OF_FIFTHS_ORDER[(startIndex + circleExerciseStepOffset) % CIRCLE_OF_FIFTHS_ORDER.size]
        selectedChordRoot = root
        val exerciseDefinition = ChordDefinition(
            root = root,
            quality = selectedChordQuality
        )
        val exerciseRows = ChordPlanner.chooseChordStrings(
            rows = rows,
            definition = exerciseDefinition,
            toneOffsetsToInclude = selectedChordToneOffsets,
            maxNotes = chordVoicingNoteCount
        )
        playChord(exerciseRows, chordPlaybackMode)
        circleExerciseStepOffset = (circleExerciseStepOffset + 1) % CIRCLE_OF_FIFTHS_ORDER.size
    }
    val scheduleTimedExercisePrompt: (Long) -> Unit = { currentTick ->
        val nextRoot = when (exerciseChoiceMode) {
            ExerciseChoiceMode.PRESET -> {
                val startIndex = circleOfFifthsIndexFor(circleExerciseStartRoot)
                val root = CIRCLE_OF_FIFTHS_ORDER[
                    (startIndex + timedExerciseStepOffset) % CIRCLE_OF_FIFTHS_ORDER.size
                ]
                timedExerciseStepOffset = (timedExerciseStepOffset + 1) % CIRCLE_OF_FIFTHS_ORDER.size
                root
            }

            ExerciseChoiceMode.RANDOM -> CIRCLE_OF_FIFTHS_ORDER.random()
        }

        timedExerciseTargetRoot = nextRoot
        timedExerciseChoiceRoots = buildExerciseChoices(nextRoot, size = 4)
        timedExerciseDueTick = currentTick + timedExerciseChordIntervalBeats.coerceIn(1, 4)
    }

    LaunchedEffect(
        isTimedExerciseRunning,
        exerciseChoiceMode,
        circleExerciseStartRoot,
        metronomeTimeSignature
    ) {
        if (!isTimedExerciseRunning) {
            timedExerciseTargetRoot = null
            timedExerciseChoiceRoots = emptyList()
            timedExerciseDueTick = 0L
            return@LaunchedEffect
        }
        if (exerciseChoiceMode == ExerciseChoiceMode.PRESET) {
            timedExerciseStepOffset = 0
        }
        scheduleTimedExercisePrompt(metronomeTick)
    }

    LaunchedEffect(
        metronomeTick,
        isTimedExerciseRunning,
        timedExerciseTargetRoot,
        timedExerciseDueTick,
        timedExerciseChordIntervalBeats,
        selectedChordQuality,
        selectedChordToneOffsets,
        chordVoicingNoteCount,
        chordPlaybackMode,
        rows
    ) {
        if (!isTimedExerciseRunning || metronomeTick == 0L) {
            return@LaunchedEffect
        }
        if (timedExerciseTargetRoot == null) {
            scheduleTimedExercisePrompt(metronomeTick)
            return@LaunchedEffect
        }
        if (metronomeTick < timedExerciseDueTick) {
            return@LaunchedEffect
        }

        val targetRoot = requireNotNull(timedExerciseTargetRoot)
        selectedChordRoot = targetRoot
        val definition = ChordDefinition(
            root = targetRoot,
            quality = selectedChordQuality
        )
        val exerciseRows = ChordPlanner.chooseChordStrings(
            rows = rows,
            definition = definition,
            toneOffsetsToInclude = selectedChordToneOffsets,
            maxNotes = chordVoicingNoteCount
        )
        playChord(exerciseRows, chordPlaybackMode)
        scheduleTimedExercisePrompt(metronomeTick)
    }

    val timedExerciseDueBeatInBar = if (timedExerciseDueTick <= 0L) {
        1
    } else {
        (((timedExerciseDueTick - 1L) % metronomeTimeSignature.numerator.toLong()) + 1L).toInt()
    }
    val timedExerciseBeatsUntilDue = (timedExerciseDueTick - metronomeTick)
        .coerceAtLeast(0L)
        .toInt()

    val onStringTouched: (PegCorrectStringResult) -> Unit = { row ->
        val stringNumber = row.stringNumber
        val nowMs = SystemClock.elapsedRealtime()
        val previousTapMs = lastTapAtByString[stringNumber]

        val shouldStop = previousTapMs != null &&
            (nowMs - previousTapMs) <= DOUBLE_TAP_STOP_WINDOW_MS &&
            stringNumber in playingStringNumbers

        if (shouldStop) {
            tonePlayer.stop(stringNumber)
            playingStringNumbers = playingStringNumbers - stringNumber
            playGenerationByString.remove(stringNumber)
            lastTapAtByString.remove(stringNumber)
        } else {
            lastTapAtByString[stringNumber] = nowMs
            playStringPluck(row)
        }
    }
    val isScreenScrollEnabled = !(viewMode == OverviewViewMode.DIAGRAM && diagramZoom > 1f)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_instant_overview)) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(
                    state = scrollState,
                    enabled = isScreenScrollEnabled
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = uiState.profileStatus,
                style = MaterialTheme.typography.bodyMedium
            )

            OverviewSelectionControls(
                rootNote = uiState.rootNote,
                scaleType = uiState.scaleType,
                onRootNoteSelected = onRootNoteSelected,
                onScaleTypeSelected = onScaleTypeSelected
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = viewMode == OverviewViewMode.DIAGRAM,
                    onClick = { viewMode = OverviewViewMode.DIAGRAM },
                    label = { Text(stringResource(R.string.overview_view_diagram)) }
                )
                FilterChip(
                    selected = viewMode == OverviewViewMode.TABLE,
                    onClick = { viewMode = OverviewViewMode.TABLE },
                    label = { Text(stringResource(R.string.overview_view_table)) }
                )
                FilterChip(
                    selected = viewMode == OverviewViewMode.CHORDS,
                    onClick = { viewMode = OverviewViewMode.CHORDS },
                    label = { Text(stringResource(R.string.overview_view_chords)) }
                )
                FilterChip(
                    selected = viewMode == OverviewViewMode.EXERCISE,
                    onClick = { viewMode = OverviewViewMode.EXERCISE },
                    label = { Text(stringResource(R.string.overview_view_exercise)) }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (playingStringNumbers.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            tonePlayer.stopAll()
                            playingStringNumbers = emptySet()
                            playGenerationByString.clear()
                            lastTapAtByString.clear()
                        }
                    ) {
                        Text(stringResource(R.string.overview_action_stop_all_tones))
                    }
                }
            }

            when (viewMode) {
                OverviewViewMode.DIAGRAM -> DiagramOverview(
                    rows = rows,
                    playingStringNumbers = playingStringNumbers,
                    onStringTouched = onStringTouched,
                    onPlayAllStrings = {
                        rows.forEach { row -> playStringPluck(row) }
                    },
                    showLeverInfo = showLeverInfo,
                    diagramZoom = diagramZoom,
                    onDiagramZoomChanged = { value -> diagramZoom = value }
                )
                OverviewViewMode.TABLE -> TableOverview(
                    rows = rows,
                    playingStringNumbers = playingStringNumbers,
                    onStringTouched = onStringTouched,
                    showLeverInfo = showLeverInfo
                )
                OverviewViewMode.CHORDS -> ChordOverview(
                    rows = rows,
                    selectedRoot = selectedChordRoot,
                    onRootSelected = { note -> selectedChordRoot = note },
                    selectedQuality = selectedChordQuality,
                    onQualitySelected = { quality -> selectedChordQuality = quality },
                    selectedMatch = selectedChordMatch,
                    selectedChordStrings = selectedChordStrings,
                    selectedToneOffsets = selectedChordToneOffsets,
                    onToneOffsetToggled = { offset ->
                        selectedChordToneOffsets = if (offset in selectedChordToneOffsets) {
                            val next = selectedChordToneOffsets - offset
                            if (next.isEmpty()) {
                                selectedChordToneOffsets
                            } else {
                                next
                            }
                        } else {
                            selectedChordToneOffsets + offset
                        }
                    },
                    playbackMode = chordPlaybackMode,
                    onPlaybackModeSelected = { mode -> chordPlaybackMode = mode },
                    chordVoicingNoteCount = chordVoicingNoteCount,
                    onChordVoicingNoteCountChanged = { count ->
                        chordVoicingNoteCount = count.coerceIn(1, 4)
                    },
                    onPlaySelectedChord = {
                        playChord(selectedChordStrings, chordPlaybackMode)
                    },
                    showLeverInfo = showLeverInfo,
                    circleExerciseStartRoot = circleExerciseStartRoot,
                    onCircleExerciseStartRootChanged = { note ->
                        circleExerciseStartRoot = note
                        circleExerciseStepOffset = 0
                    },
                    nextCircleExerciseRoot = CIRCLE_OF_FIFTHS_ORDER[
                        (circleOfFifthsIndexFor(circleExerciseStartRoot) + circleExerciseStepOffset) % CIRCLE_OF_FIFTHS_ORDER.size
                    ],
                    onRunCircleExerciseStep = runCircleExerciseStep,
                    exerciseChoiceMode = exerciseChoiceMode,
                    onExerciseChoiceModeSelected = { mode ->
                        exerciseChoiceMode = mode
                    },
                    isTimedExerciseRunning = isTimedExerciseRunning,
                    onTimedExerciseRunningChanged = { running ->
                        isTimedExerciseRunning = running
                        if (running && !isMetronomeRunning) {
                            isMetronomeRunning = true
                        }
                    },
                    timedExerciseChordIntervalBeats = timedExerciseChordIntervalBeats,
                    onTimedExerciseChordIntervalBeatsChanged = { beats ->
                        timedExerciseChordIntervalBeats = beats.coerceIn(1, 4)
                    },
                    timedExerciseTargetRoot = timedExerciseTargetRoot,
                    timedExerciseChoiceRoots = timedExerciseChoiceRoots,
                    timedExerciseDueBeat = timedExerciseDueBeatInBar,
                    timedExerciseBeatsUntilDue = timedExerciseBeatsUntilDue,
                    metronomeBpm = metronomeBpm,
                    onMetronomeBpmChanged = { bpm ->
                        metronomeBpm = bpm.coerceIn(40, 250)
                    },
                    metronomeTimeSignature = metronomeTimeSignature,
                    onMetronomeTimeSignatureChanged = { signature ->
                        metronomeTimeSignature = signature
                    },
                    metronomeEnabledBeats = metronomeEnabledBeats,
                    onMetronomeBeatToggled = { beat ->
                        metronomeEnabledBeats = if (beat in metronomeEnabledBeats) {
                            val next = metronomeEnabledBeats - beat
                            if (next.isEmpty()) metronomeEnabledBeats else next
                        } else {
                            metronomeEnabledBeats + beat
                        }
                    },
                    metronomeSound = metronomeSound,
                    onMetronomeSoundChanged = { sound -> metronomeSound = sound },
                    isMetronomeRunning = isMetronomeRunning,
                    onMetronomeRunningChanged = { running -> isMetronomeRunning = running },
                    metronomeCurrentBeat = metronomeCurrentBeat,
                    suggestedNonDetunedChord = suggestedNonDetunedChord,
                    bestMatches = bestChordMatches,
                    onChordSelected = { definition ->
                        selectedChordRoot = definition.root
                        selectedChordQuality = definition.quality
                        circleExerciseStepOffset = 0
                    },
                    playingStringNumbers = playingStringNumbers,
                    onStringTouched = onStringTouched
                )
                OverviewViewMode.EXERCISE -> ChordExerciseOverview(
                    selectedQuality = selectedChordQuality,
                    circleExerciseStartRoot = circleExerciseStartRoot,
                    onCircleExerciseStartRootChanged = { note ->
                        circleExerciseStartRoot = note
                        circleExerciseStepOffset = 0
                    },
                    nextCircleExerciseRoot = CIRCLE_OF_FIFTHS_ORDER[
                        (circleOfFifthsIndexFor(circleExerciseStartRoot) + circleExerciseStepOffset) % CIRCLE_OF_FIFTHS_ORDER.size
                    ],
                    onRunCircleExerciseStep = runCircleExerciseStep,
                    exerciseChoiceMode = exerciseChoiceMode,
                    onExerciseChoiceModeSelected = { mode ->
                        exerciseChoiceMode = mode
                    },
                    isTimedExerciseRunning = isTimedExerciseRunning,
                    onTimedExerciseRunningChanged = { running ->
                        isTimedExerciseRunning = running
                        if (running && !isMetronomeRunning) {
                            isMetronomeRunning = true
                        }
                    },
                    timedExerciseChordIntervalBeats = timedExerciseChordIntervalBeats,
                    onTimedExerciseChordIntervalBeatsChanged = { beats ->
                        timedExerciseChordIntervalBeats = beats.coerceIn(1, 4)
                    },
                    timedExerciseTargetRoot = timedExerciseTargetRoot,
                    timedExerciseChoiceRoots = timedExerciseChoiceRoots,
                    timedExerciseDueBeat = timedExerciseDueBeatInBar,
                    timedExerciseBeatsUntilDue = timedExerciseBeatsUntilDue,
                    onRootSelected = { note -> selectedChordRoot = note },
                    metronomeBpm = metronomeBpm,
                    onMetronomeBpmChanged = { bpm ->
                        metronomeBpm = bpm.coerceIn(40, 250)
                    },
                    metronomeTimeSignature = metronomeTimeSignature,
                    onMetronomeTimeSignatureChanged = { signature ->
                        metronomeTimeSignature = signature
                    },
                    metronomeEnabledBeats = metronomeEnabledBeats,
                    onMetronomeBeatToggled = { beat ->
                        metronomeEnabledBeats = if (beat in metronomeEnabledBeats) {
                            val next = metronomeEnabledBeats - beat
                            if (next.isEmpty()) metronomeEnabledBeats else next
                        } else {
                            metronomeEnabledBeats + beat
                        }
                    },
                    metronomeSound = metronomeSound,
                    onMetronomeSoundChanged = { sound -> metronomeSound = sound },
                    isMetronomeRunning = isMetronomeRunning,
                    onMetronomeRunningChanged = { running -> isMetronomeRunning = running },
                    metronomeCurrentBeat = metronomeCurrentBeat
                )
            }
        }
    }
}

@Composable
private fun OverviewSelectionControls(
    rootNote: NoteName,
    scaleType: ScaleType,
    onRootNoteSelected: (NoteName) -> Unit,
    onScaleTypeSelected: (ScaleType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.scale_root_note_label),
            style = MaterialTheme.typography.titleMedium
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(NoteName.entries) { note ->
                FilterChip(
                    selected = note == rootNote,
                    onClick = { onRootNoteSelected(note) },
                    label = { Text(note.symbol) }
                )
            }
        }

        Text(
            text = stringResource(R.string.scale_type_label),
            style = MaterialTheme.typography.titleMedium
        )
        ScaleTypeDropdownMenus(
            selectedScaleType = scaleType,
            onScaleTypeSelected = onScaleTypeSelected
        )
    }
}

@Composable
private fun DiagramOverview(
    rows: List<PegCorrectStringResult>,
    playingStringNumbers: Set<Int>,
    onStringTouched: (PegCorrectStringResult) -> Unit,
    onPlayAllStrings: () -> Unit,
    showLeverInfo: Boolean,
    diagramZoom: Float,
    onDiagramZoomChanged: (Float) -> Unit
) {
    val left = rows
        .filter { row -> row.role.side == StringSide.LEFT }
        .sortedBy { row -> row.role.positionFromLow }
    val right = rows
        .filter { row -> row.role.side == StringSide.RIGHT }
        .sortedBy { row -> row.role.positionFromLow }
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val maxTapDistancePx = with(density) { 20.dp.toPx() }
    var diagramSize by remember { mutableStateOf(IntSize.Zero) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.overview_diagram_title),
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.overview_diagram_zoom_label,
                        "%.0f".format(diagramZoom * 100f)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = {
                        onDiagramZoomChanged((diagramZoom - 0.2f).coerceAtLeast(1f))
                    },
                    enabled = diagramZoom > 1f
                ) {
                    Text("-")
                }
                OutlinedButton(
                    onClick = {
                        onDiagramZoomChanged((diagramZoom + 0.2f).coerceAtMost(3f))
                    },
                    enabled = diagramZoom < 3f
                ) {
                    Text("+")
                }
                OutlinedButton(
                    onClick = { onDiagramZoomChanged(1f) },
                    enabled = diagramZoom != 1f
                ) {
                    Text(stringResource(R.string.action_reset))
                }
            }
            OutlinedButton(
                onClick = onPlayAllStrings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.overview_action_play_all_strings))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.86f)
                    .clipToBounds()
                    .onSizeChanged { size -> diagramSize = size }
                    .pointerInput(left, right, diagramZoom, diagramSize) {
                        detectMultiTouchStringPresses(
                            resolveHitRow = { tapOffset ->
                                if (diagramSize.width == 0 || diagramSize.height == 0) {
                                    return@detectMultiTouchStringPresses null
                                }
                                val size = Size(
                                    width = diagramSize.width.toFloat(),
                                    height = diagramSize.height.toFloat()
                                )
                                val unscaledTap = unscaleTapOffset(
                                    tapOffset = tapOffset,
                                    size = size,
                                    zoom = diagramZoom
                                )
                                findTappedString(
                                    tapOffset = unscaledTap,
                                    segments = buildDiagramStringSegments(
                                        leftRows = left,
                                        rightRows = right,
                                        size = size
                                    ),
                                    maxDistancePx = maxTapDistancePx
                                )
                            },
                            onStringTouched = onStringTouched
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = diagramZoom,
                            scaleY = diagramZoom
                        )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_kora_diagram_base),
                        contentDescription = stringResource(R.string.content_desc_kora_diagram),
                        modifier = Modifier.fillMaxSize()
                    )
                     Canvas(modifier = Modifier.fillMaxSize()) {
                         drawKoraDiagram(
                             leftRows = left,
                             rightRows = right,
                             colors = colors,
                             activeStringNumbers = playingStringNumbers,
                             showLeverInfo = showLeverInfo
                         )
                     }
                 }
             }
 
             DiagramLegend(showLeverInfo = showLeverInfo)
             TouchStringRows(
                 leftRows = left,
                 rightRows = right,
                 playingStringNumbers = playingStringNumbers,
                 onStringTouched = onStringTouched
            )
        }
    }
}

private fun DrawScope.drawKoraDiagram(
    leftRows: List<PegCorrectStringResult>,
    rightRows: List<PegCorrectStringResult>,
    colors: ColorScheme,
    activeStringNumbers: Set<Int>,
    showLeverInfo: Boolean
) {
    val bridgeCenterY = size.height * 0.56f
    val bridgeTop = bridgeCenterY - (size.height * 0.17f)
    val bridgeBottom = bridgeCenterY + (size.height * 0.17f)

    drawStringSet(
        rows = leftRows,
        isLeft = true,
        bridgeTop = bridgeTop,
        bridgeBottom = bridgeBottom,
        colors = colors,
        activeStringNumbers = activeStringNumbers,
        showLeverInfo = showLeverInfo
    )
    drawStringSet(
        rows = rightRows,
        isLeft = false,
        bridgeTop = bridgeTop,
        bridgeBottom = bridgeBottom,
        colors = colors,
        activeStringNumbers = activeStringNumbers,
        showLeverInfo = showLeverInfo
    )
}

private fun DrawScope.drawStringSet(
    rows: List<PegCorrectStringResult>,
    isLeft: Boolean,
    bridgeTop: Float,
    bridgeBottom: Float,
    colors: ColorScheme,
    activeStringNumbers: Set<Int>,
    showLeverInfo: Boolean
) {
    if (rows.isEmpty()) {
        return
    }

    val width = size.width
    val topY = size.height * 0.1f
    val bottomY = size.height * 0.9f
    val pegX = if (isLeft) width * 0.11f else width * 0.89f
    val bridgeX = if (isLeft) width * 0.485f else width * 0.515f
    val maxIndex = rows.lastIndex.coerceAtLeast(1)

    rows.forEachIndexed { index, row ->
        val ratio = index.toFloat() / maxIndex.toFloat()
        val pegY = lerp(start = bottomY, stop = topY, fraction = ratio)
        val bridgeY = lerp(start = bridgeBottom, stop = bridgeTop, fraction = ratio)
        val leverColor = if (showLeverInfo) {
            when (row.selectedLeverState) {
                LeverState.OPEN -> KoraOpenLeverColor
                LeverState.CLOSED -> KoraClosedLeverColor
            }
        } else {
            colors.outline
        }
        val isActive = row.stringNumber in activeStringNumbers
        val baseStrokeWidth = if (row.pegRetuneRequired) {
            width * 0.0058f
        } else {
            width * 0.004f
        }
        val strokeWidth = if (isActive) baseStrokeWidth * 1.8f else baseStrokeWidth
        val stringColor = if (isActive) colors.primary else leverColor

        drawLine(
            color = stringColor,
            start = Offset(pegX, pegY),
            end = Offset(bridgeX, bridgeY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawCircle(
            color = if (row.pegRetuneRequired) KoraDetunedColor else colors.outline,
            radius = if (row.pegRetuneRequired) width * 0.010f else width * 0.007f,
            center = Offset(pegX, pegY)
        )

        drawCircle(
            color = stringColor,
            radius = if (isActive) width * 0.0082f else width * 0.006f,
            center = Offset(bridgeX, bridgeY)
        )
    }
}

private fun buildDiagramStringSegments(
    leftRows: List<PegCorrectStringResult>,
    rightRows: List<PegCorrectStringResult>,
    size: Size
): List<DiagramStringSegment> {
    val bridgeCenterY = size.height * 0.56f
    val bridgeTop = bridgeCenterY - (size.height * 0.17f)
    val bridgeBottom = bridgeCenterY + (size.height * 0.17f)

    return buildSideStringSegments(
        rows = leftRows,
        isLeft = true,
        size = size,
        bridgeTop = bridgeTop,
        bridgeBottom = bridgeBottom
    ) + buildSideStringSegments(
        rows = rightRows,
        isLeft = false,
        size = size,
        bridgeTop = bridgeTop,
        bridgeBottom = bridgeBottom
    )
}

private fun buildSideStringSegments(
    rows: List<PegCorrectStringResult>,
    isLeft: Boolean,
    size: Size,
    bridgeTop: Float,
    bridgeBottom: Float
): List<DiagramStringSegment> {
    if (rows.isEmpty()) {
        return emptyList()
    }

    val topY = size.height * 0.1f
    val bottomY = size.height * 0.9f
    val pegX = if (isLeft) size.width * 0.11f else size.width * 0.89f
    val bridgeX = if (isLeft) size.width * 0.485f else size.width * 0.515f
    val maxIndex = rows.lastIndex.coerceAtLeast(1)

    return rows.mapIndexed { index, row ->
        val ratio = index.toFloat() / maxIndex.toFloat()
        val pegY = lerp(start = bottomY, stop = topY, fraction = ratio)
        val bridgeY = lerp(start = bridgeBottom, stop = bridgeTop, fraction = ratio)
        DiagramStringSegment(
            row = row,
            peg = Offset(x = pegX, y = pegY),
            bridge = Offset(x = bridgeX, y = bridgeY)
        )
    }
}

private fun findTappedString(
    tapOffset: Offset,
    segments: List<DiagramStringSegment>,
    maxDistancePx: Float
): PegCorrectStringResult? {
    val nearest = segments.minByOrNull { segment ->
        distancePointToSegment(
            point = tapOffset,
            segmentStart = segment.peg,
            segmentEnd = segment.bridge
        )
    } ?: return null

    val distance = distancePointToSegment(
        point = tapOffset,
        segmentStart = nearest.peg,
        segmentEnd = nearest.bridge
    )
    return if (distance <= maxDistancePx) nearest.row else null
}

private suspend fun PointerInputScope.detectMultiTouchStringPresses(
    resolveHitRow: (Offset) -> PegCorrectStringResult?,
    onStringTouched: (PegCorrectStringResult) -> Unit
) {
    awaitEachGesture {
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { change ->
                if (change.changedToDownIgnoreConsumed()) {
                    resolveHitRow(change.position)?.let(onStringTouched)
                    change.consume()
                }
            }
        } while (event.changes.any { change -> change.pressed })
    }
}

private suspend fun PointerInputScope.consumeAllTouches() {
    awaitEachGesture {
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { change -> change.consume() }
        } while (event.changes.any { change -> change.pressed })
    }
}

private fun distancePointToSegment(
    point: Offset,
    segmentStart: Offset,
    segmentEnd: Offset
): Float {
    val dx = segmentEnd.x - segmentStart.x
    val dy = segmentEnd.y - segmentStart.y
    val segmentLengthSquared = (dx * dx) + (dy * dy)

    if (segmentLengthSquared <= 0f) {
        val px = point.x - segmentStart.x
        val py = point.y - segmentStart.y
        return sqrt((px * px) + (py * py))
    }

    val projection = (
        ((point.x - segmentStart.x) * dx) +
            ((point.y - segmentStart.y) * dy)
        ) / segmentLengthSquared
    val t = projection.coerceIn(0f, 1f)
    val closestX = segmentStart.x + (t * dx)
    val closestY = segmentStart.y + (t * dy)
    val diffX = point.x - closestX
    val diffY = point.y - closestY
    return sqrt((diffX * diffX) + (diffY * diffY))
}

private fun unscaleTapOffset(
    tapOffset: Offset,
    size: Size,
    zoom: Float
): Offset {
    if (zoom <= 1f) {
        return tapOffset
    }
    val centerX = size.width * 0.5f
    val centerY = size.height * 0.5f
    return Offset(
        x = centerX + ((tapOffset.x - centerX) / zoom),
        y = centerY + ((tapOffset.y - centerY) / zoom)
    )
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

@Composable
private fun DiagramLegend(showLeverInfo: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showLeverInfo) {
            LegendItem(
                color = KoraOpenLeverColor,
                text = stringResource(R.string.overview_legend_open_lever)
            )
            LegendItem(
                color = KoraClosedLeverColor,
                text = stringResource(R.string.overview_legend_closed_lever)
            )
        }
        LegendItem(
            color = KoraDetunedColor,
            text = stringResource(R.string.overview_legend_peg_retune_required)
        )
    }
}

@Composable
private fun TouchStringRows(
    leftRows: List<PegCorrectStringResult>,
    rightRows: List<PegCorrectStringResult>,
    playingStringNumbers: Set<Int>,
    onStringTouched: (PegCorrectStringResult) -> Unit
) {
    Text(
        text = stringResource(R.string.overview_diagram_tap_to_hear),
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        text = stringResource(R.string.instrument_tuning_assistant_left_side),
        style = MaterialTheme.typography.labelMedium
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = leftRows, key = { row -> row.stringNumber }) { row ->
            FilterChip(
                selected = row.stringNumber in playingStringNumbers,
                onClick = { onStringTouched(row) },
                label = { Text("${row.role.asLabel()} ${row.selectedPitch.asText()}") }
            )
        }
    }
    Text(
        text = stringResource(R.string.instrument_tuning_assistant_right_side),
        style = MaterialTheme.typography.labelMedium
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = rightRows, key = { row -> row.stringNumber }) { row ->
            FilterChip(
                selected = row.stringNumber in playingStringNumbers,
                onClick = { onStringTouched(row) },
                label = { Text("${row.role.asLabel()} ${row.selectedPitch.asText()}") }
            )
        }
    }
}

@Composable
private fun ChordOverview(
    rows: List<PegCorrectStringResult>,
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
    val leftRows = rows
        .filter { row -> row.role.side == StringSide.LEFT }
        .sortedBy { row -> row.role.positionFromLow }
    val rightRows = rows
        .filter { row -> row.role.side == StringSide.RIGHT }
        .sortedBy { row -> row.role.positionFromLow }
    val rowCount = maxOf(leftRows.size, rightRows.size)
    val selectedChordStringNumbers = selectedChordStrings
        .map { row -> row.stringNumber }
        .toSet()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.overview_chords_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.overview_chords_subtitle),
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = stringResource(R.string.overview_chords_root_label),
                style = MaterialTheme.typography.labelMedium
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(NoteName.entries) { note ->
                    FilterChip(
                        selected = note == selectedRoot,
                        onClick = { onRootSelected(note) },
                        label = { Text(note.symbol) }
                    )
                }
            }

            Text(
                text = stringResource(R.string.overview_chords_quality_label),
                style = MaterialTheme.typography.labelMedium
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { isQualityMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(chordQualityLabel(selectedQuality))
                }
                DropdownMenu(
                    expanded = isQualityMenuExpanded,
                    onDismissRequest = { isQualityMenuExpanded = false }
                ) {
                    ChordQuality.entries.forEach { quality ->
                        DropdownMenuItem(
                            text = { Text(chordQualityLabel(quality)) },
                            onClick = {
                                isQualityMenuExpanded = false
                                onQualitySelected(quality)
                            }
                        )
                    }
                }
            }

            Text(
                text = stringResource(
                    R.string.overview_chords_notes_line,
                    selectedMatch.definition.chordNotes.joinToString(" ") { note -> note.symbol }
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.overview_chords_positions_label),
                style = MaterialTheme.typography.labelMedium
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    items = selectedQuality.tones,
                    key = { tone -> tone.semitoneOffset }
                ) { tone ->
                    FilterChip(
                        selected = tone.semitoneOffset in selectedToneOffsets,
                        onClick = { onToneOffsetToggled(tone.semitoneOffset) },
                        label = { Text(tone.label) }
                    )
                }
            }
            Text(
                text = if (selectedMatch.missingNotes.isEmpty()) {
                    stringResource(R.string.overview_chords_coverage_complete)
                } else {
                    stringResource(
                        R.string.overview_chords_coverage_missing,
                        selectedMatch.missingNotes.joinToString(" ") { note -> note.symbol }
                    )
                },
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(
                    R.string.overview_chords_strings_to_play_line,
                    selectedChordStrings.size,
                    4
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(
                    R.string.overview_chords_pool_line,
                    selectedMatch.playedStringNumbers.size,
                    selectedMatch.openPlayCount,
                    selectedMatch.closedPlayCount,
                    selectedMatch.detunedPlayCount
                ),
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = stringResource(R.string.overview_chords_playback_label),
                style = MaterialTheme.typography.labelMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChordPlaybackMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == playbackMode,
                        onClick = { onPlaybackModeSelected(mode) },
                        label = { Text(chordPlaybackModeLabel(mode)) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3, 4).forEach { count ->
                    FilterChip(
                        selected = count == chordVoicingNoteCount,
                        onClick = { onChordVoicingNoteCountChanged(count) },
                        label = { Text(stringResource(R.string.overview_chords_notes_count_chip, count)) }
                    )
                }
            }
            OutlinedButton(
                onClick = onPlaySelectedChord,
                enabled = selectedChordStrings.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.overview_chords_action_sound_chord))
            }

            if (selectedMatch.usesDetunedStrings) {
                val stableChordLabel = suggestedNonDetunedChord?.definition?.let { definition ->
                    chordDefinitionLabel(definition)
                } ?: stringResource(R.string.value_none_found)
                Text(
                    text = stringResource(R.string.overview_chords_detuned_warning, stableChordLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = KoraDetunedColor
                )
                if (suggestedNonDetunedChord != null) {
                    OutlinedButton(
                        onClick = { onChordSelected(suggestedNonDetunedChord.definition) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.overview_chords_action_use_stable_alternative))
                    }
                }
            }

            Text(
                text = stringResource(R.string.overview_chords_exercise_note),
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = stringResource(R.string.overview_chords_diagram_label),
                style = MaterialTheme.typography.labelMedium
            )
            ChordPictorialKoraDiagram(
                rows = rows,
                chordStringNumbers = selectedChordStringNumbers,
                playingStringNumbers = playingStringNumbers,
                onStringTouched = onStringTouched,
                showLeverInfo = showLeverInfo
            )
            SideColumnHeader()
            repeat(rowCount) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val leftRow = leftRows.getOrNull(index)
                    if (leftRow != null) {
                        ChordSideCell(
                            row = leftRow,
                            shouldPlay = leftRow.stringNumber in selectedChordStringNumbers,
                            isSounding = leftRow.stringNumber in playingStringNumbers,
                            onStringTouched = onStringTouched,
                            showLeverInfo = showLeverInfo,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    val rightRow = rightRows.getOrNull(index)
                    if (rightRow != null) {
                        ChordSideCell(
                            row = rightRow,
                            shouldPlay = rightRow.stringNumber in selectedChordStringNumbers,
                            isSounding = rightRow.stringNumber in playingStringNumbers,
                            onStringTouched = onStringTouched,
                            showLeverInfo = showLeverInfo,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.overview_chords_closest_title),
                style = MaterialTheme.typography.titleSmall
            )
            bestMatches.forEach { match ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = chordDefinitionLabel(match.definition),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(
                                R.string.overview_chords_match_notes_play_line,
                                match.definition.chordNotes.joinToString(" ") { note -> note.symbol },
                                match.playedStringNumbers.size
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (match.usesDetunedStrings) {
                                stringResource(R.string.overview_chords_match_includes_detuned)
                            } else {
                                stringResource(R.string.overview_chords_match_no_detuned)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (match.usesDetunedStrings) {
                                KoraDetunedColor
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = { onChordSelected(match.definition) }
                    ) {
                        Text(stringResource(R.string.overview_chords_action_use))
                    }
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
    isMetronomeRunning: Boolean,
    onMetronomeRunningChanged: (Boolean) -> Unit,
    metronomeCurrentBeat: Int
) {
    var isTimeSignatureMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.overview_exercise_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.overview_exercise_order_note),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.overview_exercise_start_key_label),
                style = MaterialTheme.typography.labelMedium
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CIRCLE_OF_FIFTHS_ORDER) { note ->
                    FilterChip(
                        selected = note == circleExerciseStartRoot,
                        onClick = { onCircleExerciseStartRootChanged(note) },
                        label = { Text(circleOfFifthsLabel(note)) }
                    )
                }
            }
            Text(
                text = stringResource(R.string.overview_exercise_choice_mode_label),
                style = MaterialTheme.typography.labelMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseChoiceMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == exerciseChoiceMode,
                        onClick = { onExerciseChoiceModeSelected(mode) },
                        label = { Text(exerciseChoiceModeLabel(mode)) }
                    )
                }
            }
            CircleOfFifthsExerciseDiagram(
                startRoot = circleExerciseStartRoot,
                nextRoot = timedExerciseTargetRoot ?: nextCircleExerciseRoot,
                choiceRoots = timedExerciseChoiceRoots
            )
            if (timedExerciseTargetRoot != null) {
                Text(
                    text = pluralStringResource(
                        R.plurals.overview_exercise_suggested_next,
                        timedExerciseBeatsUntilDue,
                        circleOfFifthsLabel(timedExerciseTargetRoot),
                        chordQualityLabel(selectedQuality),
                        timedExerciseBeatsUntilDue,
                        timedExerciseDueBeat
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(timedExerciseChoiceRoots) { choiceRoot ->
                        FilterChip(
                            selected = choiceRoot == timedExerciseTargetRoot,
                            onClick = { onRootSelected(choiceRoot) },
                            label = {
                                Text(
                                    text = stringResource(
                                        R.string.overview_exercise_choice_item,
                                        circleOfFifthsLabel(choiceRoot),
                                        chordQualityLabel(selectedQuality)
                                    )
                                )
                            }
                        )
                    }
                }
            }
            Text(
                text = stringResource(
                    R.string.overview_exercise_next_step,
                    circleOfFifthsLabel(nextCircleExerciseRoot),
                    chordQualityLabel(selectedQuality)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = onRunCircleExerciseStep,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.overview_exercise_action_next_fifth))
            }
            Text(
                text = stringResource(R.string.overview_exercise_timed_interval_label),
                style = MaterialTheme.typography.labelMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3, 4).forEach { beats ->
                    FilterChip(
                        selected = beats == timedExerciseChordIntervalBeats,
                        onClick = { onTimedExerciseChordIntervalBeatsChanged(beats) },
                        label = {
                            Text(
                                pluralStringResource(
                                    R.plurals.overview_exercise_interval_chip,
                                    beats,
                                    beats
                                )
                            )
                        }
                    )
                }
            }
            OutlinedButton(
                onClick = { onTimedExerciseRunningChanged(!isTimedExerciseRunning) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isTimedExerciseRunning) {
                        stringResource(R.string.overview_exercise_action_stop_timed)
                    } else {
                        stringResource(R.string.overview_exercise_action_start_timed)
                    }
                )
            }
            Text(
                text = stringResource(R.string.overview_exercise_timed_note),
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = stringResource(R.string.overview_metronome_title),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = stringResource(R.string.overview_metronome_bpm_line, metronomeBpm),
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = metronomeBpm.toFloat(),
                onValueChange = { value ->
                    onMetronomeBpmChanged(value.roundToInt())
                },
                valueRange = 40f..250f
            )
            Text(
                text = stringResource(R.string.overview_metronome_time_signature_label),
                style = MaterialTheme.typography.labelMedium
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { isTimeSignatureMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(metronomeTimeSignature.label)
                }
                DropdownMenu(
                    expanded = isTimeSignatureMenuExpanded,
                    onDismissRequest = { isTimeSignatureMenuExpanded = false }
                ) {
                    METRONOME_TIME_SIGNATURES.forEach { signature ->
                        DropdownMenuItem(
                            text = { Text(signature.label) },
                            onClick = {
                                isTimeSignatureMenuExpanded = false
                                onMetronomeTimeSignatureChanged(signature)
                            }
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.overview_metronome_beats_to_click_label),
                style = MaterialTheme.typography.labelMedium
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((1..metronomeTimeSignature.numerator).toList()) { beat ->
                    FilterChip(
                        selected = beat in metronomeEnabledBeats,
                        onClick = { onMetronomeBeatToggled(beat) },
                        label = {
                            val beatLabel = if (beat == metronomeCurrentBeat) {
                                "$beat *"
                            } else {
                                beat.toString()
                            }
                            Text(beatLabel)
                        }
                    )
                }
            }
            Text(
                text = stringResource(R.string.overview_metronome_sound_label),
                style = MaterialTheme.typography.labelMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetronomeSoundOption.entries.forEach { sound ->
                    FilterChip(
                        selected = sound == metronomeSound,
                        onClick = { onMetronomeSoundChanged(sound) },
                        label = { Text(metronomeSoundOptionLabel(sound)) }
                    )
                }
            }
            OutlinedButton(
                onClick = { onMetronomeRunningChanged(!isMetronomeRunning) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isMetronomeRunning) {
                        stringResource(R.string.overview_metronome_action_stop)
                    } else {
                        stringResource(R.string.overview_metronome_action_start)
                    }
                )
            }
        }
    }
}

@Composable
private fun CircleOfFifthsExerciseDiagram(
    startRoot: NoteName,
    nextRoot: NoteName,
    choiceRoots: List<NoteName>
) {
    val colorScheme = MaterialTheme.colorScheme
    val textPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    textPaint.textSize = with(LocalDensity.current) { 11.sp.toPx() }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.overview_exercise_diagram_title),
                style = MaterialTheme.typography.bodySmall
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(startRoot, nextRoot, choiceRoots) {
                        consumeAllTouches()
                    }
            ) {
                val center = Offset(size.width * 0.5f, size.height * 0.5f)
                val ringRadius = size.minDimension * 0.38f
                drawCircle(
                    color = colorScheme.outlineVariant,
                    radius = ringRadius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.006f)
                )

                CIRCLE_OF_FIFTHS_ORDER.forEachIndexed { index, note ->
                    val angleRadians = Math.toRadians((-90.0 + (index * 30.0)))
                    val markerCenter = Offset(
                        x = center.x + (cos(angleRadians).toFloat() * ringRadius),
                        y = center.y + (sin(angleRadians).toFloat() * ringRadius)
                    )
                    val isStart = note == startRoot
                    val isNext = note == nextRoot
                    val isChoice = note in choiceRoots

                    val markerColor = when {
                        isNext -> KoraClosedLeverColor
                        isStart -> KoraOpenLeverColor
                        isChoice -> colorScheme.tertiary
                        else -> colorScheme.surfaceVariant
                    }
                    val markerRadius = when {
                        isNext -> size.minDimension * 0.056f
                        isStart -> size.minDimension * 0.048f
                        else -> size.minDimension * 0.042f
                    }
                    drawCircle(
                        color = markerColor,
                        radius = markerRadius,
                        center = markerCenter
                    )
                    if (isNext || isStart) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.9f),
                            radius = markerRadius + (size.minDimension * 0.006f),
                            center = markerCenter,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = size.minDimension * 0.006f
                            )
                        )
                    }

                    textPaint.color = if (isNext || isStart) {
                        android.graphics.Color.WHITE
                    } else {
                        colorScheme.onSurface.toArgb()
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        circleOfFifthsLabel(note),
                        markerCenter.x,
                        markerCenter.y + (textPaint.textSize * 0.33f),
                        textPaint
                    )
                }
            }

            Text(
                text = stringResource(R.string.overview_exercise_diagram_legend),
                style = MaterialTheme.typography.bodySmall
            )
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
    val leftRows = rows
        .filter { row -> row.role.side == StringSide.LEFT }
        .sortedBy { row -> row.role.positionFromLow }
    val rightRows = rows
        .filter { row -> row.role.side == StringSide.RIGHT }
        .sortedBy { row -> row.role.positionFromLow }
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val maxTapDistancePx = with(density) { 20.dp.toPx() }
    var diagramSize by remember { mutableStateOf(IntSize.Zero) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.overview_chords_diagram_title),
                style = MaterialTheme.typography.bodySmall
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.86f)
                    .clipToBounds()
                    .onSizeChanged { size -> diagramSize = size }
                    .pointerInput(leftRows, rightRows, diagramSize) {
                        detectMultiTouchStringPresses(
                            resolveHitRow = { tapOffset ->
                                if (diagramSize.width == 0 || diagramSize.height == 0) {
                                    return@detectMultiTouchStringPresses null
                                }
                                val size = Size(
                                    width = diagramSize.width.toFloat(),
                                    height = diagramSize.height.toFloat()
                                )
                                findTappedString(
                                    tapOffset = tapOffset,
                                    segments = buildDiagramStringSegments(
                                        leftRows = leftRows,
                                        rightRows = rightRows,
                                        size = size
                                    ),
                                    maxDistancePx = maxTapDistancePx
                                )
                            },
                            onStringTouched = onStringTouched
                        )
                    }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_kora_diagram_base),
                    contentDescription = stringResource(R.string.content_desc_chord_kora_diagram),
                    modifier = Modifier.fillMaxSize()
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawKoraDiagram(
                        leftRows = leftRows,
                        rightRows = rightRows,
                        colors = colors,
                        activeStringNumbers = playingStringNumbers,
                        showLeverInfo = showLeverInfo
                    )
                    drawChordMarkers(
                        segments = buildDiagramStringSegments(
                            leftRows = leftRows,
                            rightRows = rightRows,
                            size = size
                        ),
                        chordStringNumbers = chordStringNumbers
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawChordMarkers(
    segments: List<DiagramStringSegment>,
    chordStringNumbers: Set<Int>
) {
    val markerHalf = size.width * 0.017f
    val innerStroke = size.width * 0.0072f
    val outerStroke = innerStroke * 1.9f
    segments.forEach { segment ->
        if (segment.row.stringNumber !in chordStringNumbers) {
            return@forEach
        }
        val center = Offset(
            x = (segment.peg.x + segment.bridge.x) * 0.5f,
            y = (segment.peg.y + segment.bridge.y) * 0.5f
        )
        drawLine(
            color = Color.White.copy(alpha = 0.95f),
            start = Offset(center.x - markerHalf, center.y - markerHalf),
            end = Offset(center.x + markerHalf, center.y + markerHalf),
            strokeWidth = outerStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.95f),
            start = Offset(center.x - markerHalf, center.y + markerHalf),
            end = Offset(center.x + markerHalf, center.y - markerHalf),
            strokeWidth = outerStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = KoraClosedLeverColor,
            start = Offset(center.x - markerHalf, center.y - markerHalf),
            end = Offset(center.x + markerHalf, center.y + markerHalf),
            strokeWidth = innerStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = KoraClosedLeverColor,
            start = Offset(center.x - markerHalf, center.y + markerHalf),
            end = Offset(center.x + markerHalf, center.y - markerHalf),
            strokeWidth = innerStroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ChordSideCell(
    row: PegCorrectStringResult,
    shouldPlay: Boolean,
    isSounding: Boolean,
    onStringTouched: (PegCorrectStringResult) -> Unit,
    showLeverInfo: Boolean,
    modifier: Modifier = Modifier
) {
    val baseColor = when {
        row.pegRetuneRequired -> KoraDetunedColor.copy(alpha = 0.24f)
        !showLeverInfo -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        row.selectedLeverState == LeverState.OPEN -> KoraOpenLeverColor.copy(alpha = 0.22f)
        else -> KoraClosedLeverColor.copy(alpha = 0.22f)
    }
    val containerColor = if (isSounding) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        baseColor
    }
    val borderColor = if (shouldPlay) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = modifier.clickable { onStringTouched(row) },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = buildString {
                append(if (shouldPlay) "X " else "- ")
                append("${row.role.asLabel()} (S${row.stringNumber})\n")
                append(row.selectedPitch.asText())
                if (showLeverInfo) {
                    append("  ${row.selectedLeverState.name}")
                }
                if (row.pegRetuneRequired) {
                    append("  ${stringResource(R.string.value_detuned)}")
                }
            },
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LegendItem(
    color: Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color = color, shape = MaterialTheme.shapes.small)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun TableOverview(
    rows: List<PegCorrectStringResult>,
    playingStringNumbers: Set<Int>,
    onStringTouched: (PegCorrectStringResult) -> Unit,
    showLeverInfo: Boolean
) {
    val leftRows = rows
        .filter { row -> row.role.side == StringSide.LEFT }
        .sortedBy { row -> row.role.positionFromLow }
    val rightRows = rows
        .filter { row -> row.role.side == StringSide.RIGHT }
        .sortedBy { row -> row.role.positionFromLow }
    val rowCount = maxOf(leftRows.size, rightRows.size)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.overview_table_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.scale_engine_two_column_note),
                style = MaterialTheme.typography.bodySmall
            )
            SideColumnHeader()
            repeat(rowCount) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val leftRow = leftRows.getOrNull(index)
                    if (leftRow != null) {
                        SideCell(
                            text = formatOverviewRow(leftRow, showLeverInfo = showLeverInfo),
                            isActive = leftRow.stringNumber in playingStringNumbers,
                            onClick = { onStringTouched(leftRow) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    val rightRow = rightRows.getOrNull(index)
                    if (rightRow != null) {
                        SideCell(
                            text = formatOverviewRow(rightRow, showLeverInfo = showLeverInfo),
                            isActive = rightRow.stringNumber in playingStringNumbers,
                            onClick = { onStringTouched(rightRow) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SideColumnHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.table_left_header),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.table_right_header),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SideCell(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun formatOverviewRow(
    row: PegCorrectStringResult,
    showLeverInfo: Boolean
): String {
    val pegIndicator = if (row.pegRetuneRequired) {
        signed(row.pegRetuneSemitones)
    } else {
        "0"
    }
    return buildString {
        append("${row.role.asLabel()} (S${row.stringNumber})\n")
        if (showLeverInfo) {
            append(
                stringResource(
                    R.string.scale_engine_peg_row_target_lever,
                    row.selectedPitch.asText(),
                    row.selectedLeverState.name
                )
            )
        } else {
            append(stringResource(R.string.scale_engine_peg_tuning_row_target, row.selectedPitch.asText()))
        }
        append("\n")
        append(stringResource(R.string.scale_engine_row_int_peg, signed(row.selectedIntonationCents), pegIndicator))
    }
}

private fun signed(value: Int): String {
    return if (value >= 0) "+$value" else value.toString()
}

private fun signed(value: Double): String {
    return if (value >= 0.0) "+${"%.1f".format(value)}" else "%.1f".format(value)
}

private const val DOUBLE_TAP_STOP_WINDOW_MS = 280L
private const val PLUCK_VISUAL_HOLD_MS = 760L
private const val BROKEN_CHORD_STEP_MS = 140L
private const val SPREAD_CHORD_STEP_MS = 120L

private val METRONOME_TIME_SIGNATURES = listOf(
    MetronomeTimeSignature(2, 4),
    MetronomeTimeSignature(3, 4),
    MetronomeTimeSignature(4, 4),
    MetronomeTimeSignature(6, 8),
    MetronomeTimeSignature(9, 9),
    MetronomeTimeSignature(12, 8)
)

private val CIRCLE_OF_FIFTHS_ORDER = listOf(
    NoteName.C,
    NoteName.F,
    NoteName.A_SHARP, // Bb
    NoteName.D_SHARP, // Eb
    NoteName.G_SHARP, // Ab
    NoteName.C_SHARP, // Db
    NoteName.F_SHARP,
    NoteName.B,
    NoteName.E,
    NoteName.A,
    NoteName.D,
    NoteName.G
)

private fun spreadOrder(rows: List<PegCorrectStringResult>): List<PegCorrectStringResult> {
    if (rows.size <= 2) {
        return rows
    }
    val ordered = rows.sortedBy { row ->
        (row.selectedPitch.octave * 12) + row.selectedPitch.note.semitone
    }.toMutableList()
    val spread = mutableListOf<PegCorrectStringResult>()
    while (ordered.isNotEmpty()) {
        spread += ordered.removeAt(0)
        if (ordered.isNotEmpty()) {
            spread += ordered.removeAt(ordered.lastIndex)
        }
    }
    return spread
}

private fun circleOfFifthsIndexFor(note: NoteName): Int {
    val index = CIRCLE_OF_FIFTHS_ORDER.indexOf(note)
    return if (index >= 0) index else 0
}

private fun circleOfFifthsLabel(note: NoteName): String {
    return when (note) {
        NoteName.A_SHARP -> "Bb"
        NoteName.D_SHARP -> "Eb"
        NoteName.G_SHARP -> "Ab"
        NoteName.C_SHARP -> "Db"
        NoteName.F_SHARP -> "F# / Gb"
        else -> note.symbol
    }
}

private fun buildExerciseChoices(
    targetRoot: NoteName,
    size: Int
): List<NoteName> {
    val desiredSize = size.coerceAtLeast(1)
    val distractors = CIRCLE_OF_FIFTHS_ORDER
        .filter { note -> note != targetRoot }
        .shuffled()
        .take((desiredSize - 1).coerceAtLeast(0))
    return (distractors + targetRoot)
        .shuffled()
        .take(desiredSize)
}

@Composable
private fun chordQualityLabel(quality: ChordQuality): String {
    return when (quality) {
        ChordQuality.MAJOR -> stringResource(R.string.chord_quality_major)
        ChordQuality.MINOR -> stringResource(R.string.chord_quality_minor)
        ChordQuality.DIMINISHED -> stringResource(R.string.chord_quality_diminished)
        ChordQuality.HALF_DIMINISHED -> stringResource(R.string.chord_quality_half_diminished)
        ChordQuality.SUS2 -> stringResource(R.string.chord_quality_sus2)
        ChordQuality.SUS4 -> stringResource(R.string.chord_quality_sus4)
        ChordQuality.DOMINANT7 -> stringResource(R.string.chord_quality_dominant7)
        ChordQuality.MAJOR7 -> stringResource(R.string.chord_quality_major7)
        ChordQuality.MINOR7 -> stringResource(R.string.chord_quality_minor7)
    }
}

@Composable
private fun chordDefinitionLabel(definition: ChordDefinition): String {
    return "${definition.root.symbol} ${chordQualityLabel(definition.quality)}"
}

@Composable
private fun metronomeSoundOptionLabel(sound: MetronomeSoundOption): String {
    return when (sound) {
        MetronomeSoundOption.WOOD_SOFT -> stringResource(R.string.metronome_sound_soft_wood)
        MetronomeSoundOption.WOOD_BLOCK -> stringResource(R.string.metronome_sound_wood_block)
        MetronomeSoundOption.WOOD_CLICK -> stringResource(R.string.metronome_sound_bright_click)
    }
}

@Composable
private fun chordPlaybackModeLabel(mode: ChordPlaybackMode): String {
    return when (mode) {
        ChordPlaybackMode.BLOCK -> stringResource(R.string.overview_chords_playback_block)
        ChordPlaybackMode.BROKEN -> stringResource(R.string.overview_chords_playback_broken)
        ChordPlaybackMode.SPREAD -> stringResource(R.string.overview_chords_playback_spread)
    }
}

@Composable
private fun exerciseChoiceModeLabel(mode: ExerciseChoiceMode): String {
    return when (mode) {
        ExerciseChoiceMode.PRESET -> stringResource(R.string.overview_exercise_choice_preset)
        ExerciseChoiceMode.RANDOM -> stringResource(R.string.overview_exercise_choice_random)
    }
}

