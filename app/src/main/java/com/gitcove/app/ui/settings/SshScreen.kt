package com.gitcove.app.ui.settings

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
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveChip
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveSectionTitle
import com.gitcove.app.ui.components.CoveTextField
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** SSH 密钥（二级页面）：密钥列表、生成与导入 */
@Composable
fun SshScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var sshKeys by remember { mutableStateOf(container.auth.sshKeys()) }
    var sshGenDialog by remember { mutableStateOf(false) }
    var sshImportDialog by remember { mutableStateOf(false) }
    var sshShowPub by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.sectionSsh,
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
            CoveSectionTitle(S.sectionSsh)
            if (sshKeys.isEmpty()) {
                Text(
                    S.noSshKeySet,
                    style = MaterialTheme.typography.body2,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
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
                        Text(k.name, style = MaterialTheme.typography.subtitle2)
                        Text(
                            "${k.type} · ${container.auth.fingerprintOf(k.publicKey).take(23)}…",
                            style = MaterialTheme.typography.caption,
                            color = c.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        container.auth.deleteSshKey(k.name)
                        sshKeys = container.auth.sshKeys()
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = S.delete, tint = c.error)
                    }
                }
                CoveDivider()
            }
            Row(Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = { sshGenDialog = true }) { Text(S.generateKey) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { sshImportDialog = true }) { Text(S.importKey) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ── 生成对话框 ──
    if (sshGenDialog) {
        var name by remember { mutableStateOf("gitcove_key") }
        var comment by remember { mutableStateOf(container.auth.defaultIdentity()?.email.orEmpty()) }
        var type by remember { mutableStateOf(SshKeyGen.KeyType.ECDSA256) }
        AlertDialog(
            onDismissRequest = { sshGenDialog = false },
            title = { Text(S.genSshTitle, style = MaterialTheme.typography.subtitle1) },
            text = {
                Column {
                    CoveTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = S.keyName,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.padding(vertical = 6.dp)) {
                        SshKeyGen.KeyType.entries.forEach { t ->
                            CoveChip(label = t.label, selected = type == t, onClick = { type = t }, modifier = Modifier.padding(end = 6.dp))
                        }
                    }
                    CoveTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = S.keyComment,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
                                .onFailure { sshGenDialog = false }
                        }
                    },
                    enabled = name.isNotBlank()
                ) { Text(S.generate) }
            },
            dismissButton = { TextButton(onClick = { sshGenDialog = false }) { Text(S.cancel) } }
        )
    }

    // ── 导入对话框 ──
    if (sshImportDialog) {
        var name by remember { mutableStateOf("") }
        var privateKey by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { sshImportDialog = false },
            title = { Text(S.importSshTitle, style = MaterialTheme.typography.subtitle1) },
            text = {
                Column {
                    CoveTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = S.keyName,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    CoveTextField(
                        value = privateKey,
                        onValueChange = { privateKey = it },
                        label = S.pastePrivateKey,
                        textStyle = MaterialTheme.typography.caption.copy(fontFamily = MonoFont),
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                    Text(
                        S.privateKeyHint,
                        style = MaterialTheme.typography.overline,
                        color = c.onSurfaceVariant
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
            title = { Text(S.pubKeyTitle, style = MaterialTheme.typography.subtitle1) },
            text = {
                Text(
                    pub,
                    style = MaterialTheme.typography.caption.copy(fontFamily = MonoFont),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { clipboard.setText(AnnotatedString(pub)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(pub))
                }) { Text(S.copy) }
            },
            dismissButton = { TextButton(onClick = { sshShowPub = null }) { Text(S.close) } }
        )
    }
}
