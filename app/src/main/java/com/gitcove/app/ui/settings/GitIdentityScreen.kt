package com.gitcove.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gitcove.app.data.store.GitHubAccount
import com.gitcove.app.data.store.GitIdentity
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.Avatar
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveSectionTitle
import com.gitcove.app.ui.components.CoveTextField
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.components.EmptyView
import com.gitcove.app.ui.theme.CoveTheme

/**
 * Git 身份（二级页面）：可维护多套提交身份（姓名 + 邮箱 + 别名），
 * 其中一套为默认，提交与克隆/初始化时使用。
 */
@Composable
fun GitIdentityScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    var identities by remember { mutableStateOf(container.auth.identities()) }
    // 默认身份本地镜像：设默认后同步写入并触发重组，徽章立即切换
    // （只写 SharedPreferences 不引起重组，界面要等下一次任意状态变化才“补显”，
    //  这是“必须点两下才切换成功”的根源）
    var defaultId by remember { mutableStateOf(container.auth.defaultIdentityId) }
    var editing by remember { mutableStateOf<GitIdentity?>(null) }   // null = 新增
    var showDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<GitIdentity?>(null) }

    /** 重载列表与默认身份（保存/删除后调用；删除默认时存储层会回退到首条） */
    fun refresh() {
        identities = container.auth.identities()
        defaultId = container.auth.defaultIdentityId
    }
    // GitHub 账号摘要：进入页面时自动刷新，身份行据此展示 GitHub 头像
    var account by remember { mutableStateOf(container.auth.githubAccount) }
    LaunchedEffect(Unit) {
        container.githubApi.refreshAccountCache()
        account = container.auth.githubAccount
    }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.sectionGitIdentity,
                navigationIcon = { CoveBackButton { nav.popBackStack() } }
            )
        },
        backgroundColor = c.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            CoveSectionTitle(S.sectionGitIdentity, trailing = "${identities.size}")
            if (identities.isEmpty()) {
                EmptyView(text = S.emptyIdentities, icon = Icons.Outlined.Person)
            }
            identities.forEach { identity ->
                val isDefault = identity.id == defaultId ||
                    (defaultId == -1L && identities.firstOrNull()?.id == identity.id)
                IdentityRow(
                    identity = identity,
                    isDefault = isDefault,
                    account = account,
                    onEdit = { editing = identity; showDialog = true },
                    onSetDefault = {
                        container.auth.setDefaultIdentity(identity.id)
                        defaultId = identity.id   // 立即生效，徽章当场切换
                    },
                    onDelete = { deleteTarget = identity }
                )
                CoveDivider()
            }
            TextButton(
                onClick = { editing = null; showDialog = true },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(S.addIdentity)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ── 新增 / 编辑对话框 ──
    if (showDialog) {
        var label by remember { mutableStateOf(editing?.label.orEmpty()) }
        var name by remember { mutableStateOf(editing?.name.orEmpty()) }
        var email by remember { mutableStateOf(editing?.email.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    if (editing == null) S.addIdentity else S.editIdentity,
                    style = MaterialTheme.typography.subtitle1
                )
            },
            text = {
                Column {
                    CoveTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = S.identityLabel,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    CoveTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = S.username,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                    CoveTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = S.email,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val current = editing
                        val item = GitIdentity(
                            id = current?.id ?: System.currentTimeMillis(),
                            label = label.trim(),
                            name = name.trim(),
                            email = email.trim()
                        )
                        container.auth.saveIdentity(item)
                        refresh()
                        showDialog = false
                    },
                    enabled = name.isNotBlank() && email.isNotBlank()
                ) { Text(S.save) }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text(S.cancel) } }
        )
    }

    // ── 删除确认 ──
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(S.delete, style = MaterialTheme.typography.subtitle1) },
            text = { Text("${target.displayName} <${target.email}>") },
            confirmButton = {
                TextButton(onClick = {
                    container.auth.deleteIdentity(target.id)
                    refresh()
                    deleteTarget = null
                }) { Text(S.delete, color = c.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(S.cancel) } }
        )
    }
}

@Composable
private fun IdentityRow(
    identity: GitIdentity,
    isDefault: Boolean,
    account: GitHubAccount?,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // GitHub 头像（自动获取；取不到时回退首字符圆标）
        Avatar(email = identity.email, name = identity.name, size = 32.dp, account = account)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(identity.displayName, style = MaterialTheme.typography.subtitle2)
            Text(
                if (identity.label.isBlank()) identity.email else "${identity.name} <${identity.email}>",
                style = MaterialTheme.typography.caption,
                color = c.onSurfaceVariant,
                maxLines = 1
            )
        }
        // 默认徽章 / 设默认
        if (isDefault) {
            DefaultBadge(label = S.defaultBadge, filled = true, onClick = null)
        } else {
            DefaultBadge(label = S.noDefaultBadge, filled = false, onClick = onSetDefault)
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = S.delete, tint = c.error, modifier = Modifier.size(18.dp))
        }
    }
}

/** 「默认 / 设默认」小徽章：默认态主色填充，非默认态描边可点 */
@Composable
private fun DefaultBadge(label: String, filled: Boolean, onClick: (() -> Unit)?) {
    val c = CoveTheme.colors
    val shape = RoundedCornerShape(3.dp)
    if (filled) {
        Row(
            Modifier
                .background(c.primary.copy(alpha = 0.14f), shape)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = c.primary, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(2.dp))
            Text(label, style = MaterialTheme.typography.overline, color = c.primary)
        }
    } else {
        Row(
            Modifier
                .clickable(onClick = { onClick?.invoke() })
                .background(Color.Transparent, shape)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.overline, color = c.onSurfaceVariant)
        }
    }
}
