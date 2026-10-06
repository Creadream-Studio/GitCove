package com.gitcove.app.ui.repo.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitcove.app.data.remote.GitHubApi
import com.gitcove.app.di.AppContainer
import com.gitcove.app.ui.components.SectionHeader
import com.gitcove.app.ui.repo.RepoViewModel
import com.gitcove.app.ui.theme.MonoFont
import kotlinx.coroutines.launch

/**
 * 分支 Tab（功能 23-28 / 40 / 42）：
 * 当前分支信息、本地/远程分支管理、合并、标签、GitHub 一键 PR
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BranchTab(
    vm: RepoViewModel,
    nav: androidx.navigation.NavHostController,
    container: AppContainer
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var createDialog by remember { mutableStateOf(false) }
    var mergeDialog by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf(false) }
    var prDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var branchMenu by remember { mutableStateOf<String?>(null) }

    val st = vm.status
    val locals = vm.branches.filter { !it.isRemote }
    val remotes = vm.branches.filter { it.isRemote }
    val isGithub = GitHubApi.githubOwnerRepo(vm.repo?.remoteUrl) != null

    LazyColumn(Modifier.fillMaxSize()) {

        item {
            SectionHeader("当前分支")
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    if (st?.detached == true) "(detached) ${st.branch.take(10)}" else st?.branch ?: "",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    buildString {
                        if (st != null && !st.detached) {
                            append("领先远端 ${st.ahead} · 落后 ${st.behind}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                TextButton(onClick = { createDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("新建分支", modifier = Modifier.padding(start = 4.dp))
                }
                TextButton(onClick = { mergeDialog = true }) {
                    Icon(Icons.Filled.CallMerge, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("合并到当前", modifier = Modifier.padding(start = 4.dp))
                }
                TextButton(onClick = { tagDialog = true }) {
                    Icon(Icons.Filled.Sell, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("打标签", modifier = Modifier.padding(start = 4.dp))
                }
                if (isGithub) {
                    TextButton(onClick = { prDialog = true }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("创建 PR", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            HorizontalDivider()
        }

        // 本地分支
        item { SectionHeader("本地分支 (${locals.size})") }
        items(locals, key = { "l_" + it.name }) { b ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (!b.isCurrent) vm.checkoutBranch(b.name) },
                        onLongClick = { if (!b.isCurrent) branchMenu = b.name }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (b.isCurrent) "✓ " else "  ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(b.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                b.lastCommitHash?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonoFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }

        // 远程分支
        item { SectionHeader("远程分支 (${remotes.size}) · 点击创建跟踪分支") }
        items(remotes, key = { "r_" + it.name }) { b ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { vm.checkoutBranch(b.name.removePrefix("origin/")) },
                        onLongClick = { branchMenu = b.name }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Tag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    b.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }

        // 标签
        item { SectionHeader("标签 (${vm.tags.size})") }
        items(vm.tags, key = { "t_" + it.name }) { tag ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Tag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(tag.name, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                tag.hash?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonoFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // 分支操作菜单
    branchMenu?.let { name ->
        DropdownMenu(expanded = true, onDismissRequest = { branchMenu = null }) {
            DropdownMenuItem(
                text = { Text("合并 $name 到当前分支") },
                onClick = { branchMenu = null; vm.merge(name) }
            )
            DropdownMenuItem(
                text = { Text("删除分支", color = MaterialTheme.colorScheme.error) },
                onClick = { branchMenu = null; deleteTarget = name }
            )
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除分支") },
            text = { Text("确定删除分支 $deleteTarget 吗？（未合并的提交将被丢弃）") },
            confirmButton = {
                TextButton(onClick = { vm.deleteBranch(deleteTarget!!); deleteTarget = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }

    if (createDialog) {
        var name by remember { mutableStateOf("") }
        var from by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text("新建分支", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("分支名") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = from,
                        onValueChange = { from = it },
                        label = { Text("基于（可选：分支名 / 提交哈希）") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.createBranch(name, from.takeIf { it.isNotBlank() })
                        createDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text("取消") } }
        )
    }

    if (mergeDialog) {
        var ref by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { mergeDialog = false },
            title = { Text("合并到当前分支", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text("将指定分支合并进当前分支：", style = MaterialTheme.typography.bodySmall)
                    locals.filter { !it.isCurrent }.forEach { b ->
                        TextButton(onClick = { ref = b.name }) { Text(if (ref == b.name) "✓ ${b.name}" else b.name) }
                    }
                    OutlinedTextField(
                        value = ref,
                        onValueChange = { ref = it },
                        label = { Text("或输入任意引用") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.merge(ref); mergeDialog = false }, enabled = ref.isNotBlank()) { Text("合并") }
            },
            dismissButton = { TextButton(onClick = { mergeDialog = false }) { Text("取消") } }
        )
    }

    if (tagDialog) {
        var name by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { tagDialog = false },
            title = { Text("创建标签", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("标签名（如 v1.0.0）") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("附注信息（可选，留空为轻量标签）") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.createTag(name, message.takeIf { it.isNotBlank() }); tagDialog = false },
                    enabled = name.isNotBlank()
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { tagDialog = false }) { Text("取消") } }
        )
    }

    if (prDialog) {
        var title by remember { mutableStateOf("") }
        var base by remember { mutableStateOf("main") }
        var body by remember { mutableStateOf("") }
        var creating by remember { mutableStateOf(false) }
        val (owner, repo) = GitHubApi.githubOwnerRepo(vm.repo?.remoteUrl) ?: ("") to ("")
        AlertDialog(
            onDismissRequest = { if (!creating) prDialog = false },
            title = { Text("创建 Pull Request", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text(
                        "$owner/$repo · ${st?.branch} → $base",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = MonoFont
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("PR 标题") },
                        singleLine = true,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    OutlinedTextField(
                        value = base,
                        onValueChange = { base = it },
                        label = { Text("目标分支") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("描述（可选）") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        creating = true
                        scope.launch {
                            container.githubApi.createPullRequest(
                                owner, repo,
                                head = st?.branch ?: "", base = base,
                                title = title, body = body
                            ).onSuccess { url ->
                                vm.toast("PR 已创建：$url")
                                creating = false
                                prDialog = false
                            }.onFailure {
                                vm.toast(it.message ?: "创建 PR 失败")
                                creating = false
                            }
                        }
                    },
                    enabled = title.isNotBlank() && !creating
                ) { Text(if (creating) "创建中…" else "创建") }
            },
            dismissButton = { TextButton(onClick = { prDialog = false }) { Text("取消") } }
        )
    }
}
