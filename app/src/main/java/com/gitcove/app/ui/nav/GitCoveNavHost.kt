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
import com.gitcove.app.ui.file.FileViewerScreen
import com.gitcove.app.ui.graph.CommitGraphScreen
import com.gitcove.app.ui.repo.RepoScreen
import com.gitcove.app.ui.repos.RepoListScreen
import com.gitcove.app.ui.resolve.ConflictResolverScreen
import com.gitcove.app.ui.settings.AboutScreen
import com.gitcove.app.ui.settings.AccountsScreen
import com.gitcove.app.ui.settings.AiConfigScreen
import com.gitcove.app.ui.settings.AppearanceScreen
import com.gitcove.app.ui.settings.BackupRestoreScreen
import com.gitcove.app.ui.settings.GitIdentityScreen
import com.gitcove.app.ui.settings.LogScreen
import com.gitcove.app.ui.settings.SettingsScreen
import com.gitcove.app.ui.settings.SshScreen
import com.gitcove.app.ui.settings.SyncScreen
import com.gitcove.app.ui.settings.UpdateScreen

object Routes {
    const val REPOS = "repos"
    const val CLONE = "clone"
    const val SETTINGS = "settings"
    const val SETTINGS_GIT = "settings/git"
    const val SETTINGS_AUTH = "settings/auth"
    const val SETTINGS_SSH = "settings/ssh"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_SYNC = "settings/sync"
    const val SETTINGS_AI = "settings/ai"
    const val SETTINGS_BACKUP = "settings/backup"
    const val SETTINGS_ABOUT = "settings/about"
    const val SETTINGS_UPDATE = "settings/update"
    const val LOG = "log"
    const val REPO = "repo/{repoId}"
    const val GRAPH = "graph/{repoId}"
    const val DIFF = "diff/{repoId}?path={path}&cached={cached}&rev={rev}"
    const val RESOLVE = "resolve/{repoId}?path={path}"
    const val EDITOR = "editor/{repoId}?path={path}&isNew={isNew}"
    const val FILE = "file/{repoId}?path={path}"

    fun repo(id: Long) = "repo/$id"

    /** 提交图谱独立页（历史树） */
    fun graph(id: Long) = "graph/$id"

    /** 差异页：rev 非空 = 查看某次提交中该文件的变更（cached 忽略） */
    fun diff(id: Long, path: String, cached: Boolean, rev: String? = null) =
        "diff/$id?path=${Uri.encode(path)}&cached=$cached" +
            (rev?.takeIf { it.isNotBlank() }?.let { "&rev=${Uri.encode(it)}" } ?: "")

    /** 冲突解决器：path 为空 = 仓库级（解决全部冲突） */
    fun resolve(id: Long, path: String? = null) =
        "resolve/$id?path=${Uri.encode(path.orEmpty())}"

    fun editor(id: Long, path: String, isNew: Boolean = false) =
        "editor/$id?path=${Uri.encode(path)}&isNew=$isNew"

    fun file(id: Long, path: String) = "file/$id?path=${Uri.encode(path)}"
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

        composable(Routes.SETTINGS_GIT) {
            GitIdentityScreen(container = container, nav = nav)
        }

        composable(Routes.SETTINGS_AUTH) {
            AccountsScreen(container = container, nav = nav)
        }

        composable(Routes.SETTINGS_SSH) {
            SshScreen(container = container, nav = nav)
        }

        composable(Routes.SETTINGS_APPEARANCE) {
            AppearanceScreen(container = container, nav = nav)
        }

        composable(Routes.SETTINGS_SYNC) {
            SyncScreen(container = container, nav = nav)
        }

        composable(Routes.SETTINGS_AI) {
            AiConfigScreen(container = container, nav = nav)
        }

        composable(Routes.SETTINGS_BACKUP) {
            BackupRestoreScreen(container = container, nav = nav)
        }

        composable(Routes.SETTINGS_ABOUT) {
            AboutScreen(container = container, nav = nav)
        }

        composable(Routes.SETTINGS_UPDATE) {
            UpdateScreen(container = container, nav = nav)
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
            Routes.GRAPH,
            arguments = listOf(navArgument("repoId") { type = NavType.LongType })
        ) { entry ->
            val repoId = entry.arguments?.getLong("repoId") ?: return@composable
            CommitGraphScreen(container = container, nav = nav, repoId = repoId)
        }

        composable(
            Routes.DIFF,
            arguments = listOf(
                navArgument("repoId") { type = NavType.LongType },
                navArgument("path") { type = NavType.StringType },
                navArgument("cached") { type = NavType.BoolType; defaultValue = false },
                navArgument("rev") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val repoId = entry.arguments?.getLong("repoId") ?: return@composable
            val path = entry.arguments?.getString("path").orEmpty()
            val cached = entry.arguments?.getBoolean("cached") ?: false
            val rev = entry.arguments?.getString("rev").orEmpty().ifBlank { null }
            DiffScreen(container = container, nav = nav, repoId = repoId, path = path, cached = cached, rev = rev)
        }

        composable(
            Routes.RESOLVE,
            arguments = listOf(
                navArgument("repoId") { type = NavType.LongType },
                navArgument("path") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val repoId = entry.arguments?.getLong("repoId") ?: return@composable
            val path = entry.arguments?.getString("path").orEmpty()
            ConflictResolverScreen(
                container = container,
                nav = nav,
                repoId = repoId,
                pathParam = path.ifBlank { null }
            )
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

        composable(
            Routes.FILE,
            arguments = listOf(
                navArgument("repoId") { type = NavType.LongType },
                navArgument("path") { type = NavType.StringType }
            )
        ) { entry ->
            val repoId = entry.arguments?.getLong("repoId") ?: return@composable
            val path = entry.arguments?.getString("path").orEmpty()
            FileViewerScreen(container = container, nav = nav, repoId = repoId, path = path)
        }
    }
}
