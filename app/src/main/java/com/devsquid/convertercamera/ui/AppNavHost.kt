package com.devsquid.convertercamera.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.devsquid.convertercamera.ui.camera.CameraScreen
import com.devsquid.convertercamera.ui.selector.CurrencySelectorScreen
import kotlinx.serialization.Serializable


@Serializable
object CameraRoute

@Serializable
data class SelectorRoute(val mode: Int)


@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val activity = LocalActivity.current
    NavHost(navController = navController, startDestination = CameraRoute) {
        composable<CameraRoute> {
            CameraScreen(
                onNavigateToSelectorRoute = { mode: Int ->
                    navController.navigate(SelectorRoute(mode))
                },
                onBack = { navController.popBackStack() },
                onClose = { activity?.finish() }
            )
        }
        composable<SelectorRoute> {
            CurrencySelectorScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}