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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitcove.app.data.git.GitConflictException
import com.gitcove.app.di.AppContainer
import com.gitcove.app.domain.model.DiffLine
import com.gitcove.app.domain.model.LineType
import com.gitcove.app.domain.model.Status
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.theme.MonoFont
import com.gitcove.app.ui.theme.diffColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveOutlinedButton
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.theme.CoveTheme

/**
 * 差异对比页（功能 64/65）：统一视图逐行着色；
 * 冲突文件模式下提供 采用我方/采用对方/手动编辑/标记已解决（功能 35-38）
 */
@Composable
fun DiffScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController,
    repoId: Long,
    path: String,
    cached: Boolean
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val S = LocalStrings.current
    var lines by remember { mutableStateOf<List<DiffLine>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var isConflict by remember { mutableStateOf(false) }
    var repoDir by remember { mutableStateOf<java.io.File?>(null) }
    val dc = diffColors()

    fun reload() {
        scope.launch(Dispatchers.IO) {
            val repo = container.repoStore.get(repoId)
            val dir = repo?.let { java.io.File(it.path) }
            repoDir = dir
            if (dir == null) { loading = false; return@launch }
            val st = container.gitCore.status(dir).getOrNull()
            isConflict = st?.conflicts?.contains(path) == true
            lines = if (isConflict) {
                // 冲突文件直接展示工作区内容（含 <<<<<<< 标记）
                val f = java.io.File(dir, path)
                if (f.exists()) f.readLines().map { DiffLine(LineType.CONTEXT, it) } else emptyList()
            } else {
                container.gitCore.diffText(dir, path, cached).getOrDefault(emptyList())
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val adds = lines.count { it.type == LineType.ADD }
    val dels = lines.count { it.type == LineType.DEL }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = path.substringAfterLast('/'),
                subtitle = buildString {
                    append(path)
                    append("  ·  ")
                    append(if (isConflict) S.diffConflict else if (cached) S.diffStaged else S.diffUnstaged)
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

            if (isConflict) {
                fun resolve(action: com.gitcove.app.domain.model.ConflictAction, okMsg: String) {
                    scope.launch(Dispatchers.IO) {
                        val result = repoDir?.let { container.gitCore.resolveConflict(it, path, action) }
                        val msg = when {
                            result == null -> S.repoUnavailable
                            result.isSuccess -> okMsg
                            else -> result.exceptionOrNull()?.message ?: S.opFailed
                        }
                        withContext(Dispatchers.Main) {
                            snackbar.showSnackbar(msg)
                            if (result?.isSuccess == true) nav.popBackStack()
                        }
                    }
                }
                ConflictBar(
                    onOurs = { resolve(com.gitcove.app.domain.model.ConflictAction.OURS, S.tookOurs) },
                    onTheirs = { resolve(com.gitcove.app.domain.model.ConflictAction.THEIRS, S.tookTheirs) },
                    onManual = { nav.navigate(Routes.editor(repoId, path)) },
                    onChunks = { nav.navigate(Routes.resolve(repoId, path)) }
                )
            }

            CoveDivider()

            if (loading) {
                Text(
                    S.loadingDiff,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(12.dp)
                )
            } else if (lines.isEmpty()) {
                Column(Modifier.padding(12.dp)) {
                    Text(S.noDiff, style = MaterialTheme.typography.body2)
                    Text(
                        if (cached) S.noDiffStagedHint else S.noDiffUnstagedHint,
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

@Composable
private fun ConflictBar(
    onOurs: () -> Unit,
    onTheirs: () -> Unit,
    onManual: () -> Unit,
    onChunks: () -> Unit
) {
    val S = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Text(
            S.conflictBarTitle,
            style = MaterialTheme.typography.caption,
            color = CoveTheme.colors.error
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            CoveButton(text = S.resolveTool, onClick = onChunks)
            CoveButton(text = S.takeOurs, onClick = onOurs)
            CoveButton(text = S.takeTheirs, onClick = onTheirs)
            CoveOutlinedButton(text = S.manualEdit, onClick = onManual)
        }
    }
}
