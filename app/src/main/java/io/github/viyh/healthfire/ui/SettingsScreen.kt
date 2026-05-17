package io.github.viyh.healthfire.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import io.github.viyh.healthfire.BuildConfig
import io.github.viyh.healthfire.MainUiState
import io.github.viyh.healthfire.MainViewModel
import io.github.viyh.healthfire.sync.SyncSettingsStore

private const val REPO_URL = "https://github.com/viyh/healthfire"

/** The Settings tab: permissions, automatic-sync settings, account, and about. */
@Composable
fun SettingsScreen(state: MainUiState, viewModel: MainViewModel, contentPadding: PaddingValues) {
    ScreenColumn(contentPadding) {
        PermissionsSection(state, viewModel)
        SyncSettingsSection(state, viewModel)
        AccountSection(state, viewModel)
        AboutSection()
    }
}

@Composable
private fun PermissionsSection(state: MainUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val permissionRequest = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refresh() }
    SectionCard {
        SectionHeader("Permissions")
        InlineStat(
            label = "Record types",
            value = "${state.grantedTypeCount} of ${state.knownTypeCount} granted",
        )
        InlineStat("History access", yesNo(state.historyGranted))
        InlineStat("Background access", yesNo(state.backgroundGranted))
        if (!state.backgroundGranted) {
            Text(
                text = "Without background access, syncs may not run while the app is closed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(
            onClick = {
                if (state.allPermissionsGranted) {
                    openHealthConnectSettings(context)
                } else {
                    permissionRequest.launch(viewModel.permissionsToRequest)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = ButtonShape,
            colors = warmOutlinedColors(),
        ) {
            Text(
                if (state.allPermissionsGranted) {
                    "Manage Health Connect permissions"
                } else {
                    "Grant Health Connect permissions"
                },
            )
        }
    }
}

@Composable
private fun SyncSettingsSection(state: MainUiState, viewModel: MainViewModel) {
    SectionCard {
        SectionHeader("Automatic sync")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sync in the background", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = state.autoSyncEnabled,
                onCheckedChange = viewModel::setAutoSync,
                colors = warmSwitchColors(),
            )
        }
        if (state.autoSyncEnabled) {
            Text(
                text = "How often to sync:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SyncSettingsStore.INTERVAL_CHOICES.forEach { hours ->
                    SelectChip(
                        label = "${hours}h",
                        selected = hours == state.syncIntervalHours,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setSyncInterval(hours) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sync on mobile data", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Off keeps background syncs on unmetered Wi-Fi only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.syncOnMetered,
                    onCheckedChange = viewModel::setSyncOnMetered,
                    colors = warmSwitchColors(),
                )
            }
        } else {
            Text(
                text = "Off - sync runs only when you tap Sync now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountSection(state: MainUiState, viewModel: MainViewModel) {
    SectionCard {
        SectionHeader("Account")
        InlineStat("Signed in as", state.accountEmail ?: "Unknown account")
        OutlinedButton(
            onClick = viewModel::signOut,
            enabled = !state.busy,
            shape = ButtonShape,
            colors = warmOutlinedColors(),
        ) {
            Text("Sign out")
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    SectionCard {
        SectionHeader("About")
        Text(
            text = "HealthFire exports your Android Health Connect records to a Firebase " +
                "Cloud Storage bucket that you own and control.",
            style = MaterialTheme.typography.bodyMedium,
        )
        InlineStat(
            label = "Version",
            value = "${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_REVISION})",
        )
        InlineStat("License", "GPL-3.0")
        OutlinedButton(
            onClick = { openUrl(context, REPO_URL) },
            shape = ButtonShape,
            colors = warmOutlinedColors(),
        ) {
            Text("View on GitHub")
        }
    }
}

private fun yesNo(granted: Boolean): String = if (granted) "Granted" else "Not granted"

/** Opens the system Health Connect screen, where app permissions are managed. */
private fun openHealthConnectSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
    }
}

/** Opens [url] in the device browser. */
private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
