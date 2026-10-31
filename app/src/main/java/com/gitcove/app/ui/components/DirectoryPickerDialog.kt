package com.gitcove.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 应用内目录选择器（克隆/新建的保存目录、导入本地目录共用）。
 *
 * 不再使用系统 SAF 目录选择器（ACTION_OPEN_DOCUMENT_TREE），原因：
 * 1. 其返回的树 URI 在多级目录下换算真实路径会被截断成顶层目录（%2F 解码歧义），
 *    导致"选了 /AA/BB/CC 却导入 /AA"；
 * 2. 系统选择器界面默认隐藏以 . 开头的文件/文件夹，看不到 .gitignore 等条目，
 *    无法确认所选目录确实是仓库根。
 *
 * 本选择器直接以 File API 列目录（依赖已授予「所有文件访问」权限，入口处已拦截）：
 * 全部条目可见（含点开头），仅隐藏 .git 目录本身；文件夹可进入，文件仅展示
 * （用于确认目录内容，如看到 .gitignore 即仓库根）。
 */
@Composable
fun DirectoryPickerDialog(
    title: String,
    initialDir: File,
    onSelect: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val context = LocalContext.current
    val storageRoot = remember {
        android.os.Environment.getExternalStorageDirectory()?.takeIf { it.isDirectory }
            ?: File("/storage/emulated/0")
    }
    var cur by remember { mutableStateOf(initialDir.takeIf { it.isDirectory } ?: storageRoot) }
    var entries by remember { mutableStateOf<List<File>?>(null) }

    LaunchedEffect(cur) {
        entries = withContext(Dispatchers.IO) {
            runCatching {
                cur.listFiles()
                    ?.asSequence()
                    // 只隐藏 .git 本身，其余（含点开头文件/文件夹）全部显示
                    ?.filter { !(it.isDirectory && it.name == ".git") }
                    ?.sortedWith(
                        compareByDescending<File> { it.isDirectory }
                            .thenBy { it.name.lowercase(Locale.ROOT) }
                    )
                    ?.toList()
            }.getOrNull()
        }
    }

    // 是否还能向上一级：最多回到 /storage（卷列表），不再往上（/ 无意义）
    val canUp = cur.absolutePath != "/storage" && cur.parentFile?.let { it.absolutePath != "/" } == true

    CoveAlertDialog(
        onDismiss = onDismiss,
        title = title,
        confirmLabel = S.pickerUseThisDir,
        onConfirm = { onSelect(cur) },
        dismissLabel = S.cancel,
        content = {
            // 当前路径
            Text(
                cur.absolutePath,
                style = MaterialTheme.typography.caption,
                fontFamily = MonoFont,
                color = c.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                S.pickerHint,
                style = MaterialTheme.typography.caption,
                color = c.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )
            // 快捷跳转 + 上一级
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { cur = storageRoot; entries = null }) {
                    Icon(
                        Icons.Filled.Smartphone,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(S.pickerInternalStorage, style = MaterialTheme.typography.button)
                }
                Spacer(Modifier.weight(1f))
                if (canUp) {
                    TextButton(onClick = { cur = cur.parentFile ?: cur; entries = null }) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = null,
                            tint = c.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(S.pickerUpLevel, style = MaterialTheme.typography.button)
                    }
                }
            }
            // 目录内容
            val list = entries
            when {
                list == null -> Row(
                    Modifier.fillMaxWidth().height(120.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) { CircularProgressIndicator(color = c.primary, modifier = Modifier.size(26.dp)) }
                list.isEmpty() -> Text(
                    S.emptyDir,
                    style = MaterialTheme.typography.body2,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
                else -> Column(
                    Modifier.height(340.dp).verticalScroll(rememberScrollState())
                ) {
                    list.forEach { f ->
                        val isDir = f.isDirectory
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isDir) { cur = f; entries = null }
                                .padding(horizontal = 4.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isDir) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = if (isDir) Color(0xFFF3B04A) else c.onSurfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                f.name,
                                style = MaterialTheme.typography.body2,
                                fontWeight = if (isDir) FontWeight.Bold else FontWeight.Normal,
                                color = if (isDir) c.onSurface else c.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        CoveDivider()
                    }
                }
            }
        }
    )
}
