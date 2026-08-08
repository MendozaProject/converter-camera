package com.devsquid.convertercamera.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.devsquid.convertercamera.ui.camera.CameraScreen
import com.devsquid.convertercamera.ui.camera.CameraViewModel
import com.devsquid.convertercamera.ui.selector.CurrencySelectorScreen
import kotlinx.serialization.Serializable


@Serializable
object CameraRoute

@Serializable
data class SelectorRoute(val mode: Int)


@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = CameraRoute) {
        composable<CameraRoute> {
            CameraScreen(
                onNavigateToSelectorRoute = { mode: Int ->
                    navController.navigate(SelectorRoute(mode))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable<SelectorRoute> {
            CurrencySelectorScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}