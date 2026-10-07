package com.gitcove.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gitcove.app.domain.model.Status
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.theme.StatusAddedColor
import com.gitcove.app.ui.theme.StatusConflictColor
import com.gitcove.app.ui.theme.StatusDeletedColor
import com.gitcove.app.ui.theme.StatusModifiedColor
import com.gitcove.app.ui.theme.StatusUntrackedColor

/** 区块标题（紧凑） */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** 文件状态圆点徽章 */
@Composable
fun StatusDot(status: Status, modifier: Modifier = Modifier) {
    val color = when (status) {
        Status.ADDED -> StatusAddedColor
        Status.MODIFIED -> StatusModifiedColor
        Status.DELETED -> StatusDeletedColor
        Status.UNTRACKED -> StatusUntrackedColor
        Status.CONFLICT -> StatusConflictColor
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .background(color, CircleShape)
    )
}

/** 状态字母角标（A/M/D/U/C） */
@Composable
fun StatusLetterBadge(status: Status, modifier: Modifier = Modifier) {
    val color = when (status) {
        Status.ADDED -> StatusAddedColor
        Status.MODIFIED -> StatusModifiedColor
        Status.DELETED -> StatusDeletedColor
        Status.UNTRACKED -> StatusUntrackedColor
        Status.CONFLICT -> StatusConflictColor
    }
    Box(
        modifier = modifier
            .size(18.dp)
            .background(color.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = com.gitcove.app.util.statusLetter(status).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/** 通用确认对话框（取消按钮文案取自当前语言文案库） */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val S = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm() },
                colors = if (danger) androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ) else androidx.compose.material3.ButtonDefaults.textButtonColors()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel) } }
    )
}

/** 空状态占位 */
@Composable
fun EmptyView(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        action?.let { Row(Modifier.padding(top = 12.dp)) { it() } }
    }
}
