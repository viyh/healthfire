@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.viyh.healthfire.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import io.github.viyh.healthfire.MainUiState
import io.github.viyh.healthfire.MainViewModel
import io.github.viyh.healthfire.R

/** The three primary destinations, shown along the bottom navigation bar. */
private enum class MainTab(val title: String) {
    SYNC("Sync"),
    STATS("Stats"),
    SETTINGS("Settings"),
}

/** Post-setup shell: a top bar, the selected screen, and the bottom tab bar. */
@Composable
fun MainScreen(state: MainUiState, viewModel: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(MainTab.SYNC) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(title = { Text(tab.title) })
                HorizontalDivider()
            }
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = entry == tab,
                        onClick = { tab = entry },
                        icon = { TabIcon(entry) },
                        label = { Text(entry.title) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (tab) {
            MainTab.SYNC -> SyncScreen(state, viewModel, innerPadding)
            MainTab.STATS -> StatsScreen(state, viewModel, innerPadding)
            MainTab.SETTINGS -> SettingsScreen(state, viewModel, innerPadding)
        }
    }
}

@Composable
private fun TabIcon(tab: MainTab) {
    when (tab) {
        MainTab.SYNC -> Icon(Icons.Filled.Refresh, contentDescription = null)
        MainTab.STATS -> Icon(painterResource(R.drawable.ic_stats), contentDescription = null)
        MainTab.SETTINGS -> Icon(Icons.Filled.Settings, contentDescription = null)
    }
}
