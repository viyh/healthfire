package io.github.viyh.healthfire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.viyh.healthfire.hc.HcAvailability
import io.github.viyh.healthfire.ui.theme.HealthfireTheme

/**
 * Step-1 verification surface: shows Health Connect availability, requests
 * permissions, and triggers a logged read. The real first-run and home UI is
 * built in a later milestone.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealthfireTheme {
                HealthConnectStatusScreen()
            }
        }
    }
}

@Composable
private fun HealthConnectStatusScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) {
        viewModel.refresh()
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "healthfire", style = MaterialTheme.typography.headlineMedium)

            HorizontalDivider()

            LabeledValue(
                label = "Health Connect",
                value = when (state.availability) {
                    HcAvailability.AVAILABLE -> "Available"
                    HcAvailability.UPDATE_REQUIRED -> "Update required"
                    HcAvailability.NOT_SUPPORTED -> "Not supported on this device"
                },
            )
            LabeledValue(
                label = "Granted record types",
                value = "${state.grantedTypeCount} of ${state.knownTypeCount}",
            )
            LabeledValue(
                label = "History access",
                value = if (state.historyGranted) "Granted" else "Not granted",
            )
            LabeledValue(
                label = "Background access",
                value = if (state.backgroundGranted) "Granted" else "Not granted",
            )

            HorizontalDivider()

            Button(
                onClick = { permissionLauncher.launch(viewModel.permissionsToRequest) },
                enabled = state.availability == HcAvailability.AVAILABLE,
            ) {
                Text(text = "Grant Health Connect permissions")
            }
            Button(
                onClick = viewModel::readRecentData,
                enabled = state.availability == HcAvailability.AVAILABLE && !state.isReading,
            ) {
                Text(text = if (state.isReading) "Reading..." else "Read last 30 days")
            }

            state.lastReadSummary?.let { summary ->
                HorizontalDivider()
                Text(text = summary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
