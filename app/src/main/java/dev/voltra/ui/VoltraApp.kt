package dev.voltra.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.voltra.CsvExport
import dev.voltra.R
import dev.voltra.ui.screens.HealthScreen
import dev.voltra.ui.screens.HistoryScreen
import dev.voltra.ui.screens.OverviewScreen
import dev.voltra.ui.screens.SettingsScreen
import kotlinx.coroutines.delay

private data class NavItemSpec(
    val destination: NavDestination,
    val labelRes: Int,
    val headerRes: Int,
    val icon: ImageVector,
)

private val NavItems = listOf(
    NavItemSpec(NavDestination.OVERVIEW, R.string.nav_overview, R.string.header_overview, Icons.Rounded.Bolt),
    NavItemSpec(NavDestination.HISTORY, R.string.nav_history, R.string.header_history, Icons.Rounded.Timeline),
    NavItemSpec(NavDestination.HEALTH, R.string.nav_health, R.string.header_health, Icons.Rounded.Favorite),
    NavItemSpec(NavDestination.SETTINGS, R.string.nav_settings, R.string.header_settings, Icons.Rounded.Settings),
)

@Composable
fun VoltraApp(
    viewModel: VoltraViewModel,
    isActive: Boolean,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val monitorPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onMonitorPermissionResult(granted)
    }
    val chargeAlertPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onChargeAlertPermissionResult(granted)
    }
    val tempAlertPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onTempAlertPermissionResult(granted)
    }
    val lowAlertPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onLowAlertPermissionResult(granted)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportCsv(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(isActive) {
        while (isActive) {
            viewModel.refresh()
            delay(15_000L)
        }
    }

    fun requestNotificationOrRun(
        launcher: androidx.activity.result.ActivityResultLauncher<String>,
        onAllowed: () -> Unit,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onAllowed()
        }
    }

    fun triggerCsvExport() {
        val current = state.snapshot
        if (current == null || current.observations.isEmpty()) {
            viewModel.notifyEmptyExport()
        } else {
            exportLauncher.launch(CsvExport.fileName(current.reading.time))
        }
    }

    fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        }
        runCatching { context.startActivity(intent) }
    }

    // Confirmation dialogs
    state.pendingRetentionDays?.let { days ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRetentionDialog,
            title = { Text(stringResource(R.string.dialog_shorten_retention_title)) },
            text = { Text(stringResource(R.string.dialog_shorten_retention_body, days)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRetentionChange) {
                    Text(stringResource(R.string.action_delete_old_data))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRetentionDialog) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (state.confirmClearDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearHistory,
            title = { Text(stringResource(R.string.dialog_clear_title)) },
            text = { Text(stringResource(R.string.dialog_clear_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::clearHistory) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearHistory) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val currentSnapshot = state.snapshot
    if (currentSnapshot == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val currentNav = NavItems.first { it.destination == state.selectedTab }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val useRail = maxWidth >= 600.dp
        val metricColumns = if (maxWidth >= 720.dp) 4 else 2

        val body: @Composable (Modifier) -> Unit = { contentModifier ->
            Column(
                modifier = contentModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.brand_wordmark),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp,
                        )
                        Text(
                            text = stringResource(currentNav.headerRes),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (state.selectedTab != NavDestination.SETTINGS) {
                        IconButton(
                            onClick = { viewModel.selectTab(NavDestination.SETTINGS) },
                            modifier = Modifier.background(
                                MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(16.dp),
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.cd_open_settings),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (state.selectedTab) {
                        NavDestination.OVERVIEW -> OverviewScreen(
                            state = state,
                            snapshot = currentSnapshot,
                            metricColumns = metricColumns,
                            onRefresh = { viewModel.refresh(userInitiated = true) },
                            onOpenSettings = { viewModel.selectTab(NavDestination.SETTINGS) },
                            onDismissSetup = viewModel::dismissSetupCard,
                        )
                        NavDestination.HISTORY -> HistoryScreen(
                            state = state,
                            snapshot = currentSnapshot,
                            onSelectMetric = viewModel::selectChartMetric,
                            onSelectDays = viewModel::selectChartDays,
                            onShowMoreSessions = viewModel::showMoreSessions,
                            onExportCsv = ::triggerCsvExport,
                        )
                        NavDestination.HEALTH -> HealthScreen(
                            state = state,
                            snapshot = currentSnapshot,
                            onOpenSettings = { viewModel.selectTab(NavDestination.SETTINGS) },
                        )
                        NavDestination.SETTINGS -> SettingsScreen(
                            state = state,
                            retentionDays = currentSnapshot.retentionDays,
                            onDesignInputChanged = viewModel::onDesignInputChanged,
                            onSaveDesign = viewModel::updateDesignCapacity,
                            onSelectMultiplier = viewModel::setCurrentMultiplier,
                            onToggleMonitoring = { enabled ->
                                if (enabled) {
                                    requestNotificationOrRun(monitorPermission) { viewModel.setMonitoring(true) }
                                } else {
                                    viewModel.setMonitoring(false)
                                }
                            },
                            onToggleBackground = viewModel::setBackgroundSampling,
                            onToggleChargeAlert = { enabled ->
                                if (enabled) {
                                    requestNotificationOrRun(chargeAlertPermission) { viewModel.setChargeAlertEnabled(true) }
                                } else {
                                    viewModel.setChargeAlertEnabled(false)
                                }
                            },
                            onPreviewChargeThreshold = viewModel::previewChargeAlertThreshold,
                            onCommitChargeThreshold = viewModel::commitChargeAlertThreshold,
                            onToggleTempAlert = { enabled ->
                                if (enabled) {
                                    requestNotificationOrRun(tempAlertPermission) { viewModel.setTempAlertEnabled(true) }
                                } else {
                                    viewModel.setTempAlertEnabled(false)
                                }
                            },
                            onPreviewTempThreshold = viewModel::previewTempAlertThreshold,
                            onCommitTempThreshold = viewModel::commitTempAlertThreshold,
                            onToggleLowAlert = { enabled ->
                                if (enabled) {
                                    requestNotificationOrRun(lowAlertPermission) { viewModel.setLowAlertEnabled(true) }
                                } else {
                                    viewModel.setLowAlertEnabled(false)
                                }
                            },
                            onPreviewLowThreshold = viewModel::previewLowAlertThreshold,
                            onCommitLowThreshold = viewModel::commitLowAlertThreshold,
                            onOpenNotificationSettings = ::openNotificationSettings,
                            onSelectTheme = viewModel::setThemeMode,
                            onSelectTemperatureUnit = viewModel::setTemperatureUnit,
                            onSelectRetention = viewModel::requestRetentionChange,
                            onExportCsv = ::triggerCsvExport,
                            onRequestClearHistory = viewModel::requestClearHistory,
                        )
                    }
                }
            }
        }

        if (useRail) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End,
                ),
            ) { innerPadding ->
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        windowInsets = WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Vertical + WindowInsetsSides.Start,
                        ),
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Spacer(Modifier.height(12.dp))
                        NavItems.forEach { item ->
                            NavigationRailItem(
                                selected = state.selectedTab == item.destination,
                                onClick = { viewModel.selectTab(item.destination) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                    body(
                        Modifier
                            .weight(1f)
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding),
                    )
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        windowInsets = WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ) {
                        NavItems.forEach { item ->
                            NavigationBarItem(
                                selected = state.selectedTab == item.destination,
                                onClick = { viewModel.selectTab(item.destination) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                },
            ) { innerPadding ->
                body(
                    Modifier
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                )
            }
        }
    }
}
