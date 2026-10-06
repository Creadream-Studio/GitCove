package com.gitcove.app.ui.clone

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitcove.app.data.git.GitCredentials
import com.gitcove.app.di.AppContainer
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.util.vmFactory

/**
 * 克隆 / 新建 / 导入仓库页（文档 5.3）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val vm: CloneViewModel = viewModel(factory = vmFactory { CloneViewModel(container) })
    val snackbar = remember { SnackbarHostState() }
    var mode by remember { mutableStateOf(0) }   // 0 克隆 / 1 新建 / 2 导入
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var nameTouched by remember { mutableStateOf(false) }
    var showGithubList by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加仓库") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == 0,
                    onClick = { mode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 3)
                ) { Text("克隆") }
                SegmentedButton(
                    selected = mode == 1,
                    onClick = { mode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 3)
                ) { Text("新建") }
                SegmentedButton(
                    selected = mode == 2,
                    onClick = { mode = 2 },
                    shape = SegmentedButtonDefaults.itemShape(2, 3)
                ) { Text("导入") }
            }

            Spacer(Modifier.height(16.dp))

            when (mode) {
                0 -> {
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            if (!nameTouched) name = GitCredentials.repoNameOf(it)
                        },
                        label = { Text("远程地址（https / ssh / git）") },
                        placeholder = { Text("https://github.com/user/repo.git") },
                        singleLine = true,
                        supportingText = {
                            if (url.isNotBlank()) Text("平台：${GitCredentials.platformOf(url)}")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; nameTouched = true },
                        label = { Text("仓库名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (container.auth.tokenFor("github.com") != null) {
                        TextButton(onClick = {
                            vm.loadMyGithubRepos()
                            showGithubList = true
                        }) { Text("从我的 GitHub 选择仓库") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.cloneRepo(url, name) { nav.popBackStack() } },
                        enabled = !vm.busy && url.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("开始克隆") }
                }

                1 -> {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("仓库名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.initRepo(name) { nav.popBackStack() } },
                        enabled = !vm.busy && name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("创建新仓库") }
                }

                2 -> {
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        label = { Text("本地目录路径") },
                        placeholder = { Text("/storage/emulated/0/...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; nameTouched = true },
                        label = { Text("显示名称（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.importRepo(path, name.takeIf { nameTouched && it.isNotBlank() }) { nav.popBackStack() } },
                        enabled = !vm.busy && path.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("导入该目录") }
                }
            }

            if (vm.busy) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    vm.progress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showGithubList) {
        GithubRepoPickerDialog(
            repos = vm.githubRepos,
            loading = vm.busy,
            onPick = { repo ->
                url = repo.clone_url
                if (!nameTouched) name = repo.full_name.substringAfter('/')
                nameTouched = false
                showGithubList = false
            },
            onDismiss = { showGithubList = false; vm.dismissGithubList() }
        )
    }
}

@Composable
private fun GithubRepoPickerDialog(
    repos: List<com.gitcove.app.data.remote.GitHubRepoInfo>,
    loading: Boolean,
    onPick: (com.gitcove.app.data.remote.GitHubRepoInfo) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择 GitHub 仓库", style = MaterialTheme.typography.titleMedium) },
        text = {
            if (loading && repos.isEmpty()) {
                Text("加载中…")
            } else if (repos.isEmpty()) {
                Text("未获取到仓库，请检查令牌权限")
            } else {
                Column(Modifier.height(360.dp).verticalScroll(rememberScrollState())) {
                    repos.forEach { repo ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { onPick(repo) }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    repo.full_name,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(Modifier.width(6.dp))
                                if (repo.isPrivate) {
                                    Text(
                                        "私有",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            repo.description?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
