package com.gitcove.app.ui.repos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.ConfirmDialog
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveEmptyState
import com.gitcove.app.ui.components.CoveProgressBar
import com.gitcove.app.ui.components.CoveSectionTitle
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.components.EmptyView
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.repos.RepoListViewModel.Item
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.util.relativeTime
import kotlinx.coroutines.launch

/**
 * 仓库列表页（MD2 密集列表）：应用栏 + 分组标题条 + 单行仓库条目 + 右下角扩展 FAB
 */
@Composable
fun RepoListScreen(container: AppContainer, nav: androidx.navigation.NavHostController) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val vm: RepoListViewModel = viewModel(factory = com.gitcove.app.util.vmFactory { RepoListViewModel(container) })
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deleteTarget by remember { mutableStateOf<Item?>(null) }
    var deleteFiles by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.appName,
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = S.settings, modifier = Modifier.size(20.dp))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.CLONE) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp)) },
                text = { Text(S.cloneOrCreate, style = MaterialTheme.typography.button) },
                backgroundColor = c.secondary,
                contentColor = c.onSecondary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 12.dp)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        backgroundColor = c.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.loading) {
                CoveProgressBar()
            }
            if (vm.items.isEmpty() && !vm.loading) {
                EmptyView(
                    text = S.emptyRepos,
                    icon = Icons.Outlined.AccountTree,
                    action = {
                        TextButton(onClick = { nav.navigate(Routes.CLONE) }) {
                            Text(S.cloneOrCreateRepo, style = MaterialTheme.typography.button, color = c.primary)
                        }
                    }
                )
            } else {
                CoveSectionTitle(text = S.sectionRepos, trailing = "${vm.items.size}")
                LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.items, key = { it.repo.id }) { item ->
                        RepoRow(
                            item = item,
                            onClick = { nav.navigate(Routes.repo(item.repo.id)) },
                            onLongClick = { deleteTarget = item }
                        )
                        CoveDivider()
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = S.deleteRepo,
            text = S.deleteRepoConfirm(target.repo.name),
            confirmLabel = S.delete,
            danger = true,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                vm.delete(target.repo, deleteFiles) { msg -> scope.launch { snackbar.showSnackbar(msg) } }
                deleteTarget = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RepoRow(item: Item, onClick: () -> Unit, onLongClick: () -> Unit) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧图标：MD2 惯用的圆形浅色底 + 主色图标
        Box(
            Modifier
                .size(32.dp)
                .background(c.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.AccountTree,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.repo.name,
                style = MaterialTheme.typography.subtitle2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildString {
                if (item.broken) append(S.repoDirUnavailable)
                else if (item.notGit) append(S.notGitRepoMeta)
                else {
                    append(item.repo.currentBranch.ifBlank { S.unknownBranch })
                    item.repo.remoteUrl?.let { append("  ·  ").append(it.substringAfter("://").substringBefore('/')) }
                    item.repo.lastSync?.let { append("  ·  ").append(S.syncedAt(relativeTime(it, S))) }
                }
            }
            Text(
                meta,
                style = MaterialTheme.typography.caption,
                color = c.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.conflicts > 0) {
            BadgeText(S.conflictsBadge(item.conflicts), c.error)
            Spacer(Modifier.width(5.dp))
        }
        if (item.dirty > 0) {
            BadgeText(S.changesBadge(item.dirty), c.tertiary)
            Spacer(Modifier.width(5.dp))
        }
        if (item.ahead > 0) BadgeText("↑${item.ahead}", c.secondary)
        if (item.behind > 0) BadgeText("↓${item.behind}", c.secondary)
    }
}

@Composable
private fun BadgeText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.overline,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(2.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}
