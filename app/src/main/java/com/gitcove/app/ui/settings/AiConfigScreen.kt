package com.gitcove.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.unit.dp
import com.gitcove.app.data.ai.AiCommit
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveButton
import com.gitcove.app.ui.components.CoveChip
import com.gitcove.app.ui.components.CoveOutlinedButton
import com.gitcove.app.ui.components.CoveSectionTitle
import com.gitcove.app.ui.components.CoveTextField
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.theme.CoveTheme
import kotlinx.coroutines.launch

/**
 * AI 配置（二级页面，功能 4.6）：
 * 服务商快捷预设（OpenAI / DeepSeek / Gemini / OpenRouter）回填 Base URL 与推荐模型，
 * API Key 密码输入，测试连接用「界面上尚未保存」的值即时验证，保存后写入加密存储。
 */
@Composable
fun AiConfigScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf(container.aiConfig.baseUrl) }
    var apiKey by remember { mutableStateOf(container.aiConfig.apiKey) }
    var model by remember { mutableStateOf(container.aiConfig.model) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    // null = 尚无结果；Pair(成功?, 文案)
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val allFilled = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.aiConfigTitle,
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
            // ── 快捷预设：点按回填 Base URL + 推荐模型（不动 API Key）──
            CoveSectionTitle(S.aiPresetsSection)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                container.aiConfig.presets.forEach { p ->
                    val selected = AiCommit.normalizeBaseUrl(baseUrl) == p.baseUrl && model.trim() == p.model
                    CoveChip(
                        label = p.label,
                        selected = selected,
                        onClick = {
                            baseUrl = p.baseUrl
                            model = p.model
                            testResult = null
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            CoveTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; testResult = null },
                label = S.aiBaseUrl,
                supporting = S.aiBaseUrlHint,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(4.dp))
            CoveTextField(
                value = apiKey,
                onValueChange = { apiKey = it; testResult = null },
                label = S.aiApiKey,
                singleLine = true,
                visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(4.dp))
            CoveTextField(
                value = model,
                onValueChange = { model = it; testResult = null },
                label = S.aiModel,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )

            // ── 测试连接 / 保存 ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoveOutlinedButton(
                    text = if (testing) S.aiTesting else S.aiTestBtn,
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            runCatching {
                                container.aiChat.testConnection(baseUrl, apiKey, model)
                            }.onSuccess {
                                testResult = true to "${S.aiTestOk}（$it）"
                            }.onFailure {
                                testResult = false to (it.message ?: it.javaClass.simpleName)
                            }
                            testing = false
                        }
                    },
                    enabled = allFilled && !testing,
                    modifier = Modifier.weight(1f)
                )
                CoveButton(
                    text = S.save,
                    onClick = {
                        container.aiConfig.save(baseUrl, apiKey, model)
                        Toast.makeText(context, S.aiSaved, Toast.LENGTH_SHORT).show()
                    },
                    enabled = allFilled,
                    modifier = Modifier.weight(1f)
                )
            }

            // 测试结果（成功主色 / 失败错误色）
            testResult?.let { (ok, text) ->
                Text(
                    text,
                    style = MaterialTheme.typography.caption,
                    color = if (ok) c.primary else c.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Text(
                S.aiSecureNote,
                style = MaterialTheme.typography.caption,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}
