package com.gitcove.app.ui.clone

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitcove.app.BuildConfig
import com.gitcove.app.data.git.GitCredentials
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveAlertDialog
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveProgressBar
import com.gitcove.app.ui.components.CoveSectionTitle
import com.gitcove.app.ui.components.CoveSegmentedRow
import com.gitcove.app.ui.components.CoveTextField
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.components.DirectoryPickerDialog
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.util.StorageAccess
import com.gitcove.app.util.vmFactory
import java.io.File

/**
 * 克隆 / 新建 / 导入仓库页（MD2）：
 * 分段按钮切模式 + filler 风格文本字段 + 底部主按钮，密集表单自上而下。
 */
@Composable
fun CloneScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val vm: CloneViewModel = viewModel(factory = vmFactory { CloneViewModel(container) })
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var mode by remember { mutableStateOf(0) }   // 0 克隆 / 1 新建 / 2 导入
    var url by remember { mutableStateOf("") }
    // 克隆协议（HTTPS / SSH / Git）：先选协议，地址按协议填写；切换协议自动转换已输入地址
    var proto by remember { mutableStateOf(GitCredentials.CloneProto.HTTPS) }
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var parentDir by remember { mutableStateOf(container.repoParent.absolutePath) }
    var nameTouched by remember { mutableStateOf(false) }
    var showGithubList by remember { mutableStateOf(false) }
    var showPermissionHint by remember { mutableStateOf(false) }
    // 当前目录选择器的写入目标：0 = 保存目录（克隆/新建），2 = 本地目录（导入）
    var pickTarget by remember { mutableStateOf(0) }
    var showDirPicker by remember { mutableStateOf(false) }
    var pickerInitial by remember { mutableStateOf(File(pickRootPath())) }

    fun ensureStorageOrShowHint(): Boolean {
        return if (StorageAccess.hasAllFilesAccess(context)) true
        else { showPermissionHint = true; false }
    }

    /**
     * 打开应用内目录选择器（target：0 = 保存目录，2 = 本地目录）。
     * 不再用系统 SAF 选择器：多级目录会被其 URI 换算截断成顶层目录，
     * 且系统选择器默认隐藏点开头文件/文件夹，看不到 .git 无法确认仓库根。
     */
    fun pickDir(target: Int, initialPath: String) {
        if (ensureStorageOrShowHint()) {
            pickTarget = target
            pickerInitial = File(initialPath)
            showDirPicker = true
        }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.addRepo,
                navigationIcon = { CoveBackButton { nav.popBackStack() } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        backgroundColor = c.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // 模式切换（MD2 分段按钮）
            CoveSegmentedRow(
                options = listOf(S.modeClone, S.modeNew, S.modeImport),
                selectedIndex = mode,
                onSelect = { mode = it },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )

            when (mode) {
                0 -> {
                    CoveSectionTitle(S.modeClone)
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        // 协议分段：选中段高亮；切换时把已输入地址转换成对应协议形式
                        CoveSegmentedRow(
                            options = listOf(S.protoHttps, S.protoSsh, S.protoGit),
                            selectedIndex = proto.ordinal,
                            onSelect = { idx ->
                                val target = GitCredentials.CloneProto.values()[idx]
                                if (target != proto) {
                                    proto = target
                                    if (url.isNotBlank()) url = GitCredentials.convertToProto(url, target)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            S.protoSwitchHint,
                            style = MaterialTheme.typography.caption,
                            color = c.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        CoveTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                if (!nameTouched) name = GitCredentials.repoNameOf(it)
                            },
                            label = S.remoteUrlLabel,
                            placeholder = when (proto) {
                                GitCredentials.CloneProto.SSH -> "git@github.com:user/repo.git"
                                GitCredentials.CloneProto.GIT -> "git://github.com/user/repo.git"
                                else -> "https://github.com/user/repo.git"
                            },
                            singleLine = true,
                            supporting = if (url.isNotBlank()) S.platformOf(GitCredentials.platformOf(url)) else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        CoveTextField(
                            value = name,
                            onValueChange = { name = it; nameTouched = true },
                            label = S.repoName,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        // 目标目录选择（默认仓库根目录，可改任意目录）
                        DirectoryField(
                            label = S.saveDir,
                            dirPath = parentDir,
                            hint = S.tapToPick,
                            supporting = S.pickDirDesc,
                            onClick = { pickDir(0, parentDir) }
                        )
                        if (container.auth.tokenFor("github.com") != null) {
                            TextButton(onClick = {
                                vm.loadMyGithubRepos()
                                showGithubList = true
                            }) { Text(S.pickFromGithub, style = MaterialTheme.typography.button, color = c.primary) }
                        }
                        Spacer(Modifier.height(10.dp))
                        CoveButton(
                            text = S.startClone,
                            onClick = { vm.cloneRepo(url, name, File(parentDir)) { nav.popBackStack() } },
                            enabled = !vm.busy && url.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                1 -> {
                    CoveSectionTitle(S.modeNew)
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        CoveTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = S.repoName,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        DirectoryField(
                            label = S.saveDir,
                            dirPath = parentDir,
                            hint = S.tapToPick,
                            supporting = S.pickDirDesc,
                            onClick = { pickDir(0, parentDir) }
                        )
                        Spacer(Modifier.height(10.dp))
                        CoveButton(
                            text = S.createNewRepo,
                            onClick = { vm.initRepo(name, File(parentDir)) { nav.popBackStack() } },
                            enabled = !vm.busy && name.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                2 -> {
                    CoveSectionTitle(S.modeImport)
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        CoveTextField(
                            value = path,
                            onValueChange = { path = it },
                            label = S.localDirPath,
                            placeholder = "/storage/emulated/0/...",
                            singleLine = true,
                            supporting = S.importHint,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        DirectoryField(
                            label = S.localDir,
                            dirPath = path,
                            hint = S.tapToPick,
                            supporting = S.pickDirDesc,
                            onClick = { pickDir(2, path.ifBlank { pickRootPath() }) }
                        )
                        Spacer(Modifier.height(8.dp))
                        CoveTextField(
                            value = name,
                            onValueChange = { name = it; nameTouched = true },
                            label = S.displayNameOptional,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        CoveButton(
                            text = S.importDir,
                            onClick = {
                                vm.importRepo(path, name.takeIf { nameTouched && it.isNotBlank() }) { nav.popBackStack() }
                            },
                            enabled = !vm.busy && path.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (vm.busy) {
                Spacer(Modifier.height(14.dp))
                CoveProgressBar(modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    vm.progress,
                    style = MaterialTheme.typography.caption,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showGithubList) {
        GithubRepoPickerDialog(
            repos = vm.githubRepos,
            loading = vm.busy,
            onPick = { repo ->
                url = repo.clone_url
                proto = GitCredentials.detectProto(repo.clone_url)
                if (!nameTouched) name = repo.full_name.substringAfter('/')
                nameTouched = false
                showGithubList = false
            },
            onDismiss = { showGithubList = false; vm.dismissGithubList() }
        )
    }

    if (showPermissionHint) {
        CoveAlertDialog(
            onDismiss = { showPermissionHint = false },
            title = S.permissionTitle,
            content = { Text(S.permissionText, style = MaterialTheme.typography.body2) },
            confirmLabel = S.grantAccess,
            onConfirm = {
                showPermissionHint = false
                StorageAccess.requestAllFilesAccess(context)
            },
            dismissLabel = S.cancel
        )
    }

    // 应用内目录选择器（克隆/新建的保存目录与导入本地目录共用）
    if (showDirPicker) {
        DirectoryPickerDialog(
            title = S.pickDir,
            initialDir = pickerInitial,
            onSelect = { dir ->
                if (pickTarget == 2) {
                    path = dir.absolutePath
                    if (!nameTouched) name = dir.name
                } else {
                    parentDir = dir.absolutePath
                }
                showDirPicker = false
            },
            onDismiss = { showDirPicker = false }
        )
    }
}

/** 系统外部存储根（部分 ROM 为 /storage/emulated/0，异常时退回外部存储目录） */
private fun pickRootPath(): String =
    android.os.Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"

/** 目录选择字段：只读展示 + 点击选择（MD2 只读文本字段 + 尾部图标按钮） */
@Composable
private fun DirectoryField(
    label: String,
    dirPath: String,
    hint: String,
    supporting: String,
    onClick: () -> Unit
) {
    val c = CoveTheme.colors
    CoveTextField(
        value = dirPath,
        onValueChange = { },
        readOnly = true,
        label = label,
        placeholder = hint,
        singleLine = true,
        supporting = supporting,
        textStyle = MaterialTheme.typography.body2,
        trailingIcon = {
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = hint,
                    tint = c.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clickable { onClick() }
    )
}

@Composable
private fun GithubRepoPickerDialog(
    repos: List<com.gitcove.app.data.remote.GitHubRepoInfo>,
    loading: Boolean,
    onPick: (com.gitcove.app.data.remote.GitHubRepoInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val context = LocalContext.current
    CoveAlertDialog(
        onDismiss = onDismiss,
        title = S.githubPickerTitle,
        content = {
            if (loading && repos.isEmpty()) {
                Text(S.loading, style = MaterialTheme.typography.body2)
            } else if (repos.isEmpty()) {
                Text(S.noReposFound, style = MaterialTheme.typography.body2)
            } else {
                Column(Modifier.height(360.dp).verticalScroll(rememberScrollState())) {
                    repos.forEach { repo ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(repo) }
                                .padding(vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(repo.full_name, style = MaterialTheme.typography.subtitle2)
                                Spacer(Modifier.width(6.dp))
                                if (repo.isPrivate) {
                                    Text(
                                        S.privateBadge,
                                        style = MaterialTheme.typography.overline,
                                        color = c.tertiary
                                    )
                                }
                            }
                            repo.description?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.caption,
                                    color = c.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        CoveDivider()
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                S.orgAccessHint,
                style = MaterialTheme.typography.caption,
                color = c.primary,
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://github.com/settings/connections/applications/" +
                                        BuildConfig.GITHUB_CLIENT_ID
                                )
                            )
                        )
                    }
                }
            )
        },
        confirmLabel = S.close,
        onConfirm = onDismiss
    )
}
