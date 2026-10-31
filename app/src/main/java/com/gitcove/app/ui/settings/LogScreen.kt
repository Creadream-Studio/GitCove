package com.gitcove.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.EmptyView
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.theme.CoveTheme

/**
 * 操作日志页（功能 79）：查看 / 清空
 */
@Composable
fun LogScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val S = LocalStrings.current
    var entries by remember { mutableStateOf(container.opLog.entries()) }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.opLogTitle,
                navigationIcon = { CoveBackButton { nav.popBackStack() } },
                actions = {
                    IconButton(onClick = {
                        container.opLog.clear()
                        entries = emptyList()
                    }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = S.clearLogs)
                    }
                }
            )
        },
        backgroundColor = CoveTheme.colors.background
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyView(S.noLogs, modifier = Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(entries) { entry ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(
                            buildString {
                                append("[${entry.tag}] ")
                                append(entry.message)
                            },
                            style = MaterialTheme.typography.caption,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    CoveDivider()
                }
            }
        }
    }
}
