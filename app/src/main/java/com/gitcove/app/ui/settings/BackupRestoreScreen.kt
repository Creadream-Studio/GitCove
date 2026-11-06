package com.gitcove.app.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitcove.app.data.backup.BackupPasswordNeededException
import com.gitcove.app.data.backup.BackupWrongPasswordException
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveAlertDialog
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveOutlinedButton
import com.gitcove.app.ui.components.CoveSectionTitle
import com.gitcove.app.ui.components.CoveTextField
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.theme.CoveTheme
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 备份与还原（v0.5.2）：
 * - 备份：除仓库以外的全部数据导出为 .gitcove 文件（SAF 自选位置），可选密码加密；
 * - 还原：从备份文件导入。文件信息行右侧「覆盖现有数据」复选框——
 *   默认合并（只补本机缺的项），勾选则先清空再导入（以备份为准，含界面设置）。
 */
@Composable
fun BackupRestoreScreen(container: AppContainer, nav: androidx.navigation.NavHostController) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var busy by remember { mutableStateOf(false) }
    fun showMsg(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    // ── 备份流程：可选密码 → SAF 建文件 → 收集/序列化/写出 ──
    var showBackupPwDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var showBackupPw by remember { mutableStateOf(false) }
    var pendingBackupPassword by remember { mutableStateOf("") }

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) { pendingBackupPassword = ""; return@rememberLauncherForActivityResult }
        val pw = pendingBackupPassword
        pendingBackupPassword = ""
        busy = true
        scope.launch(Dispatchers.IO) {
            runCatching {
                val payload = container.backup.collect()
                val text = container.backup.serialize(payload, pw)
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    ?: error("cannot open output stream")
                container.opLog.append("backup", "Exported backup (encrypted=${pw.isNotEmpty()})")
            }.onSuccess {
                withContext(Dispatchers.Main) { busy = false; showMsg(S.backupDone) }
            }.onFailure { e ->
                withContext(Dispatchers.Main) { busy = false; showMsg(S.backupFailed + (e.message ?: "")) }
            }
        }
    }

    // ── 导入流程：SAF 选文件 → 导入对话框（覆盖复选框 + 加密密码）→ 解析/还原 ──
    var importTarget by remember { mutableStateOf<ImportTarget?>(null) }
    var overwrite by remember { mutableStateOf(false) }
    var importPassword by remember { mutableStateOf("") }
    var showImportPw by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                val header = container.backup.peek(text)
                ImportTarget(
                    name = queryDisplayName(context, uri) ?: "backup.gitcove",
                    text = text,
                    encrypted = header.encrypted,
                    createdAt = header.createdAt,
                    summary = header.payload?.summary()
                )
            }.onSuccess { target ->
                withContext(Dispatchers.Main) {
                    importPassword = ""; passwordError = false; overwrite = false
                    importTarget = target
                }
            }.onFailure {
                withContext(Dispatchers.Main) { showMsg(S.restoreBadFile) }
            }
        }
    }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.navBackup,
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
            // ── 备份 ──
            CoveSectionTitle(S.backupSectionBackup)
            Text(
                S.backupDesc,
                style = MaterialTheme.typography.body2,
                color = c.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            CoveButton(
                text = S.backupButton,
                onClick = { backupPassword = ""; showBackupPw = false; showBackupPwDialog = true },
                enabled = !busy,
                icon = { Icon(Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            )
            CoveDivider(insetStart = 12)

            // ── 还原 ──
            CoveSectionTitle(S.restoreSection)
            Text(
                S.restoreDesc,
                style = MaterialTheme.typography.body2,
                color = c.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            CoveOutlinedButton(
                text = S.restoreButton,
                onClick = { openDoc.launch(arrayOf("*/*")) },
                enabled = !busy,
                icon = { Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    // ── 备份密码对话框（留空 = 明文备份） ──
    if (showBackupPwDialog) {
        CoveAlertDialog(
            onDismiss = { showBackupPwDialog = false; backupPassword = "" },
            title = S.backupButton,
            confirmLabel = S.confirm,
            onConfirm = {
                pendingBackupPassword = backupPassword
                backupPassword = ""
                showBackupPwDialog = false
                val stamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                createDoc.launch("GitCove-备份-$stamp.gitcove")
            },
            dismissLabel = S.cancel,
            content = {
                CoveTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = S.backupPasswordLabel,
                    singleLine = true,
                    visualTransformation = if (showBackupPw) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showBackupPw = !showBackupPw }) {
                            Icon(
                                if (showBackupPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    // ── 导入对话框：文件信息 + 右侧覆盖复选框 +（加密时）密码 ──
    importTarget?.let { target ->
        CoveAlertDialog(
            onDismiss = { importTarget = null },
            title = S.restoreTitle,
            confirmLabel = S.restoreImport,
            confirmEnabled = !target.encrypted || importPassword.isNotEmpty(),
            onConfirm = {
                busy = true
                val pw = importPassword
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        val payload = container.backup.parse(target.text, pw)
                        container.backup.restore(payload, overwrite)
                    }.onSuccess { sum ->
                        withContext(Dispatchers.Main) {
                            busy = false
                            importTarget = null
                            syncUiStates(container)
                            showMsg(S.restoreDone + sum.text())
                        }
                    }.onFailure { e ->
                        when (e) {
                            is BackupWrongPasswordException, is BackupPasswordNeededException ->
                                withContext(Dispatchers.Main) { busy = false; passwordError = true }
                            else -> withContext(Dispatchers.Main) {
                                busy = false
                                importTarget = null
                                showMsg(S.restoreFailed + (e.message ?: ""))
                            }
                        }
                    }
                }
            },
            dismissLabel = S.cancel,
            content = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            target.name,
                            style = MaterialTheme.typography.subtitle2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(target.createdAt)),
                            style = MaterialTheme.typography.caption,
                            color = c.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = overwrite, onCheckedChange = { overwrite = it })
                        Text(S.restoreOverwrite, style = MaterialTheme.typography.caption)
                    }
                }
                if (target.summary != null) {
                    Text(
                        target.summary,
                        style = MaterialTheme.typography.caption,
                        color = c.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (target.encrypted) {
                    CoveTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it; passwordError = false },
                        label = S.restoreEncryptedHint,
                        isError = passwordError,
                        supporting = if (passwordError) S.restoreWrongPassword else null,
                        singleLine = true,
                        visualTransformation = if (showImportPw) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showImportPw = !showImportPw }) {
                                Icon(
                                    if (showImportPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        )
    }
}

/** 导入目标：SAF 选中的备份文件（内容已读入内存） */
private data class ImportTarget(
    val name: String,
    val text: String,
    val encrypted: Boolean,
    val createdAt: Long,
    val summary: String?
)

/** 设置类数据还原后，同步 Compose 响应式状态（主题 / 强调色 / 圆角 / 语言立即生效） */
private fun syncUiStates(container: AppContainer) {
    container.setThemeMode(container.auth.themeMode)
    container.setAccent(container.auth.accent)
    container.setShapeStyle(container.auth.shapeStyle)
    container.setAppLanguage(container.auth.appLanguage)
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
        if (cur.moveToFirst()) cur.getString(0) else null
    }
}.getOrNull()
