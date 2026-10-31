package com.gitcove.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveChip
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveSectionTitle
import com.gitcove.app.ui.components.CoveTextField
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.theme.CoveTheme

/** 账号与令牌（二级页面）：GitHub / GitLab / Gitea 等平台的 HTTPS 访问令牌 */
@Composable
fun AccountsScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    var tokens by remember { mutableStateOf(container.auth.tokens()) }
    var tokenDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.navAccounts,
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
            CoveSectionTitle(S.sectionTokens)
            if (tokens.isEmpty()) {
                Text(
                    S.noTokensSet,
                    style = MaterialTheme.typography.body2,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            tokens.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.host, style = MaterialTheme.typography.subtitle2)
                        Text(
                            t.username,
                            style = MaterialTheme.typography.caption,
                            color = c.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        container.auth.removeToken(t.host)
                        tokens = container.auth.tokens()
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = S.delete, tint = c.error)
                    }
                }
                CoveDivider()
            }
            TextButton(onClick = { tokenDialog = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(S.addToken)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (tokenDialog) {
        var host by remember { mutableStateOf("github.com") }
        var username by remember { mutableStateOf("") }
        var token by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { tokenDialog = false },
            title = { Text(S.addTokenTitle, style = MaterialTheme.typography.subtitle1) },
            text = {
                Column {
                    Text(S.hostLabel, style = MaterialTheme.typography.caption)
                    Row {
                        listOf("github.com", "gitlab.com", "gitea.com").forEach { h ->
                            CoveChip(
                                label = h.substringBefore('.'),
                                selected = host == h,
                                onClick = { host = h },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                    CoveTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = S.customHost,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    CoveTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = S.username,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                    CoveTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = S.tokenLabel,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
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
}
