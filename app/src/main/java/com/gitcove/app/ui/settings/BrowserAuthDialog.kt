package com.gitcove.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import com.gitcove.app.BuildConfig
import com.gitcove.app.data.remote.GitHubBrowserFlow
import com.gitcove.app.data.remote.GitHubDeviceFlow
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveAlertDialog
import com.gitcove.app.ui.theme.CoveTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** 浏览器授权对话框的界面状态 */
private sealed interface BrowserUiState {
    /** 正在打开浏览器 */
    data object Opening : BrowserUiState

    /** 已打开授权页，等待用户在浏览器完成授权并跳回 gitcove://callback */
    data object Waiting : BrowserUiState

    /** 已拿到授权码，正在换取令牌 */
    data object Exchanging : BrowserUiState

    /** 失败（可重试） */
    data class Failed(val message: String) : BrowserUiState
}

/**
 * GitHub 浏览器授权登录对话框（Custom Scheme + PKCE）：
 * 打开浏览器授权 → gitcove://callback 深链返回 → code + code_verifier 换令牌 →
 * 保存令牌并生成 Git 身份。与设备码登录、手动 PAT 三选一。
 */
@Composable
fun BrowserAuthDialog(
    container: AppContainer,
    onDone: (login: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val context = LocalContext.current
    var state by remember { mutableStateOf<BrowserUiState>(BrowserUiState.Opening) }
    var retryTick by remember { mutableStateOf(0) }   // 重试时触发重新发起

    LaunchedEffect(retryTick) {
        val pkce = GitHubBrowserFlow.newPkce()
        val stateToken = GitHubBrowserFlow.newState()
        state = BrowserUiState.Opening
        val opened = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(GitHubBrowserFlow.authorizeUrl(pkce, stateToken)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (!opened) {
            state = BrowserUiState.Failed(S.browserUnavailable)
            return@LaunchedEffect
        }
        state = BrowserUiState.Waiting

        // 等待深链回调（10 分钟超时），按 state 防 CSRF
        val uri = withTimeoutOrNull(10 * 60_000L) {
            GitHubBrowserFlow.deepLinks.first { it.getQueryParameter("state") == stateToken }
        }
        if (uri == null) {
            state = BrowserUiState.Failed(S.authTimeout)
            return@LaunchedEffect
        }
        uri.getQueryParameter("error")?.let { err ->
            state = BrowserUiState.Failed(uri.getQueryParameter("error_description") ?: err)
            return@LaunchedEffect
        }
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            state = BrowserUiState.Failed(S.authFailed("missing code"))
            return@LaunchedEffect
        }
        state = BrowserUiState.Exchanging

        val resp = GitHubBrowserFlow.exchange(code, pkce).getOrElse {
            state = BrowserUiState.Failed(it.message ?: "network")
            return@LaunchedEffect
        }
        val token = resp.access_token
        if (token.isNullOrBlank()) {
            // 未配置 client_secret 时 GitHub 会拒绝换 token，给出可操作的提示
            val needSecret = resp.error == "incorrect_client_credentials" &&
                BuildConfig.GITHUB_CLIENT_SECRET.isBlank()
            state = BrowserUiState.Failed(
                if (needSecret) S.oauthNeedSecret
                else (resp.error_description ?: resp.error ?: "unknown")
            )
            return@LaunchedEffect
        }
        val user = GitHubDeviceFlow.fetchUser(token).getOrNull()
        GitHubDeviceFlow.saveAuth(container.auth, token, user)
        onDone(user?.login)
    }

    CoveAlertDialog(
        onDismiss = onDismiss,
        title = S.browserAuthTitle,
        content = {
            Column(Modifier.fillMaxWidth()) {
                when (val s = state) {
                    is BrowserUiState.Failed -> Text(
                        S.authFailed(s.message),
                        style = MaterialTheme.typography.body2
                    )
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = c.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.width(16.dp).height(16.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when (state) {
                                BrowserUiState.Opening -> S.requestingCode
                                BrowserUiState.Exchanging -> S.exchangingToken
                                else -> S.browserAuthWaiting
                            },
                            style = MaterialTheme.typography.body2,
                            color = c.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmLabel = if (state is BrowserUiState.Failed) S.retry else null,
        onConfirm = { retryTick++ },
        dismissLabel = S.cancel,
        onDismissClick = onDismiss
    )
}
