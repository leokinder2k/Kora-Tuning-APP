package com.leokinder2k.koratuningcompanion.navigation

import androidx.annotation.StringRes
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.instrumentconfig.ui.InstrumentConfigurationRoute
import com.leokinder2k.koratuningcompanion.instrumentconfig.ui.TraditionalPresetsRoute
import com.leokinder2k.koratuningcompanion.livetuner.ui.LiveTunerRoute
import com.leokinder2k.koratuningcompanion.scaleengine.ui.GuidedSetupScreen
import com.leokinder2k.koratuningcompanion.scaleengine.ui.InstantOverviewScreen
import com.leokinder2k.koratuningcompanion.scaleengine.ui.ScaleCalculationScreen
import com.leokinder2k.koratuningcompanion.scaleengine.ui.ScaleCalculationViewModel

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
                            text = stringResource(destination.shortLabelRes),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(destination.labelRes),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
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
    @param:StringRes val labelRes: Int,
    @param:StringRes val shortLabelRes: Int
) {
    INSTRUMENT_CONFIG(R.string.nav_instrument_label, R.string.nav_instrument_short),
    SCALE_ENGINE(R.string.nav_scale_label, R.string.nav_scale_short),
    GUIDED_SETUP(R.string.nav_guided_label, R.string.nav_guided_short),
    INSTANT_OVERVIEW(R.string.nav_overview_label, R.string.nav_overview_short),
    LIVE_TUNER(R.string.nav_tuner_label, R.string.nav_tuner_short),
    PRESETS(R.string.nav_presets_label, R.string.nav_presets_short),
}

