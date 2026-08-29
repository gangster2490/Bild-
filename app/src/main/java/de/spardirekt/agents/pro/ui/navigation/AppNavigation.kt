package de.spardirekt.agents.pro.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.spardirekt.agents.pro.ui.create.CreateScreen
import de.spardirekt.agents.pro.ui.create.CreateViewModel
import de.spardirekt.agents.pro.ui.history.HistoryScreen
import de.spardirekt.agents.pro.ui.history.HistoryViewModel
import de.spardirekt.agents.pro.ui.result.ResultScreen
import de.spardirekt.agents.pro.ui.result.ResultViewModel
import de.spardirekt.agents.pro.ui.settings.SettingsScreen
import de.spardirekt.agents.pro.ui.settings.SettingsViewModel
import de.spardirekt.agents.pro.ui.theme.VppColors

object Routes {
    const val CREATE = "create"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val RESULT = "result/{projectId}"
    fun result(id: String) = "result/$id"
}

@Composable
fun VeoPromptProNav() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = route == Routes.CREATE || route == Routes.HISTORY || route == Routes.SETTINGS

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.CREATE,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Routes.CREATE) {
                val vm: CreateViewModel = viewModel()
                CreateScreen(
                    viewModel = vm,
                    onOpenHistory = {
                        navController.navigate(Routes.HISTORY) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenResult = { id -> navController.navigate(Routes.result(id)) }
                )
            }
            composable(Routes.HISTORY) {
                val vm: HistoryViewModel = viewModel()
                HistoryScreen(
                    viewModel = vm,
                    onOpenResult = { id -> navController.navigate(Routes.result(id)) },
                    onOpenCreate = {
                        navController.navigate(Routes.CREATE) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel()
                SettingsScreen(
                    viewModel = vm,
                    onOpenCreate = {
                        navController.navigate(Routes.CREATE) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenHistory = {
                        navController.navigate(Routes.HISTORY) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(
                route = Routes.RESULT,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("projectId").orEmpty()
                val vm: ResultViewModel = viewModel()
                ResultScreen(
                    projectId = id,
                    viewModel = vm,
                    onBackToCreate = {
                        navController.navigate(Routes.CREATE) {
                            popUpTo(Routes.CREATE) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
        }

        if (showBottomBar) {
            BottomBar(
                currentRoute = route,
                onSelect = { target ->
                    navController.navigate(target) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun BottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(VppColors.bottomBar)
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomItem("Создать", Icons.Outlined.AddCircleOutline, currentRoute == Routes.CREATE) {
            onSelect(Routes.CREATE)
        }
        BottomItem("История", Icons.Outlined.History, currentRoute == Routes.HISTORY) {
            onSelect(Routes.HISTORY)
        }
        BottomItem("Настройки", Icons.Outlined.Settings, currentRoute == Routes.SETTINGS) {
            onSelect(Routes.SETTINGS)
        }
    }
}

@Composable
private fun BottomItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) VppColors.navPill else Color.Transparent
    val fg = if (selected) VppColors.cardNavy else VppColors.textMuted
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = fg, fontSize = 11.sp)
    }
}
