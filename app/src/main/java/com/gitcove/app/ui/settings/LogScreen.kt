package com.gitcove.app.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

/**
 * 操作日志页（功能 79）：查看 / 清空；
 * 附崩溃日志区（CrashCapture 落盘的闪退堆栈，魔改 ROM 排查用）。
 *
 * v0.4.8 起日志存放于外部应用目录（/storage/emulated/0/Android/data/<包名>/files/）：
 * 可直接用文件管理器取用，应用内也提供「分享单份崩溃日志」「导出全部（zip）」。
 */
@Composable
fun LogScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val S = LocalStrings.current
    val context = LocalContext.current
    var entries by remember { mutableStateOf(container.opLog.entries()) }
    var crashFiles by remember { mutableStateOf(CrashCapture.list(container.appContext)) }
    // 正在查看的崩溃文件内容（点击行打开）
    var viewingCrash by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 日志存放位置说明（顶栏文件夹图标）
    var showLocation by remember { mutableStateOf(false) }

    fun reload() {
        entries = container.opLog.entries()
        crashFiles = CrashCapture.list(container.appContext)
    }

    fun shareFile(file: File) = shareViaIntent(context, file, file.name)

    fun exportAll() {
        if (entries.isEmpty() && crashFiles.isEmpty()) {
            android.widget.Toast.makeText(context, S.exportNothing, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        exportLogsZip(context, container) { zip ->
            if (zip != null) shareViaIntent(context, zip, zip.name)
            else android.widget.Toast.makeText(context, S.exportFailed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.opLogTitle,
                navigationIcon = { CoveBackButton { nav.popBackStack() } },
                actions = {
                    // 导出全部（zip 经分享面板发出）
                    IconButton(onClick = { exportAll() }) {
                        Icon(Icons.Filled.Share, contentDescription = S.exportAllLogs)
                    }
                    // 日志所在目录说明（外部应用目录可直接取用）
                    IconButton(onClick = { showLocation = true }) {
                        Icon(Icons.Filled.Folder, contentDescription = S.logsLocationTitle)
                    }
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
                        CrashFileRow(file = file, onShare = { shareFile(file) }) {
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

    // ── 崩溃堆栈查看（对话框内容区自带滚动，禁止再嵌套同向滚动）──
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

    // ── 日志存放位置说明 ──
    if (showLocation) {
        CoveAlertDialog(
            onDismiss = { showLocation = false },
            title = S.logsLocationTitle,
            confirmLabel = S.close,
            onConfirm = { showLocation = false },
            content = {
                Column(Modifier.fillMaxWidth()) {
                    Text(S.logsLocationDesc, style = MaterialTheme.typography.body2)
                    Text(
                        container.opLog.dir.parentFile?.absolutePath ?: "—",
                        style = MaterialTheme.typography.caption,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        )
    }
}

/** 崩溃文件行：文件名 + 首行摘要（异常类与信息），点击看全文，行尾分享 */
@Composable
private fun CrashFileRow(file: File, onShare: () -> Unit, onClick: () -> Unit) {
    // 摘要：文件里第一处 "Exception/Error" 行，方便不开全文也能定位
    val summary = remember(file) {
        CrashCapture.read(file).lineSequence()
            .firstOrNull { it.contains("Exception") || it.contains("Error") }
            ?.trim()
            ?.take(120)
            ?: ""
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(Modifier.weight(1f)) {
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
        // 分享这份崩溃日志（FileProvider + ACTION_SEND）
        IconButton(onClick = onShare) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** 经系统分享面板发送单个文件 */
private fun shareViaIntent(context: Context, file: File, title: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "com.gitcove.app.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension.equals("zip", true)) "application/zip" else "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
    }
}

/** 全部日志（操作日志 + 崩溃堆栈）打包为 cache 目录下的 zip，完成后回调（主线程） */
private fun exportLogsZip(context: Context, container: AppContainer, onDone: (File?) -> Unit) {
    thread(name = "log-export") {
        val zip = runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val out = File(context.cacheDir, "GitCove-logs-$stamp.zip")
            ZipOutputStream(out.outputStream().buffered()).use { zos ->
                fun add(source: File, entryDir: String) {
                    if (!source.isFile) return
                    zos.putNextEntry(ZipEntry("$entryDir/${source.name}"))
                    source.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
                CrashCapture.list(container.appContext).forEach { add(it, "crash") }
                container.opLog.dir.listFiles { f -> f.name.endsWith(".log") }
                    ?.sortedBy { it.name }
                    ?.forEach { add(it, "log") }
            }
            out
        }.getOrNull()
        Handler(Looper.getMainLooper()).post { onDone(zip) }
    }
}
