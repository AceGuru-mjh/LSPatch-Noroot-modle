package com.vipunlock.noroot.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.vipunlock.noroot.models.VipConfig
import com.vipunlock.noroot.ui.screens.AboutScreen
import com.vipunlock.noroot.ui.screens.DiagnosticsScreen
import com.vipunlock.noroot.ui.screens.FeaturesScreen
import com.vipunlock.noroot.ui.screens.HomeScreen
import com.vipunlock.noroot.ui.screens.UpdateScreen
import com.vipunlock.noroot.ui.theme.VipUnlockerTheme
import com.vipunlock.noroot.utils.ConfigManager
import com.vipunlock.noroot.services.FloatingBallService
import com.vipunlock.noroot.utils.LogStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigManager.init(applicationContext)
        LogStore.init(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivityForResult(intent, 100)
        } else {
            startFloatingBallService()
        }

        setContent {
            MainScreen()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingBallService()
                Toast.makeText(this, "悬浮球已启动", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startFloatingBallService() {
        try {
            val intent = Intent(this, FloatingBallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) { }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    // 暗色模式开�?
    var darkMode by rememberSaveable { mutableStateOf(false) }

    VipUnlockerTheme(darkTheme = darkMode) {
        val navController = rememberNavController()
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

        var cfg by remember { mutableStateOf(ConfigManager.getGlobalConfig()) }
        val onCfgChange: (VipConfig) -> Unit = { cfg = it }

        val screens = listOf(
            Triple("home", "总开�?, Icons.Default.PowerSettingsNew),
            Triple("features", "功能", Icons.Default.Build),
            Triple("diagnostics", "诊断", Icons.Default.BugReport),
            Triple("update", "更新", Icons.Default.CloudDownload),
            Triple("about", "关于", Icons.Default.Info)
        )

        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = { Text("VipUnlocker NoRoot") },
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
                    AppNavHost(navController, cfg, onCfgChange, darkMode, { darkMode = !darkMode }, PaddingValues(0.dp))
                }
            } else {
                AppNavHost(navController, cfg, onCfgChange, darkMode, { darkMode = !darkMode }, inner)
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    cfg: VipConfig,
    onCfgChange: (VipConfig) -> Unit,
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    padding: PaddingValues
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize().padding(padding)
    ) {
        composable("home") { HomeScreen(cfg, onCfgChange, darkMode, onToggleDarkMode) }
        composable("features") { FeaturesScreen(cfg, onCfgChange) }
        composable("diagnostics") { DiagnosticsScreen() }
        composable("update") { UpdateScreen() }
        composable("about") { AboutScreen() }
    }
}
