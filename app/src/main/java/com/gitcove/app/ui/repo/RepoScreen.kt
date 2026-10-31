package com.gitcove.app.ui.repo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.ConfirmDialog
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveBadge
import com.gitcove.app.ui.components.CoveBottomSheet
import com.gitcove.app.ui.components.CoveButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveOutlinedButton
import com.gitcove.app.ui.components.CoveProgressBar
import com.gitcove.app.ui.components.CoveTabRow
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.repo.tabs.BranchTab
import com.gitcove.app.ui.repo.tabs.ChangesTab
import com.gitcove.app.ui.repo.tabs.FilesTab
import com.gitcove.app.ui.repo.tabs.HistoryTab
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.MonoFont
import com.gitcove.app.util.findLifecycleOwner
import com.gitcove.app.util.vmFactory

/**
 * 仓库详情页（MD2）：主色应用栏（标题 + 分支状态副标题）+ MD2 Tab 行 +
 * 右下角悬浮状态 FAB（带改动角标）+ 底部状态抽屉。
 */
@Composable
fun RepoScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController,
    repoId: Long
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
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

    // 从编辑器保存、差异页解决冲突等子页面返回本页时，导航栈条目会重新 RESUMED，
    // 借此立刻刷新仓库状态，改动无需重进仓库即可看到
    val backEntry = nav.currentBackStackEntry
    DisposableEffect(backEntry) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshQuick()
        }
        backEntry?.lifecycle?.addObserver(obs)
        onDispose { backEntry?.lifecycle?.removeObserver(obs) }
    }

    // 应用切后台再回到前台（例如去 MT 管理器改完文件切回来）时自动轻量刷新：
    // 导航栈条目的生命周期覆盖不到 Activity 级别的暂停/恢复，必须观察 Activity 本身
    val hostContext = LocalContext.current
    val hostLifecycle = remember(hostContext) { hostContext.findLifecycleOwner() }
    DisposableEffect(hostLifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshQuick()
        }
        hostLifecycle?.lifecycle?.addObserver(obs)
        onDispose { hostLifecycle?.lifecycle?.removeObserver(obs) }
    }

    val st = vm.status
    val dirty = st?.dirtyCount ?: 0
    val conflicts = st?.conflicts?.size ?: 0
    // 非 Git 仓库：仅保留文件浏览 / 编辑，并提示先初始化（读取见使用处，保证随状态重组）

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = vm.repo?.name ?: "…",
                subtitle = buildString {
                    append(if (st?.detached == true) "(detached HEAD @ ${st.branch.take(8)})" else st?.branch ?: "")
                    if ((st?.ahead ?: 0) > 0) append("  ↑${st!!.ahead}")
                    if ((st?.behind ?: 0) > 0) append("  ↓${st!!.behind}")
                },
                navigationIcon = { CoveBackButton { nav.popBackStack() } },
                actions = {
                    // 分支快速切换（功能 23；非 Git 仓库置灰）
                    if (vm.isGitRepo) {
                        Box {
                            TextButton(onClick = { branchMenu = true }) {
                                Text(
                                    st?.branch?.take(12) ?: "",
                                    style = MaterialTheme.typography.button,
                                    color = c.onAppBar
                                )
                            }
                            DropdownMenu(expanded = branchMenu, onDismissRequest = { branchMenu = false }) {
                                vm.branches.filter { !it.isRemote }.forEach { b ->
                                    DropdownMenuItem(onClick = {
                                        branchMenu = false
                                        if (!b.isCurrent) vm.checkoutBranch(b.name)
                                    }) {
                                        Text(
                                            if (b.isCurrent) "✓ ${b.name}" else b.name,
                                            style = MaterialTheme.typography.body2
                                        )
                                    }
                                }
                                CoveDivider()
                                DropdownMenuItem(onClick = { branchMenu = false; tab = 3 }) {
                                    Text(S.branchManager, style = MaterialTheme.typography.body2)
                                }
                            }
                        }
                        // 同步快捷操作（detached HEAD 下 pull/push 无分支可言，置灰防怪异失败）
                        val detached = st?.detached == true
                        IconButton(onClick = { vm.fetch() }, enabled = !vm.busy) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = "Fetch", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { vm.pull() }, enabled = !vm.busy && !detached) {
                            Icon(Icons.Filled.Download, contentDescription = "Pull", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { vm.push() }, enabled = !vm.busy && !detached) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = "Push", modifier = Modifier.size(20.dp))
                        }
                        // 提交图谱独立页（4.5）：历史树专门入口
                        IconButton(onClick = { nav.navigate(Routes.graph(repoId)) }, enabled = !vm.busy) {
                            Icon(Icons.Filled.AccountTree, contentDescription = S.commitGraph, modifier = Modifier.size(20.dp))
                        }
                    }
                    Box {
                        IconButton(onClick = { overflowMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = S.more, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = overflowMenu, onDismissRequest = { overflowMenu = false }) {
                            DropdownMenuItem(
                                onClick = { overflowMenu = false; forcePushConfirm = true },
                                enabled = vm.isGitRepo
                            ) { Text(S.forcePushMenu, style = MaterialTheme.typography.body2) }
                            DropdownMenuItem(onClick = { overflowMenu = false; nav.navigate(Routes.SETTINGS) }) {
                                Text(S.openSettings, style = MaterialTheme.typography.body2)
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // 悬浮状态按钮：点击展开仓库状态面板（非 Git 仓库不显示）
            if (vm.isGitRepo) {
                Box {
                    FloatingActionButton(
                        onClick = { showPanel = true },
                        backgroundColor = c.secondary,
                        contentColor = c.onSecondary,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 10.dp),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = S.statusPanelDesc, modifier = Modifier.size(22.dp))
                    }
                    val count = dirty + conflicts
                    if (count > 0) {
                        CoveBadge(
                            count = count,
                            color = if (conflicts > 0) c.error else c.tertiary,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp)
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        backgroundColor = c.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.busy) {
                CoveProgressBar(progress = if (vm.progress.isBlank()) 0.3f else 0.7f)
            }
            // 非 Git 仓库只保留「文件」Tab（其余功能无意义，MD2 下不做灰色禁用态堆叠）
            val tabs = if (vm.isGitRepo) {
                listOf(S.tabFiles, S.tabChanges(dirty), S.tabHistory, S.tabBranches)
            } else {
                listOf(S.tabFiles)
            }
            CoveTabRow(
                tabs = tabs,
                selectedIndex = tab.coerceIn(0, tabs.lastIndex),
                onSelect = { tab = it }
            )
            // 非 Git 仓库：显示初始化引导横幅
            if (!vm.isGitRepo && vm.repo != null) {
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

    // 悬浮状态面板（MD2 底部抽屉）
    if (showPanel) {
        CoveBottomSheet(onDismiss = { showPanel = false }) {
            StatusPanel(vm = vm, nav = nav, onClose = { showPanel = false }, onGoCommit = { showPanel = false; tab = 1 })
        }
    }

    if (forcePushConfirm) {
        ConfirmDialog(
            title = S.forcePush,
            text = S.forcePushConfirmText,
            confirmLabel = S.forcePush,
            danger = true,
            onDismiss = { forcePushConfirm = false },
            onConfirm = { forcePushConfirm = false; vm.push(force = true) }
        )
    }
}

/** 非 Git 仓库提示横幅：Git 功能置灰，引导初始化 */
@Composable
private fun NotGitRepoBanner(onInit: () -> Unit, busy: Boolean) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    Surface(color = c.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                S.notGitBannerTitle,
                style = MaterialTheme.typography.subtitle2,
                color = c.onTertiaryContainer
            )
            Text(
                S.notGitBannerDesc,
                style = MaterialTheme.typography.caption,
                color = c.onTertiaryContainer
            )
            CoveButton(
                text = S.initGitRepo,
                onClick = onInit,
                enabled = !busy,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** 仓库状态面板（改动文件 + 快捷操作） */
@Composable
private fun StatusPanel(
    vm: RepoViewModel,
    nav: androidx.navigation.NavHostController,
    onClose: () -> Unit,
    onGoCommit: () -> Unit
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val st = vm.status
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(
            S.statusPanelTitle,
            style = MaterialTheme.typography.subtitle1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        CoveDivider()
        val all = (st?.staged.orEmpty() + st?.unstaged.orEmpty())
        if (all.isEmpty() && (st?.conflicts?.isEmpty() != false)) {
            Text(
                S.cleanWorkingTree,
                style = MaterialTheme.typography.body2,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            LazyColumn(Modifier.height(200.dp)) {
                items(st!!.conflicts) { path ->
                    Text(
                        "⚔ $path",
                        style = MaterialTheme.typography.caption,
                        color = c.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                    )
                }
                items(all) { f ->
                    Text(
                        "${com.gitcove.app.util.statusLetter(f.status)} ${f.path}",
                        style = MaterialTheme.typography.caption,
                        fontFamily = MonoFont,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CoveButton(
                text = S.commit,
                onClick = onGoCommit,
                enabled = (vm.status?.dirtyCount ?: 0) > 0,
                icon = { Icon(Icons.Filled.Commit, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            CoveOutlinedButton(text = S.fetch, onClick = { vm.fetch(); onClose() })
            CoveOutlinedButton(text = S.pull, onClick = { vm.pull(); onClose() })
            CoveOutlinedButton(text = S.push, onClick = { vm.push(); onClose() })
        }
    }
}
