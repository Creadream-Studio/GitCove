package com.gitcove.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.gitcove.app.di.AppContainer
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveChip
import com.gitcove.app.ui.components.CoveSectionTitle
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.work.SyncWorker

/** 同步（二级页面）：后台自动同步开关与间隔 */
@Composable
fun SyncScreen(
    container: AppContainer,
    nav: androidx.navigation.NavHostController
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val context = LocalContext.current
    var autoSync by remember { mutableStateOf(container.auth.autoSyncEnabled) }
    var interval by remember { mutableStateOf(container.auth.autoSyncIntervalMin) }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.navSync,
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
            CoveSectionTitle(S.sectionAutoSync)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(S.autoSyncDesc, style = MaterialTheme.typography.body2)
                    Text(
                        S.everyNMinutes(interval),
                        style = MaterialTheme.typography.caption,
                        color = c.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoSync,
                    onCheckedChange = { enabled ->
                        autoSync = enabled
                        container.auth.autoSyncEnabled = enabled
                        if (enabled) {
                            SyncWorker.schedule(context, interval)
                            Toast.makeText(context, S.autoSyncOn, Toast.LENGTH_SHORT).show()
                        } else {
                            SyncWorker.cancel(context)
                        }
                    }
                )
            }
            Row(Modifier.padding(horizontal = 12.dp)) {
                listOf(15, 30, 60).forEach { min ->
                    CoveChip(label = "${min}min", selected = interval == min, onClick = {
                        interval = min
                        container.auth.autoSyncIntervalMin = min
                        if (autoSync) SyncWorker.schedule(context, min)
                    }, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
    }
}
