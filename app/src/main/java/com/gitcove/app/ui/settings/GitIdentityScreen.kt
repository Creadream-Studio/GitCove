package com.gitcove.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.theme.CoveTheme

/** Git 身份（二级页面）：提交时使用的用户名与邮箱 */
@Composable
fun GitIdentityScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    var gitName by remember { mutableStateOf(container.auth.gitName) }
    var gitEmail by remember { mutableStateOf(container.auth.gitEmail) }

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
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                com.gitcove.app.ui.components.CoveTextField(
                    value = gitName,
                    onValueChange = { gitName = it },
                    label = S.username,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                com.gitcove.app.ui.components.CoveTextField(
                    value = gitEmail,
                    onValueChange = { gitEmail = it },
                    label = S.email,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = {
                    container.auth.gitName = gitName.trim()
                    container.auth.gitEmail = gitEmail.trim()
                }) { Text(S.saveIdentity) }
                Text(
                    S.identitySaved,
                    style = MaterialTheme.typography.caption,
                    color = c.onSurfaceVariant
                )
            }
        }
    }
}
