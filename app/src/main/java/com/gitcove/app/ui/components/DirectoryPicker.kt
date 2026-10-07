package com.gitcove.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 目录选择对话框：在应用内浏览文件系统并选择一个目录。
 *
 * 返回的是真实文件路径（File），可直接用于 JGit 操作，
 * 这也是不使用 SAF (ACTION_OPEN_DOCUMENT_TREE) 的原因 —— 它只给 content:// URI。
 *
 * @param initialDir 初始目录（通常为默认仓库根目录）
 * @param quickLinks 快捷跳转（名称 to 路径）
 * @param onPick     选中目录回调
 */
@Composable
fun DirectoryPickerDialog(
    initialDir: File,
    quickLinks: List<Pair<String, String>> = emptyList(),
    onPick: (File) -> Unit,
    onDismiss: () -> Unit
) {
    var current by remember { mutableStateOf(initialDir) }
    var subDirs by remember(current) { mutableStateOf<List<File>?>(null) }

    LaunchedEffect(current) {
        subDirs = null
        withContext(Dispatchers.IO) {
            subDirs = current.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
                ?.sortedWith(compareBy({ it.name.lowercase() }))
                .orEmpty()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择目录", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                // 当前路径 + 返回上级
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { current.parentFile?.let { current = it } },
                        enabled = current.parentFile != null,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回上级", Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        current.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 快捷跳转
                if (quickLinks.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickLinks.forEach { (label, path) ->
                            AssistChip(
                                onClick = { current = File(path) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        if (current.absolutePath == path) Icons.Filled.Check else Icons.Filled.Home,
                                        null,
                                        Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                val dirs = subDirs
                when {
                    // listFiles 为 null 时 subDirs 尚未赋值，加载中
                    dirs == null -> Text(
                        "读取中…",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    dirs.isEmpty() -> Text(
                        "此目录没有子目录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    else -> LazyColumn(Modifier.height(320.dp)) {
                        items(dirs, key = { it.absolutePath }) { dir ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { current = dir }
                                    .padding(vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Folder,
                                    null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    dir.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(current) }) {
                Icon(Icons.Filled.FolderOpen, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("选择此目录")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
