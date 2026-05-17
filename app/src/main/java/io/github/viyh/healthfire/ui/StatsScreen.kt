package io.github.viyh.healthfire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.viyh.healthfire.MainUiState
import io.github.viyh.healthfire.MainViewModel
import io.github.viyh.healthfire.sync.MetricsWindow
import io.github.viyh.healthfire.sync.TypeCoverage
import io.github.viyh.healthfire.sync.TypeMetrics
import java.time.LocalDate
import java.time.ZoneOffset

/** The Stats tab: per-record-type export figures, with a time-window filter. */
@Composable
fun StatsScreen(state: MainUiState, viewModel: MainViewModel, contentPadding: PaddingValues) {
    var window by remember { mutableStateOf(MetricsWindow.ALL_TIME) }
    var confirmReset by remember { mutableStateOf(false) }
    val byType = state.metrics.windowed(window, LocalDate.now(ZoneOffset.UTC))

    ScreenColumn(contentPadding) {
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricsWindow.entries.forEach { option ->
                    SelectChip(
                        label = option.label(),
                        selected = option == window,
                        modifier = Modifier.weight(1f),
                        onClick = { window = option },
                    )
                }
            }
            if (byType.isEmpty()) {
                Text(
                    text = "Nothing exported in this period yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val total = byType.values.fold(TypeMetrics()) { acc, m -> acc + m }
                Text(
                    text = "${byType.size} types · ${formatCount(total.records)} datapoints · " +
                        formatBytes(total.bytes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                byType.entries.sortedByDescending { it.value.bytes }.forEach { (type, metrics) ->
                    TypeStatsBlock(type, metrics, state.metrics.coverage[type])
                }
            }
            Button(
                onClick = { confirmReset = true },
                shape = ButtonShape,
                colors = dangerButtonColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset stats")
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset export stats?") },
            text = {
                Text(
                    "This clears the recorded file, datapoint, and date counts. The " +
                        "data already exported to your bucket is not touched.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.resetMetrics()
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

/** One record type's stats: the type name, then its figures indented below. */
@Composable
private fun TypeStatsBlock(type: String, metrics: TypeMetrics, coverage: TypeCoverage?) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = type,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            StatLine("Datapoints", formatCount(metrics.records))
            StatLine("Files", formatCount(metrics.files))
            StatLine("Size", formatBytes(metrics.bytes))
            StatLine("Date range", dateRange(coverage))
            StatLine(
                label = "Days with data",
                value = coverage?.days?.takeIf { it > 0 }?.let { formatCount(it) } ?: "-",
            )
        }
    }
}

/** An indented `label   value` line inside a [TypeStatsBlock]. */
@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Formats a record type's coverage as "first to last", or "-" if unknown. */
private fun dateRange(coverage: TypeCoverage?): String {
    val first = coverage?.firstDay
    val last = coverage?.lastDay
    return if (first != null && last != null) "$first to $last" else "-"
}
