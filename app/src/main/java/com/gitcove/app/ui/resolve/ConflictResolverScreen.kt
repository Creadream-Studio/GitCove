package com.gitcove.app.ui.resolve

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gitcove.app.data.git.Diff3
import com.gitcove.app.data.git.GitCore
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.theme.CoveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 冲突解决器（Working Copy 4.6 Resolve 工具 → VS Code 式重写）。
 *
 * - 仓库级（path 为空）：顶部文件 chips 切换，逐一解决；
 * - 文本冲突：ConflictBoard——上下堆叠全宽、词级高亮、内联选项、结果即时可见；
 * - 二进制：BinaryConflictPane 并排两版本点选；
 * - Resolve 写回并暂存标记，下次提交完成合并（改动页自动带入合并提交信息）。
 */
@Composable
fun ConflictResolverScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController,
    repoId: Long,
    pathParam: String?
) {
    val S = LocalStrings.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var dir by remember { mutableStateOf<File?>(null) }

    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf(pathParam.orEmpty()) }
    val doneFiles = remember { mutableStateMapOf<String, Boolean>() }

    // 当前文件三版本与分块
    var sides by remember(selected) { mutableStateOf<GitCore.ConflictSides?>(null) }
    var chunks by remember(selected) { mutableStateOf<List<Diff3.Chunk>?>(null) }
    val decisions = remember(selected) { mutableStateMapOf<Int, Diff3.Decision>() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val repo = withContext(Dispatchers.IO) { container.repoStore.get(repoId) }
        dir = repo?.let { File(it.path) }
        if (dir == null) return@LaunchedEffect
        if (pathParam.isNullOrBlank()) {
            val st = withContext(Dispatchers.IO) { container.gitCore.status(dir!!) }
            files = st.getOrNull()?.conflicts.orEmpty()
            if (selected.isBlank()) selected = files.firstOrNull().orEmpty()
        } else {
            files = listOf(pathParam)
        }
    }

    LaunchedEffect(selected, dir) {
        decisions.clear()
        val d = dir ?: return@LaunchedEffect
        if (selected.isBlank()) return@LaunchedEffect
        val s = withContext(Dispatchers.IO) { container.gitCore.conflictSides(d, selected) }
        sides = s
        chunks = null
        if (s != null && !s.binary) {
            val base = s.base?.toString(Charsets.UTF_8)?.lines() ?: emptyList()
            val ours = s.ours?.toString(Charsets.UTF_8)?.lines() ?: emptyList()
            val theirs = s.theirs?.toString(Charsets.UTF_8)?.lines() ?: emptyList()
            val cs = Diff3.merge(base, ours, theirs)
            chunks = cs
            // 单侧改动块预设决定（与 assemble 的默认一致；冲突块留给用户）
            cs.forEachIndexed { i, c ->
                when (c.kind) {
                    Diff3.Kind.OURS -> decisions[i] = Diff3.Decision.OURS
                    Diff3.Kind.THEIRS -> decisions[i] = Diff3.Decision.THEIRS
                    else -> {}
                }
            }
        }
    }

    val pendingCount = chunks?.indices?.count { chunks!![it].kind == Diff3.Kind.CONFLICT && decisions[it] == null } ?: 0

    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    /** 单文件解决完成：标记 → 自动翻到下一个未解决文件 → 全部完成则返回 */
    fun afterResolved() {
        doneFiles[selected] = true
        val next = files.firstOrNull { it != selected && doneFiles[it] != true }
        if (next != null) selected = next else nav.popBackStack()
    }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.resolverTitle,
                subtitle = if (files.size > 1) S.conflictsSection(files.size) else selected,
                navigationIcon = { CoveBackButton { nav.popBackStack() } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        backgroundColor = CoveTheme.colors.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 文件 chips（仓库级多文件）
            if (files.size > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    files.forEach { f ->
                        val isSel = f == selected
                        val done = doneFiles[f] == true
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                done -> CoveTheme.colors.primary.copy(alpha = 0.18f)
                                isSel -> CoveTheme.colors.primary
                                else -> CoveTheme.colors.surfaceVariant
                            },
                            border = if (isSel && !done) BorderStroke(1.dp, CoveTheme.colors.primary) else null,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clickable { selected = f }
                        ) {
                            Text(
                                if (done) "${S.fileDoneMark} $f" else f.substringAfterLast('/'),
                                style = MaterialTheme.typography.caption,
                                color = if (isSel && !done) CoveTheme.colors.surface else CoveTheme.colors.onSurface,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                CoveDivider()
            }

            when {
                dir == null || selected.isBlank() || sides == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { Text(S.loadingDiff, style = MaterialTheme.typography.caption) }

                sides!!.binary -> BinaryConflictPane(
                    gitCore = container.gitCore,
                    dir = dir!!,
                    path = selected,
                    sides = sides!!,
                    onResolved = ::afterResolved,
                    toast = ::toast
                )

                else -> ConflictBoard(
                    gitCore = container.gitCore,
                    dir = dir!!,
                    path = selected,
                    sides = sides!!,
                    chunks = chunks.orEmpty(),
                    decisions = decisions,
                    listState = listState,
                    pendingCount = pendingCount,
                    onManualEdit = { nav.navigate(Routes.editor(repoId, selected)) },
                    onResolved = ::afterResolved,
                    toast = ::toast
                )
            }
        }
    }
}
