package com.gitcove.app.ui.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gitcove.app.data.git.Diff3
import com.gitcove.app.data.git.GitCore
import com.gitcove.app.di.AppContainer
import com.gitcove.app.domain.model.DiffLine
import com.gitcove.app.domain.model.LineType
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveOutlinedButton
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.resolve.BinaryConflictPane
import com.gitcove.app.ui.resolve.ConflictBoard
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.MonoFont
import com.gitcove.app.ui.theme.diffColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 差异对比页（功能 64/65）。
 *
 * - 普通文件：统一视图逐行着色（不变）；
 * - 冲突文件：内容区改为内嵌 VS Code 式冲突解决器（ConflictBoard）——
 *   顶部三个快捷项：采用我方（整侧）/ 采用对方（整侧）/ 手动编辑；
 * - rev 非空 = 提交模式：展示某次提交对该文件的变更（git show <rev> -- <path>）。
 */
@Composable
fun DiffScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController,
    repoId: Long,
    path: String,
    cached: Boolean,
    rev: String? = null
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val S = LocalStrings.current
    var lines by remember { mutableStateOf<List<DiffLine>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var isConflict by remember { mutableStateOf(false) }
    var repoDir by remember { mutableStateOf<File?>(null) }
    // 冲突模式：三版本 + 分块 + 决定（与冲突解决器共用数据结构）
    var sides by remember { mutableStateOf<GitCore.ConflictSides?>(null) }
    var chunks by remember { mutableStateOf<List<Diff3.Chunk>?>(null) }
    val decisions = remember { mutableStateMapOf<Int, Diff3.Decision>() }
    val listState = rememberLazyListState()
    val dc = diffColors()

    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    fun reload() {
        scope.launch(Dispatchers.IO) {
            decisions.clear()
            val repo = container.repoStore.get(repoId)
            val dir = repo?.let { File(it.path) }
            repoDir = dir
            if (dir == null) { loading = false; return@launch }
            if (rev != null) {
                // 提交模式：该提交对此文件的逐行变更（与其第一父提交对比）
                isConflict = false
                sides = null
                chunks = null
                lines = container.gitCore.commitFileDiff(dir, rev, path)
                    .getOrDefault(null to emptyList()).second
            } else {
                val st = container.gitCore.status(dir).getOrNull()
                isConflict = st?.conflicts?.contains(path) == true
                if (isConflict) {
                    // 冲突模式：加载三版本并分块（内嵌解决器的数据）
                    val s = container.gitCore.conflictSides(dir, path)
                    sides = s
                    if (s != null && !s.binary) {
                        val base = s.base?.toString(Charsets.UTF_8)?.lines() ?: emptyList()
                        val ours = s.ours?.toString(Charsets.UTF_8)?.lines() ?: emptyList()
                        val theirs = s.theirs?.toString(Charsets.UTF_8)?.lines() ?: emptyList()
                        val cs = Diff3.merge(base, ours, theirs)
                        chunks = cs
                        lines = emptyList()
                        cs.forEachIndexed { i, c ->
                            when (c.kind) {
                                Diff3.Kind.OURS -> decisions[i] = Diff3.Decision.OURS
                                Diff3.Kind.THEIRS -> decisions[i] = Diff3.Decision.THEIRS
                                else -> {}
                            }
                        }
                    } else {
                        chunks = null
                        // 取不到三版本（如特殊冲突形态）：退化为工作区原文（含 <<<<<<< 标记）
                        val f = File(dir, path)
                        lines = if (f.exists()) f.readLines().map { DiffLine(LineType.CONTEXT, it) } else emptyList()
                    }
                } else {
                    sides = null
                    chunks = null
                    lines = container.gitCore.diffText(dir, path, cached).getOrDefault(emptyList())
                }
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val adds = lines.count { it.type == LineType.ADD }
    val dels = lines.count { it.type == LineType.DEL }
    val pendingCount = chunks?.indices?.count { chunks!![it].kind == Diff3.Kind.CONFLICT && decisions[it] == null } ?: 0
    // 整侧写入中：防连点重复写盘
    var resolving by remember { mutableStateOf(false) }

    /** 整侧一键解决：所有冲突块取同一侧，拼装写回并暂存，随即返回 */
    fun applyWholeSide(ours: Boolean) {
        val cs = chunks ?: return
        if (resolving) return
        resolving = true
        val d = if (ours) Diff3.Decision.OURS else Diff3.Decision.THEIRS
        cs.forEachIndexed { i, c -> if (c.kind == Diff3.Kind.CONFLICT) decisions[i] = d }
        val content = Diff3.assemble(cs, decisions)
        scope.launch(Dispatchers.IO) {
            val result = repoDir?.let { container.gitCore.writeResolvedText(it, path, content) }
            withContext(Dispatchers.Main) {
                resolving = false
                when {
                    result == null -> snackbar.showSnackbar(S.repoUnavailable)
                    result.isSuccess -> {
                        snackbar.showSnackbar(S.conflictResolvedMsg)
                        nav.popBackStack()
                    }
                    else -> snackbar.showSnackbar(result.exceptionOrNull()?.message ?: S.opFailed)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = path.substringAfterLast('/'),
                subtitle = buildString {
                    append(path)
                    append("  ·  ")
                    append(
                        if (rev != null) S.viewerDiffCommit(rev.take(8))
                        else if (isConflict) S.diffConflict
                        else if (cached) S.diffStaged
                        else S.diffUnstaged
                    )
                    if (adds + dels > 0) append("  ·  +$adds -$dels")
                },
                navigationIcon = { CoveBackButton { nav.popBackStack() } },
                actions = {
                    IconButton(onClick = {
                        nav.navigate(Routes.editor(repoId, path))
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = S.edit)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        backgroundColor = CoveTheme.colors.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── 冲突文件：三个快捷项（内容区即新冲突解决器）──
            if (isConflict) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        S.conflictBarTitle,
                        style = MaterialTheme.typography.caption,
                        color = CoveTheme.colors.error
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        CoveButton(
                            text = S.takeOurs,
                            onClick = { applyWholeSide(true) },
                            enabled = chunks != null && !resolving
                        )
                        CoveButton(
                            text = S.takeTheirs,
                            onClick = { applyWholeSide(false) },
                            enabled = chunks != null && !resolving
                        )
                        CoveOutlinedButton(
                            text = S.manualEdit,
                            onClick = { nav.navigate(Routes.editor(repoId, path)) }
                        )
                    }
                }
                CoveDivider()
            }

            when {
                loading -> Text(
                    S.loadingDiff,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(12.dp)
                )

                // ── 冲突模式：内嵌新冲突解决器 ──
                isConflict && sides != null && sides!!.binary -> BinaryConflictPane(
                    gitCore = container.gitCore,
                    dir = repoDir!!,
                    path = path,
                    sides = sides!!,
                    onResolved = { nav.popBackStack() },
                    toast = ::toast
                )

                isConflict && sides != null && chunks != null -> ConflictBoard(
                    gitCore = container.gitCore,
                    dir = repoDir!!,
                    path = path,
                    sides = sides!!,
                    chunks = chunks!!,
                    decisions = decisions,
                    listState = listState,
                    pendingCount = pendingCount,
                    onManualEdit = { nav.navigate(Routes.editor(repoId, path)) },
                    onResolved = { nav.popBackStack() },
                    toast = ::toast
                )

                // ── 普通差异（或冲突形态退化：无三版本时的原文展示）──
                else -> {
                    if (lines.isEmpty()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(S.noDiff, style = MaterialTheme.typography.body2)
                            Text(
                                if (rev != null) S.noDiffCommitHint
                                else if (isConflict) S.noDiffUnstagedHint
                                else if (cached) S.noDiffStagedHint
                                else S.noDiffUnstagedHint,
                                style = MaterialTheme.typography.caption,
                                color = CoveTheme.colors.onSurfaceVariant
                            )
                        }
                    } else {
                        val scroll = rememberScrollState()
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(lines) { line ->
                                val bg = when (line.type) {
                                    LineType.ADD -> dc.addBg
                                    LineType.DEL -> dc.delBg
                                    else -> CoveTheme.colors.background
                                }
                                val fg = when (line.type) {
                                    LineType.ADD -> dc.addFg
                                    LineType.DEL -> dc.delFg
                                    LineType.HUNK -> dc.hunkFg
                                    LineType.META -> dc.metaFg
                                    LineType.CONTEXT -> CoveTheme.colors.onBackground
                                }
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.caption,
                                    fontFamily = MonoFont,
                                    color = fg,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bg)
                                        .horizontalScroll(scroll)
                                        .padding(horizontal = 8.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
