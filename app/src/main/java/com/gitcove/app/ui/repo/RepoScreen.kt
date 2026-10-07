package com.gitcove.app.ui.repo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitcove.app.di.AppContainer
import com.gitcove.app.ui.components.ConfirmDialog
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.repo.tabs.BranchTab
import com.gitcove.app.ui.repo.tabs.ChangesTab
import com.gitcove.app.ui.repo.tabs.FilesTab
import com.gitcove.app.ui.repo.tabs.HistoryTab
import com.gitcove.app.util.vmFactory
import com.gitcove.app.ui.theme.MonoFont

/**
 * 仓库详情页（文档 5.3）：顶部操作栏 + 四 Tab + 悬浮状态按钮（功能 77）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController,
    repoId: Long
) {
    val vm: RepoViewModel = viewModel(
        key = "repo_$repoId",
        factory = vmFactory { RepoViewModel(container, repoId) }
    )
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(0) }
    var branchMenu by remember { mutableStateOf(false) }
    var overflowMenu by remember { mutableStateOf(false) }
    var showPanel by remember { mutableStateOf(false) }
    var forcePushConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val st = vm.status
    val dirty = st?.dirtyCount ?: 0
    val conflicts = st?.conflicts?.size ?: 0
    // 非 Git 仓库：所有 Git 相关功能置灰，仅保留文件浏览 / 编辑，提示先初始化
    val gitEnabled = vm.isGitRepo

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            vm.repo?.name ?: "…",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            buildString {
                                append(if (st?.detached == true) "(detached HEAD @ ${st.branch.take(8)})" else st?.branch ?: "")
                                if ((st?.ahead ?: 0) > 0) append("  ↑${st!!.ahead}")
                                if ((st?.behind ?: 0) > 0) append("  ↓${st!!.behind}")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 分支快速切换（功能 23；非 Git 仓库置灰）
                    if (gitEnabled) {
                        Box {
                            TextButton(onClick = { branchMenu = true }) {
                                Text(
                                    st?.branch?.take(14) ?: "",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(expanded = branchMenu, onDismissRequest = { branchMenu = false }) {
                                vm.branches.filter { !it.isRemote }.forEach { b ->
                                    DropdownMenuItem(
                                        text = { Text(if (b.isCurrent) "✓ ${b.name}" else b.name) },
                                        onClick = {
                                            branchMenu = false
                                            if (!b.isCurrent) vm.checkoutBranch(b.name)
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("分支管理…") },
                                    onClick = { branchMenu = false; tab = 3 }
                                )
                            }
                        }
                        // 同步快捷操作
                        IconButton(onClick = { vm.fetch() }, enabled = !vm.busy) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = "Fetch")
                        }
                        IconButton(onClick = { vm.pull() }, enabled = !vm.busy) {
                            Icon(Icons.Filled.Download, contentDescription = "Pull")
                        }
                        IconButton(onClick = { vm.push() }, enabled = !vm.busy) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = "Push")
                        }
                    }
                    Box {
                        IconButton(onClick = { overflowMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = overflowMenu, onDismissRequest = { overflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("强制推送（Force Push）") },
                                onClick = { overflowMenu = false; forcePushConfirm = true },
                                enabled = gitEnabled
                            )
                            DropdownMenuItem(
                                text = { Text("打开设置") },
                                onClick = { overflowMenu = false; nav.navigate(Routes.SETTINGS) }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // 悬浮状态按钮：点击展开仓库状态面板（非 Git 仓库不显示）
            if (gitEnabled) {
                BadgedBox(badge = {
                    if (dirty > 0 || conflicts > 0) {
                        Text(
                            "${dirty + conflicts}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .padding(2.dp)
                                .background(
                                    if (conflicts > 0) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.tertiary,
                                    androidx.compose.foundation.shape.CircleShape
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }) {
                    FloatingActionButton(
                        onClick = { showPanel = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = "仓库状态面板")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.busy) {
                LinearProgressIndicator(
                    progress = { if (vm.progress.isBlank()) 0.3f else 0.7f },
                    modifier = Modifier.fillMaxWidth().height(2.dp)
                )
            }
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text("文件") })
                Tab(tab == 1, onClick = { tab = 1 }, enabled = gitEnabled, text = { Text("改动 $dirty") })
                Tab(tab == 2, onClick = { tab = 2 }, enabled = gitEnabled, text = { Text("历史") })
                Tab(tab == 3, onClick = { tab = 3 }, enabled = gitEnabled, text = { Text("分支") })
            }
            // 非 Git 仓库：显示初始化引导横幅
            if (!gitEnabled && vm.repo != null) {
                NotGitRepoBanner(onInit = { vm.initGitRepo() }, busy = vm.busy)
            }
            when (tab) {
                0 -> FilesTab(vm = vm, nav = nav)
                1 -> ChangesTab(vm = vm, nav = nav)
                2 -> HistoryTab(vm = vm, nav = nav)
                3 -> BranchTab(vm = vm, nav = nav, container = container)
            }
        }
    }

    // 悬浮状态面板
    if (showPanel) {
        ModalBottomSheet(onDismissRequest = { showPanel = false }) {
            StatusPanel(vm = vm, nav = nav, onClose = { showPanel = false }, onGoCommit = { showPanel = false; tab = 1 })
        }
    }

    if (forcePushConfirm) {
        ConfirmDialog(
            title = "强制推送",
            text = "将用本地分支覆盖远端，远端上未经合并的提交会丢失。确定继续吗？",
            confirmLabel = "强制推送",
            danger = true,
            onDismiss = { forcePushConfirm = false },
            onConfirm = { forcePushConfirm = false; vm.push(force = true) }
        )
    }
}

/** 非 Git 仓库提示横幅：Git 功能置灰，引导初始化 */
@Composable
private fun NotGitRepoBanner(onInit: () -> Unit, busy: Boolean) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "当前目录不是 Git 仓库，Git 相关功能已禁用",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                "初始化 Git 仓库后即可使用提交、分支、历史等功能（文件浏览与编辑不受影响）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Button(
                onClick = onInit,
                enabled = !busy,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text("初始化 Git 仓库")
            }
        }
    }
}

/** 仓库状态面板（改动文件 + 快捷操作，文档 5.4） */
@Composable
private fun StatusPanel(
    vm: RepoViewModel,
    nav: androidx.navigation.NavHostController,
    onClose: () -> Unit,
    onGoCommit: () -> Unit
) {
    val st = vm.status
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).verticalScroll(rememberScrollState())) {
        Text(
            "仓库状态",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        val all = (st?.staged.orEmpty() + st?.unstaged.orEmpty())
        if (all.isEmpty() && (st?.conflicts?.isEmpty() != false)) {
            Text("工作区干净，没有待提交的改动", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(Modifier.height(200.dp)) {
                items(st!!.conflicts) { path ->
                    Text(
                        "⚔ $path",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                items(all) { f ->
                    Text(
                        "${com.gitcove.app.util.statusLetter(f.status)} ${f.path}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = MonoFont,
                        maxLines = 1
                    )
                }
            }
        }
        Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            Button(onClick = onGoCommit, enabled = (vm.status?.dirtyCount ?: 0) > 0) {
                Icon(Icons.Filled.Commit, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("提交", modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(onClick = { vm.fetch(); onClose() }) { Text("Fetch") }
            OutlinedButton(onClick = { vm.pull(); onClose() }) { Text("Pull") }
            OutlinedButton(onClick = { vm.push(); onClose() }) { Text("Push") }
        }
    }
}
