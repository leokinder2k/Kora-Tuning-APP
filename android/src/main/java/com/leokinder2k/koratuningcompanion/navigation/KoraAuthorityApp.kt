package com.leokinder2k.koratuningcompanion.navigation

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leokinder2k.koratuningcompanion.BuildConfig
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.EnharmonicDisplayState
import com.leokinder2k.koratuningcompanion.instrumentconfig.model.EnharmonicPreference
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.instrumentconfig.ui.InstrumentConfigurationRoute
import com.leokinder2k.koratuningcompanion.instrumentconfig.ui.TraditionalPresetsRoute
import com.leokinder2k.koratuningcompanion.leverharp.LeverHarpRoute
import com.leokinder2k.koratuningcompanion.livetuner.ui.LiveTunerRoute
import com.leokinder2k.koratuningcompanion.notation.ui.KoraNotationRoute
import com.leokinder2k.koratuningcompanion.scaleengine.ui.GuidedSetupScreen
import com.leokinder2k.koratuningcompanion.scaleengine.ui.InstantOverviewScreen
import com.leokinder2k.koratuningcompanion.scaleengine.ui.ScaleCalculationScreen
import com.leokinder2k.koratuningcompanion.scaleengine.ui.ScaleCalculationViewModel
import com.leokinder2k.koratuningcompanion.synth.SynthRoute
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun KoraAuthorityApp(
    modifier: Modifier = Modifier,
    themeMode: String = "SYSTEM",
    onThemeModeChange: (String) -> Unit = {},
    navigationTabOrder: String = "",
    visibleNavigationTabs: String = "",
    onNavigationTabsChange: (visibleTabNames: List<String>, tabOrderNames: List<String>) -> Unit = { _, _ -> }
) {
    val appContext = LocalContext.current.applicationContext
    val scaleViewModelFactory = remember(appContext) {
        ScaleCalculationViewModel.factory(appContext)
    }

    val destinationOrder = remember(navigationTabOrder) {
        parseDestinationOrder(navigationTabOrder)
    }
    val destinations = remember(destinationOrder, visibleNavigationTabs) {
        destinationOrder.visibleDestinations(visibleNavigationTabs)
    }
    val pagerState = rememberPagerState(
        initialPage = destinations.indexOf(AppDestination.INSTRUMENT_CONFIG).takeIf { it >= 0 } ?: 0,
        pageCount = { destinations.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val selectedPage by remember(destinations) {
        derivedStateOf { pagerState.currentPage.coerceIn(0, destinations.lastIndex) }
    }
    val selectedDestination by remember(destinations) {
        derivedStateOf { destinations[selectedPage] }
    }
    var selectedDestinationName by rememberSaveable {
        mutableStateOf(AppDestination.INSTRUMENT_CONFIG.name)
    }

    var isMuted by rememberSaveable { mutableStateOf(false) }
    var enharmonicPreferenceName by rememberSaveable { mutableStateOf(EnharmonicPreference.SHARPS.name) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTabSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val enharmonicPreference = EnharmonicPreference.valueOf(enharmonicPreferenceName)

    SideEffect {
        EnharmonicDisplayState.preference = enharmonicPreference
    }

    LaunchedEffect(destinations) {
        val targetPage = destinations.indexOfFirst { it.name == selectedDestinationName }
        if (targetPage >= 0) {
            if (pagerState.currentPage != targetPage) {
                pagerState.scrollToPage(targetPage)
            }
        } else {
            val fallbackPage = pagerState.currentPage.coerceIn(0, destinations.lastIndex)
            selectedDestinationName = destinations[fallbackPage].name
            if (pagerState.currentPage != fallbackPage) {
                pagerState.scrollToPage(fallbackPage)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        selectedDestinationName = destinations.getOrNull(pagerState.currentPage)?.name
            ?: selectedDestinationName
    }

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
                    selected = destination == selectedDestination,
                    onClick = {
                        if (index != selectedPage) {
                            selectedDestinationName = destination.name
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
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
                        TextButton(
                            onClick = {
                                enharmonicPreferenceName = if (enharmonicPreference == EnharmonicPreference.SHARPS) {
                                    EnharmonicPreference.FLATS.name
                                } else {
                                    EnharmonicPreference.SHARPS.name
                                }
                            },
                            modifier = Modifier.testTag("enharmonic-toggle")
                        ) {
                            Text(
                                text = stringResource(
                                    if (enharmonicPreference == EnharmonicPreference.SHARPS) {
                                        R.string.enharmonic_preference_sharps
                                    } else {
                                        R.string.enharmonic_preference_flats
                                    }
                                )
                            )
                        }
                        IconButton(onClick = { isMuted = !isMuted }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = stringResource(if (isMuted) R.string.action_unmute else R.string.action_mute),
                                tint = if (isMuted) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showOverflowMenu = true }, modifier = Modifier.size(48.dp)) {
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
                                text = { Text(stringResource(R.string.menu_customize_tabs)) },
                                onClick = { showOverflowMenu = false; showTabSettings = true }
                            )
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = androidx.compose.ui.Alignment.TopCenter
            ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                key = { page -> destinations[page].name },
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxHeight()
            ) { page ->
                when (destinations[page]) {
                    AppDestination.INSTRUMENT_CONFIG -> InstrumentConfigurationRoute(
                        enharmonicPreference = enharmonicPreference,
                        isMuted = isMuted,
                        onToggleMute = { isMuted = !isMuted },
                        isActive = page == selectedPage
                    )
                    AppDestination.SCALE_ENGINE -> {
                        val scaleViewModel: ScaleCalculationViewModel = viewModel(
                            factory = scaleViewModelFactory
                        )
                        val scaleUiState by scaleViewModel.uiState.collectAsStateWithLifecycle()
                        ScaleCalculationScreen(
                            enharmonicPreference = enharmonicPreference,
                            uiState = scaleUiState,
                            onRootNoteSelected = scaleViewModel::onScaleRootNoteSelected,
                            onScaleTypeSelected = scaleViewModel::onScaleTypeSelected,
                            onScaleRootReferenceSelected = scaleViewModel::onScaleRootReferenceSelected
                        )
                    }
                    AppDestination.GUIDED_SETUP -> {
                        val scaleViewModel: ScaleCalculationViewModel = viewModel(
                            factory = scaleViewModelFactory
                        )
                        val scaleUiState by scaleViewModel.uiState.collectAsStateWithLifecycle()
                        GuidedSetupScreen(
                            enharmonicPreference = enharmonicPreference,
                            uiState = scaleUiState,
                            onScaleTypeSelected = scaleViewModel::onScaleTypeSelected
                        )
                    }
                    AppDestination.INSTANT_OVERVIEW -> {
                        val scaleViewModel: ScaleCalculationViewModel = viewModel(
                            factory = scaleViewModelFactory
                        )
                        val scaleUiState by scaleViewModel.uiState.collectAsStateWithLifecycle()
                        InstantOverviewScreen(
                            enharmonicPreference = enharmonicPreference,
                            uiState = scaleUiState,
                            onScaleTypeSelected = scaleViewModel::onScaleTypeSelected,
                            onScaleRootReferenceSelected = scaleViewModel::onScaleRootReferenceSelected,
                            isMuted = isMuted,
                            onToggleMute = { isMuted = !isMuted }
                        )
                    }
                    AppDestination.LIVE_TUNER -> {
                        val scaleViewModel: ScaleCalculationViewModel = viewModel(
                            factory = scaleViewModelFactory
                        )
                        val scaleUiState by scaleViewModel.uiState.collectAsStateWithLifecycle()
                        LiveTunerRoute(
                            enharmonicPreference = enharmonicPreference,
                            scaleUiState = scaleUiState,
                            onScaleTypeSelected = scaleViewModel::onScaleTypeSelected,
                            isMuted = isMuted,
                            onToggleMute = { isMuted = !isMuted }
                        )
                    }
                    AppDestination.LEVER_HARP -> LeverHarpRoute(
                        enharmonicPreference = enharmonicPreference,
                        isMuted = isMuted,
                        modifier = Modifier.fillMaxSize()
                    )
                    AppDestination.SYNTH -> SynthRoute(
                        modifier = Modifier.fillMaxSize()
                    )
                    AppDestination.PRESETS -> TraditionalPresetsRoute(
                        enharmonicPreference = enharmonicPreference
                    )
                    AppDestination.NOTATION -> KoraNotationRoute(isMuted = isMuted)
                }
            }
            } // Box
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
            tabOrder = destinationOrder,
            visibleDestinations = destinations,
            onTabVisibilityChange = { destination, isVisible ->
                val visible = if (isVisible) {
                    (destinations + destination).distinct().orderedBy(destinationOrder)
                } else {
                    destinations.filterNot { it == destination }
                }
                if (visible.isNotEmpty()) {
                    onNavigationTabsChange(visible.map { it.name }, destinationOrder.map { it.name })
                }
            },
            onMoveTab = { destination, delta ->
                val updatedOrder = destinationOrder.moveBy(destination, delta)
                onNavigationTabsChange(
                    destinations.orderedBy(updatedOrder).map { it.name },
                    updatedOrder.map { it.name }
                )
            },
            onDismiss = { showSettings = false }
        )
    }

    if (showTabSettings) {
        TabSettingsDialog(
            tabOrder = destinationOrder,
            visibleDestinations = destinations,
            onTabVisibilityChange = { destination, isVisible ->
                val visible = if (isVisible) {
                    (destinations + destination).distinct().orderedBy(destinationOrder)
                } else {
                    destinations.filterNot { it == destination }
                }
                if (visible.isNotEmpty()) {
                    onNavigationTabsChange(visible.map { it.name }, destinationOrder.map { it.name })
                }
            },
            onMoveTab = { destination, delta ->
                val updatedOrder = destinationOrder.moveBy(destination, delta)
                onNavigationTabsChange(
                    destinations.orderedBy(updatedOrder).map { it.name },
                    updatedOrder.map { it.name }
                )
            },
            onDismiss = { showTabSettings = false }
        )
    }

    if (showAbout) {
        val appContext = LocalContext.current
        val privacyPolicyUrl = stringResource(R.string.about_privacy_policy_url)
        val supportUrl = stringResource(R.string.about_support_url)
        fun openExternalUrl(url: String) {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )
            appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        AboutDialog(
            onPrivacyPolicy = { openExternalUrl(privacyPolicyUrl) },
            onSupport = { openExternalUrl(supportUrl) },
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
    tabOrder: List<AppDestination>,
    visibleDestinations: List<AppDestination>,
    onTabVisibilityChange: (AppDestination, Boolean) -> Unit,
    onMoveTab: (AppDestination, Int) -> Unit,
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
                TabSettingsContent(
                    tabOrder = tabOrder,
                    visibleDestinations = visibleDestinations,
                    onTabVisibilityChange = onTabVisibilityChange,
                    onMoveTab = onMoveTab
                )

                Spacer(Modifier.height(16.dp))

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
                            .selectable(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = null
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
                            .selectable(
                                selected = currentLocaleTag == tag,
                                onClick = { onLocaleChange(tag) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = currentLocaleTag == tag,
                            onClick = null
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

@Composable
private fun TabSettingsDialog(
    tabOrder: List<AppDestination>,
    visibleDestinations: List<AppDestination>,
    onTabVisibilityChange: (AppDestination, Boolean) -> Unit,
    onMoveTab: (AppDestination, Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_tabs_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                TabSettingsContent(
                    tabOrder = tabOrder,
                    visibleDestinations = visibleDestinations,
                    onTabVisibilityChange = onTabVisibilityChange,
                    onMoveTab = onMoveTab
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

@Composable
private fun TabSettingsContent(
    tabOrder: List<AppDestination>,
    visibleDestinations: List<AppDestination>,
    onTabVisibilityChange: (AppDestination, Boolean) -> Unit,
    onMoveTab: (AppDestination, Int) -> Unit
) {
    Text(
        text = stringResource(R.string.settings_tabs_label),
        style = MaterialTheme.typography.labelLarge
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.settings_tabs_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (visibleDestinations.size == 1) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_tabs_keep_one),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(8.dp))
    tabOrder.forEachIndexed { index, destination ->
        val isVisible = destination in visibleDestinations
        val isOnlyVisibleTab = isVisible && visibleDestinations.size == 1
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-tab-${destination.name}")
                .padding(vertical = 2.dp)
        ) {
            Checkbox(
                checked = isVisible,
                enabled = !isOnlyVisibleTab,
                onCheckedChange = { checked ->
                    onTabVisibilityChange(destination, checked)
                }
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(destination.labelRes),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                enabled = index > 0,
                onClick = { onMoveTab(destination, -1) }
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.settings_tabs_move_up)
                )
            }
            IconButton(
                enabled = index < tabOrder.lastIndex,
                onClick = { onMoveTab(destination, 1) }
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.settings_tabs_move_down)
                )
            }
        }
    }
}

private fun getCurrentLocaleTag(): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    val firstLocaleTag = locales[0]?.toLanguageTag().orEmpty()
    return firstLocaleTag.ifEmpty { "system" }
}

private fun parseDestinationOrder(savedNames: String): List<AppDestination> {
    return NavigationTabSettings
        .parseOrder(savedNames, AppDestination.entries.map { it.name })
        .mapNotNull { name -> AppDestination.entries.firstOrNull { it.name == name } }
}

private fun List<AppDestination>.visibleDestinations(savedNames: String): List<AppDestination> {
    val destinationsByName = associateBy { it.name }
    return NavigationTabSettings
        .parseVisible(map { it.name }, savedNames)
        .mapNotNull(destinationsByName::get)
}

private fun List<AppDestination>.orderedBy(order: List<AppDestination>): List<AppDestination> {
    val destinationSet = toSet()
    return order.filter { it in destinationSet }
}

private fun List<AppDestination>.moveBy(destination: AppDestination, delta: Int): List<AppDestination> {
    val destinationsByName = associateBy { it.name }
    return NavigationTabSettings
        .moveBy(map { it.name }, destination.name, delta)
        .mapNotNull(destinationsByName::get)
}

@Composable
private fun AboutDialog(
    onPrivacyPolicy: () -> Unit,
    onSupport: () -> Unit,
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
                    modifier = Modifier
                        .semantics { role = Role.Button }
                        .clickable { onPrivacyPolicy() }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.about_support),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .semantics { role = Role.Button }
                        .clickable { onSupport() }
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
    GUIDED_SETUP(R.string.nav_guided_label, Icons.Default.Tune),
    INSTANT_OVERVIEW(R.string.nav_overview_label, Icons.Default.GridView),
    LIVE_TUNER(R.string.nav_tuner_label, Icons.Default.GraphicEq),
    LEVER_HARP(R.string.nav_lever_harp_label, Icons.Default.Piano),
    SYNTH(R.string.nav_synth_label, Icons.Default.Piano),
    PRESETS(R.string.nav_presets_label, Icons.Default.LibraryMusic),
    NOTATION(R.string.nav_notation_label, Icons.Default.Piano),
}
