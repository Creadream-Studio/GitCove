package com.gitcove.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.sp
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.theme.CoveTheme

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

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = path.substringAfterLast('/'),
                subtitle = path,
                navigationIcon = { CoveBackButton { nav.popBackStack() } },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val repo = container.repoStore.get(repoId) ?: return@launch
                                val dir = java.io.File(repo.path)
                                container.gitCore.writeFile(dir, path, content)
                                    .onSuccess {
                                        if (isConflict) {
                                            container.gitCore.markResolved(dir, path)
                                            withContext(Dispatchers.Main) {
                                                snackbar.showSnackbar(S.savedResolved)
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) { snackbar.showSnackbar(S.saved) }
                                        }
                                        original = content
                                    }
                                    .onFailure {
                                        withContext(Dispatchers.Main) { snackbar.showSnackbar(it.message ?: S.saveFailed) }
                                    }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = S.save)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        backgroundColor = CoveTheme.colors.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (isConflict) {
                Text(
                    S.conflictEditorHint,
                    style = MaterialTheme.typography.caption,
                    color = CoveTheme.colors.tertiary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            CoveDivider()
            if (!loaded) {
                Text(S.loading, style = MaterialTheme.typography.caption, modifier = Modifier.padding(12.dp))
            } else {
                // MD2 代码编辑区：surface 底 + 等宽字体 + 主色光标（不用带边框的输入框样式）
                BasicTextField(
                    value = content,
                    onValueChange = { content = it },
                    textStyle = TextStyle(
                        fontFamily = MonoFont,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        color = CoveTheme.colors.onSurface
                    ),
                    cursorBrush = SolidColor(CoveTheme.colors.primary),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CoveTheme.colors.surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
    }
}
