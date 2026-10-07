package com.gitcove.app.ui.repos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitcove.app.di.AppContainer
import com.gitcove.app.ui.components.ConfirmDialog
import com.gitcove.app.ui.components.EmptyView
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.repos.RepoListViewModel.Item
import com.gitcove.app.util.relativeTime
import kotlinx.coroutines.launch

/**
 * 仓库列表页（文档 5.3）：紧凑列表 + 分支/改动徽章 + 设置入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(container: AppContainer, nav: androidx.navigation.NavHostController) {
    val vm: RepoListViewModel = viewModel(factory = com.gitcove.app.util.vmFactory { RepoListViewModel(container) })
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deleteTarget by remember { mutableStateOf<Item?>(null) }
    var deleteFiles by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("码湾 GitCove", style = MaterialTheme.typography.titleLarge)
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.CLONE) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("克隆 / 新建") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            }
            if (vm.items.isEmpty() && !vm.loading) {
                EmptyView(
                    text = "还没有仓库\n点击右下角按钮，从远程克隆或新建一个仓库开始",
                    action = {
                        TextButton(onClick = { nav.navigate(Routes.CLONE) }) { Text("克隆 / 新建仓库") }
                    }
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.items, key = { it.repo.id }) { item ->
                        RepoRow(
                            item = item,
                            onClick = { nav.navigate(Routes.repo(item.repo.id)) },
                            onLongClick = { deleteTarget = item }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "删除仓库",
            text = "确定从码湾移除「${target.repo.name}」吗？",
            confirmLabel = "删除",
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
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.AccountTree,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                item.repo.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (item.conflicts > 0) {
                BadgeText("${item.conflicts} 冲突", MaterialTheme.colorScheme.error)
                Spacer(Modifier.size(6.dp))
            }
            if (item.dirty > 0) {
                BadgeText("${item.dirty} 改动", MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.size(6.dp))
            }
            if (item.ahead > 0) BadgeText("↑${item.ahead}", MaterialTheme.colorScheme.secondary)
            if (item.behind > 0) BadgeText("↓${item.behind}", MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val meta = buildString {
                if (item.broken) append("仓库目录不可用")
                else if (item.notGit) append("非 Git 仓库 · 待初始化")
                else {
                    append(item.repo.currentBranch.ifBlank { "未知分支" })
                    item.repo.remoteUrl?.let { append("  ·  ").append(it.substringAfter("://").substringBefore('/')) }
                    item.repo.lastSync?.let { append("  ·  同步于 ").append(relativeTime(it)) }
                }
            }
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BadgeText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
