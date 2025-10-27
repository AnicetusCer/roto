package org.schooldinners.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.schooldinners.domain.DayMenuResult

@Composable
fun MenuRoot(
    viewModel: MenuViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Persistable permission may not be granted; continue with transient access
            }
            viewModel.onExternalFileChosen(context, uri)
        }
    }

    MenuScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onChooseFile = {
            openDocumentLauncher.launch(arrayOf("application/json", "text/plain"))
        },
        onClearSelection = viewModel::clearMenuSelection,
        modifier = modifier
    )
}

@Composable
fun MenuScreen(
    state: MenuUiState,
    onRefresh: () -> Unit,
    onChooseFile: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        state.isLoading -> LoadingState(modifier)
        state.error != null -> ErrorState(
            message = state.error,
            onRefresh = onRefresh,
            onChooseFile = onChooseFile,
            onClearSelection = onClearSelection,
            usingCustomSelection = state.usingCustomSelection,
            sourceLabel = state.selectedSourceLabel,
            modifier = modifier
        )
        state.tomorrowMenu != null || state.todayMenu != null -> MenuContent(
            todayMenu = state.todayMenu,
            tomorrowMenu = state.tomorrowMenu,
            selectedSourceLabel = state.selectedSourceLabel,
            coverageMessage = state.coverageMessage,
            usingCustomSelection = state.usingCustomSelection,
            onChooseFile = onChooseFile,
            onClearSelection = onClearSelection,
            modifier = modifier
        )
        else -> ErrorState(
            message = "No menu available yet. When a new rota arrives, copy the AI Instructions and refresh SchoolNomNomsMenu.json.",
            onRefresh = onRefresh,
            onChooseFile = onChooseFile,
            onClearSelection = onClearSelection,
            usingCustomSelection = state.usingCustomSelection,
            sourceLabel = state.selectedSourceLabel,
            modifier = modifier
        )
    }
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
        Spacer(modifier = Modifier.padding(8.dp))
        Text(text = "Loading menu…")
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRefresh: () -> Unit,
    onChooseFile: () -> Unit,
    onClearSelection: () -> Unit,
    usingCustomSelection: Boolean,
    sourceLabel: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SourceControls(
            selectedSourceLabel = sourceLabel,
            usingCustomSelection = usingCustomSelection,
            onChooseFile = onChooseFile,
            onClearSelection = onClearSelection
        )
        Spacer(modifier = Modifier.height(12.dp))
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Button(onClick = onRefresh) {
            Text("Try again")
        }
    }
}

@Composable
private fun MenuContent(
    todayMenu: DayMenuResult?,
    tomorrowMenu: DayMenuResult?,
    selectedSourceLabel: String,
    coverageMessage: String?,
    usingCustomSelection: Boolean,
    onChooseFile: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "School Nom Noms",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        SourceControls(
            selectedSourceLabel = selectedSourceLabel,
            usingCustomSelection = usingCustomSelection,
            onChooseFile = onChooseFile,
            onClearSelection = onClearSelection
        )

        coverageMessage?.let {
            InfoCard(message = it)
        }

        tomorrowMenu?.let {
            MenuCard(
                title = "Tomorrow · ${friendlyDate(it)}",
                menu = it
            )
        } ?: Text(
            text = "No menu found for tomorrow.",
            style = MaterialTheme.typography.bodyMedium
        )

        todayMenu?.let {
            MenuCard(
                title = "Today · ${friendlyDate(it)}",
                menu = it
            )
        }
    }
}

@Composable
private fun SourceControls(
    selectedSourceLabel: String,
    usingCustomSelection: Boolean,
    onChooseFile: () -> Unit,
    onClearSelection: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Menu source: $selectedSourceLabel",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onChooseFile) {
                Text("Choose JSON")
            }
            if (usingCustomSelection) {
                TextButton(onClick = onClearSelection) {
                    Text("Use bundled menu")
                }
            }
        }
    }
}

@Composable
private fun InfoCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
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
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MenuCard(
    title: String,
    menu: DayMenuResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            MealRow(label = "Main", value = menu.menu.main)
            MealRow(label = "Alt / Veg", value = menu.menu.altHot)
            MealRow(label = "Deli", value = menu.menu.deliOption)
            MealRow(label = "Dessert", value = menu.menu.dessert)

            if (menu.notes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    menu.notes.forEach { note ->
                        Text(
                            text = "• $note",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Text(
                text = "Week ${menu.weekId} · WC ${menu.weekCommencing}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MealRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun friendlyDate(result: DayMenuResult): String {
    val dayName = result.dayOfWeek.name.lowercase().replaceFirstChar(Char::uppercaseChar)
    return "$dayName ${result.date}"
}
