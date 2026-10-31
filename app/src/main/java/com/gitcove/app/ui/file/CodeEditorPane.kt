package com.gitcove.app.ui.file

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitcove.app.domain.model.DiffLine
import com.gitcove.app.domain.model.LineType
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.MonoFont
import com.gitcove.app.ui.theme.diffColors

/**
 * 代码编辑器面板：固定行号栏 + 等宽文本区，共用一个纵向滚动。
 *
 * 实现要点（长行不软换行）：
 * - 行号按「逻辑行」逐行生成，而 BasicTextField 软换行时一行内容会占多个视觉行，
 *   行号立即全部错位——所以文本区宽度固定为「最宽行 + 内边距」（TextMeasurer 预先
 *   测量），永不在视口内折行，超宽部分由文本区自己的横向滚动承担，行号栏不动。
 * - BasicTextField 高度固定为「行数 × 行高」（不低于可视区高度），自身永不内部
 *   纵向滚动，纵向滚动全部交给外层 verticalScroll，行号因此天然同步。
 * - 超大文件（> [NO_WRAP_MAX_LINES] 行）逐键全文测量代价过高，退回软换行模式
 *   （行号可能错位，但该体量下行号栏本身已失去对照意义）。
 */
@Composable
fun CodeEditorPane(
    content: String,
    onContentChange: (String) -> Unit,
    readOnly: Boolean = false,
    hint: String? = null,
    modifier: Modifier = Modifier
) {
    val c = CoveTheme.colors
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val fontSize = 12.5f
    val lineHsp = 17f

    val style = TextStyle(
        fontFamily = MonoFont,
        fontSize = fontSize.sp,
        lineHeight = lineHsp.sp,
        color = if (readOnly) c.onSurfaceVariant else c.onSurface
    )
    val lineCount = content.split('\n').size.coerceAtLeast(1)
    val lineHpx = with(density) { lineHsp.sp.toPx() }

    // 预测量内容最宽行（无约束 → 不会折行，得到真实单行宽度）
    val noWrap = lineCount <= NO_WRAP_MAX_LINES
    val measurer = rememberTextMeasurer()
    var contentWpx by remember { mutableIntStateOf(0) }
    LaunchedEffect(content) {
        contentWpx = if (noWrap) {
            runCatching { measurer.measure(AnnotatedString(content), style).size.width }.getOrDefault(0)
        } else 0
    }

    Column(modifier.fillMaxSize()) {
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.caption,
                color = c.tertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            CoveDivider()
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val minFieldHpx = constraints.maxHeight.coerceAtLeast(0)
            val fieldHpx = (lineCount * lineHpx).toInt().coerceAtLeast(minFieldHpx)
            val fieldHdp = with(density) { fieldHpx.toDp() }
            val gutterWidth = if (lineCount >= 1000) 52.dp else 44.dp
            val hPadPx = with(density) { (8.dp + 12.dp).toPx() }
            val viewportWpx = (constraints.maxWidth - with(density) { (gutterWidth + 2.dp).toPx() })
                .toInt().coerceAtLeast(0)
            val fieldWpx = if (noWrap) {
                (contentWpx + hPadPx).toInt().coerceAtLeast(viewportWpx)
            } else viewportWpx
            val fieldWdp = with(density) { fieldWpx.toDp() }

            // 输入时光标在末尾（外层滚动贴底）则跟随贴底，避免新行被键盘挡住
            val stickToBottom = scroll.value >= scroll.maxValue - lineHpx * 2
            LaunchedEffect(content) {
                if (stickToBottom) scroll.scrollTo(scroll.maxValue)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .height(fieldHdp)
            ) {
                // 行号栏（固定不动，不参与横向滚动）
                Text(
                    text = (1..lineCount).joinToString("\n"),
                    style = style,
                    textAlign = TextAlign.End,
                    color = c.outline,
                    modifier = Modifier
                        .width(gutterWidth)
                        .fillMaxSize()
                        .background(c.surfaceVariant.copy(alpha = 0.55f))
                        .padding(vertical = 10.dp)
                )
                Spacer(Modifier.width(2.dp))
                if (noWrap) {
                    // 定宽不折行：超宽部分横向滚动，行号与内容永远逐行对齐
                    Box(Modifier.horizontalScroll(rememberScrollState())) {
                        BasicTextField(
                            value = content,
                            onValueChange = onContentChange,
                            readOnly = readOnly,
                            textStyle = style,
                            cursorBrush = SolidColor(c.primary),
                            modifier = Modifier
                                .width(fieldWdp)
                                .height(fieldHdp)
                                .background(c.surface)
                                .padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
                        )
                    }
                } else {
                    // 超大文件退回软换行（见类注释）
                    BasicTextField(
                        value = content,
                        onValueChange = onContentChange,
                        readOnly = readOnly,
                        textStyle = style,
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(c.surface)
                            .padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
                    )
                }
            }
        }
    }
}

/** 行号对齐（不换行）模式的最大行数；超过则退回软换行 */
private const val NO_WRAP_MAX_LINES = 3000

/**
 * 差异行渲染（代码编辑器风格）：左侧增删符号栏 + 等宽内容行，
 * 增删行着背景色，hunk 头与元信息弱化显示。
 */
@Composable
fun DiffLinesView(lines: List<DiffLine>, modifier: Modifier = Modifier) {
    val c = CoveTheme.colors
    val dc = diffColors()
    val hScroll = rememberScrollState()
    LazyColumn(modifier.fillMaxSize()) {
        items(lines) { line ->
            val bg = when (line.type) {
                LineType.ADD -> dc.addBg
                LineType.DEL -> dc.delBg
                else -> c.surface
            }
            val fg = when (line.type) {
                LineType.ADD -> dc.addFg
                LineType.DEL -> dc.delFg
                LineType.HUNK -> dc.hunkFg
                LineType.META -> dc.metaFg
                LineType.CONTEXT -> c.onBackground
            }
            val sign = when (line.type) {
                LineType.ADD -> "+"
                LineType.DEL -> "-"
                LineType.HUNK -> "@"
                else -> " "
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(bg)
            ) {
                Text(
                    sign,
                    style = MaterialTheme.typography.caption,
                    fontFamily = MonoFont,
                    color = fg,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(18.dp)
                        .padding(vertical = 1.dp)
                )
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.caption,
                    fontFamily = MonoFont,
                    color = fg,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll)
                        .padding(vertical = 1.dp)
                        .padding(start = 2.dp)
                )
            }
        }
    }
}
