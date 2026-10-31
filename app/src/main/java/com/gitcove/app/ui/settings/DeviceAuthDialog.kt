package com.gitcove.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gitcove.app.data.remote.GitHubDeviceFlow
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveAlertDialog
import com.gitcove.app.ui.components.CoveButton
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.MonoFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 设备授权对话框的界面状态 */
private sealed interface DeviceUiState {
    /** 正在请求设备码 */
    data object Requesting : DeviceUiState

    /** 已拿到设备码，等待用户在浏览器完成授权 */
    data class CodeReady(val code: GitHubDeviceFlow.DeviceCode) : DeviceUiState

    /** 设备码过期 */
    data object Expired : DeviceUiState

    /** 其它失败 */
    data class Failed(val message: String) : DeviceUiState
}

/**
 * GitHub 设备授权登录对话框：
 * 获取设备码 → 打开浏览器授权 → 轮询换令牌 → 保存令牌并生成 Git 身份。
 * 与手动粘贴 PAT 两种方式二选一。
 */
@Composable
fun DeviceAuthDialog(
    container: AppContainer,
    onDone: (login: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var state by remember { mutableStateOf<DeviceUiState>(DeviceUiState.Requesting) }
    var pollTick by remember { mutableStateOf(0) }   // 重试时触发重新请求

    // 请求设备码
    LaunchedEffect(pollTick) {
        if (state is DeviceUiState.CodeReady || state is DeviceUiState.Requesting) {
            state = DeviceUiState.Requesting
            GitHubDeviceFlow.start()
                .onSuccess { state = DeviceUiState.CodeReady(it) }
                .onFailure { state = DeviceUiState.Failed(it.message ?: "network") }
        }
    }

    // 轮询令牌（拿到设备码后启动；用户在浏览器完成授权后自动继续）
    when (val s = state) {
        is DeviceUiState.CodeReady -> {
            val device = s.code
            LaunchedEffect(device.device_code) {
                val deadline = System.currentTimeMillis() + device.expires_in * 1000L
                var interval = device.interval.coerceAtLeast(3)
                while (isActive && System.currentTimeMillis() < deadline) {
                    delay(interval * 1000L)
                    val resp = GitHubDeviceFlow.pollOnce(device.device_code)
                    when {
                        resp.access_token != null -> {
                            val token = resp.access_token!!
                            val user = GitHubDeviceFlow.fetchUser(token).getOrNull()
                            GitHubDeviceFlow.saveAuth(container.auth, token, user)
                            onDone(user?.login)
                            return@LaunchedEffect
                        }
                        resp.error == "authorization_pending" -> Unit
                        resp.error == "slow_down" -> interval = (resp.interval ?: interval + 5).coerceAtLeast(interval + 5)
                        resp.error == "expired_token" -> { state = DeviceUiState.Expired; return@LaunchedEffect }
                        else -> {
                            state = DeviceUiState.Failed(resp.error_description ?: resp.error ?: "unknown")
                            return@LaunchedEffect
                        }
                    }
                }
                if (state is DeviceUiState.CodeReady) state = DeviceUiState.Expired
            }
        }
        else -> Unit
    }

    fun openBrowser(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Toast.makeText(context, S.browserUnavailable, Toast.LENGTH_LONG).show()
        }
    }

    CoveAlertDialog(
        onDismiss = onDismiss,
        title = S.deviceAuthTitle,
        content = {
            when (val s = state) {
                is DeviceUiState.Requesting -> {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = c.primary, strokeWidth = 2.5.dp, modifier = Modifier.width(28.dp).height(28.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(S.requestingCode, style = MaterialTheme.typography.body2, color = c.onSurfaceVariant)
                    }
                }
                is DeviceUiState.CodeReady -> {
                    Text(
                        S.deviceAuthIntro,
                        style = MaterialTheme.typography.body2,
                        color = c.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    // 设备码：大号等宽字，点击复制
                    Text(
                        S.deviceCodeLabel,
                        style = MaterialTheme.typography.overline,
                        color = c.onSurfaceVariant
                    )
                    Text(
                        s.code.user_code,
                        style = MaterialTheme.typography.h5.copy(fontFamily = MonoFont, fontSize = 24.sp),
                        color = c.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.surfaceVariant, RoundedCornerShape(4.dp))
                            .clickable { clipboard.setText(AnnotatedString(s.code.user_code)) }
                            .padding(vertical = 12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    CoveButton(
                        text = S.openAuthPage,
                        onClick = { openBrowser(s.code.openUrl) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.width(14.dp).height(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(S.waitingAuth, style = MaterialTheme.typography.caption, color = c.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                is DeviceUiState.Expired -> {
                    Text(S.authExpired, style = MaterialTheme.typography.body2)
                }
                is DeviceUiState.Failed -> {
                    Text(S.authFailed(s.message), style = MaterialTheme.typography.body2)
                }
            }
        },
        confirmLabel = when (state) {
            is DeviceUiState.Expired, is DeviceUiState.Failed -> S.retry
            else -> null
        },
        onConfirm = {
            state = DeviceUiState.Requesting
            pollTick++
        },
        dismissLabel = S.cancel,
        onDismissClick = onDismiss
    )
}
