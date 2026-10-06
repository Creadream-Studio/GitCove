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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.theme.MonoFont
import com.gitcove.app.ui.theme.diffColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 差异对比页（功能 64/65）：统一视图逐行着色；
 * 冲突文件模式下提供 采用我方/采用对方/手动编辑/标记已解决（功能 35-38）
 */
@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = {
                    Column {
                        Text(
                            path.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            buildString {
                                append(path)
                                append("  ·  ")
                                append(if (isConflict) "冲突" else if (cached) "已暂存" else "未暂存")
                                if (adds + dels > 0) append("  ·  +$adds -$dels")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        nav.navigate(Routes.editor(repoId, path))
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "编辑")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            if (isConflict) {
                fun resolve(action: com.gitcove.app.domain.model.ConflictAction, okMsg: String) {
                    scope.launch(Dispatchers.IO) {
                        val result = repoDir?.let { container.gitCore.resolveConflict(it, path, action) }
                        val msg = when {
                            result == null -> "仓库不可用"
                            result.isSuccess -> okMsg
                            else -> result.exceptionOrNull()?.message ?: "操作失败"
                        }
                        withContext(Dispatchers.Main) {
                            snackbar.showSnackbar(msg)
                            if (result?.isSuccess == true) nav.popBackStack()
                        }
                    }
                }
                ConflictBar(
                    onOurs = { resolve(com.gitcove.app.domain.model.ConflictAction.OURS, "已采用我方版本") },
                    onTheirs = { resolve(com.gitcove.app.domain.model.ConflictAction.THEIRS, "已采用对方版本") },
                    onManual = { nav.navigate(Routes.editor(repoId, path)) }
                )
            }

            HorizontalDivider()

            if (loading) {
                Text(
                    "加载差异…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            } else if (lines.isEmpty()) {
                Column(Modifier.padding(12.dp)) {
                    Text("没有差异内容", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (cached) "（暂存区与 HEAD 一致）" else "（工作区与暂存区一致，或为未跟踪文件）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val scroll = rememberScrollState()
                LazyColumn(Modifier.fillMaxSize()) {
                    items(lines) { line ->
                        val bg = when (line.type) {
                            LineType.ADD -> dc.addBg
                            LineType.DEL -> dc.delBg
                            else -> MaterialTheme.colorScheme.background
                        }
                        val fg = when (line.type) {
                            LineType.ADD -> dc.addFg
                            LineType.DEL -> dc.delFg
                            LineType.HUNK -> dc.hunkFg
                            LineType.META -> dc.metaFg
                            LineType.CONTEXT -> MaterialTheme.colorScheme.onBackground
                        }
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodySmall,
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
    onManual: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Text(
            "该文件存在合并冲突，选择处理方式：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOurs) { Text("采用我方") }
            Button(onClick = onTheirs) { Text("采用对方") }
            OutlinedButton(onClick = onManual) { Text("手动编辑") }
        }
    }
}
