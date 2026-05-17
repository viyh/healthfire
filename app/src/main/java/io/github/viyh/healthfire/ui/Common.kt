package io.github.viyh.healthfire.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.viyh.healthfire.hc.HcAvailability
import io.github.viyh.healthfire.sync.MetricsWindow
import io.github.viyh.healthfire.ui.theme.KnobOff
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** The shared corner rounding for buttons and chips - one consistent shape. */
val ButtonShape = RoundedCornerShape(8.dp)

/** Blocking screen shown when Health Connect cannot be used on this device. */
@Composable
fun HealthConnectUnavailableScreen(availability: HcAvailability) {
    val context = LocalContext.current
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("HealthFire", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = when (availability) {
                    HcAvailability.UPDATE_REQUIRED ->
                        "Health Connect needs to be updated before HealthFire can run."
                    else ->
                        "This device does not support Health Connect, so HealthFire cannot run."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (availability == HcAvailability.UPDATE_REQUIRED) {
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(HEALTH_CONNECT_MARKET_URI)),
                            )
                        }
                    },
                    shape = ButtonShape,
                ) {
                    Text("Update Health Connect")
                }
            }
        }
    }
}

/** A dismissible status or error banner. */
@Composable
fun MessageCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Dismiss")
            }
        }
    }
}

/** A scrolling, padded column - the body of each bottom-nav screen. */
@Composable
fun ScreenColumn(
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** The card shell shared by every screen section. */
@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** A section title with an underline rule. Sits below the larger tab heading. */
@Composable
fun SectionHeader(title: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    }
}

/** A `label ... value` row: muted label on the left, value aligned right. */
@Composable
fun InlineStat(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * A pill-shaped selectable option: an amber outline when unselected, a solid
 * amber fill when selected. Pass a weight modifier so a row of these is even.
 */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shell = if (selected) {
        Modifier.clip(ButtonShape).background(scheme.secondary)
    } else {
        Modifier.clip(ButtonShape).border(1.dp, scheme.secondary, ButtonShape)
    }
    Box(
        modifier = modifier
            .then(shell)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onSecondary else scheme.secondary,
        )
    }
}

/** Outlined-button colours for the app: an amber outline and amber label. */
@Composable
fun warmOutlinedColors(): ButtonColors =
    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)

/** Filled-button colours for destructive actions: a deep-red background. */
@Composable
fun dangerButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
)

/** Switch colours: an orange track, with an amber knob on and a grey knob off. */
@Composable
fun warmSwitchColors(): SwitchColors {
    val scheme = MaterialTheme.colorScheme
    return SwitchDefaults.colors(
        checkedThumbColor = scheme.secondary,
        checkedTrackColor = scheme.primary,
        checkedBorderColor = scheme.primary,
        uncheckedThumbColor = KnobOff,
        uncheckedTrackColor = scheme.primary,
        uncheckedBorderColor = scheme.primary,
    )
}

/** Short human label for a metrics interval. */
fun MetricsWindow.label(): String = when (this) {
    MetricsWindow.TODAY -> "Today"
    MetricsWindow.LAST_7_DAYS -> "7d"
    MetricsWindow.LAST_30_DAYS -> "30d"
    MetricsWindow.ALL_TIME -> "All"
}

/** Formats a byte count as a short human string: "0 B", "12.0 KB", "3.4 MB". */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (value >= 100) {
        "${value.roundToInt()} ${units[unit]}"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unit])
    }
}

/** Formats a count with thousands separators: 1234567 -> "1,234,567". */
fun formatCount(count: Int): String = "%,d".format(count)

/** Formats an RFC 3339 instant as a short relative time, or "never" if null. */
fun formatRelativeTime(rfc3339: String?, now: Instant = Instant.now()): String {
    if (rfc3339 == null) return "never"
    val then = runCatching { Instant.parse(rfc3339) }.getOrNull() ?: return "unknown"
    val seconds = Duration.between(then, now).seconds
    return when {
        seconds < 0 -> "just now"
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 86_400 -> "${seconds / 3600} hr ago"
        seconds < 7 * 86_400 -> "${seconds / 86_400} days ago"
        else -> DateTimeFormatter.ofPattern("MMM d, yyyy")
            .withZone(ZoneId.systemDefault())
            .format(then)
    }
}

private const val HEALTH_CONNECT_MARKET_URI =
    "market://details?id=com.google.android.apps.healthdata"
