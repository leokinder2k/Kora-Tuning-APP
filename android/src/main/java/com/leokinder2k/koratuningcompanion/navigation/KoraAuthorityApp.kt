package com.leokinder2k.koratuningcompanion.navigation

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leokinder2k.koratuningcompanion.BuildConfig
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.instrumentconfig.ui.InstrumentConfigurationRoute
import com.leokinder2k.koratuningcompanion.instrumentconfig.ui.TraditionalPresetsRoute
import com.leokinder2k.koratuningcompanion.livetuner.ui.LiveTunerRoute
import com.leokinder2k.koratuningcompanion.scaleengine.ui.InstantOverviewScreen
import com.leokinder2k.koratuningcompanion.scaleengine.ui.ScaleCalculationScreen
import com.leokinder2k.koratuningcompanion.scaleengine.ui.ScaleCalculationViewModel
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun KoraAuthorityApp(
    modifier: Modifier = Modifier,
    themeMode: String = "SYSTEM",
    onThemeModeChange: (String) -> Unit = {}
) {
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
            // NavigationSuiteScaffold already manages system insets; zero out the
            // inner Scaffold's insets so they are not double-applied.
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_top_bar_title)) },
                    actions = {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.menu_settings)
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_settings)) },
                                onClick = { showOverflowMenu = false; showSettings = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_about)) },
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
                if (tag == "system") {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                } else {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                }
            },
            onDismiss = { showSettings = false }
        )
    }

    if (showAbout) {
        val appContext = LocalContext.current
        val privacyPolicyUrl = stringResource(R.string.about_privacy_policy_url)
        AboutDialog(
            onPrivacyPolicy = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(privacyPolicyUrl)
                )
                appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
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
        "SYSTEM" to R.string.settings_theme_system,
        "LIGHT" to R.string.settings_theme_light,
        "DARK" to R.string.settings_theme_dark,
    )
    // Native display names hardcoded — they must be readable regardless of current UI language
    val languageOptions = listOf(
        "system" to stringResource(R.string.settings_language_system),
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
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Theme section
                Text(
                    text = stringResource(R.string.settings_theme_label),
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

                // Language section
                Text(
                    text = stringResource(R.string.settings_language_label),
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
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

private fun getCurrentLocaleTag(): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    val firstLocaleTag = locales[0]?.toLanguageTag().orEmpty()
    return firstLocaleTag.ifEmpty { "system" }
}

@Composable
private fun AboutDialog(
    onPrivacyPolicy: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.about_privacy_policy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onPrivacyPolicy() }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

private enum class AppDestination(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
) {
    INSTRUMENT_CONFIG(R.string.nav_instrument_label, Icons.Default.Tune),
    SCALE_ENGINE(R.string.nav_scale_label, Icons.Default.MusicNote),
    INSTANT_OVERVIEW(R.string.nav_overview_label, Icons.Default.GridView),
    LIVE_TUNER(R.string.nav_tuner_label, Icons.Default.GraphicEq),
    PRESETS(R.string.nav_presets_label, Icons.Default.LibraryMusic),
}
