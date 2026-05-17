package io.github.viyh.healthfire.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import io.github.viyh.healthfire.MainUiState
import io.github.viyh.healthfire.MainViewModel
import io.github.viyh.healthfire.sync.SyncProgress
import io.github.viyh.healthfire.sync.TypeMetrics
import kotlinx.coroutines.delay

/** The Sync tab: sync status, manual sync actions, and an export summary. */
@Composable
fun SyncScreen(state: MainUiState, viewModel: MainViewModel, contentPadding: PaddingValues) {
    val permissionRequest = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refresh() }
    var confirmStartOver by remember { mutableStateOf(false) }

    ScreenColumn(contentPadding) {
        state.message?.let { MessageCard(it, onDismiss = viewModel::dismissMessage) }

        if (!state.allPermissionsGranted) {
            PermissionsNotice(
                onReview = { permissionRequest.launch(viewModel.permissionsToRequest) },
            )
        }

        SectionCard {
            InlineStat("Last sync", formatRelativeTime(state.lastSyncAt))
            InlineStat(
                label = "Initial backfill",
                value = if (state.backfillComplete) "Complete" else "Pending",
            )
            InlineStat("Automatic sync", autoSyncStatus(state))
            val progress = state.syncProgress
            if (progress is SyncProgress.Running) {
                SyncProgressView(progress = progress, onStop = viewModel::stopSync)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = viewModel::syncNow,
                        enabled = !state.busy,
                        shape = ButtonShape,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Sync now")
                    }
                    Button(
                        onClick = { confirmStartOver = true },
                        enabled = !state.busy,
                        shape = ButtonShape,
                        colors = dangerButtonColors(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Start over")
                    }
                }
            }
        }

        TotalDataCard(state)
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
                    viewModel.startOver()
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

/** Lifetime export totals, so the Sync tab shows what is backed up at a glance. */
@Composable
private fun TotalDataCard(state: MainUiState) {
    val lifetime = state.metrics.lifetime
    SectionCard {
        SectionHeader("Total Data")
        if (lifetime.isEmpty()) {
            Text(
                text = "Nothing exported yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val total = lifetime.values.fold(TypeMetrics()) { acc, m -> acc + m }
            InlineStat("Datapoints", formatCount(total.records))
            InlineStat("Files", formatCount(total.files))
            InlineStat("Total size", formatBytes(total.bytes))
            InlineStat("Record types", "${lifetime.size}")
        }
    }
}

/** A highlighted call-to-action shown when Health Connect access is incomplete. */
@Composable
private fun PermissionsNotice(onReview: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Health Connect access is incomplete",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "HealthFire exports only the record types you grant. Review them " +
                    "to export more of your data.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = onReview,
                modifier = Modifier.fillMaxWidth(),
                shape = ButtonShape,
                colors = warmOutlinedColors(),
            ) {
                Text("Review permissions")
            }
        }
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
        OutlinedButton(
            onClick = onStop,
            shape = ButtonShape,
            colors = warmOutlinedColors(),
        ) {
            Text("Stop")
        }
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

/** A short description of the background-sync setting for the status line. */
private fun autoSyncStatus(state: MainUiState): String {
    if (!state.autoSyncEnabled) return "Off"
    val hours = state.syncIntervalHours
    return "Every $hours ${if (hours == 1) "hour" else "hours"}"
}
