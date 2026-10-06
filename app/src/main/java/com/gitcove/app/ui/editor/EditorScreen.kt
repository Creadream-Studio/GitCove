package com.gitcove.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.gitcove.app.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 文本编辑器（功能 53 部分）：编辑仓库文件；冲突文件保存后自动标记已解决
 */
@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = {
                    Column {
                        Text(
                            path.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
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
                                                snackbar.showSnackbar("已保存并标记为已解决")
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) { snackbar.showSnackbar("已保存") }
                                        }
                                        original = content
                                    }
                                    .onFailure {
                                        withContext(Dispatchers.Main) { snackbar.showSnackbar(it.message ?: "保存失败") }
                                    }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "保存")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (isConflict) {
                Text(
                    "⚠ 该文件存在冲突标记（<<<<<<< / ======= / >>>>>>>），编辑完成后保存将自动标记为已解决。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            HorizontalDivider()
            if (!loaded) {
                Text("加载中…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            } else {
                androidx.compose.material3.OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    textStyle = TextStyle(fontFamily = MonoFont, fontSize = MaterialTheme.typography.bodySmall.fontSize),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }
        }
    }
}
