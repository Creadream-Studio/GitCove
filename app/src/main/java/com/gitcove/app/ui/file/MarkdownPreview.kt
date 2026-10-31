package com.gitcove.app.ui.file

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.MonoFont
import java.io.File

// ─────────────────────────── Markdown 预览 ───────────────────────────
// 轻量渲染：标题 / 加粗 / 斜体 / 行内代码 / 代码块 / 列表 / 引用 / 分隔线 / 链接
// （覆盖仓库文档日常阅读需要，不支持表格与图片内嵌）

private data class MdBlock(val type: String, val lines: List<String>)

private fun parseMarkdown(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val para = mutableListOf<String>()
    fun flushPara() {
        if (para.isNotEmpty()) { blocks.add(MdBlock("p", para.toList())); para.clear() }
    }
    var i = 0
    val lines = src.replace("\r\n", "\n").split('\n')
    while (i < lines.size) {
        val raw = lines[i]
        val t = raw.trim()
        when {
            t.startsWith("```") -> {
                flushPara()
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) { code.add(lines[i]); i++ }
                blocks.add(MdBlock("code", code))
            }
            t.isEmpty() -> flushPara()
            t.startsWith("#") && t.length > 1 && t.drop(1).firstOrNull()?.isWhitespace() != false -> {
                flushPara()
                val level = t.takeWhile { it == '#' }.length.coerceIn(1, 6)
                blocks.add(MdBlock("h$level", listOf(t.dropWhile { it == '#' }.trim())))
            }
            t == "---" || t == "***" || t == "___" -> { flushPara(); blocks.add(MdBlock("hr", emptyList())) }
            t.startsWith(">") -> {
                flushPara()
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quote.add(lines[i].trim().removePrefix(">").trim()); i++
                }
                blocks.add(MdBlock("quote", quote)); continue
            }
            (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) -> {
                flushPara()
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val lt = lines[i].trim()
                    if (lt.startsWith("- ") || lt.startsWith("* ") || lt.startsWith("+ ")) {
                        items.add(lt.substring(2).trim()); i++
                    } else if (lt.isNotEmpty() && items.isNotEmpty() && !lt.startsWith("#") && !lt.startsWith("```")) {
                        items[items.lastIndex] += " " + lt; i++   // 折行并入上一项
                    } else break
                }
                blocks.add(MdBlock("ul", items)); continue
            }
            Regex("^\\d+\\. ").containsMatchIn(t) -> {
                flushPara()
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val lt = lines[i].trim()
                    val m = Regex("^(\\d+)\\. (.*)").find(lt)
                    if (m != null) { items.add(m.groupValues[2].trim()); i++ }
                    else if (lt.isNotEmpty() && items.isNotEmpty() && !lt.startsWith("#")) {
                        items[items.lastIndex] += " " + lt; i++
                    } else break
                }
                blocks.add(MdBlock("ol", items)); continue
            }
            else -> { para.add(t); i++ }
        }
        i++
    }
    flushPara()
    return blocks
}

/** 行内标记：**粗** / *斜* / `码` / [链](接) */
private fun mdInline(text: String, linkColor: Color, codeBg: Color, codeFg: Color): AnnotatedString =
    buildAnnotatedString {
        val regex = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`([^`]+)`|\[([^\]]+)]\(([^)]+)\)""")
        var pos = 0
        for (m in regex.findAll(text)) {
            append(text.substring(pos, m.range.first))
            val (style, content) = when {
                m.groupValues[1].isNotEmpty() -> SpanStyle(fontWeight = FontWeight.Bold) to m.groupValues[1]
                m.groupValues[2].isNotEmpty() -> SpanStyle(fontStyle = FontStyle.Italic) to m.groupValues[2]
                m.groupValues[3].isNotEmpty() -> SpanStyle(fontFamily = MonoFont, background = codeBg, color = codeFg) to m.groupValues[3]
                else -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline) to m.groupValues[4]
            }
            pushStyle(style)
            append(content)
            pop()
            pos = m.range.last + 1
        }
        append(text.substring(pos))
    }

@Composable
fun MarkdownPreview(content: String, modifier: Modifier = Modifier) {
    val c = CoveTheme.colors
    val blocks = remember(content) { parseMarkdown(content) }
    val codeBg = c.surfaceVariant.copy(alpha = 0.7f)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        blocks.forEach { b ->
            when (b.type) {
                "code" -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(codeBg)
                            .padding(10.dp)
                    ) {
                        val hScroll = rememberScrollState()
                        Text(
                            b.lines.joinToString("\n"),
                            style = MaterialTheme.typography.caption.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                            fontFamily = MonoFont,
                            color = c.onSurface,
                            modifier = Modifier.horizontalScroll(hScroll)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                "hr" -> {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.outline.copy(alpha = 0.5f)))
                    Spacer(Modifier.height(10.dp))
                }
                "quote" -> {
                    val accent = c.primary
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(
                                    color = accent,
                                    start = Offset(2.dp.toPx(), 4f),
                                    end = Offset(2.dp.toPx(), size.height - 4f),
                                    strokeWidth = 3.dp.toPx()
                                )
                            }
                            .padding(start = 14.dp, top = 2.dp, bottom = 2.dp)
                    ) {
                        b.lines.forEach {
                            Text(
                                mdInline(it, c.primary, codeBg, c.onSurface),
                                style = MaterialTheme.typography.body2,
                                color = c.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                "ul" -> {
                    b.lines.forEach {
                        Row {
                            Text("•  ", style = MaterialTheme.typography.body2, color = c.primary)
                            Text(
                                mdInline(it, c.primary, codeBg, c.onSurface),
                                style = MaterialTheme.typography.body2,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                "ol" -> {
                    b.lines.forEachIndexed { idx, it ->
                        Row {
                            Text("${idx + 1}. ", style = MaterialTheme.typography.body2, color = c.primary)
                            Text(
                                mdInline(it, c.primary, codeBg, c.onSurface),
                                style = MaterialTheme.typography.body2,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                else -> {
                    val level = b.type.removePrefix("h").toIntOrNull() ?: 0
                    if (level in 1..6) {
                        val size = when (level) {
                            1 -> 22f; 2 -> 19f; 3 -> 17f; 4 -> 15.5f; 5 -> 14.5f; else -> 14f
                        }
                        Text(
                            mdInline(b.lines.first(), c.primary, codeBg, c.onSurface),
                            style = MaterialTheme.typography.body1.copy(fontSize = size.sp, lineHeight = (size + 6).sp),
                            fontWeight = FontWeight.Bold,
                            color = c.onBackground,
                            modifier = Modifier.padding(top = if (level <= 2) 10.dp else 6.dp, bottom = 6.dp)
                        )
                    } else {
                        Text(
                            mdInline(b.lines.joinToString(" "), c.primary, codeBg, c.onSurface),
                            style = MaterialTheme.typography.body2.copy(lineHeight = 19.sp),
                            color = c.onBackground,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────── HTML 预览 ───────────────────────────

/** HTML 预览：WebView 渲染（本地文件，无脚本），深色模式下注入底色 */
@Composable
fun HtmlPreview(file: File, content: String, modifier: Modifier = Modifier) {
    val c = CoveTheme.colors
    val html = remember(content, c.isDark) {
        if (!content.contains("<html", ignoreCase = true) && !content.contains("<body", ignoreCase = true) && c.isDark) {
            """<style>body{background:#121212;color:#ddd}</style>""" + content
        } else if (c.isDark && content.contains("</head>", ignoreCase = true)) {
            content.replace("</head>", """<style>body{background:#121212;color:#ddd}</style></head>""")
        } else if (c.isDark) {
            """<style>body{background:#121212;color:#ddd}</style>""" + content
        } else content
    }
    Box(modifier.fillMaxSize().background(if (c.isDark) Color(0xFF121212) else Color.White)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = true
                }
            },
            update = { wv ->
                val base = runCatching { "file://" + file.parent + "/" }.getOrNull()
                wv.loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
