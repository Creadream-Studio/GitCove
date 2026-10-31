package com.gitcove.app.ui.resolve

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gitcove.app.data.git.Diff3
import com.gitcove.app.data.git.GitCore
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveOutlinedButton
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * 冲突解决器（Working Copy 4.6 Resolve 工具）。
 *
 * - 仓库级（path 为空）：顶部文件 chips 切换，逐一解决；
 * - 文本文件：diff3 分块——双方一致的稳定区居中折叠展示，
 *   冲突块左右两半（我方=琥珀 / 对方=蓝），点选或左右滑动选择，
 *   支持整侧采用（HEAD / 对方）、两者都留、结果预览；
 * - 二进制（图片等）：并排两版本点选，粗边框标记；
 * - Resolve 校验全部块已决定 → 写回并暂存标记，下次提交完成合并。
 */
private enum class Side { OURS, THEIRS }

private val oursColor = Color(0xFFE8A33D)
private val theirsColor = Color(0xFF4C8DF6)
private val imageExts = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

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
    var binaryPick by remember(selected) { mutableStateOf<Side?>(null) }
    var preview by remember(selected) { mutableStateOf(false) }
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
        binaryPick = null
        preview = false
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

    fun resolve() {
        val d = dir ?: return
        val s = sides ?: return
        scope.launch {
            val result = if (s.binary) {
                val bytes = when (binaryPick) {
                    Side.OURS -> s.ours
                    Side.THEIRS -> s.theirs
                    null -> null
                }
                if (bytes == null) {
                    toast(S.binaryPickHint); return@launch
                }
                withContext(Dispatchers.IO) { container.gitCore.writeResolved(d, selected, bytes) }
            } else {
                val cs = chunks ?: return@launch
                val firstPending = cs.indices.firstOrNull { cs[it].kind == Diff3.Kind.CONFLICT && decisions[it] == null }
                if (firstPending != null) {
                    toast(S.pendingChunks(pendingCount))
                    listState.animateScrollToItem(firstPending)
                    return@launch
                }
                val content = Diff3.assemble(cs, decisions)
                withContext(Dispatchers.IO) { container.gitCore.writeResolvedText(d, selected, content) }
            }
            if (result.isSuccess) {
                toast(S.conflictResolvedMsg)
                doneFiles[selected] = true
                val next = files.firstOrNull { it != selected && doneFiles[it] != true }
                if (next != null) selected = next else nav.popBackStack()
            } else {
                toast(result.exceptionOrNull()?.message ?: S.opFailed)
            }
        }
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

                sides!!.binary -> BinaryPane(
                    dir = dir!!,
                    path = selected,
                    sides = sides!!,
                    pick = binaryPick,
                    onPick = { binaryPick = it }
                )

                else -> TextResolverPane(
                    dir = dir!!,
                    path = selected,
                    sides = sides!!,
                    chunks = chunks.orEmpty(),
                    decisions = decisions,
                    pendingCount = pendingCount,
                    preview = preview,
                    onTogglePreview = { preview = !preview },
                    listState = listState,
                    nav = nav,
                    repoId = repoId,
                    onEditAll = { side ->
                        chunks?.forEachIndexed { i, c ->
                            if (c.kind != Diff3.Kind.STABLE) {
                                decisions[i] = if (side == Side.OURS) Diff3.Decision.OURS else Diff3.Decision.THEIRS
                            }
                        }
                    },
                    toast = ::toast
                )
            }

            // 底部操作区
            if (sides != null) {
                CoveDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!sides!!.binary) {
                        Text(
                            if (pendingCount > 0) S.pendingChunks(pendingCount) else "",
                            style = MaterialTheme.typography.caption,
                            color = CoveTheme.colors.error,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            S.binaryPickHint,
                            style = MaterialTheme.typography.caption,
                            color = CoveTheme.colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    CoveButton(text = S.resolveAllBtn, onClick = ::resolve)
                }
            }
        }
    }
}

/** 二进制冲突：并排两版本点选（图片直接预览） */
@Composable
private fun BinaryPane(
    dir: File,
    path: String,
    sides: GitCore.ConflictSides,
    pick: Side?,
    onPick: (Side) -> Unit
) {
    val S = LocalStrings.current
    val ext = path.substringAfterLast('.', "").lowercase()
    val isImage = ext in imageExts

    Row(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BinarySide(
            label = S.sideOurs,
            detail = sides.oursLabel,
            bytes = sides.ours,
            color = oursColor,
            selected = pick == Side.OURS,
            showImage = isImage,
            onClick = { onPick(Side.OURS) },
            modifier = Modifier.weight(1f)
        )
        BinarySide(
            label = S.sideTheirs,
            detail = sides.theirsLabel,
            bytes = sides.theirs,
            color = theirsColor,
            selected = pick == Side.THEIRS,
            showImage = isImage,
            onClick = { onPick(Side.THEIRS) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BinarySide(
    label: String,
    detail: String,
    bytes: ByteArray?,
    color: Color,
    selected: Boolean,
    showImage: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.button, color = color)
        Text(
            detail,
            style = MaterialTheme.typography.overline,
            color = CoveTheme.colors.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) color else CoveTheme.colors.outline),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                if (showImage && bytes != null) {
                    AsyncImage(
                        model = bytes,
                        contentDescription = label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(
                            Icons.Outlined.Image,
                            contentDescription = null,
                            tint = CoveTheme.colors.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            bytes?.let { "${it.size / 1024} KB" } ?: "—",
                            style = MaterialTheme.typography.caption,
                            color = CoveTheme.colors.onSurfaceVariant
                        )
                    }
                }
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    )
                }
            }
        }
    }
}

/** 文本冲突：分块视图 / 结果预览 */
@Composable
private fun TextResolverPane(
    dir: File,
    path: String,
    sides: GitCore.ConflictSides,
    chunks: List<Diff3.Chunk>,
    decisions: MutableMap<Int, Diff3.Decision>,
    pendingCount: Int,
    preview: Boolean,
    onTogglePreview: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    nav: androidx.navigation.NavHostController,
    repoId: Long,
    onEditAll: (Side) -> Unit,
    toast: (String) -> Unit
) {
    val S = LocalStrings.current

    Column(Modifier.fillMaxSize()) {
        // 操作行：整侧采用 + 视图切换
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onEditAll(Side.OURS) }) {
                Text("${S.takeAllOurs}（${sides.oursLabel}）", maxLines = 1)
            }
            TextButton(onClick = { onEditAll(Side.THEIRS) }) {
                Text("${S.takeAllTheirs}（${sides.theirsLabel}）", maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            CoveOutlinedButton(
                text = if (preview) S.mergedView else S.resultPreview,
                onClick = onTogglePreview
            )
            Spacer(Modifier.width(6.dp))
            CoveOutlinedButton(
                text = S.manualEdit,
                onClick = { nav.navigate(Routes.editor(repoId, path)) }
            )
        }
        CoveDivider()

        if (preview) {
            // 结果预览：实时拼装
            val text = Diff3.assemble(chunks, decisions)
            LazyColumn(Modifier.fillMaxSize()) {
                items(text.lines().size) { i ->
                    Text(
                        text.lines().getOrElse(i) { "" },
                        style = MaterialTheme.typography.caption,
                        fontFamily = MonoFont,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 1.dp)
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(chunks.size) { i ->
                    val chunk = chunks[i]
                    when (chunk.kind) {
                        Diff3.Kind.STABLE -> StableChunkRow(chunk)
                        else -> ConflictChunkCard(
                            index = i,
                            chunk = chunk,
                            oursLabel = sides.oursLabel,
                            theirsLabel = sides.theirsLabel,
                            decision = decisions[i],
                            onDecide = { d -> decisions[i] = d }
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

/** 稳定上下文：默认折叠 */
@Composable
private fun StableChunkRow(chunk: Diff3.Chunk) {
    var open by remember { mutableStateOf(false) }
    val lines = chunk.ours
    Column(Modifier.fillMaxWidth().background(CoveTheme.colors.surfaceVariant.copy(alpha = 0.4f))) {
        Text(
            if (open) "▾ ${lines.size} 行" else "⋯ ${lines.size} 行相同内容（点开查看）",
            style = MaterialTheme.typography.overline,
            color = CoveTheme.colors.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = 12.dp, vertical = 3.dp)
        )
        if (open) {
            lines.take(40).forEach { l ->
                Text(
                    l,
                    style = MaterialTheme.typography.caption,
                    fontFamily = MonoFont,
                    color = CoveTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            if (lines.size > 40) {
                Text(
                    "…",
                    style = MaterialTheme.typography.caption,
                    color = CoveTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

/** 冲突块：左右两半，点选 / 滑动 / 底部 chips */
@Composable
private fun ConflictChunkCard(
    index: Int,
    chunk: Diff3.Chunk,
    oursLabel: String,
    theirsLabel: String,
    decision: Diff3.Decision?,
    onDecide: (Diff3.Decision) -> Unit
) {
    val S = LocalStrings.current
    val pending = decision == null
    var drag by remember { mutableStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val threshold = with(density) { 56.dp.toPx() }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .offset { IntOffset(drag.roundToInt(), 0) }
            .border(
                BorderStroke(
                    1.dp,
                    if (pending) CoveTheme.colors.error.copy(alpha = 0.6f) else CoveTheme.colors.outline
                ),
                RoundedCornerShape(10.dp)
            )
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            drag < -threshold -> onDecide(Diff3.Decision.THEIRS)
                            drag > threshold -> onDecide(Diff3.Decision.OURS)
                        }
                        drag = 0f
                    },
                    onHorizontalDrag = { change, amt ->
                        drag += amt
                        change.consume()
                    }
                )
            }
    ) {
        // 块头：状态 + 提示
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    if (pending) CoveTheme.colors.error.copy(alpha = 0.08f)
                    else CoveTheme.colors.primary.copy(alpha = 0.08f)
                )
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "#${index + 1} · " + when (decision) {
                    Diff3.Decision.OURS -> "✓ ${S.chunkOurs}"
                    Diff3.Decision.THEIRS -> "✓ ${S.chunkTheirs}"
                    Diff3.Decision.BOTH -> "✓ ${S.chunkBoth}"
                    Diff3.Decision.NONE -> "✓ ${S.chunkNone}"
                    null -> S.pendingLabel
                },
                style = MaterialTheme.typography.overline,
                color = if (pending) CoveTheme.colors.error else CoveTheme.colors.primary
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (pending) S.swipeHint else "",
                style = MaterialTheme.typography.overline,
                color = CoveTheme.colors.onSurfaceVariant
            )
        }
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SideHalf(
                label = oursLabel,
                lines = chunk.ours,
                color = oursColor,
                selected = decision == Diff3.Decision.OURS,
                onClick = { onDecide(Diff3.Decision.OURS) },
                modifier = Modifier.weight(1f)
            )
            SideHalf(
                label = theirsLabel,
                lines = chunk.theirs,
                color = theirsColor,
                selected = decision == Diff3.Decision.THEIRS,
                onClick = { onDecide(Diff3.Decision.THEIRS) },
                modifier = Modifier.weight(1f)
            )
        }
        // 快捷 chips
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            listOf(
                Diff3.Decision.OURS to S.chunkOurs,
                Diff3.Decision.BOTH to S.chunkBoth,
                Diff3.Decision.THEIRS to S.chunkTheirs,
                Diff3.Decision.NONE to S.chunkNone
            ).forEach { (d, label) ->
                val on = decision == d
                Text(
                    label,
                    style = MaterialTheme.typography.overline,
                    color = if (on) CoveTheme.colors.surface else CoveTheme.colors.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .background(
                            if (on) CoveTheme.colors.primary else CoveTheme.colors.surfaceVariant,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onDecide(d) }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/** 单侧文本半区 */
@Composable
private fun SideHalf(
    label: String,
    lines: List<String>,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(8.dp))
            .border(
                BorderStroke(
                    if (selected) 2.dp else 0.5.dp,
                    if (selected) color else CoveTheme.colors.outline.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.overline,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (lines.isEmpty()) {
            Text(
                "∅",
                style = MaterialTheme.typography.caption,
                fontFamily = MonoFont,
                color = CoveTheme.colors.onSurfaceVariant
            )
        } else {
            Column(
                Modifier
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                lines.take(200).forEach { l ->
                    Text(
                        l,
                        style = MaterialTheme.typography.caption,
                        fontFamily = MonoFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (lines.size > 200) {
                    Text("…", style = MaterialTheme.typography.caption)
                }
            }
        }
    }
}
