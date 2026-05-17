package io.github.viyh.healthfire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.viyh.healthfire.ui.HealthConnectUnavailableScreen
import io.github.viyh.healthfire.ui.MainScreen
import io.github.viyh.healthfire.ui.SetupScreen
import io.github.viyh.healthfire.ui.theme.HealthfireTheme

/**
 * The single app activity. It shows the first-run setup flow until Health
 * Connect, Firebase and sign-in are all ready, then the home/status screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealthfireTheme {
                HealthfireRoot()
            }
        }
    }
}

@Composable
private fun HealthfireRoot(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Permissions and sign-in can change outside the app, so re-derive on resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    when {
        state.loading -> LoadingScreen()
        !state.healthConnectReady -> HealthConnectUnavailableScreen(state.availability)
        !state.setupComplete -> SetupScreen(state = state, viewModel = viewModel)
        else -> MainScreen(state = state, viewModel = viewModel)
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
