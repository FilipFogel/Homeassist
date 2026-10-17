@file:OptIn(ExperimentalMaterial3Api::class)

package se.hemma.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.hemma.app.ui.HomeScreen
import se.hemma.app.ui.MachinesScreen
import se.hemma.app.ui.MainViewModel
import se.hemma.app.ui.SettingsScreen
import se.hemma.app.ui.theme.HemmaTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HemmaTheme {
                HemmaRoot(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}

private data class Tab(val label: String, val title: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Hem", "Hemma", Icons.Filled.Home),
    Tab("Maskiner", "Maskiner", Icons.Filled.Build),
    Tab("Inställningar", "Inställningar", Icons.Filled.Settings),
)

@Composable
private fun HemmaRoot(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (tab == 0) "Hemma · ${state.settings.area}" else TABS[tab].title,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    if (tab == 0) {
                        IconButton(onClick = { vm.refresh(force = true) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Uppdatera")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> HomeScreen(
                    state = state,
                    onRefresh = { vm.refresh(force = true) },
                    onOpenMachines = { tab = 1 },
                )
                1 -> MachinesScreen(
                    machines = state.machines,
                    onSave = vm::saveMachine,
                    onDelete = vm::deleteMachine,
                    onMove = vm::moveMachine,
                )
                else -> SettingsScreen(
                    settings = state.settings,
                    dashboard = state.dashboard,
                    onChange = vm::updateSettings,
                )
            }
        }
    }
}
