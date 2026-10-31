package com.gitcove.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gitcove.app.domain.model.Status
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.StatusAddedColor
import com.gitcove.app.ui.theme.StatusConflictColor
import com.gitcove.app.ui.theme.StatusDeletedColor
import com.gitcove.app.ui.theme.StatusModifiedColor
import com.gitcove.app.ui.theme.StatusUntrackedColor

/** 区块标题（MD2 分组条） */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    CoveSectionTitle(text = text, modifier = modifier)
}

/**
 * 文件状态圆点标记（显示在文件/文件夹名称后）：
 * 新增（含未跟踪）绿 ⬤、修改黄 ⬤、删除红 ⬤、冲突品红 ⬤
 */
@Composable
fun StatusDot(status: Status, modifier: Modifier = Modifier) {
    val color = when (status) {
        Status.ADDED, Status.UNTRACKED -> StatusAddedColor
        Status.MODIFIED -> StatusModifiedColor
        Status.DELETED -> StatusDeletedColor
        Status.CONFLICT -> StatusConflictColor
    }
    Box(
        modifier = modifier
            .size(7.dp)
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
            .size(16.dp)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = com.gitcove.app.util.statusLetter(status).toString(),
            style = MaterialTheme.typography.overline,
            color = color
        )
    }
}

/** 通用确认对话框（MD2 AlertDialog：4dp 圆角、24dp 投影、surface 底） */
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
    val c = CoveTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(4.dp),
        backgroundColor = c.surface,
        contentColor = c.onSurface,
        title = { Text(title, style = MaterialTheme.typography.subtitle1) },
        text = { Text(text, style = MaterialTheme.typography.body2) },
        confirmButton = {
            TextButton(onClick = { onConfirm() }) {
                Text(
                    confirmLabel,
                    style = MaterialTheme.typography.button,
                    color = if (danger) c.error else c.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(S.cancel, style = MaterialTheme.typography.button, color = c.onSurfaceVariant)
            }
        }
    )
}

/** 空状态占位 */
@Composable
fun EmptyView(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    CoveEmptyState(text = text, modifier = modifier, action = action)
}
