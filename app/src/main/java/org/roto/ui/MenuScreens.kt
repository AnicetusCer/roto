package org.roto.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch
import org.roto.R
import org.roto.data.MenuSelection
import org.roto.data.MenuSelectionType
import org.roto.data.RecentRota
import org.roto.data.ThemeMode
import org.roto.data.ThemeOption
import org.roto.domain.DayDataSource
import org.roto.domain.DayResult
import org.roto.domain.SlotEntry

@Composable
fun MenuRoot(
    viewModel: MenuViewModel,
    themeOption: ThemeOption,
    themeMode: ThemeMode,
    onThemeChange: (ThemeOption) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    applySystemPadding: Boolean,
    onSystemPaddingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // not fatal; we'll rely on transient access
            }
            viewModel.onExternalFileChosen(context, uri)
        }
    }

    MenuScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onChooseFile = { openDocumentLauncher.launch(arrayOf("application/json", "text/plain")) },
        onSubmitSharedLink = viewModel::submitSharedLink,
        onCopyInstructions = {
            clipboard.setText(AnnotatedString(viewModel.getAiInstructions()))
        },
        onCopySample = viewModel::copySampleToDownloads,
        onApplySampleSelection = viewModel::applySampleSelection,
        onDismissSamplePrompt = viewModel::dismissSampleCopyPrompt,
        onClearMenu = viewModel::clearMenuSelection,
        onSelectWeek = viewModel::selectWeek,
        onClearWeek = viewModel::clearSelectedWeek,
        themeOption = themeOption,
        onThemeChange = onThemeChange,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        applySystemPadding = applySystemPadding,
        onSystemPaddingChange = onSystemPaddingChange,
        recentRotas = state.recentRotas,
        recentLimit = state.recentLimit,
        onOpenRecent = viewModel::openRecent,
        onRecentLimitChange = viewModel::setRecentLimit,
        onClearRecent = viewModel::clearRecentRotas,
        onRenameRecent = viewModel::renameRecent,
        modifier = modifier
    )
}

@Composable
fun MenuScreen(
    state: MenuUiState,
    onRefresh: () -> Unit,
    onChooseFile: () -> Unit,
    onSubmitSharedLink: (String) -> Unit,
    onCopyInstructions: () -> Unit,
    onCopySample: (String) -> Unit,
    onApplySampleSelection: (MenuSelection) -> Unit,
    onDismissSamplePrompt: () -> Unit,
    onClearMenu: () -> Unit,
    onSelectWeek: (String) -> Unit,
    onClearWeek: () -> Unit,
    themeOption: ThemeOption,
    themeMode: ThemeMode,
    onThemeChange: (ThemeOption) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    applySystemPadding: Boolean,
    onSystemPaddingChange: (Boolean) -> Unit,
    recentRotas: List<RecentRota>,
    recentLimit: Int,
    onOpenRecent: (RecentRota) -> Unit,
    onRecentLimitChange: (Int) -> Unit,
    onClearRecent: () -> Unit,
    onRenameRecent: (RecentRota, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSetupWhileLoaded by rememberSaveable { mutableStateOf(false) }
    val hasSelection = state.selectedSourceLabel != "No rota selected"
    var showSharedLinkDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(state.selectedSourceLabel, state.hasMenuData) {
        if (state.hasMenuData) {
            showSetupWhileLoaded = false
        }
    }

    if (showSharedLinkDialog) {
        SharedLinkDialog(
            initialValue = state.remoteUrl.orEmpty(),
            onSubmit = {
                onSubmitSharedLink(it)
                showSharedLinkDialog = false
            },
            onDismiss = { showSharedLinkDialog = false }
        )
    }

    when {
        state.isLoading -> LoadingState(modifier)
        state.hasMenuData && !showSetupWhileLoaded -> MenuContent(
            state = state,
            onRefresh = onRefresh,
            onChooseFile = onChooseFile,
            onUseSharedLink = { showSharedLinkDialog = true },
            onCopyInstructions = onCopyInstructions,
            onBackToSetup = { showSetupWhileLoaded = true },
            onSelectWeek = onSelectWeek,
            onClearWeek = onClearWeek,
            modifier = modifier
        )
        else -> SetupState(
            onChooseFile = onChooseFile,
            onUseSharedLink = { showSharedLinkDialog = true },
            onCopyInstructions = onCopyInstructions,
            onCopySample = onCopySample,
            onApplySampleSelection = onApplySampleSelection,
            onDismissSamplePrompt = onDismissSamplePrompt,
            showClear = hasSelection,
            onClearMenu = onClearMenu,
            onViewCurrent = { showSetupWhileLoaded = false },
            onRefresh = onRefresh,
            currentSelectionLabel = state.selectedSourceLabel.takeUnless { it == "No rota selected" },
            sourceLabel = state.selectedSourceLabel,
            sourceType = state.selectedSourceType,
            remoteStatus = state.remoteStatus,
            message = state.setupMessage,
            sampleCopyPrompt = state.sampleCopyPrompt,
            onOpenSettings = { showSettings = true },
            recentRotas = recentRotas,
            recentLimit = recentLimit,
            onOpenRecent = onOpenRecent,
            onClearRecent = onClearRecent,
            onRenameRecent = onRenameRecent,
            modifier = modifier
        )
    }

    if (showSettings) {
        SettingsDialog(
            themeOption = themeOption,
            onThemeChange = onThemeChange,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            applySystemPadding = applySystemPadding,
            onSystemPaddingChange = onSystemPaddingChange,
            recentLimit = recentLimit,
            onRecentLimitChange = onRecentLimitChange,
            onClearRecent = onClearRecent,
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun SettingsDialog(
    themeOption: ThemeOption,
    onThemeChange: (ThemeOption) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    applySystemPadding: Boolean,
    onSystemPaddingChange: (Boolean) -> Unit,
    recentLimit: Int,
    onRecentLimitChange: (Int) -> Unit,
    onClearRecent: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppearanceSelector(
                    selected = themeMode,
                    onSelect = onThemeModeChange
                )
                ThemeSelector(
                    selected = themeOption,
                    onSelect = {
                        onThemeChange(it)
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Safe area padding",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Pad around status/nav bars to avoid overlap",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = applySystemPadding,
                        onCheckedChange = onSystemPaddingChange
                    )
                }
                RecentListSettings(
                    currentLimit = recentLimit,
                    onLimitChange = onRecentLimitChange,
                    onClearRecent = onClearRecent
                )
            }
        }
    )
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text("Loading rota…")
    }
}

@Composable
private fun SetupState(
    onChooseFile: () -> Unit,
    onUseSharedLink: () -> Unit,
    onCopyInstructions: () -> Unit,
    onCopySample: (String) -> Unit,
    onApplySampleSelection: (MenuSelection) -> Unit,
    onDismissSamplePrompt: () -> Unit,
    showClear: Boolean,
    onClearMenu: () -> Unit,
    onViewCurrent: () -> Unit,
    onRefresh: () -> Unit,
    currentSelectionLabel: String?,
    sourceLabel: String,
    sourceType: MenuSelectionType?,
    remoteStatus: RemoteStatusUi?,
    message: SetupMessage?,
    sampleCopyPrompt: SampleCopyPrompt?,
    onOpenSettings: () -> Unit,
    recentRotas: List<RecentRota>,
    recentLimit: Int,
    onOpenRecent: (RecentRota) -> Unit,
    onClearRecent: () -> Unit,
    onRenameRecent: (RecentRota, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sampleFiles = remember(context) {
        context.assets.list("sample_rotas")?.toList()?.filter { it.endsWith(".json") }?.sorted() ?: emptyList()
    }
    var showInstructions by remember { mutableStateOf(false) }

    sampleCopyPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = onDismissSamplePrompt,
            confirmButton = {
                TextButton(
                    onClick = {
                        val selection = prompt.pendingSelection
                        if (selection != null) {
                            onApplySampleSelection(selection)
                            onDismissSamplePrompt()
                        } else {
                            onDismissSamplePrompt()
                            onChooseFile()
                        }
                    }
                ) { Text("Load now") }
            },
            dismissButton = {
                TextButton(onClick = onDismissSamplePrompt) { Text("Later") }
            },
            title = { Text("Sample copied") },
            text = { Text("Open ${prompt.locationHint} now?") }
        )
    }

    if (showInstructions) {
        InstructionsDialog(
            sampleFiles = sampleFiles,
            onCopyHelperPrompt = onCopyInstructions,
            onCopySample = onCopySample,
            onDismiss = { showInstructions = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_roto),
            contentDescription = null,
            modifier = Modifier.size(108.dp)
        )

        val sourceText = if (sourceLabel == "No rota selected") {
            "No rota loaded yet"
        } else {
            "Current rota: $sourceLabel"
        }

        Text(
            text = sourceText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onChooseFile,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Load rota file")
        }

        Button(
            onClick = onUseSharedLink,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use shared link")
        }

        currentSelectionLabel?.let { label ->
            val (statusText, statusColor) = when {
                sourceType == MenuSelectionType.REMOTE_LINK && remoteStatus != null -> {
                    if (remoteStatus.isUsingCache) {
                        "Cached from ${formatLastSynced(remoteStatus.lastSyncedEpochMillis)}." to MaterialTheme.colorScheme.tertiary
                    } else {
                        "Last synced ${formatLastSynced(remoteStatus.lastSyncedEpochMillis)}." to MaterialTheme.colorScheme.primary
                    }
                }
                sourceType == MenuSelectionType.REMOTE_LINK -> "Shared link ready. Refresh to pull latest." to MaterialTheme.colorScheme.primary
                else -> "Local file loaded" to MaterialTheme.colorScheme.primary
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Loaded rota",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = onViewCurrent,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("View", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                                Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                            TextButton(
                                onClick = onClearMenu,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("✕", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (recentRotas.isNotEmpty()) {
            RecentRotasList(
                recentRotas = recentRotas,
                limit = recentLimit,
                onOpenRecent = onOpenRecent,
                onClearRecent = onClearRecent,
                onRenameRecent = onRenameRecent
            )
        }

        Button(
            onClick = { showInstructions = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Instructions & sample rotas")
        }
        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Settings")
        }

        message?.let {
            val color = if (it.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Card(
                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = it.text,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
        }

        TipJarLinks()
        LegalLinks()
    }
}

@Composable
private fun RecentRotasList(
    recentRotas: List<RecentRota>,
    limit: Int,
    onOpenRecent: (RecentRota) -> Unit,
    onClearRecent: () -> Unit,
    onRenameRecent: (RecentRota, String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var renameTarget by remember { mutableStateOf<RecentRota?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recently opened (last $limit)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(
                onClick = onClearRecent,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Clear list")
            }
        }
        recentRotas.forEach { recent ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRecent(recent) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = recent.displayName?.takeIf { it.isNotBlank() }
                            ?: deriveRecentLabel(recent),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = deriveRecentSubtitle(recent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (recent.type == MenuSelectionType.REMOTE_LINK) "Shared link" else "Local file",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (recent.type == MenuSelectionType.REMOTE_LINK && recent.remoteUrl != null) {
                            TextButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, recent.remoteUrl)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share rota link"))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Share")
                            }
                            TextButton(
                                onClick = { clipboard.setText(AnnotatedString(recent.remoteUrl)) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Copy link")
                            }
                        }
                        TextButton(
                            onClick = {
                                renameTarget = recent
                                renameText = recent.displayName.orEmpty()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Rename")
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameRecent(target, renameText)
                        renameTarget = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
            title = { Text("Rename rota") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuContent(
    state: MenuUiState,
    onRefresh: () -> Unit,
    onChooseFile: () -> Unit,
    onUseSharedLink: () -> Unit,
    onCopyInstructions: () -> Unit,
    onBackToSetup: () -> Unit,
    onSelectWeek: (String) -> Unit,
    onClearWeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appTitle = state.rotaName.ifBlank { "Roto" }
    val scrollState = rememberScrollState()
    var showBrowse by remember { mutableStateOf(false) }
    val weekCardBringIntoView = remember { BringIntoViewRequester() }

    LaunchedEffect(state.weekMenus.size) {
        if (state.weekMenus.isEmpty()) {
            showBrowse = false
        }
    }

    LaunchedEffect(state.selectedWeekMenu?.id, showBrowse) {
        if (showBrowse && state.selectedWeekMenu != null) {
            weekCardBringIntoView.bringIntoView()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            text = appTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Image(
            painter = painterResource(id = R.drawable.roto_long_banner),
            contentDescription = null,
            modifier = Modifier
                .width(180.dp)
                .height(54.dp)
                .align(Alignment.CenterHorizontally)
        )

        SourceControls(
            selectedSourceLabel = state.selectedSourceLabel,
            selectedSourceType = state.selectedSourceType,
            remoteStatus = state.remoteStatus,
            onUseSharedLink = onUseSharedLink,
            onChooseFile = onChooseFile,
            onRefresh = onRefresh,
            showClear = state.selectedSourceLabel != "No rota selected",
            onClearMenu = onBackToSetup,
            onOpenSettings = null
        )

        state.setupMessage?.let { msg ->
            val color = if (msg.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
                Text(
                    text = msg.text,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
        }

        state.coverageStatus?.let { InfoCard(it.message) }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp)
        ) {
            Text(
                text = "Today's Rota",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            state.todayMenu?.let {
                MenuCard(
                    title = friendlyDate(it),
                    menu = it,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    borderColor = MaterialTheme.colorScheme.tertiary
                )
            } ?: Text("No rota recorded for today.")

            Text(
                text = "Tomorrow's Rota",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            state.tomorrowMenu?.let {
                MenuCard(
                    title = friendlyDate(it),
                    menu = it,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    borderColor = MaterialTheme.colorScheme.secondary
                )
            } ?: Text("No rota recorded for tomorrow.")
        }

        if (state.globalNotes.isNotEmpty()) {
            NotesColumn(title = "Notes", notes = state.globalNotes)
        }

        if (state.weekMenus.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    showBrowse = !showBrowse
                    if (!showBrowse) {
                        onClearWeek()
                    }
                }) {
                    Text(if (showBrowse) "Hide full rota" else "Browse full rota")
                }
                TextButton(onClick = onCopyInstructions) {
                    Text("Copy AI instructions")
                }
            }
            if (showBrowse) {
                Spacer(modifier = Modifier.height(12.dp))
                BrowseWeeksSection(
                    weekMenus = state.weekMenus,
                    selectedWeekMenu = state.selectedWeekMenu,
                    onSelectWeek = onSelectWeek,
                    onClearWeek = onClearWeek,
                    bringIntoViewRequester = weekCardBringIntoView
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCopyInstructions) {
                    Text("Copy AI instructions")
                }
            }
        }

        TipJarLinks(modifier = Modifier.fillMaxWidth())
        LegalLinks(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SourceControls(
    selectedSourceLabel: String,
    selectedSourceType: MenuSelectionType?,
    remoteStatus: RemoteStatusUi?,
    onUseSharedLink: () -> Unit,
    onChooseFile: () -> Unit,
    onRefresh: () -> Unit,
    showClear: Boolean,
    onClearMenu: () -> Unit,
    onOpenSettings: (() -> Unit)?
) {
    val isLoadedView = onOpenSettings == null
    val refreshPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    val compactButtonHeight = Modifier.heightIn(min = 32.dp)
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            text = "Rota source: $selectedSourceLabel",
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoadedView) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedSourceType == MenuSelectionType.REMOTE_LINK) {
                        val (message, color) = when {
                            remoteStatus == null -> "Shared link ready. Refresh to pull latest." to MaterialTheme.colorScheme.primary
                            remoteStatus.isUsingCache -> "Cached from ${formatLastSynced(remoteStatus.lastSyncedEpochMillis)}." to MaterialTheme.colorScheme.tertiary
                            else -> "Last synced ${formatLastSynced(remoteStatus.lastSyncedEpochMillis)}." to MaterialTheme.colorScheme.primary
                        }
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onRefresh,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = compactButtonHeight
                        ) {
                            Text("Refresh", style = MaterialTheme.typography.labelSmall)
                        }
                        if (showClear) {
                            TextButton(
                                onClick = onClearMenu,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = compactButtonHeight
                            ) {
                                Text("Back", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            } else {
                Button(
                    onClick = onChooseFile,
                    contentPadding = refreshPadding,
                    modifier = Modifier.heightIn(min = 36.dp)
                ) { Text("Load rota file") }
                Button(
                    onClick = onUseSharedLink,
                    contentPadding = refreshPadding,
                    modifier = Modifier.heightIn(min = 36.dp)
                ) { Text("Use shared link") }
                Spacer(modifier = Modifier.weight(1f))
                if (showClear) {
                    TextButton(
                        onClick = onClearMenu,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.heightIn(min = 28.dp)
                    ) {
                        Text("Back")
                    }
                }
                onOpenSettings?.let { open ->
                    TextButton(
                        onClick = open,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.heightIn(min = 28.dp)
                    ) {
                        Text("Settings", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (!isLoadedView && selectedSourceType == MenuSelectionType.REMOTE_LINK) {
            RemoteStatusInfo(
                status = remoteStatus,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RemoteStatusInfo(
    status: RemoteStatusUi?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (message, color) = when {
        status == null -> "Shared link ready. Refresh to pull latest." to MaterialTheme.colorScheme.primary
        status.isUsingCache -> "Using cached link from ${formatLastSynced(status.lastSyncedEpochMillis)}." to MaterialTheme.colorScheme.tertiary
        else -> "Last synced ${formatLastSynced(status.lastSyncedEpochMillis)}." to MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // URL/extra info omitted to keep height minimal
        }
        TextButton(
            onClick = onRefresh,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.heightIn(min = 28.dp)
        ) {
            Text("Refresh")
        }
    }
}

@Composable
private fun SharedLinkDialog(
    initialValue: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(url)
                }
            ) { Text("Save link") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Use shared link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Paste a GitHub Gist link (page or raw) or any HTTPS URL that returns JSON. We'll fetch it, mirror the file into Downloads/Roto, and keep it available offline.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Shared rota link") },
                    singleLine = true,
                    placeholder = { Text("https://gist.githubusercontent.com/.../raw/rota.json") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

private fun formatLastSynced(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.getDefault())
    return formatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
}

@Composable
private fun InfoCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = Icons.Filled.Info, contentDescription = null)
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RecentListSettings(
    currentLimit: Int,
    onLimitChange: (Int) -> Unit,
    onClearRecent: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Recent rotas",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LimitChip(label = "Last 5", value = 5, current = currentLimit, onSelect = onLimitChange)
            LimitChip(label = "Last 10", value = 10, current = currentLimit, onSelect = onLimitChange)
        }
        TextButton(
            onClick = onClearRecent,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("Clear recent list")
        }
    }
}

@Composable
private fun ThemeSelector(
    selected: ThemeOption,
    onSelect: (ThemeOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            ThemeChip(label = "Forest", option = ThemeOption.FOREST, selected = selected, onSelect = onSelect)
            ThemeChip(label = "Sunset", option = ThemeOption.SUNSET, selected = selected, onSelect = onSelect)
            ThemeChip(label = "Ocean", option = ThemeOption.OCEAN, selected = selected, onSelect = onSelect)
            ThemeChip(label = "Blossom", option = ThemeOption.BLOSSOM, selected = selected, onSelect = onSelect)
            ThemeChip(label = "Midnight", option = ThemeOption.MIDNIGHT, selected = selected, onSelect = onSelect)
            ThemeChip(label = "Sand", option = ThemeOption.SAND, selected = selected, onSelect = onSelect)
        }
    }
}

@Composable
private fun AppearanceSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(label = "System", mode = ThemeMode.SYSTEM, selected = selected, onSelect = onSelect)
            ModeChip(label = "Light", mode = ThemeMode.LIGHT, selected = selected, onSelect = onSelect)
            ModeChip(label = "Dark", mode = ThemeMode.DARK, selected = selected, onSelect = onSelect)
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    option: ThemeOption,
    selected: ThemeOption,
    onSelect: (ThemeOption) -> Unit
) {
    FilterChip(
        selected = selected == option,
        onClick = { onSelect(option) },
        label = { Text(label) },
        leadingIcon = if (selected == option) {
            { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
        } else null
    )
}

@Composable
private fun ModeChip(
    label: String,
    mode: ThemeMode,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    FilterChip(
        selected = selected == mode,
        onClick = { onSelect(mode) },
        label = { Text(label) },
        leadingIcon = if (selected == mode) {
            { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
        } else null
    )
}

@Composable
private fun LimitChip(
    label: String,
    value: Int,
    current: Int,
    onSelect: (Int) -> Unit
) {
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
        leadingIcon = if (current == value) {
            { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
        } else null
    )
}

@Composable
private fun NotesColumn(title: String, notes: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            notes.forEach { note ->
                Text("• $note", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun InstructionsDialog(
    sampleFiles: List<String>,
    onCopyHelperPrompt: () -> Unit,
    onCopySample: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showSchema by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val quickStartRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        title = { Text("Get a rota file") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Quick start instructions below",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            quickStartRequester.bringIntoView()
                        }
                    },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text("Jump to Quick Start")
                }
                Text("This app can help you build a rota or weekly schedule.")
                Text("I originally wrote it to display a weekly school dinner menu, but realised it’s useful for many schedules such as:")
                val examples = listOf(
                    "Garbage collection days.",
                    "Work shifts.",
                    "Seasonal events.",
                    "One time team production schedule timelines."
                )
                examples.forEach { example ->
                    Text("- $example")
                }
                Text("The rota schema (a JSON structure) can be created by hand, but it’s much faster and easier to ask an AI to build it for you. Copy the helper prompt below, paste it into your favorite AI (ChatGPT, Gemini etc), then follow its instructions—I’ve tested this with photos, text docs, and simple chats.")
                Text("At the end of the conversation the AI will provide text output (the rota file contents); save this to a file. Even though the data is JSON, it’s fine to store it as .txt for readability on Android—the app only cares that the structure is valid.")
                Text("Once saved, you can load the file in the app, share it with others or post it to a website and share the link (I recommend GitHub Gist).")
                Text("The \"Copy Prompt\" button remains available even after you’ve loaded a rota. Use it any time you want AI to tweak an existing file: paste the current rota file contents into the AI prompt, describe the changes you want, and repeat as needed.")
                Text("Please double-check any rota details an AI generates—AI can get things wrong. If something looks off, ask the AI to fix it save the updated file (keep the same name) and then tap Refresh in Roto.")
                Text(
                    text = "NOTE: Alone, Roto is private and offline, It is your choice to use an AI to help build a rota file, it is also your choice how you share the file to others. This app collects no data.",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("RECOMMENDATION: Add the Roto widget! so you can instantly see what is upcoming, no need to open the app.")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(quickStartRequester),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Quick Start Instructions.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("1) Tap \"Copy helper prompt\" below.")
                    Button(onClick = onCopyHelperPrompt, modifier = Modifier.fillMaxWidth()) {
                        Text("Copy helper prompt")
                    }
                    Text("2) Open your favourite AI and paste the prompt into it.")
                    Text("3) Follow the AI’s instructions to build your rota file.")
                    Text("4) Save the file to your phone’s \"Downloads\" folder.")
                    Text("5) Open the Roto app again and load your rota file.")
                    TextButton(onClick = { showSchema = !showSchema }) {
                        Text(if (showSchema) "Hide JSON schema" else "Show JSON schema")
                    }
                    if (showSchema) {
                        FormatInfoCard()
                    }
                }
                if (sampleFiles.isNotEmpty()) {
                    Text("Want to see the app in action or tweak a rota by hand? Copy one of these sample files to your Downloads folder, then load it in Roto.")
                    Text(
                        text = "Sample Files:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    sampleFiles.forEach { name ->
                        val display = when (name) {
                            "Simple_School_Menu_Infinite.json" -> "Simple School Menu"
                            "Nurse_Night_Shift_Infinite.json" -> "Nurse Night Shift"
                            else -> name.removeSuffix(".json").replace('_', ' ').replace('-', ' ')
                        }
                        Button(
                            onClick = { onCopySample(name) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Copy $display")
                        }
                    }
                    Text(
                        text = "Samples are copied into Downloads/Roto. You can load them immediately or tap \"Load rota file\" later to select them manually.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}

@Composable
private fun FormatInfoCard() {
    val exampleJson = """
        {
          "schema_version": "0.3",
          "school_name": "Your Rota Name",
          "notes": ["Optional reminders"],
          "special_events": {
            "2025-10-20": "Special Event: World Book Day costumes today"
          },
          "cycle": {
            "repeat": {
              "start_date": "2025-09-01",
              "start_week_id": "Week 1"
            },
            "weeks": [
              {
                "week_id": "Week 1",
                "week_commencing": ["2025-09-01"],
                "days": {
                  "monday": {
                    "slots": [
                      { "label": "Option 1", "text": "Activity or meal name" },
                      { "label": "Option 2", "text": "Alternative option", "tags": ["optional tag"] }
                    ],
                    "notes": ["Optional day-specific note"]
                  }
                }
              }
            ]
          },
          "overrides": {
            "2025-10-20": {
              "closed": true,
              "reason": "No rota today (holiday)"
            }
          }
        }
    """.trimIndent()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("What the rota file looks like", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            SelectionContainer {
                Text(
                    text = exampleJson,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "Save the file exactly as shown (tech folks call it JSON) and load it with the button above.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MenuCard(
    title: String,
    menu: DayResult,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color? = null,
    showWeekMetadata: Boolean = true
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = borderColor?.let { BorderStroke(1.dp, it) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            DayDetails(menu = menu, showWeekMetadata = showWeekMetadata)
        }
    }
}

@Composable
private fun DayDetails(
    menu: DayResult,
    showWeekMetadata: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
    menu.specialEvents.forEach { SpecialEventBanner(text = it) }

        if (menu.isClosed) {
            Text(
                text = "Closed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
            menu.closedReason?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            menu.closedReason?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            if (menu.slots.isNotEmpty()) {
                menu.slots.forEach { slot -> SlotRow(slot) }
            } else {
                Text("No rota slots recorded.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (menu.notes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Notes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                menu.notes.forEach { note ->
                    Text("• $note", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (showWeekMetadata) {
            val metadata = buildList {
                menu.weekId?.let { add("Pattern: $it") }
                menu.weekCommencing?.let { add("WC $it") }
                add("Source: ${formatSource(menu.source)}")
            }
            if (metadata.isNotEmpty()) {
                Text(
                    text = metadata.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SpecialEventBanner(
    text: String,
    modifier: Modifier = Modifier
) {
    val borderColor = MaterialTheme.colorScheme.primary
    val fillColor = borderColor.copy(alpha = 0.08f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeWidth = 2.dp.toPx()
                val cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                drawRoundRect(
                    color = fillColor,
                    cornerRadius = cornerRadius
                )
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = cornerRadius,
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    )
                )
            }
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SlotRow(slot: SlotEntry, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = slot.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(text = slot.text, style = MaterialTheme.typography.bodyMedium)
        if (slot.tags.isNotEmpty()) {
            Text(
                text = "Tags: ${slot.tags.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

private fun formatSource(source: DayDataSource): String =
    when (source) {
        DayDataSource.ROTATION -> "Rotation"
        DayDataSource.OVERRIDE -> "Override"
    }

private fun deriveRecentLabel(recent: RecentRota): String =
    when (recent.type) {
        MenuSelectionType.REMOTE_LINK -> {
            recent.displayName?.takeIf { it.isNotBlank() }
                ?: recent.remoteUrl?.takeIf { it.isNotBlank() }
                ?: hostOrLast(recent.reference)
        }
        MenuSelectionType.LOCAL_FILE -> {
            recent.displayName?.takeIf { it.isNotBlank() }
                ?: runCatching { Uri.parse(recent.reference).lastPathSegment }.getOrNull()
                ?: "Local file"
        }
    }

private fun deriveRecentSubtitle(recent: RecentRota): String =
    when (recent.type) {
        MenuSelectionType.REMOTE_LINK -> recent.remoteUrl?.takeIf { it.isNotBlank() } ?: recent.reference
        MenuSelectionType.LOCAL_FILE -> runCatching { Uri.parse(recent.reference).path?.substringAfterLast('/') }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: recent.reference
    }

private fun hostOrLast(value: String): String {
    val parsed = runCatching { Uri.parse(value) }.getOrNull()
    val host = parsed?.host?.takeIf { it.isNotBlank() }
    val last = parsed?.lastPathSegment?.takeIf { it.isNotBlank() }
    return when {
        host != null && last != null && host != last -> "$host/$last"
        host != null -> host
        else -> value
    }
}

@Composable
private fun TipJarLinks(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val onOpenLink: (String) -> Unit = remember(uriHandler) {
        { url ->
            runCatching { uriHandler.openUri(url) }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "One-person spare-time project; a tip tells me it helped.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        TipJarTextButton(label = "Tip via Ko-fi", onClick = { onOpenLink("https://ko-fi.com/U7U21NKZ0Z") })
    }
}

@Composable
private fun TipJarTextButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowseWeeksSection(
    weekMenus: List<WeekMenu>,
    selectedWeekMenu: WeekMenu?,
    onSelectWeek: (String) -> Unit,
    onClearWeek: () -> Unit,
    bringIntoViewRequester: BringIntoViewRequester? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Browse rota weeks",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        weekMenus.forEach { week ->
            Button(onClick = { onSelectWeek(week.id) }) {
                Text(week.title)
            }
        }
        selectedWeekMenu?.let { week ->
            val cardModifier = bringIntoViewRequester?.let { Modifier.bringIntoViewRequester(it) } ?: Modifier
            WeekMenuCard(week, modifier = cardModifier)
            TextButton(onClick = onClearWeek) { Text("Clear selection") }
        }
    }
}

@Composable
private fun LegalLinks(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val onOpenLink: (String) -> Unit = remember(uriHandler) {
        { url ->
            runCatching { uriHandler.openUri(url) }
        }
    }
    val appSiteUrl = "https://anicetuscer.github.io/roto/"
    TextButton(
        onClick = { onOpenLink(appSiteUrl) },
        modifier = modifier
    ) {
        Text(
            text = "Privacy & legal information",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun WeekMenuCard(weekMenu: WeekMenu, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = weekMenu.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            weekMenu.days.forEachIndexed { index, day ->
                val headerText = day.menu?.formattedDate ?: run {
                    val dayName = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    "$dayName · ${day.date}"
                }
                val (containerColor, contentColor, borderColor) = when (day.date) {
                    today -> Triple(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        MaterialTheme.colorScheme.tertiary
                    )
                    tomorrow -> Triple(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        MaterialTheme.colorScheme.secondary
                    )
                    else -> Triple(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.outline
                    )
                }
                day.menu?.let {
                    MenuCard(
                        title = headerText,
                        menu = it,
                        containerColor = containerColor,
                        contentColor = contentColor,
                        borderColor = borderColor,
                        showWeekMetadata = false
                    )
                } ?: Card(
                    colors = CardDefaults.cardColors(
                        containerColor = containerColor,
                        contentColor = contentColor
                    ),
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = headerText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("No rota recorded for this day.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (index != weekMenu.days.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun friendlyDate(result: DayResult): String = result.formattedDate
