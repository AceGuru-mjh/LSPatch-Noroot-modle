package com.batteryopt.noroot.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.batteryopt.noroot.models.BatteryConfig
import com.batteryopt.noroot.ui.screens.AboutScreen
import com.batteryopt.noroot.ui.screens.DiagnosticsScreen
import com.batteryopt.noroot.ui.screens.UpdateScreen
import com.batteryopt.noroot.ui.screens.FeaturesScreen
import com.batteryopt.noroot.ui.screens.HomeScreen
import com.batteryopt.noroot.ui.theme.BatteryOptimizerTheme
import com.batteryopt.noroot.utils.ConfigManager
import com.batteryopt.noroot.utils.LogStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigManager.init(applicationContext)
        LogStore.init(applicationContext)
        setContent {
            BatteryOptimizerTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    var cfg by remember { mutableStateOf(ConfigManager.getGlobalConfig()) }
    val onCfgChange: (BatteryConfig) -> Unit = { cfg = it }

    val screens = listOf(
        Triple("home", "总开�?, Icons.Default.BatteryStd),
        Triple("features", "功能", Icons.Default.Build),
        Triple("diagnostics", "诊断", Icons.Default.BugReport),
            Triple("update", "更新", Icons.Default.CloudDownload),
            Triple("about", "关于", Icons.Default.Info)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("BatteryOptimizer NoRoot") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (!isLandscape) {
                val current by navController.currentBackStackEntryAsState()
                val route = current?.destination?.route
                NavigationBar {
                    screens.forEach { (r, label, icon) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = { navController.navigate(r) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { inner ->
        if (isLandscape) {
            androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize().padding(inner)) {
                val current by navController.currentBackStackEntryAsState()
                val route = current?.destination?.route
                NavigationRail {
                    screens.forEach { (r, label, icon) ->
                        NavigationRailItem(
                            selected = route == r,
                            onClick = { navController.navigate(r) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
                AppNavHost(navController, cfg, onCfgChange, PaddingValues(0.dp))
            }
        } else {
            AppNavHost(navController, cfg, onCfgChange, inner)
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    cfg: BatteryConfig,
    onCfgChange: (BatteryConfig) -> Unit,
    padding: PaddingValues
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize().padding(padding)
    ) {
        composable("home") { HomeScreen(cfg, onCfgChange) }
        composable("features") { FeaturesScreen(cfg, onCfgChange) }
        composable("diagnostics") { DiagnosticsScreen() }
        composable("update") { UpdateScreen() }
        composable("about") { AboutScreen() }
    }
}
