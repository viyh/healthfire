@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.viyh.healthfire.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import io.github.viyh.healthfire.BuildConfig
import io.github.viyh.healthfire.MainUiState
import io.github.viyh.healthfire.MainViewModel
import io.github.viyh.healthfire.sync.MetricsWindow
import io.github.viyh.healthfire.sync.SyncProgress
import io.github.viyh.healthfire.sync.TypeMetrics
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.delay

/** Home/status screen: sync state, exported-data metrics, and the account. */
@Composable
fun HomeScreen(state: MainUiState, viewModel: MainViewModel) {
    val permissionRequest = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refresh() }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("healthfire", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = viewModel::refresh) { Text("Refresh") }
            }

            SyncCard(
                state = state,
                onSyncNow = viewModel::syncNow,
                onStopSync = viewModel::stopSync,
                onStartOver = viewModel::startOver,
                onSetAutoSync = viewModel::setAutoSync,
            )
            MetricsCard(state = state)
            AccountCard(
                state = state,
                onManagePermissions = {
                    permissionRequest.launch(viewModel.permissionsToRequest)
                },
            )

            state.message?.let { MessageCard(it, onDismiss = viewModel::dismissMessage) }

            TextButton(
                onClick = viewModel::signOut,
                enabled = !state.busy,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Sign out")
            }
            Text(
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_REVISION})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun SyncCard(
    state: MainUiState,
    onSyncNow: () -> Unit,
    onStopSync: () -> Unit,
    onStartOver: () -> Unit,
    onSetAutoSync: (Boolean) -> Unit,
) {
    var confirmStartOver by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sync", style = MaterialTheme.typography.titleMedium)
            LabeledValue("Last sync", formatRelativeTime(state.lastSyncAt))
            LabeledValue(
                label = "Initial backfill",
                value = if (state.backfillComplete) "Complete" else "Pending",
            )

            val progress = state.syncProgress
            if (progress is SyncProgress.Running) {
                SyncProgressView(progress = progress, onStop = onStopSync)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSyncNow, enabled = !state.busy) {
                        Text("Sync now")
                    }
                    OutlinedButton(
                        onClick = { confirmStartOver = true },
                        enabled = !state.busy,
                    ) {
                        Text("Start over")
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Automatic sync", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Sync in the background every 6 hours",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.autoSyncEnabled, onCheckedChange = onSetAutoSync)
            }
        }
    }

    if (confirmStartOver) {
        AlertDialog(
            onDismissRequest = { confirmStartOver = false },
            title = { Text("Start over?") },
            text = {
                Text(
                    "This clears the sync checkpoint. The next sync re-exports your " +
                        "entire Health Connect history from the beginning.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmStartOver = false
                    onStartOver()
                }) {
                    Text("Start over")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStartOver = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SyncProgressView(progress: SyncProgress.Running, onStop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SyncSpinner()
            Text(
                text = if (progress.backfill) "Backfilling history" else "Syncing",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (progress.backfill && progress.recordTypesTotal > 0) {
            LinearProgressIndicator(
                progress = {
                    progress.recordTypesDone.toFloat() / progress.recordTypesTotal
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = when {
                    progress.currentType != null ->
                        "Record type ${progress.recordTypesDone + 1} of " +
                            "${progress.recordTypesTotal}: ${progress.currentType}"
                    progress.recordTypesDone >= progress.recordTypesTotal -> "Finishing up"
                    else -> "Starting"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        progress.currentDate?.let { date ->
            Text(
                text = "Exported through $date",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = "${formatCount(progress.recordsExported)} datapoints, " +
                "${formatCount(progress.filesUploaded)} files exported",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onStop) { Text("Stop") }
    }
}

/**
 * A small rotating arc that signals an active sync. Driven by a timer and plain
 * recomposition rather than the animation framework, so it keeps moving even
 * while the sync is doing heavy work on the device.
 */
@Composable
private fun SyncSpinner() {
    var angle by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(40)
            angle = (angle + 18f) % 360f
        }
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(20.dp)) {
        rotate(angle) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun MetricsCard(state: MainUiState) {
    var window by remember { mutableStateOf(MetricsWindow.TODAY) }
    val byType = state.metrics.windowed(window, LocalDate.now(ZoneOffset.UTC))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Exported data", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricsWindow.entries.forEach { option ->
                    FilterChip(
                        selected = option == window,
                        onClick = { window = option },
                        label = { Text(option.label()) },
                    )
                }
            }
            MetricsTable(byType)
        }
    }
}

@Composable
private fun MetricsTable(byType: Map<String, TypeMetrics>) {
    if (byType.isEmpty()) {
        Text(
            text = "Nothing exported in this period yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val total = byType.values.fold(TypeMetrics()) { acc, m -> acc + m }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MetricsRow("Record type", "Files", "Points", "Size", header = true)
        byType.entries.sortedByDescending { it.value.bytes }.forEach { (type, m) ->
            MetricsRow(type, "${m.files}", "${m.records}", formatBytes(m.bytes))
        }
        HorizontalDivider()
        MetricsRow(
            type = "Total",
            files = "${total.files}",
            points = "${total.records}",
            size = formatBytes(total.bytes),
            header = true,
        )
    }
}

@Composable
private fun MetricsRow(
    type: String,
    files: String,
    points: String,
    size: String,
    header: Boolean = false,
) {
    val style = if (header) {
        MaterialTheme.typography.labelMedium
    } else {
        MaterialTheme.typography.bodyMedium
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(type, style = style, modifier = Modifier.weight(2.2f))
        Text(files, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(points, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(size, style = style, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
    }
}

@Composable
private fun AccountCard(state: MainUiState, onManagePermissions: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Account", style = MaterialTheme.typography.titleMedium)
            LabeledValue("Signed in as", state.accountEmail ?: "Unknown account")
            LabeledValue(
                label = "Record types",
                value = "${state.grantedTypeCount} of ${state.knownTypeCount} granted",
            )
            LabeledValue("Older-than-30-days history", yesNo(state.historyGranted))
            LabeledValue("Background access", yesNo(state.backgroundGranted))
            if (!state.backgroundGranted) {
                Text(
                    text = "Without background access, syncs may not run while the app " +
                        "is closed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(onClick = onManagePermissions) {
                Text("Manage Health Connect permissions")
            }
        }
    }
}

private fun yesNo(granted: Boolean): String = if (granted) "Granted" else "Not granted"
