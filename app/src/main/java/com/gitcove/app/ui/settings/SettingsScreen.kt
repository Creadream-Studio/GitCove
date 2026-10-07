package com.gitcove.app.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.gitcove.app.data.auth.SshKeyGen
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.AppLanguage
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.SectionHeader
import com.gitcove.app.ui.nav.Routes
import com.gitcove.app.ui.theme.MonoFont
import com.gitcove.app.ui.theme.ShapeStyle
import com.gitcove.app.ui.theme.ThemeMode
import com.gitcove.app.work.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 设置页：Git 身份 / 语言 / MD3 风格 / 主题 / 访问令牌 / SSH 密钥 / 自动同步 / 日志 / 关于
 * （功能 45-48 / 19-21 / 79）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val S = LocalStrings.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // 身份
    var gitName by remember { mutableStateOf(container.auth.gitName) }
    var gitEmail by remember { mutableStateOf(container.auth.gitEmail) }

    // 令牌
    var tokens by remember { mutableStateOf(container.auth.tokens()) }
    var tokenDialog by remember { mutableStateOf(false) }

    // SSH
    var sshKeys by remember { mutableStateOf(container.auth.sshKeys()) }
    var sshGenDialog by remember { mutableStateOf(false) }
    var sshImportDialog by remember { mutableStateOf(false) }
    var sshShowPub by remember { mutableStateOf<String?>(null) }

    // 自动同步
    var autoSync by remember { mutableStateOf(container.auth.autoSyncEnabled) }
    var interval by remember { mutableStateOf(container.auth.autoSyncIntervalMin) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(S.settings) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = S.back)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Git 身份 ──
            SectionHeader(S.sectionGitIdentity)
            Column(Modifier.padding(horizontal = 12.dp)) {
                OutlinedTextField(
                    value = gitName,
                    onValueChange = { gitName = it },
                    label = { Text(S.username) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gitEmail,
                    onValueChange = { gitEmail = it },
                    label = { Text(S.email) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                TextButton(onClick = {
                    container.auth.gitName = gitName.trim()
                    container.auth.gitEmail = gitEmail.trim()
                    Toast.makeText(container.appContext, S.identitySaved, Toast.LENGTH_SHORT).show()
                }) { Text(S.saveIdentity) }
            }

            // ── 语言 ──
            SectionHeader(S.sectionLanguage)
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                AppLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = container.appLanguage.value == lang,
                        onClick = { container.setAppLanguage(lang) },
                        label = { Text(if (lang == AppLanguage.SYSTEM) S.languageSystem else lang.displayName) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Text(
                S.languageHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // ── 外观 ──
            SectionHeader(S.sectionAppearance)
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = container.themeMode.value == mode,
                        onClick = { container.setThemeMode(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> S.themeSystem
                                    ThemeMode.DARK -> S.themeDark
                                    ThemeMode.LIGHT -> S.themeLight
                                }
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            // ── MD3 风格 ──
            SectionHeader(S.sectionMd3Style)
            Text(
                S.shapeStyle,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                ShapeStyle.entries.forEach { style ->
                    FilterChip(
                        selected = container.shapeStyle.value == style,
                        onClick = { container.setShapeStyle(style) },
                        label = {
                            Text(
                                when (style) {
                                    ShapeStyle.STANDARD -> S.shapeStandard
                                    ShapeStyle.ROUNDED -> S.shapeRounded
                                    ShapeStyle.COMPACT -> S.shapeCompact
                                }
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(S.dynamicColor, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        S.dynamicColorHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = container.dynamicColor.value,
                    onCheckedChange = { container.setDynamicColor(it) },
                    // Material You 动态取色仅在 Android 12+ 可用
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                )
            }

            // ── 访问令牌 ──
            SectionHeader(S.sectionTokens)
            tokens.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.host, style = MaterialTheme.typography.titleSmall)
                        Text(
                            t.username,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        container.auth.removeToken(t.host)
                        tokens = container.auth.tokens()
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = S.delete, tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            TextButton(onClick = { tokenDialog = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(S.addToken)
            }

            // ── SSH 密钥 ──
            SectionHeader(S.sectionSsh)
            sshKeys.forEach { k ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { sshShowPub = k.publicKey }
                    ) {
                        Text(k.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${k.type} · ${container.auth.fingerprintOf(k.publicKey).take(23)}…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        container.auth.deleteSshKey(k.name)
                        sshKeys = container.auth.sshKeys()
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = S.delete, tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            Row(Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = { sshGenDialog = true }) { Text(S.generateKey) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { sshImportDialog = true }) { Text(S.importKey) }
            }

            // ── 自动同步 ──
            SectionHeader(S.sectionAutoSync)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(S.autoSyncDesc, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        S.everyNMinutes(interval),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoSync,
                    onCheckedChange = { enabled ->
                        autoSync = enabled
                        container.auth.autoSyncEnabled = enabled
                        if (enabled) {
                            SyncWorker.schedule(container.appContext, interval)
                            Toast.makeText(container.appContext, S.autoSyncOn, Toast.LENGTH_SHORT).show()
                        } else {
                            SyncWorker.cancel(container.appContext)
                        }
                    }
                )
            }
            Row(Modifier.padding(horizontal = 12.dp)) {
                listOf(15, 30, 60).forEach { min ->
                    FilterChip(
                        selected = interval == min,
                        onClick = {
                            interval = min
                            container.auth.autoSyncIntervalMin = min
                            if (autoSync) SyncWorker.schedule(container.appContext, min)
                        },
                        label = { Text("${min}min") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            // ── 日志 ──
            SectionHeader(S.sectionOpLog)
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                TextButton(onClick = { nav.navigate(Routes.LOG) }) { Text(S.viewLogs) }
            }

            // ── 关于 ──
            SectionHeader(S.sectionAbout)
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(S.aboutTitle, style = MaterialTheme.typography.titleSmall)
                Text(
                    S.aboutDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    S.aboutLink,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.padding(bottom = 32.dp))
        }
    }

    // ── 令牌添加对话框 ──
    if (tokenDialog) {
        var host by remember { mutableStateOf("github.com") }
        var username by remember { mutableStateOf("") }
        var token by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { tokenDialog = false },
            title = { Text(S.addTokenTitle, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text(S.hostLabel, style = MaterialTheme.typography.labelMedium)
                    Row {
                        listOf("github.com", "gitlab.com", "gitea.com").forEach { h ->
                            FilterChip(
                                selected = host == h,
                                onClick = { host = h },
                                label = { Text(h.substringBefore('.')) },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(S.customHost) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(S.username) },
                        singleLine = true,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(S.tokenLabel) },
                        singleLine = true,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        container.auth.setToken(host.trim(), username.trim(), token.trim())
                        tokens = container.auth.tokens()
                        tokenDialog = false
                    },
                    enabled = host.isNotBlank() && token.isNotBlank()
                ) { Text(S.save) }
            },
            dismissButton = { TextButton(onClick = { tokenDialog = false }) { Text(S.cancel) } }
        )
    }

    // ── SSH 生成对话框 ──
    if (sshGenDialog) {
        var name by remember { mutableStateOf("gitcove_key") }
        var comment by remember { mutableStateOf(container.auth.gitEmail) }
        var type by remember { mutableStateOf(SshKeyGen.KeyType.ECDSA256) }
        AlertDialog(
            onDismissRequest = { sshGenDialog = false },
            title = { Text(S.genSshTitle, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(S.keyName) },
                        singleLine = true
                    )
                    Row(Modifier.padding(vertical = 6.dp)) {
                        SshKeyGen.KeyType.entries.forEach { t ->
                            FilterChip(
                                selected = type == t,
                                onClick = { type = t },
                                label = { Text(t.label) },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text(S.keyComment) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            SshKeyGen.generate(container.auth, name, type, comment)
                                .onSuccess { key ->
                                    sshKeys = container.auth.sshKeys()
                                    sshShowPub = key.publicKey
                                    sshGenDialog = false
                                }
                                .onFailure { e ->
                                    sshGenDialog = false
                                }
                        }
                    },
                    enabled = name.isNotBlank()
                ) { Text(S.generate) }
            },
            dismissButton = { TextButton(onClick = { sshGenDialog = false }) { Text(S.cancel) } }
        )
    }

    // ── SSH 导入对话框 ──
    if (sshImportDialog) {
        var name by remember { mutableStateOf("") }
        var privateKey by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { sshImportDialog = false },
            title = { Text(S.importSshTitle, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(S.keyName) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = privateKey,
                        onValueChange = { privateKey = it },
                        label = { Text(S.pastePrivateKey) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFont),
                        minLines = 4,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        S.privateKeyHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 公钥由 JSch 从私钥推导（解析公钥 blob 首字段 = 算法名）
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                val jsch = com.jcraft.jsch.JSch()
                                val tmp = java.io.File.createTempFile("import_", "_key")
                                tmp.writeText(privateKey.trim() + "\n")
                                val kp = com.jcraft.jsch.KeyPair.load(jsch, tmp.absolutePath)
                                val blob = kp.getPublicKeyBlob()
                                    ?: throw IllegalStateException(S.derivePubFailed)
                                val bb = java.nio.ByteBuffer.wrap(blob)
                                val len = bb.int
                                val algo = ByteArray(len).also { bb.get(it) }.toString(Charsets.US_ASCII)
                                val b64 = java.util.Base64.getEncoder().encodeToString(blob)
                                val pub = "$algo $b64 ${name.trim()}"
                                container.auth.saveSshKey(name.trim(), privateKey, pub)
                                tmp.delete()
                            }.onSuccess {
                                sshKeys = container.auth.sshKeys()
                                sshImportDialog = false
                            }
                        }
                    },
                    enabled = name.isNotBlank() && privateKey.contains("PRIVATE KEY")
                ) { Text(S.importAction) }
            },
            dismissButton = { TextButton(onClick = { sshImportDialog = false }) { Text(S.cancel) } }
        )
    }

    // ── 公钥展示对话框 ──
    sshShowPub?.let { pub ->
        AlertDialog(
            onDismissRequest = { sshShowPub = null },
            title = { Text(S.pubKeyTitle, style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    pub,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFont),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clipboard.setText(AnnotatedString(pub)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(pub))
                    Toast.makeText(container.appContext, S.pubKeyCopied, Toast.LENGTH_SHORT).show()
                }) { Text(S.copy) }
            },
            dismissButton = { TextButton(onClick = { sshShowPub = null }) { Text(S.close) } }
        )
    }
}
