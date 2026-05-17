package io.github.viyh.healthfire.ui

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import io.github.viyh.healthfire.MainUiState
import io.github.viyh.healthfire.MainViewModel

/**
 * First-run setup: import the Firebase config, sign in, then grant Health
 * Connect access. Each step unlocks once the previous one is done.
 */
@Composable
fun SetupScreen(state: MainUiState, viewModel: MainViewModel) {
    val activity = LocalActivity.current

    val configPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> viewModel.importConfig(uri) }

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
            Text("HealthFire", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Three steps to start exporting your Health Connect data to cloud " +
                    "storage that you own.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            SetupStep(
                number = 1,
                title = "Connect your storage",
                description = "Import the google-services.json from your own Firebase project.",
                done = state.configImported,
                actionLabel = "Import config file",
                enabled = !state.busy,
                onAction = { configPicker.launch(arrayOf("*/*")) },
            )
            SetupStep(
                number = 2,
                title = "Sign in",
                description = "Sign in with the Google account that owns the Firebase project.",
                done = state.signedIn,
                actionLabel = "Sign in with Google",
                enabled = !state.busy && state.configImported && activity != null,
                onAction = { activity?.let(viewModel::signIn) },
            )
            SetupStep(
                number = 3,
                title = "Grant Health Connect access",
                description = "Choose which health record types HealthFire may read and export.",
                done = state.permissionsGranted,
                actionLabel = "Grant permissions",
                enabled = !state.busy && state.signedIn,
                onAction = { permissionRequest.launch(viewModel.permissionsToRequest) },
            )

            state.message?.let { MessageCard(it, onDismiss = viewModel::dismissMessage) }
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    description: String,
    done: Boolean,
    actionLabel: String,
    enabled: Boolean,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (done) "Step $number  -  Done" else "Step $number",
                style = MaterialTheme.typography.labelMedium,
                color = if (done) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!done) {
                Button(onClick = onAction, enabled = enabled) { Text(actionLabel) }
            }
        }
    }
}
