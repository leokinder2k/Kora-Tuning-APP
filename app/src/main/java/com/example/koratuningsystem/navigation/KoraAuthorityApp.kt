package com.example.koratuningsystem.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.koratuningsystem.instrumentconfig.ui.InstrumentConfigurationRoute
import com.example.koratuningsystem.instrumentconfig.ui.TraditionalPresetsRoute
import com.example.koratuningsystem.livetuner.ui.LiveTunerRoute
import com.example.koratuningsystem.scaleengine.ui.GuidedSetupScreen
import com.example.koratuningsystem.scaleengine.ui.InstantOverviewScreen
import com.example.koratuningsystem.scaleengine.ui.ScaleCalculationScreen
import com.example.koratuningsystem.scaleengine.ui.ScaleCalculationViewModel

@Composable
fun KoraAuthorityApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val scaleViewModel: ScaleCalculationViewModel = viewModel(
        factory = ScaleCalculationViewModel.factory(context)
    )
    val scaleUiState by scaleViewModel.uiState.collectAsStateWithLifecycle()

    var currentDestination by rememberSaveable {
        androidx.compose.runtime.mutableStateOf(AppDestination.INSTRUMENT_CONFIG)
    }

    NavigationSuiteScaffold(
        modifier = modifier.fillMaxSize(),
        navigationSuiteItems = {
            AppDestination.entries.forEach { destination ->
                item(
                    icon = {
                        Text(
                            text = destination.shortLabel,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    label = {
                        Text(destination.label)
                    },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination }
                )
            }
        }
    ) {
        when (currentDestination) {
            AppDestination.INSTRUMENT_CONFIG -> InstrumentConfigurationRoute()
            AppDestination.SCALE_ENGINE -> ScaleCalculationScreen(
                uiState = scaleUiState,
                onRootNoteSelected = scaleViewModel::onRootNoteSelected,
                onScaleTypeSelected = scaleViewModel::onScaleTypeSelected
            )
            AppDestination.GUIDED_SETUP -> GuidedSetupScreen(
                uiState = scaleUiState,
                onRootNoteSelected = scaleViewModel::onRootNoteSelected,
                onScaleTypeSelected = scaleViewModel::onScaleTypeSelected
            )
            AppDestination.INSTANT_OVERVIEW -> InstantOverviewScreen(
                uiState = scaleUiState,
                onRootNoteSelected = scaleViewModel::onRootNoteSelected,
                onScaleTypeSelected = scaleViewModel::onScaleTypeSelected
            )
            AppDestination.LIVE_TUNER -> LiveTunerRoute(
                scaleUiState = scaleUiState,
                onRootNoteSelected = scaleViewModel::onRootNoteSelected,
                onScaleTypeSelected = scaleViewModel::onScaleTypeSelected
            )
            AppDestination.PRESETS -> TraditionalPresetsRoute()
        }
    }
}

private enum class AppDestination(
    val label: String,
    val shortLabel: String
) {
    INSTRUMENT_CONFIG("Instrument", "I"),
    SCALE_ENGINE("Scale", "S"),
    GUIDED_SETUP("Guided", "G"),
    INSTANT_OVERVIEW("Overview", "O"),
    LIVE_TUNER("Tuner", "T"),
    PRESETS("Presets", "P"),
}
