package com.safeguardian.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.safeguardian.app.presentation.screens.battery.BatteryScreen
import com.safeguardian.app.presentation.screens.dashboard.DashboardScreen
import com.safeguardian.app.presentation.screens.main.MainScreen
import com.safeguardian.app.presentation.screens.permission.PermissionScreen
import com.safeguardian.app.presentation.screens.settings.SettingsScreen
import com.safeguardian.app.presentation.screens.simulation.SimulationScreen

object Routes {
    const val PERMISSION = "permission"
    const val MAIN = "main"
    const val DASHBOARD = "dashboard"
    const val SIMULATION = "simulation"
    const val BATTERY = "battery"
    const val SETTINGS = "settings"
}

@Composable
fun SafeGuardianNavGraph(
    navController: NavHostController,
    startDestination: String = Routes.PERMISSION
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.PERMISSION) {
            PermissionScreen(
                onAllGranted = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.PERMISSION) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.PERMISSION) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                onNavigateToDashboard = { navController.navigate(Routes.DASHBOARD) },
                onNavigateToSimulation = { navController.navigate(Routes.SIMULATION) },
                onNavigateToBattery = { navController.navigate(Routes.BATTERY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SIMULATION) {
            SimulationScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.BATTERY) {
            BatteryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
