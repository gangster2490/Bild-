package com.rin.repairagent.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rin.repairagent.data.RinRepository
import com.rin.repairagent.ui.screens.ApiKeyScreen
import com.rin.repairagent.ui.screens.ExportScreen
import com.rin.repairagent.ui.screens.HomeScreen
import com.rin.repairagent.ui.screens.InstructionsScreen
import com.rin.repairagent.ui.screens.KnowledgeScreen
import com.rin.repairagent.ui.screens.NewRepairScreen
import com.rin.repairagent.ui.screens.ProjectsScreen
import com.rin.repairagent.ui.screens.ReviewScreen
import com.rin.repairagent.ui.screens.SettingsScreen
import com.rin.repairagent.ui.screens.TemplateScreen

object Routes {
    const val API_KEY = "api_key"
    const val HOME = "home"
    const val TEMPLATE = "template"
    const val NEW_REPAIR = "new_repair"
    const val PROJECTS = "projects"
    const val INSTRUCTIONS = "instructions"
    const val KNOWLEDGE = "knowledge"
    const val SETTINGS = "settings"
    const val REVIEW = "review/{projectId}"
    const val EXPORT = "export/{projectId}"

    fun review(id: String) = "review/$id"
    fun export(id: String) = "export/$id"
}

@Composable
fun RinNavGraph(repository: RinRepository) {
    val nav = rememberNavController()
    val start = remember {
        if (repository.hasApiKey()) Routes.HOME else Routes.API_KEY
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.API_KEY) {
            ApiKeyScreen(
                repository = repository,
                onDone = {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.API_KEY) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                repository = repository,
                onNewRepair = { nav.navigate(Routes.NEW_REPAIR) },
                onTemplate = { nav.navigate(Routes.TEMPLATE) },
                onProjects = { nav.navigate(Routes.PROJECTS) },
                onInstructions = { nav.navigate(Routes.INSTRUCTIONS) },
                onKnowledge = { nav.navigate(Routes.KNOWLEDGE) },
                onSettings = { nav.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.TEMPLATE) {
            TemplateScreen(repository = repository, onBack = { nav.popBackStack() })
        }
        composable(Routes.NEW_REPAIR) {
            NewRepairScreen(
                repository = repository,
                onBack = { nav.popBackStack() },
                onOpenReview = { id -> nav.navigate(Routes.review(id)) }
            )
        }
        composable(Routes.PROJECTS) {
            ProjectsScreen(
                repository = repository,
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate(Routes.review(id)) }
            )
        }
        composable(Routes.INSTRUCTIONS) {
            InstructionsScreen(
                repository = repository,
                onBack = { nav.popBackStack() },
                onOpenExport = { id -> nav.navigate(Routes.export(id)) }
            )
        }
        composable(Routes.KNOWLEDGE) {
            KnowledgeScreen(repository = repository, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                repository = repository,
                onBack = { nav.popBackStack() },
                onChangeKey = {
                    nav.navigate(Routes.API_KEY)
                }
            )
        }
        composable(
            Routes.REVIEW,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("projectId") ?: return@composable
            ReviewScreen(
                repository = repository,
                projectId = id,
                onBack = { nav.popBackStack() },
                onExport = { nav.navigate(Routes.export(id)) }
            )
        }
        composable(
            Routes.EXPORT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("projectId") ?: return@composable
            ExportScreen(
                repository = repository,
                projectId = id,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
