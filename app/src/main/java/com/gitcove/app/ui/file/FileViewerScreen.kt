package com.gitcove.app.ui.file

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
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
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gitcove.app.di.AppContainer
import com.gitcove.app.domain.model.Commit
import com.gitcove.app.domain.model.ConflictAction
import com.gitcove.app.domain.model.DiffLine
import com.gitcove.app.domain.model.LineType
import com.gitcove.app.domain.model.Repository
import com.gitcove.app.domain.model.Status
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.i18n.Strings
import com.gitcove.app.ui.components.Avatar
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveOutlinedButton
import com.gitcove.app.ui.components.CoveSectionTitle
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.components.EmptyView
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.MonoFont
import com.gitcove.app.util.FileKind
import com.gitcove.app.util.FileTypes
import com.gitcove.app.util.fileSizeText
import com.gitcove.app.util.findLifecycleOwner
import com.gitcove.app.util.relativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

/**
 * 文件查看页：点击文件进入，底部三个标签切换——
 * - 内容：文本→行号代码编辑器（可编辑保存）；图片→缩放查看；视频/音频→播放器；
 *   陌生文件（含二进制）→始终用代码编辑器呈现（二进制只读）
 * - 差异：文本→增减行着色；图片→新旧双图并排、拖动缩放同步；其余→有无改变
 *   （默认对比工作区 vs 上次提交；从历史页点提交后显示该提交的变更）
 * - 历史：该文件的提交列表，点击提交跳到差异页看它对该文件做的改动
 *
 * 非 Git 仓库只有内容页；顶栏右上角提供关闭按钮（返回文件列表）。
 */
@Composable
fun FileViewerScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController,
    repoId: Long,
    path: String
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var repo by remember { mutableStateOf<Repository?>(null) }
    var isGitRepo by remember { mutableStateOf(true) }
    var kind by remember { mutableStateOf<FileKind?>(null) }
    var isConflict by remember { mutableStateOf(false) }

    // 文本编辑状态（切换差异/历史标签后回来不丢）
    var textLoaded by remember { mutableStateOf(false) }
    var textContent by remember { mutableStateOf("") }
    var textOriginal by remember { mutableStateOf("") }

    var tab by remember { mutableStateOf(0) }
    var selectedCommit by remember { mutableStateOf<Commit?>(null) }
    // 保存/解决冲突后 +1，让差异页重新计算
    var refreshTick by remember { mutableStateOf(0) }

    val file: File? = repo?.let { safeRepoFile(File(it.path), path) }

    // 应用切后台再回到前台时从磁盘重读（外部应用如 MT 管理器改完文件切回即见最新内容）。
    // 文本正在编辑（有未保存改动）时不动用户内容；图片/视频等让差异页重新计算。
    val hostContext = LocalContext.current
    val hostLifecycle = remember(hostContext) { hostContext.findLifecycleOwner() }
    DisposableEffect(hostLifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val k = kind ?: return@LifecycleEventObserver
            val dir0 = repo?.let { File(it.path) } ?: return@LifecycleEventObserver
            if (k == FileKind.TEXT) {
                if (textLoaded && textContent != textOriginal) return@LifecycleEventObserver
                scope.launch(Dispatchers.IO) {
                    val content = container.gitCore.readFile(dir0, path).getOrDefault("")
                    textContent = content
                    textOriginal = content
                    refreshTick++
                }
            } else {
                refreshTick++
            }
        }
        hostLifecycle?.lifecycle?.addObserver(obs)
        onDispose { hostLifecycle?.lifecycle?.removeObserver(obs) }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val r = container.repoStore.get(repoId) ?: return@withContext
            repo = r
            val dir = File(r.path)
            isGitRepo = r.isGitRepo && container.gitCore.isValidRepo(dir)
            val f = safeRepoFile(dir, path)
            kind = FileTypes.classify(path.substringAfterLast('/'), f)
            if (kind == FileKind.TEXT || kind == FileKind.BINARY) {
                val content = container.gitCore.readFile(dir, path).getOrDefault("")
                textContent = content
                textOriginal = content
                textLoaded = true
            }
            val st = if (isGitRepo) container.gitCore.status(dir).getOrNull() else null
            isConflict = st?.conflicts?.contains(path) == true
        }
    }

    fun saveText() {
        val dir = repo?.let { File(it.path) } ?: return
        scope.launch(Dispatchers.IO) {
            container.gitCore.writeFile(dir, path, textContent)
                .onSuccess {
                    if (isConflict) {
                        container.gitCore.markResolved(dir, path)
                        isConflict = false
                        withContext(Dispatchers.Main) { snackbar.showSnackbar(S.savedResolved) }
                    } else {
                        withContext(Dispatchers.Main) { snackbar.showSnackbar(S.saved) }
                    }
                    textOriginal = textContent
                    refreshTick++
                }
                .onFailure {
                    withContext(Dispatchers.Main) { snackbar.showSnackbar(it.message ?: S.saveFailed) }
                }
        }
    }

    fun resolve(action: ConflictAction) {
        val dir = repo?.let { File(it.path) } ?: return
        scope.launch(Dispatchers.IO) {
            val result = container.gitCore.resolveConflict(dir, path, action)
            if (result.isSuccess) {
                // 采用我方/对方后工作区文件已更新，重载文本内容
                val content = container.gitCore.readFile(dir, path).getOrDefault("")
                textContent = content
                textOriginal = content
                isConflict = false
                refreshTick++
                withContext(Dispatchers.Main) {
                    snackbar.showSnackbar(if (action == ConflictAction.OURS) S.tookOurs else S.tookTheirs)
                }
            } else {
                withContext(Dispatchers.Main) { snackbar.showSnackbar(S.opFailed) }
            }
        }
    }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = path.substringAfterLast('/'),
                subtitle = path,
                navigationIcon = { CoveBackButton { nav.popBackStack() } },
                actions = {
                    if (kind == FileKind.TEXT && textLoaded && textContent != textOriginal) {
                        IconButton(onClick = { saveText() }) {
                            Icon(Icons.Filled.Save, contentDescription = S.save)
                        }
                    }
                    // 右上角关闭（返回文件列表）
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.Close, contentDescription = S.cancel)
                    }
                }
            )
        },
        bottomBar = {
            if (kind != null && isGitRepo) {
                ViewerBottomBar(tab = tab, onSelect = { tab = it })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        backgroundColor = c.background
    ) { padding ->
        when (kind) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.primary, modifier = Modifier.size(30.dp))
            }
            else -> Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    1 -> DiffTab(
                        container = container,
                        dir = File(repo!!.path),
                        path = path,
                        name = path.substringAfterLast('/'),
                        kind = kind!!,
                        file = file,
                        selectedCommit = selectedCommit,
                        onBackToWorking = { selectedCommit = null },
                        refreshTick = refreshTick,
                        isConflict = isConflict,
                        onResolve = { resolve(it) },
                        onManualEdit = { nav.navigate(com.gitcove.app.ui.nav.Routes.editor(repoId, path)) }
                    )
                    2 -> FileHistoryTab(
                        container = container,
                        dir = File(repo!!.path),
                        path = path,
                        onPick = { selectedCommit = it; tab = 1 }
                    )
                    else -> ContentTab(
                        kind = kind!!,
                        file = file,
                        textLoaded = textLoaded,
                        textContent = textContent,
                        onTextChange = { textContent = it },
                        isConflict = isConflict,
                        reloadKey = refreshTick
                    )
                }
            }
        }
    }
}

/** 路径安全化：限制在仓库根目录内（防 ../ 越界） */
private fun safeRepoFile(dir: File?, path: String): File? {
    if (dir == null) return null
    return try {
        val f = File(dir, path)
        if (f.canonicalPath.startsWith(dir.canonicalPath + File.separator)) f else null
    } catch (_: Exception) {
        null
    }
}

// ─────────────────────────── 内容标签 ───────────────────────────

@Composable
private fun ContentTab(
    kind: FileKind,
    file: File?,
    textLoaded: Boolean,
    textContent: String,
    onTextChange: (String) -> Unit,
    isConflict: Boolean,
    reloadKey: Int = 0
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    when {
        file == null || !file.exists() ->
            EmptyView(S.fileNotExist, icon = Icons.Filled.InsertDriveFile)
        kind == FileKind.IMAGE -> ImagePane(file, reloadKey = reloadKey)
        kind == FileKind.VIDEO -> VideoPane(file)
        kind == FileKind.AUDIO -> AudioPane(file)
        !textLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = c.primary, modifier = Modifier.size(30.dp))
        }
        file.length() > 2 * 1024 * 1024 ->
            EmptyView(S.fileTooBigEdit, icon = Icons.Filled.InsertDriveFile)
        else -> {
            // MD / HTML：源码（可编辑）与渲染预览切换
            val ext = com.gitcove.app.util.FileTypes.ext(file.name)
            val isMd = ext in com.gitcove.app.util.FileTypes.MD_EXTS
            val isHtml = ext in com.gitcove.app.util.FileTypes.HTML_EXTS
            if (!isMd && !isHtml) {
                CodeEditorPane(
                    content = textContent,
                    onContentChange = onTextChange,
                    readOnly = kind == FileKind.BINARY,
                    hint = when {
                        kind == FileKind.BINARY -> S.binaryFileHint
                        isConflict -> S.conflictEditorHint
                        else -> null
                    }
                )
            } else {
                var previewMode by remember(file.path) { mutableStateOf(false) }
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.gitcove.app.ui.components.CoveSegmentedRow(
                            options = listOf(S.sourceCode, S.preview),
                            selectedIndex = if (previewMode) 1 else 0,
                            onSelect = { previewMode = it == 1 }
                        )
                    }
                    CoveDivider()
                    if (previewMode) {
                        if (isMd) {
                            com.gitcove.app.ui.file.MarkdownPreview(textContent, Modifier.fillMaxSize())
                        } else {
                            com.gitcove.app.ui.file.HtmlPreview(file, textContent, Modifier.fillMaxSize())
                        }
                    } else {
                        CodeEditorPane(
                            content = textContent,
                            onContentChange = onTextChange,
                            hint = if (isConflict) S.conflictEditorHint else null
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────── 差异标签 ───────────────────────────

/** 差异页一次加载的全部内容 */
private data class FileDiffUi(
    val changed: Boolean,
    val changeType: String?,           // ADD / MODIFY / DELETE / RENAME / UNTRACKED
    val lines: List<DiffLine>,         // 文本差异行
    val oldImage: File?,               // 图片旧版本（临时文件）
    val newImage: File?,               // 图片新版本（临时文件）
    val oldStat: Pair<Long, String>?,  // 非文本：(大小, SHA-1)
    val newStat: Pair<Long, String>?,
    val conflictLines: List<DiffLine>  // 冲突时展示的工作区内容（含标记）
)

@Composable
private fun DiffTab(
    container: AppContainer,
    dir: File,
    path: String,
    name: String,
    kind: FileKind,
    file: File?,
    selectedCommit: Commit?,
    onBackToWorking: () -> Unit,
    refreshTick: Int,
    isConflict: Boolean,
    onResolve: (ConflictAction) -> Unit,
    onManualEdit: () -> Unit
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val context = LocalContext.current
    var ui by remember { mutableStateOf<FileDiffUi?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedCommit?.hash, refreshTick) {
        loading = true
        withContext(Dispatchers.IO) {
            ui = loadFileDiff(container, context, dir, path, name, kind, file, selectedCommit, isConflict)
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        // 对比范围条：默认工作区 vs 上次提交；从历史进入某提交后可返回
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (selectedCommit == null) S.viewerDiffWorking
                else S.viewerDiffCommit(selectedCommit.shortHash),
                style = MaterialTheme.typography.caption,
                color = c.primary,
                modifier = Modifier.weight(1f)
            )
            if (selectedCommit != null) {
                TextButton(onClick = onBackToWorking) {
                    Text(S.backToWorkingDiff, style = MaterialTheme.typography.caption)
                }
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.primary, modifier = Modifier.size(30.dp))
            }
            ui == null -> EmptyView(S.loadingDiff)
            isConflict && selectedCommit == null -> Column(Modifier.fillMaxSize()) {
                ConflictBar(
                    onOurs = { onResolve(ConflictAction.OURS) },
                    onTheirs = { onResolve(ConflictAction.THEIRS) },
                    onManual = onManualEdit
                )
                CoveDivider()
                DiffLinesView(ui!!.conflictLines, Modifier.fillMaxSize())
            }
            else -> when (kind) {
                FileKind.TEXT -> when {
                    ui!!.lines.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (ui!!.changed) S.noDiff else S.changedNo,
                            style = MaterialTheme.typography.body2,
                            color = c.onSurfaceVariant
                        )
                    }
                    else -> DiffLinesView(ui!!.lines, Modifier.fillMaxSize())
                }
                FileKind.IMAGE -> when {
                    ui!!.oldImage != null && ui!!.newImage != null ->
                        ImageDiffView(ui!!.oldImage, ui!!.newImage, null)
                    ui!!.oldImage == null && ui!!.newImage != null ->
                        ImageDiffView(null, ui!!.newImage, S.imgNewFile)
                    ui!!.newImage == null && ui!!.oldImage != null ->
                        ImageDiffView(ui!!.oldImage, null, S.imgDeletedFile)
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (ui!!.changed) S.imgOpenFailed else S.changedNo,
                            style = MaterialTheme.typography.body2,
                            color = c.onSurfaceVariant
                        )
                    }
                }
                else -> ChangedPanel(
                    changed = ui!!.changed,
                    changeType = ui!!.changeType,
                    kind = kind,
                    oldStat = ui!!.oldStat,
                    newStat = ui!!.newStat
                )
            }
        }
    }
}

/** 非文本非图片的差异结果：居中大字"有改变/无改变" + 类型与新旧大小 */
@Composable
private fun ChangedPanel(
    changed: Boolean,
    changeType: String?,
    kind: FileKind,
    oldStat: Pair<Long, String>?,
    newStat: Pair<Long, String>?
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val icon: ImageVector = when (kind) {
            FileKind.VIDEO -> Icons.Filled.Movie
            FileKind.AUDIO -> Icons.Filled.MusicNote
            else -> Icons.Filled.InsertDriveFile
        }
        Box(
            Modifier.size(56.dp).background(c.surfaceVariant, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (changed) S.changedYes else S.changedNo,
            style = MaterialTheme.typography.h6,
            color = if (changed) c.tertiary else c.onSurfaceVariant
        )
        changeType?.let {
            Spacer(Modifier.height(4.dp))
            Text(changeTypeLabel(it, S) ?: "", style = MaterialTheme.typography.caption, color = c.primary)
        }
        if (changed && (oldStat != null || newStat != null)) {
            Spacer(Modifier.height(10.dp))
            Text(
                "${S.sideOldFull}：${oldStat?.let { fileSizeText(it.first) } ?: "—"}   ·   " +
                    "${S.sideNewFull}：${newStat?.let { fileSizeText(it.first) } ?: "—"}",
                style = MaterialTheme.typography.caption,
                color = c.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConflictBar(onOurs: () -> Unit, onTheirs: () -> Unit, onManual: () -> Unit) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Text(
            S.conflictBarTitle,
            style = MaterialTheme.typography.caption,
            color = c.error
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoveButton(text = S.takeOurs, onClick = onOurs)
            CoveButton(text = S.takeTheirs, onClick = onTheirs)
            CoveOutlinedButton(text = S.manualEdit, onClick = onManual)
        }
    }
}

/** 汇总加载：提交模式（与父提交比）或工作区模式（与 HEAD/暂存区比） */
private suspend fun loadFileDiff(
    container: AppContainer,
    context: android.content.Context,
    dir: File,
    path: String,
    name: String,
    kind: FileKind,
    file: File?,
    selectedCommit: Commit?,
    isConflict: Boolean
): FileDiffUi = withContext(Dispatchers.IO) {
    if (selectedCommit != null) {
        val hash = selectedCommit.hash
        val parent = selectedCommit.parents.firstOrNull()
        val diff = container.gitCore.commitFileDiff(dir, hash, path)
            .getOrDefault(null to emptyList())
        val changeType = diff.first
        when (kind) {
            FileKind.TEXT -> FileDiffUi(
                changed = changeType != null, changeType = changeType, lines = diff.second,
                oldImage = null, newImage = null, oldStat = null, newStat = null, conflictLines = emptyList()
            )
            FileKind.IMAGE -> {
                val oldBytes = parent?.let { container.gitCore.readBlobAt(dir, it, path).getOrNull() }
                val newBytes = container.gitCore.readBlobAt(dir, hash, path).getOrNull()
                FileDiffUi(
                    changed = changeType != null, changeType = changeType, lines = emptyList(),
                    oldImage = blobToTempFile(context, oldBytes, (parent ?: "root").take(10), name),
                    newImage = blobToTempFile(context, newBytes, hash.take(10), name),
                    oldStat = null, newStat = null, conflictLines = emptyList()
                )
            }
            else -> {
                val oldStat = parent?.let { container.gitCore.blobStatAt(dir, it, path).getOrNull() }
                val newStat = container.gitCore.blobStatAt(dir, hash, path).getOrNull()
                FileDiffUi(
                    changed = changeType != null || oldStat != newStat, changeType = changeType,
                    lines = emptyList(), oldImage = null, newImage = null,
                    oldStat = oldStat, newStat = newStat, conflictLines = emptyList()
                )
            }
        }
    } else {
        val st = container.gitCore.status(dir).getOrNull()
        val fs = (st?.staged.orEmpty() + st?.unstaged.orEmpty())
            .firstOrNull { it.path == path }?.status
        val changeType = when (fs) {
            Status.ADDED, Status.UNTRACKED -> "ADD"
            Status.MODIFIED -> "MODIFY"
            Status.DELETED -> "DELETE"
            Status.CONFLICT -> "MODIFY"
            null -> null
        }
        when (kind) {
            FileKind.TEXT -> {
                var lines = container.gitCore.diffText(dir, path, cached = false).getOrDefault(emptyList())
                if (lines.isEmpty() && fs != null) {
                    lines = container.gitCore.diffText(dir, path, cached = true).getOrDefault(emptyList())
                }
                val conflictLines = if (isConflict) {
                    runCatching { File(dir, path).readLines() }.getOrDefault(emptyList())
                        .map { DiffLine(LineType.CONTEXT, it) }
                } else emptyList()
                FileDiffUi(
                    changed = lines.isNotEmpty() || fs != null, changeType = changeType, lines = lines,
                    oldImage = null, newImage = null, oldStat = null, newStat = null,
                    conflictLines = conflictLines
                )
            }
            FileKind.IMAGE -> {
                val oldBytes = container.gitCore.readBlobAt(dir, "HEAD", path).getOrNull()
                val newBytes = if (file != null && file.isFile) {
                    runCatching { file.readBytes() }.getOrNull()
                } else null
                val oldSha = oldBytes?.let { FileTypes.sha1Of(ByteArrayInputStream(it)) }
                val newSha = newBytes?.let { FileTypes.sha1Of(ByteArrayInputStream(it)) }
                val changed = fs != null || oldSha != newSha
                FileDiffUi(
                    changed = changed, changeType = if (changed) changeType else null, lines = emptyList(),
                    oldImage = blobToTempFile(context, oldBytes, "head", name),
                    newImage = blobToTempFile(context, newBytes, "work", name),
                    oldStat = null, newStat = null, conflictLines = emptyList()
                )
            }
            else -> {
                val oldStat = container.gitCore.blobStatAt(dir, "HEAD", path).getOrNull()
                val newStat = FileTypes.fileStat(file)
                val changed = fs != null || oldStat != newStat
                FileDiffUi(
                    changed = changed, changeType = if (changed) changeType else null,
                    lines = emptyList(), oldImage = null, newImage = null,
                    oldStat = oldStat, newStat = newStat, conflictLines = emptyList()
                )
            }
        }
    }
}

/** blob 字节落临时文件（cacheDir），供图片解码；bytes 为 null 返回 null */
private fun blobToTempFile(context: android.content.Context, bytes: ByteArray?, rev: String, name: String): File? {
    if (bytes == null) return null
    return runCatching {
        val ext = name.substringAfterLast('.', "img")
        val f = File(context.cacheDir, "gitcove_imgdiff_${rev}_${name.hashCode().toUInt().toString(16)}.$ext")
        f.writeBytes(bytes)
        f
    }.getOrNull()
}

private fun changeTypeLabel(t: String?, S: Strings): String? = when (t) {
    "ADD", "UNTRACKED" -> S.statusAddedText
    "MODIFY" -> S.statusModifiedText
    "DELETE" -> S.statusDeletedText
    "RENAME" -> S.rename
    else -> null
}

// ─────────────────────────── 历史标签 ───────────────────────────

@Composable
private fun FileHistoryTab(
    container: AppContainer,
    dir: File,
    path: String,
    onPick: (Commit) -> Unit
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    var commits by remember { mutableStateOf<List<Commit>?>(null) }
    var account by remember { mutableStateOf(container.auth.githubAccount) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            commits = container.gitCore.log(dir, path = path).getOrDefault(emptyList())
            runCatching { container.githubApi.refreshAccountCache() }
            account = container.auth.githubAccount
        }
    }

    Column(Modifier.fillMaxSize()) {
        CoveSectionTitle(S.viewCommitDiffHint)
        when {
            commits == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.primary, modifier = Modifier.size(30.dp))
            }
            commits!!.isEmpty() -> Box(Modifier.fillMaxSize()) {
                EmptyView(S.noFileHistory, icon = Icons.Filled.History, modifier = Modifier.fillMaxSize())
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(commits!!, key = { it.hash }) { commit ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(commit) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(
                                email = commit.email,
                                name = commit.author,
                                size = 26.dp,
                                account = account
                            )
                            if (commit != commits!!.last()) {
                                Box(
                                    Modifier
                                        .width(2.dp)
                                        .height(30.dp)
                                        .background(c.outline)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                commit.message,
                                style = MaterialTheme.typography.subtitle2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row {
                                Text(
                                    commit.shortHash,
                                    style = MaterialTheme.typography.overline,
                                    fontFamily = MonoFont,
                                    color = c.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${commit.author} · ${relativeTime(commit.date, S)}",
                                    style = MaterialTheme.typography.overline,
                                    color = c.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────── 底部三标签栏 ───────────────────────────

@Composable
private fun ViewerBottomBar(tab: Int, onSelect: (Int) -> Unit) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    Surface(elevation = 8.dp, color = c.surface, contentColor = c.onSurface) {
        Row(Modifier.fillMaxWidth()) {
            TabBtn(Icons.Filled.Article, S.viewerTabContent, tab == 0, Modifier.weight(1f)) { onSelect(0) }
            TabBtn(Icons.Filled.Difference, S.viewerTabDiff, tab == 1, Modifier.weight(1f)) { onSelect(1) }
            TabBtn(Icons.Filled.History, S.viewerTabHistory, tab == 2, Modifier.weight(1f)) { onSelect(2) }
        }
    }
}

@Composable
private fun TabBtn(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = CoveTheme.colors
    val color = if (selected) c.primary else c.onSurfaceVariant
    Column(
        modifier.clickable(onClick = onClick).padding(top = 6.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .width(28.dp)
                .height(2.dp)
                .background(if (selected) c.primary else Color.Transparent)
        )
        Spacer(Modifier.height(4.dp))
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.caption, color = color)
    }
}
