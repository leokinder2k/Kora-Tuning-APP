package com.leokinder2k.koratuningcompanion.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KoraAuthorityApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val scaleViewModel: ScaleCalculationViewModel = viewModel(
        factory = ScaleCalculationViewModel.factory(context)
    )
    val scaleUiState by scaleViewModel.uiState.collectAsStateWithLifecycle()

    val destinations = AppDestination.entries
    val pagerState = rememberPagerState(
        initialPage = destinations.indexOf(AppDestination.INSTRUMENT_CONFIG),
        pageCount = { destinations.size }
    )
    val coroutineScope = rememberCoroutineScope()

    NavigationSuiteScaffold(
        modifier = modifier.fillMaxSize(),
        navigationSuiteItems = {
            destinations.forEachIndexed { index, destination ->
                item(
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.labelRes)
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
                    selected = index == pagerState.currentPage,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                )
            }
        }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (destinations[page]) {
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
}

private enum class AppDestination(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
) {
    INSTRUMENT_CONFIG(R.string.nav_instrument_label, Icons.Default.Tune),
    SCALE_ENGINE(R.string.nav_scale_label, Icons.Default.MusicNote),
    GUIDED_SETUP(R.string.nav_guided_label, Icons.Default.PlaylistPlay),
    INSTANT_OVERVIEW(R.string.nav_overview_label, Icons.Default.GridView),
    LIVE_TUNER(R.string.nav_tuner_label, Icons.Default.GraphicEq),
    PRESETS(R.string.nav_presets_label, Icons.Default.LibraryMusic),
}

