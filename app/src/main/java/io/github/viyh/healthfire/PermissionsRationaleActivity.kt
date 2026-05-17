package io.github.viyh.healthfire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.viyh.healthfire.ui.theme.HealthfireTheme

/**
 * Shown when Health Connect asks the app to justify its data access (the
 * ACTION_SHOW_PERMISSIONS_RATIONALE intent). Health Connect requires every app
 * that requests health permissions to provide this screen.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealthfireTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "How HealthFire uses your health data",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "HealthFire reads the record types you grant from Health " +
                                "Connect and exports them to a cloud storage bucket that you own " +
                                "and configure. Your data is never sent to the app's authors and " +
                                "is not shared with anyone else. You can revoke any permission at " +
                                "any time from Health Connect.",
                        )
                    }
                }
            }
        }
    }
}
