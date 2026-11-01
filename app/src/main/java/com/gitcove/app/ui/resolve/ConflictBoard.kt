package com.gitcove.app.ui.resolve

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gitcove.app.data.git.Diff3
import com.gitcove.app.data.git.GitCore
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * VS Code 式冲突解决板（4.6 重写）。
 *
 * 把 VS Code 冲突编辑器好用的点移植到移动端：
 * 1. 冲突块上下堆叠、全宽展示——可读性优先于并排（手机并排各 45% 宽根本读不了代码）
 * 2. 每块一行内联选项（我方/对方/两者/都不要），一次点击即决定、点其他即重选——无手势
 * 3. 词级差异高亮：块内真正变化的词加深一档背景，两侧改动焦点一眼可见
 * 4. 颜色语义固定：当前(我方)=绿、传入(对方)=蓝（与 VS Code 一致）
 * 5. 决定后卡片直接展示结果内容，结果即时可见——不再有独立「预览」模式
 * 6. 底部常驻「剩余 N 处」+ 上一处/下一处跳转
 *
 * 冲突解决器整页与差异页冲突模式共用本组件。
 */

/** 当前（我方）绿 / 传入（对方）蓝 —— 与 VS Code 语义一致 */
val ConflictOursColor = Color(0xFF3FB950)
val ConflictTheirsColor = Color(0xFF4C8DF6)

@Composable
fun ConflictBoard(
    gitCore: GitCore,
    dir: File,
    path: String,
    sides: GitCore.ConflictSides,
    chunks: List<Diff3.Chunk>,
    decisions: MutableMap<Int, Diff3.Decision>,
    listState: LazyListState,
    pendingCount: Int,
    onManualEdit: () -> Unit,
    onResolved: () -> Unit,
    toast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val S = LocalStrings.current
    val scope = rememberCoroutineScope()

    // 冲突编号（第 k 处 / 共 N 处）
    val conflictIdx = remember(chunks) {
        chunks.indices.filter { chunks[it].kind == Diff3.Kind.CONFLICT }
    }
    // 待决列表（供上/下一处跳转；每次决定后重算）
    val pendingList = chunks.indices.filter { chunks[it].kind == Diff3.Kind.CONFLICT && decisions[it] == null }

    fun jumpTo(idx: Int) {
        scope.launch { listState.animateScrollToItem(idx) }
    }

    fun jumpNext() {
        val base = listState.firstVisibleItemIndex
        val target = pendingList.firstOrNull { it > base } ?: pendingList.firstOrNull() ?: return
        jumpTo(target)
    }

    fun jumpPrev() {
        val base = listState.firstVisibleItemIndex
        val target = pendingList.lastOrNull { it < base } ?: pendingList.lastOrNull() ?: return
        jumpTo(target)
    }

    /** 整侧采用：只作用于真正的冲突块（单侧改动块本就自动采用，稳定区不动） */
    fun takeAll(ours: Boolean) {
        val d = if (ours) Diff3.Decision.OURS else Diff3.Decision.THEIRS
        chunks.forEachIndexed { i, c -> if (c.kind == Diff3.Kind.CONFLICT) decisions[i] = d }
    }

    fun doResolve() {
        val firstPending = chunks.indices.firstOrNull {
            chunks[it].kind == Diff3.Kind.CONFLICT && decisions[it] == null
        }
        if (firstPending != null) {
            toast(S.pendingChunks(pendingCount))
            jumpTo(firstPending)
            return
        }
        val content = Diff3.assemble(chunks, decisions)
        scope.launch(Dispatchers.IO) {
            val result = gitCore.writeResolvedText(dir, path, content)
            if (result.isSuccess) {
                toast(S.conflictResolvedMsg)
                withContext(Dispatchers.Main) { onResolved() }
            } else {
                toast(result.exceptionOrNull()?.message ?: S.opFailed)
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        // ── 顶部操作行：整侧采用 + 手动编辑 ──
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { takeAll(true) }) {
                Text(S.takeAllOurs, color = ConflictOursColor, maxLines = 1)
            }
            TextButton(onClick = { takeAll(false) }) {
                Text(S.takeAllTheirs, color = ConflictTheirsColor, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onManualEdit) { Text(S.manualEdit, maxLines = 1) }
        }
        CoveDivider()

        // ── 分块列表：item 序号 == chunk 序号（上一处/下一处按此对齐滚动）──
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
            items(chunks.size) { i ->
                val chunk = chunks[i]
                when (chunk.kind) {
                    Diff3.Kind.STABLE -> StableRow(chunk)
                    Diff3.Kind.OURS -> AutoRow(ours = true, chunk = chunk)
                    Diff3.Kind.THEIRS -> AutoRow(ours = false, chunk = chunk)
                    Diff3.Kind.CONFLICT -> ConflictCard(
                        no = conflictIdx.indexOf(i) + 1,
                        total = conflictIdx.size,
                        chunk = chunk,
                        oursLabel = sides.oursLabel,
                        theirsLabel = sides.theirsLabel,
                        decision = decisions[i],
                        onDecide = { d -> decisions[i] = d }
                    )
                }
            }
        }

        CoveDivider()

        // ── 底部：剩余数 + 导航 + 解决并标记 ──
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    S.pendingLeft(pendingCount),
                    style = MaterialTheme.typography.caption,
                    color = if (pendingCount > 0) CoveTheme.colors.error else CoveTheme.colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = ::jumpPrev, enabled = pendingList.isNotEmpty()) {
                    Text(S.prevConflict)
                }
                TextButton(onClick = ::jumpNext, enabled = pendingList.isNotEmpty()) {
                    Text(S.nextConflict)
                }
            }
            CoveButton(
                text = S.resolveAllBtn,
                onClick = ::doResolve,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─────────────────────── 文本块渲染 ───────────────────────

/** 稳定区：默认折叠的一行；点开看内容（>80 行可再全部展开） */
@Composable
private fun StableRow(chunk: Diff3.Chunk) {
    val S = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    val lines = chunk.ours
    val shown = if (full) lines else lines.take(80)
    Column(Modifier.fillMaxWidth().background(CoveTheme.colors.surfaceVariant.copy(alpha = 0.4f))) {
        Text(
            S.stableLines(lines.size),
            style = MaterialTheme.typography.overline,
            color = CoveTheme.colors.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = 12.dp, vertical = 3.dp)
        )
        if (open) {
            shown.forEach { l ->
                Text(
                    l.ifBlank { " " },
                    style = MaterialTheme.typography.caption,
                    fontFamily = MonoFont,
                    color = CoveTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            if (lines.size > 80 && !full) {
                Text(
                    S.expandFull,
                    style = MaterialTheme.typography.overline,
                    color = CoveTheme.colors.primary,
                    modifier = Modifier
                        .clickable { full = true }
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/** 单侧改动块：git 语义自动采用，仅展示（非交互），不再混进冲突里让用户决定 */
@Composable
private fun AutoRow(ours: Boolean, chunk: Diff3.Chunk) {
    val S = LocalStrings.current
    val color = if (ours) ConflictOursColor else ConflictTheirsColor
    val content = if (ours) chunk.ours else chunk.theirs
    Column(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            if (ours) S.autoOursChunk else S.autoTheirsChunk,
            style = MaterialTheme.typography.overline,
            color = color
        )
        content.take(80).forEach { l ->
            Text(
                l.ifBlank { " " },
                style = MaterialTheme.typography.caption,
                fontFamily = MonoFont,
                color = CoveTheme.colors.onSurfaceVariant
            )
        }
        if (content.size > 80) {
            Text("…", style = MaterialTheme.typography.caption, color = CoveTheme.colors.onSurfaceVariant)
        }
    }
}

/** 冲突卡：未决=两侧堆叠全宽展示；已决=直接显示结果内容 */
@Composable
private fun ConflictCard(
    no: Int,
    total: Int,
    chunk: Diff3.Chunk,
    oursLabel: String,
    theirsLabel: String,
    decision: Diff3.Decision?,
    onDecide: (Diff3.Decision) -> Unit
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val pending = decision == null

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .border(
                BorderStroke(1.dp, if (pending) c.error.copy(alpha = 0.45f) else c.outline),
                RoundedCornerShape(10.dp)
            )
    ) {
        // 块头：编号 + 状态
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (pending) c.error.copy(alpha = 0.07f) else c.primary.copy(alpha = 0.07f))
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                S.conflictOf(no, total),
                style = MaterialTheme.typography.overline,
                color = if (pending) c.error else c.primary
            )
            Spacer(Modifier.weight(1f))
            Text(
                when (decision) {
                    Diff3.Decision.OURS -> "✓ ${S.chunkOurs}"
                    Diff3.Decision.THEIRS -> "✓ ${S.chunkTheirs}"
                    Diff3.Decision.BOTH -> "✓ ${S.chunkBoth}"
                    Diff3.Decision.NONE -> "✓ ${S.chunkNone}"
                    null -> S.pendingLabel
                },
                style = MaterialTheme.typography.overline,
                color = if (pending) c.error else c.primary
            )
        }

        if (pending) {
            // 未决：两侧上下堆叠，全宽可读；词级高亮真正变化的词
            val hl = remember(chunk) { WordDiff.lineHighlights(chunk.ours, chunk.theirs) }
            SideBlock(
                label = S.boardCurrent,
                detail = oursLabel,
                color = ConflictOursColor,
                lines = chunk.ours,
                ranges = hl.ours
            )
            Spacer(Modifier.height(4.dp))
            SideBlock(
                label = S.boardIncoming,
                detail = theirsLabel,
                color = ConflictTheirsColor,
                lines = chunk.theirs,
                ranges = hl.theirs
            )
        } else {
            // 已决：直接展示结果（结果即时可见，无独立预览步骤）
            val result = when (decision) {
                Diff3.Decision.OURS -> chunk.ours
                Diff3.Decision.THEIRS -> chunk.theirs
                Diff3.Decision.BOTH -> chunk.ours + chunk.theirs
                Diff3.Decision.NONE -> emptyList()
                null -> emptyList()
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
                    .border(BorderStroke(1.dp, c.primary.copy(alpha = 0.6f)), RoundedCornerShape(8.dp))
                    .background(c.primary.copy(alpha = 0.05f))
                    .padding(8.dp)
            ) {
                Text(S.resultBlockLabel, style = MaterialTheme.typography.overline, color = c.primary)
                if (result.isEmpty()) {
                    Text(
                        S.removedEmptyBlock,
                        style = MaterialTheme.typography.caption,
                        color = c.onSurfaceVariant
                    )
                } else {
                    result.take(120).forEach { l ->
                        Text(
                            l.ifBlank { " " },
                            style = MaterialTheme.typography.caption,
                            fontFamily = MonoFont
                        )
                    }
                    if (result.size > 120) {
                        Text("…", style = MaterialTheme.typography.caption, color = c.onSurfaceVariant)
                    }
                }
            }
        }

        // 内联选项行：一次点击即决定；点其他即重选（天然支持反悔）
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            listOf(
                Diff3.Decision.OURS to S.chunkOurs,
                Diff3.Decision.THEIRS to S.chunkTheirs,
                Diff3.Decision.BOTH to S.chunkBoth,
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
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** 单侧块：彩色标签 + 内容（词级高亮由 ranges 提供） */
@Composable
private fun SideBlock(
    label: String,
    detail: String,
    color: Color,
    lines: List<String>,
    ranges: List<List<IntRange>>
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(
            "$label · $detail",
            style = MaterialTheme.typography.overline,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
            lines.take(200).forEachIndexed { i, l ->
                val rs = ranges.getOrNull(i).orEmpty()
                if (rs.isEmpty()) {
                    Text(
                        l.ifBlank { " " },
                        style = MaterialTheme.typography.caption,
                        fontFamily = MonoFont
                    )
                } else {
                    Text(
                        buildAnnotatedString {
                            append(l)
                            rs.forEach { r -> addStyle(SpanStyle(background = color.copy(alpha = 0.32f)), r.first, r.last + 1) }
                        },
                        style = MaterialTheme.typography.caption,
                        fontFamily = MonoFont
                    )
                }
            }
            if (lines.size > 200) {
                Text("…", style = MaterialTheme.typography.caption, color = CoveTheme.colors.onSurfaceVariant)
            }
        }
    }
}

// ─────────────────────── 二进制冲突 ───────────────────────

private val imageExts = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
private enum class BinaryPick { OURS, THEIRS }

/** 二进制冲突：并排两版本点选（图片预览），选完点「解决并标记」 */
@Composable
fun BinaryConflictPane(
    gitCore: GitCore,
    dir: File,
    path: String,
    sides: GitCore.ConflictSides,
    onResolved: () -> Unit,
    toast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val S = LocalStrings.current
    val scope = rememberCoroutineScope()
    var pick by remember(path) { mutableStateOf<BinaryPick?>(null) }
    val ext = path.substringAfterLast('.', "").lowercase()
    val isImage = ext in imageExts

    fun doResolve() {
        val bytes = when (pick) {
            BinaryPick.OURS -> sides.ours
            BinaryPick.THEIRS -> sides.theirs
            null -> { toast(S.binaryPickHint); return }
        } ?: run { toast(S.opFailed); return }
        scope.launch(Dispatchers.IO) {
            val result = gitCore.writeResolved(dir, path, bytes)
            if (result.isSuccess) {
                toast(S.conflictResolvedMsg)
                withContext(Dispatchers.Main) { onResolved() }
            } else {
                toast(result.exceptionOrNull()?.message ?: S.opFailed)
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BinarySide(
                label = S.boardCurrent,
                detail = sides.oursLabel,
                bytes = sides.ours,
                color = ConflictOursColor,
                selected = pick == BinaryPick.OURS,
                showImage = isImage,
                onClick = { pick = BinaryPick.OURS },
                modifier = Modifier.weight(1f)
            )
            BinarySide(
                label = S.boardIncoming,
                detail = sides.theirsLabel,
                bytes = sides.theirs,
                color = ConflictTheirsColor,
                selected = pick == BinaryPick.THEIRS,
                showImage = isImage,
                onClick = { pick = BinaryPick.THEIRS },
                modifier = Modifier.weight(1f)
            )
        }
        CoveDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                S.binaryPickHint,
                style = MaterialTheme.typography.caption,
                color = if (pick == null) CoveTheme.colors.error else CoveTheme.colors.onSurfaceVariant
            )
            CoveButton(
                text = S.resolveAllBtn,
                onClick = ::doResolve,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
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
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().heightIn(min = 160.dp),
                contentAlignment = Alignment.Center
            ) {
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
