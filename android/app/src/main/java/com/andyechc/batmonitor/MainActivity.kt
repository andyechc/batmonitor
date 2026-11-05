package com.andyechc.batmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.andyechc.batmonitor.ble.BleCentral
import com.andyechc.batmonitor.data.BatteryStore
import com.andyechc.batmonitor.data.SavedBattery
import com.andyechc.batmonitor.ui.detail.DetailScreen
import com.andyechc.batmonitor.ui.home.HomeScreen
import com.andyechc.batmonitor.ui.theme.BatMonitorTheme
import com.andyechc.batmonitor.ui.theme.Theme
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BleCentral.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            BatMonitorTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Theme.Background) {
                    BatMonitorNav()
                }
            }
        }
    }
}

@Composable
fun BatMonitorNav() {
    val context = LocalContext.current
    val store = remember { BatteryStore(context.applicationContext) }
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                store = store,
                onOpenBattery = { battery: SavedBattery ->
                    navController.navigate("detail/${battery.id}")
                },
            )
        }
        composable(
            route = "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id")?.let(UUID::fromString) ?: return@composable
            DetailScreen(
                batteryId = id,
                store = store,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
