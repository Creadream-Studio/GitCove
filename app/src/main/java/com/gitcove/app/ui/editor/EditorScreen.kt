package com.gitcove.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveAlertDialog
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.file.CodeEditorPane
import com.gitcove.app.ui.theme.CoveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 文本编辑器（功能 53 部分）：编辑仓库文件；冲突文件保存后自动标记已解决
 */
@Composable
fun EditorScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController,
    repoId: Long,
    path: String,
    isNew: Boolean
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val S = LocalStrings.current
    var content by remember { mutableStateOf("") }
    var original by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var isConflict by remember { mutableStateOf(false) }
    // 有未保存修改时拦截返回
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = loaded && content != original

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val repo = container.repoStore.get(repoId) ?: return@withContext
            val dir = java.io.File(repo.path)
            content = container.gitCore.readFile(dir, path).getOrDefault("")
            original = content
            isConflict = container.gitCore.status(dir).getOrNull()?.conflicts?.contains(path) == true
            loaded = true
        }
    }

    // 系统返回键：无未保存修改时放行，有则先确认
    BackHandler(enabled = dirty) { confirmDiscard = true }

    if (confirmDiscard) {
        CoveAlertDialog(
            onDismiss = { confirmDiscard = false },
            title = S.unsavedTitle,
            content = { Text(S.unsavedText, style = MaterialTheme.typography.body2) },
            confirmLabel = S.discardChanges,
            danger = true,
            onConfirm = { confirmDiscard = false; nav.popBackStack() },
            dismissLabel = S.keepEditing
        )
    }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = path.substringAfterLast('/'),
                subtitle = path,
                navigationIcon = { CoveBackButton { if (dirty) confirmDiscard = true else nav.popBackStack() } },
                actions = {
                    // 仅在有改动时显示保存（与文件查看页一致，也提示「有未保存内容」）
                    if (dirty) {
                        IconButton(
                            onClick = {
                                // 先给要写盘的内容拍快照：落盘期间继续打字不会被误标为已保存
                                val snapshot = content
                                val conflictFile = isConflict
                                val okMsg = if (conflictFile) S.savedResolved else S.saved
                                scope.launch(Dispatchers.IO) {
                                    val repo = container.repoStore.get(repoId) ?: return@launch
                                    val dir = java.io.File(repo.path)
                                    container.gitCore.writeFile(dir, path, snapshot)
                                        .onSuccess {
                                            if (conflictFile) container.gitCore.markResolved(dir, path)
                                            // 关键：立刻标记干净（不等提示条）——
                                            // showSnackbar 是挂起函数，若排在它后面，
                                            // 提示条显示期间点返回仍会误报「未保存」
                                            original = snapshot
                                            // 提示条单独起协程，不阻塞保存流程
                                            scope.launch { snackbar.showSnackbar(okMsg) }
                                        }
                                        .onFailure {
                                            scope.launch { snackbar.showSnackbar(it.message ?: S.saveFailed) }
                                        }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = S.save)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        backgroundColor = CoveTheme.colors.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!loaded) {
                Text(S.loading, style = MaterialTheme.typography.caption, modifier = Modifier.padding(12.dp))
            } else {
                // 与文件查看页同款代码编辑器（行号 + 长行不折行 + 贴底跟随）
                CodeEditorPane(
                    content = content,
                    onContentChange = { content = it },
                    hint = if (isConflict) S.conflictEditorHint else null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
