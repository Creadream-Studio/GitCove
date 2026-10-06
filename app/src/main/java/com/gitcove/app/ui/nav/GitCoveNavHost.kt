package com.gitcove.app.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gitcove.app.di.AppContainer
import com.gitcove.app.ui.clone.CloneScreen
import com.gitcove.app.ui.diff.DiffScreen
import com.gitcove.app.ui.editor.EditorScreen
import com.gitcove.app.ui.repo.RepoScreen
import com.gitcove.app.ui.repos.RepoListScreen
import com.gitcove.app.ui.settings.LogScreen
import com.gitcove.app.ui.settings.SettingsScreen

object Routes {
    const val REPOS = "repos"
    const val CLONE = "clone"
    const val SETTINGS = "settings"
    const val LOG = "log"
    const val REPO = "repo/{repoId}"
    const val DIFF = "diff/{repoId}?path={path}&cached={cached}"
    const val EDITOR = "editor/{repoId}?path={path}&isNew={isNew}"

    fun repo(id: Long) = "repo/$id"
    fun diff(id: Long, path: String, cached: Boolean) =
        "diff/$id?path=${Uri.encode(path)}&cached=$cached"

    fun editor(id: Long, path: String, isNew: Boolean = false) =
        "editor/$id?path=${Uri.encode(path)}&isNew=$isNew"
}

@Composable
fun GitCoveNavHost(container: AppContainer) {
    val nav: NavHostController = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.REPOS) {

        composable(Routes.REPOS) {
            RepoListScreen(container = container, nav = nav)
        }

        composable(Routes.CLONE) {
            CloneScreen(container = container, nav = nav)
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(container = container, nav = nav)
        }

        composable(Routes.LOG) {
            LogScreen(container = container, nav = nav)
        }

        composable(
            Routes.REPO,
            arguments = listOf(navArgument("repoId") { type = NavType.LongType })
        ) { entry ->
            val repoId = entry.arguments?.getLong("repoId") ?: return@composable
            RepoScreen(container = container, nav = nav, repoId = repoId)
        }

        composable(
            Routes.DIFF,
            arguments = listOf(
                navArgument("repoId") { type = NavType.LongType },
                navArgument("path") { type = NavType.StringType },
                navArgument("cached") { type = NavType.BoolType; defaultValue = false }
            )
        ) { entry ->
            val repoId = entry.arguments?.getLong("repoId") ?: return@composable
            val path = entry.arguments?.getString("path").orEmpty()
            val cached = entry.arguments?.getBoolean("cached") ?: false
            DiffScreen(container = container, nav = nav, repoId = repoId, path = path, cached = cached)
        }

        composable(
            Routes.EDITOR,
            arguments = listOf(
                navArgument("repoId") { type = NavType.LongType },
                navArgument("path") { type = NavType.StringType },
                navArgument("isNew") { type = NavType.BoolType; defaultValue = false }
            )
        ) { entry ->
            val repoId = entry.arguments?.getLong("repoId") ?: return@composable
            val path = entry.arguments?.getString("path").orEmpty()
            val isNew = entry.arguments?.getBoolean("isNew") ?: false
            EditorScreen(container = container, nav = nav, repoId = repoId, path = path, isNew = isNew)
        }
    }
}
