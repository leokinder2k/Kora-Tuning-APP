package com.leokinder2k.koratuningcompanion.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leokinder2k.koratuningcompanion.generated.resources.Res
import com.leokinder2k.koratuningcompanion.generated.resources.*
import com.leokinder2k.koratuningcompanion.instrumentconfig.ui.InstrumentConfigurationRoute
import com.leokinder2k.koratuningcompanion.instrumentconfig.ui.TraditionalPresetsRoute
import com.leokinder2k.koratuningcompanion.livetuner.ui.LiveTunerRoute
import com.leokinder2k.koratuningcompanion.platform.changeLocale
import com.leokinder2k.koratuningcompanion.platform.getCurrentLocaleTag
import com.leokinder2k.koratuningcompanion.platform.openUrl
import com.leokinder2k.koratuningcompanion.scaleengine.ui.InstantOverviewScreen
import com.leokinder2k.koratuningcompanion.scaleengine.ui.ScaleCalculationScreen
import com.leokinder2k.koratuningcompanion.scaleengine.ui.ScaleCalculationViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun KoraAuthorityApp(
    modifier: Modifier = Modifier,
    themeMode: String = "SYSTEM",
    onThemeModeChange: (String) -> Unit = {}
) {
    val scaleViewModel: ScaleCalculationViewModel = viewModel { ScaleCalculationViewModel() }
    val scaleUiState by scaleViewModel.uiState.collectAsStateWithLifecycle()

    val destinations = AppDestination.entries
    val pagerState = rememberPagerState(
        initialPage = destinations.indexOf(AppDestination.INSTRUMENT_CONFIG),
        pageCount = { destinations.size }
    )
    val coroutineScope = rememberCoroutineScope()

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

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
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.app_top_bar_title)) },
                    actions = {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(Res.string.menu_settings)
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.menu_settings)) },
                                onClick = { showOverflowMenu = false; showSettings = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.menu_about)) },
                                onClick = { showOverflowMenu = false; showAbout = true }
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { page ->
                when (destinations[page]) {
                    AppDestination.INSTRUMENT_CONFIG -> InstrumentConfigurationRoute()
                    AppDestination.SCALE_ENGINE -> ScaleCalculationScreen(
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

    if (showSettings) {
        var currentLocaleTag by remember(showSettings) {
            mutableStateOf(getCurrentLocaleTag())
        }
        SettingsDialog(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            currentLocaleTag = currentLocaleTag,
            onLocaleChange = { tag ->
                currentLocaleTag = tag
                changeLocale(tag)
            },
            onDismiss = { showSettings = false }
        )
    }

    if (showAbout) {
        val privacyPolicyUrl = stringResource(Res.string.about_privacy_policy_url)
        AboutDialog(
            onPrivacyPolicy = { openUrl(privacyPolicyUrl) },
            onDismiss = { showAbout = false }
        )
    }
}

@Composable
private fun SettingsDialog(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    currentLocaleTag: String,
    onLocaleChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themeOptions = listOf(
        "SYSTEM" to Res.string.settings_theme_system,
        "LIGHT" to Res.string.settings_theme_light,
        "DARK" to Res.string.settings_theme_dark,
    )
    // Native display names hardcoded — they must be readable regardless of current UI language
    val languageOptions = listOf(
        "system" to stringResource(Res.string.settings_language_system),
        "en" to "English",
        "fr" to "Français",
        "de" to "Deutsch",
        "es" to "Español",
        "it" to "Italiano",
        "hu" to "Magyar",
        "wo" to "Wolof",
        "mnk" to "Mandinka",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(Res.string.settings_theme_label),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(8.dp))
                themeOptions.forEach { (mode, labelRes) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeModeChange(mode) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(labelRes))
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.settings_language_label),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(8.dp))
                languageOptions.forEach { (tag, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLocaleChange(tag) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = currentLocaleTag == tag,
                            onClick = { onLocaleChange(tag) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_ok))
            }
        }
    )
}

@Composable
private fun AboutDialog(
    onPrivacyPolicy: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.about_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.about_version, "1.0.15"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.about_description),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.about_privacy_policy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onPrivacyPolicy() }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_ok))
            }
        }
    )
}

private enum class AppDestination(
    val labelRes: StringResource,
    val icon: ImageVector
) {
    INSTRUMENT_CONFIG(Res.string.nav_instrument_label, Icons.Default.Tune),
    SCALE_ENGINE(Res.string.nav_scale_label, Icons.Default.MusicNote),
    INSTANT_OVERVIEW(Res.string.nav_overview_label, Icons.Default.GridView),
    LIVE_TUNER(Res.string.nav_tuner_label, Icons.Default.GraphicEq),
    PRESETS(Res.string.nav_presets_label, Icons.Default.LibraryMusic),
}
