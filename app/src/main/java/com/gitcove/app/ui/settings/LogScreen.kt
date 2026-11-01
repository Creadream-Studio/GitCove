package com.gitcove.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitcove.app.data.log.CrashCapture
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.EmptyView
import com.gitcove.app.ui.components.CoveAlertDialog
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveSectionTitle
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.theme.CoveTheme
import androidx.compose.material.icons.filled.ReceiptLong
import java.io.File

/**
 * 操作日志页（功能 79）：查看 / 清空；
 * 附崩溃日志区（CrashCapture 落盘的闪退堆栈，魔改 ROM 排查用）。
 */
@Composable
fun LogScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val S = LocalStrings.current
    var entries by remember { mutableStateOf(container.opLog.entries()) }
    var crashFiles by remember { mutableStateOf(CrashCapture.list(container.appContext)) }
    // 正在查看的崩溃文件内容（点击行打开）
    var viewingCrash by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun reload() {
        entries = container.opLog.entries()
        crashFiles = CrashCapture.list(container.appContext)
    }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.opLogTitle,
                navigationIcon = { CoveBackButton { nav.popBackStack() } },
                actions = {
                    IconButton(onClick = {
                        container.opLog.clear()
                        CrashCapture.clear(container.appContext)
                        reload()
                    }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = S.clearLogs)
                    }
                }
            )
        },
        backgroundColor = CoveTheme.colors.background
    ) { padding ->
        if (entries.isEmpty() && crashFiles.isEmpty()) {
            EmptyView(
                S.noLogs,
                modifier = Modifier.padding(padding),
                icon = Icons.Filled.ReceiptLong
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                // ── 崩溃日志区（有崩溃文件才显示，放最上面：排查闪退最常看）──
                if (crashFiles.isNotEmpty()) {
                    item { CoveSectionTitle(S.crashLogSection, trailing = "${crashFiles.size}") }
                    items(crashFiles) { file ->
                        CrashFileRow(file = file) {
                            viewingCrash = file.name to CrashCapture.read(file)
                        }
                        CoveDivider()
                    }
                }
                items(entries) { entry ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(
                            buildString {
                                append("[${entry.tag}] ")
                                append(entry.message)
                            },
                            style = MaterialTheme.typography.caption,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    CoveDivider()
                }
            }
        }
    }

    // ── 崩溃堆栈查看（等宽字体全文滚动）──
    // 注：内容不要再包 verticalScroll——CoveAlertDialog 内容区自带滚动，
    // 同向嵌套滚动会在测量阶段抛 IllegalStateException（点击闪退日志又闪退的根因）
    viewingCrash?.let { (name, content) ->
        CoveAlertDialog(
            onDismiss = { viewingCrash = null },
            title = name,
            confirmLabel = S.close,
            onConfirm = { viewingCrash = null },
            content = {
                Text(
                    content,
                    style = MaterialTheme.typography.caption,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

/** 崩溃文件行：文件名 + 首行摘要（异常类与信息），点击看全文 */
@Composable
private fun CrashFileRow(file: File, onClick: () -> Unit) {
    // 摘要：文件里第一处 "Exception/Error" 行，方便不开全文也能定位
    val summary = remember(file) {
        CrashCapture.read(file).lineSequence()
            .firstOrNull { it.contains("Exception") || it.contains("Error") }
            ?.trim()
            ?.take(120)
            ?: ""
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            file.name,
            style = MaterialTheme.typography.subtitle2,
            color = CoveTheme.colors.error
        )
        if (summary.isNotBlank()) {
            Text(
                summary,
                style = MaterialTheme.typography.caption,
                color = CoveTheme.colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
