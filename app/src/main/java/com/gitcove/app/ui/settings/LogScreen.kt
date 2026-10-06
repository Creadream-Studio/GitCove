package com.gitcove.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitcove.app.di.AppContainer
import com.gitcove.app.ui.components.EmptyView

/**
 * 操作日志页（功能 79）：查看 / 清空
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    var entries by remember { mutableStateOf(container.opLog.entries()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("操作日志") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        container.opLog.clear()
                        entries = emptyList()
                    }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "清空日志")
                    }
                }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyView("暂无日志", modifier = Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(entries) { entry ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(
                            buildString {
                                append("[${entry.tag}] ")
                                append(entry.message)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}
