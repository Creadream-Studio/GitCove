package com.gitcove.app.ui.repo.tabs

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gitcove.app.data.git.CommitGraph
import com.gitcove.app.domain.model.Commit
import com.gitcove.app.domain.model.CommitStat
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.Avatar
import com.gitcove.app.ui.components.CoveAlertDialog
import com.gitcove.app.ui.components.CoveBottomSheet
import com.gitcove.app.ui.components.CoveDivider
import com.gitcove.app.ui.components.CoveListRow
import com.gitcove.app.ui.components.CoveTextField
import com.gitcove.app.ui.components.EmptyView
import com.gitcove.app.ui.repo.RepoViewModel
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.MonoFont
import com.gitcove.app.util.fullTime
import com.gitcove.app.util.relativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 历史 Tab（4.5 提交图谱）：左侧 Canvas 泳道连线（分支/合并曲线），右侧
 * 提交摘要 + 分支/标签徽章 + 作者 + 增删行统计；
 * 捏合缩放展开细节（完整提交信息 / 作者邮箱 / 完整时间）；
 * 单击 → 提交详情；长按 / 双击 → 操作菜单（复制哈希/信息/邮箱、发邮件、日历、分享补丁）。
 */
private val lanePalette = listOf(
    0xFF2E86DE, 0xFFE67E22, 0xFF27AE60, 0xFF8E44AD,
    0xFFE74C3C, 0xFF16A085, 0xFFF1C40F, 0xFF7F8C8D
)

private fun laneColor(l: Int) = androidx.compose.ui.graphics.Color(lanePalette[l % lanePalette.size])

@Composable
fun HistoryTab(vm: RepoViewModel, nav: androidx.navigation.NavHostController) {
    val S = LocalStrings.current
    var detailTarget by remember { mutableStateOf<Commit?>(null) }
    var actionTarget by remember { mutableStateOf<Commit?>(null) }
    var zoom by remember { mutableStateOf(1f) }
    val expanded = zoom >= 1.35f
    // GitHub 账号摘要（登录后作者头像可精准命中）
    var account by remember { mutableStateOf(vm.auth.githubAccount) }
    LaunchedEffect(Unit) {
        vm.refreshGithubAccount()
        account = vm.auth.githubAccount
    }

    if (vm.commits.isEmpty()) {
        EmptyView(S.noCommits, icon = Icons.Filled.History)
        return
    }

    val rows = remember(vm.commits) { CommitGraph.build(vm.commits) }
    val laneCount = remember(rows) {
        var m = 0
        rows.forEach { r ->
            m = maxOf(m, r.lane, r.passLanes.maxOrNull() ?: 0, r.outEdges.maxOfOrNull { it.second } ?: 0)
        }
        (m + 1).coerceIn(2, CommitGraph.MAX_LANES + 1)
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // 双指捏合缩放：走 Initial pass 先于列表滚动；单指不消费，滚动不受影响
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            val z = event.calculateZoom()
                            if (z != 1f) {
                                zoom = (zoom * z).coerceIn(1f, 2.2f)
                                event.changes.forEach { it.consume() }
                            }
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
    ) {
        items(rows, key = { it.commit.hash }) { row ->
            GraphRowItem(
                row = row,
                laneCount = laneCount,
                zoom = zoom,
                expanded = expanded,
                account = account,
                vm = vm,
                onDetail = { detailTarget = row.commit },
                onActions = { actionTarget = row.commit }
            )
            CoveDivider(insetStart = 0)
        }
    }

    detailTarget?.let { target ->
        CommitDetailSheet(
            vm = vm,
            commit = target,
            onDismiss = { detailTarget = null }
        )
    }

    actionTarget?.let { commit ->
        CommitActionsSheet(
            vm = vm,
            commit = commit,
            onDismiss = { actionTarget = null }
        )
    }
}

/** 图谱单行 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GraphRowItem(
    row: CommitGraph.GraphRow,
    laneCount: Int,
    zoom: Float,
    expanded: Boolean,
    account: com.gitcove.app.data.store.GitHubAccount?,
    vm: RepoViewModel,
    onDetail: () -> Unit,
    onActions: () -> Unit
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val commit = row.commit
    val rowHeight by animateDpAsState(if (expanded) 100.dp else 62.dp)
    val laneW = (14 * zoom).dp
    val canvasWidth: Dp = (laneW * laneCount).coerceAtMost(120.dp)

    var stat by remember(commit.hash) { mutableStateOf<CommitStat?>(null) }
    LaunchedEffect(commit.hash) {
        stat = vm.commitStats(commit.hash)
        if (stat == null) stat = CommitStat(0, 0, 0) // 失败不再反复请求
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .combinedClickable(onClick = onDetail, onLongClick = onActions, onDoubleClick = onActions)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GraphCanvas(row, laneCount, canvasWidth, Modifier.width(canvasWidth).fillMaxHeight())

        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            // 分支/标签徽章行
            if (commit.refs.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    commit.refs.forEach { ref ->
                        RefChip(ref)
                        Spacer(Modifier.width(4.dp))
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
            Text(
                commit.message,
                style = MaterialTheme.typography.subtitle2,
                maxLines = if (expanded) 3 else 1,
                overflow = TextOverflow.Ellipsis
            )
            if (expanded) {
                commit.fullMessage.substringAfter('\n').trim().takeIf { it.isNotBlank() }?.let { body ->
                    Text(
                        body,
                        style = MaterialTheme.typography.caption,
                        color = c.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(
                    email = commit.email,
                    name = commit.author,
                    size = if (expanded) 24.dp else 18.dp,
                    account = account
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (expanded) "${commit.author} <${commit.email}>" else commit.author,
                    style = MaterialTheme.typography.overline,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (expanded) fullTime(commit.date) else relativeTime(commit.date, S),
                    style = MaterialTheme.typography.overline,
                    color = c.onSurfaceVariant,
                    maxLines = 1
                )
            }
            stat?.let { s ->
                val green = androidx.compose.ui.graphics.Color(0xFF27AE60)
                val red = androidx.compose.ui.graphics.Color(0xFFE74C3C)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = c.onSurfaceVariant)) { append("${s.files} · ") }
                        withStyle(SpanStyle(color = green)) { append("+${s.insertions}") }
                        withStyle(SpanStyle(color = red)) { append(" -${s.deletions}") }
                    },
                    style = MaterialTheme.typography.overline,
                    fontFamily = MonoFont,
                    maxLines = 1,
                    modifier = Modifier.semantics {
                        contentDescription = S.statSummary(s.files, s.insertions, s.deletions)
                    }
                )
            }
        }
    }
}

/** 泳道画布：穿行 / 汇入弯道 / 出边弯道 / 提交圆点 */
@Composable
private fun GraphCanvas(
    row: CommitGraph.GraphRow,
    laneCount: Int,
    canvasWidth: Dp,
    modifier: Modifier
) {
    val c = CoveTheme.colors
    Canvas(modifier) {
        val laneW = size.width / laneCount
        fun lx(l: Int) = (l + 0.5f) * laneW
        val midY = size.height / 2f
        val strokeW = 1.8.dp.toPx()
        val dot = Offset(lx(row.lane), midY)

        // 纵向穿行（与本提交无关的其它线）
        row.passLanes.forEach { l ->
            drawLine(
                laneColor(l), Offset(lx(l), 0f), Offset(lx(l), size.height),
                strokeWidth = strokeW, cap = StrokeCap.Round
            )
        }
        // 上方汇入本提交的弯道
        row.inLanes.forEach { l ->
            if (l != row.lane) {
                val p = Path().apply {
                    moveTo(lx(l), 0f)
                    cubicTo(lx(l), midY * 0.55f, lx(row.lane), midY * 0.45f, lx(row.lane), midY)
                }
                drawPath(p, laneColor(l), style = Stroke(strokeW, cap = StrokeCap.Round))
            }
        }
        // 自本提交向下的出边（第一父直下，其余侧移 / 汇入已有线）
        row.outEdges.forEach { (from, to) ->
            if (to == from) {
                drawLine(
                    laneColor(to), Offset(lx(from), midY), Offset(lx(from), size.height),
                    strokeWidth = strokeW, cap = StrokeCap.Round
                )
            } else {
                val p = Path().apply {
                    moveTo(lx(from), midY)
                    cubicTo(lx(from), size.height * 0.78f, lx(to), size.height * 0.78f, lx(to), size.height)
                }
                drawPath(p, laneColor(to), style = Stroke(strokeW, cap = StrokeCap.Round))
            }
        }
        // 圆点：先垫背景色圈隔开穿线，有引用时加外环
        drawCircle(c.background, radius = 5.dp.toPx(), center = dot)
        if (row.commit.refs.isNotEmpty()) {
            drawCircle(
                laneColor(row.lane), radius = 7.5f.dp.toPx(), center = dot,
                style = Stroke(1.6.dp.toPx())
            )
        }
        drawCircle(laneColor(row.lane), radius = 3.6f.dp.toPx(), center = dot)
    }
}

@Composable
private fun RefChip(ref: com.gitcove.app.domain.model.CommitRef) {
    val c = CoveTheme.colors
    val bg = when {
        ref.isHead -> c.primary
        ref.isTag -> c.secondary.copy(alpha = 0.18f)
        else -> c.primary.copy(alpha = 0.14f)
    }
    val fg = if (ref.isHead) c.surface else if (ref.isTag) c.secondary else c.primary
    Box(
        Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            if (ref.isHead && !ref.isTag) "HEAD · ${ref.name}" else ref.name,
            style = MaterialTheme.typography.overline,
            fontFamily = MonoFont,
            color = fg,
            maxLines = 1
        )
    }
}

/** 长按 / 双击操作菜单（4.5：复制、邮件、日历、分享补丁） */
@Composable
private fun CommitActionsSheet(vm: RepoViewModel, commit: Commit, onDismiss: () -> Unit) {
    val S = LocalStrings.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        vm.toast(S.copiedToClipboard)
        onDismiss()
    }

    CoveBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 8.dp)) {
            Text(
                commit.message,
                style = MaterialTheme.typography.subtitle2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
            CoveListRow(S.copyHash, onClick = { copy(commit.hash) })
            CoveListRow(
                S.copyMessage,
                onClick = { copy(commit.fullMessage.ifBlank { commit.message }) }
            )
            CoveListRow(S.copyEmail, subtitle = commit.email, onClick = { copy(commit.email) })
            CoveListRow(S.emailAuthor, onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:${commit.email}") }
                runCatching { context.startActivity(intent) }
                    .onFailure { vm.toast(S.noMailApp) }
                onDismiss()
            })
            CoveListRow(S.viewInCalendar, subtitle = fullTime(commit.date), onClick = {
                val uri = CalendarContract.CONTENT_URI
                    .buildUpon().appendPath("time").appendPath(commit.date.toString()).build()
                val intent = Intent(Intent.ACTION_VIEW).apply { data = uri }
                runCatching { context.startActivity(intent) }
                    .onFailure { vm.toast(S.noCalendarApp) }
                onDismiss()
            })
            CoveListRow(S.sharePatch, onClick = {
                scope.launch {
                    val patch = vm.commitPatch(commit.hash)
                    if (patch.isNullOrBlank()) {
                        vm.toast(S.noPatch)
                    } else {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, commit.message)
                            putExtra(Intent.EXTRA_TEXT, patch)
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, S.sharePatch)) }
                            .onFailure { vm.toast(S.noPatch) }
                    }
                    onDismiss()
                }
            })
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 提交详情底部抽屉（保留原有功能：变更文件 / Revert / 签出 / 建分支） */
@Composable
private fun CommitDetailSheet(vm: RepoViewModel, commit: Commit, onDismiss: () -> Unit) {
    val S = LocalStrings.current
    var branchName by remember { mutableStateOf("") }
    var confirmRevert by remember { mutableStateOf(false) }
    var confirmCheckout by remember { mutableStateOf(false) }

    // 加载变更文件
    androidx.compose.runtime.LaunchedEffect(commit.hash) { vm.loadCommitDetail(commit) }

    CoveBottomSheet(onDismiss = { vm.clearCommitDetail(); onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // 超长提交信息限行：抽屉不整体滚动，无限高的文本会把按钮顶出屏幕
            Text(
                commit.message,
                style = MaterialTheme.typography.subtitle1,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${commit.shortHash} · ${commit.author} <${commit.email}>",
                style = MaterialTheme.typography.overline,
                fontFamily = MonoFont,
                color = CoveTheme.colors.primary
            )
            Text(
                fullTime(commit.date),
                style = MaterialTheme.typography.overline,
                color = CoveTheme.colors.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            CoveDivider()
            Text(
                S.changedFiles(vm.lastCommitDetail?.second?.size ?: 0),
                style = MaterialTheme.typography.button,
                color = CoveTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            val files = vm.lastCommitDetail?.second.orEmpty()
            LazyColumn(Modifier.height(if (files.isEmpty()) 40.dp else 180.dp)) {
                items(files) { f ->
                    Text(
                        "[${f.changeType.take(1)}] ${f.path}",
                        style = MaterialTheme.typography.caption,
                        fontFamily = MonoFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            CoveDivider()
            Row(Modifier.padding(vertical = 8.dp)) {
                TextButton2(S.revertBtn) { confirmRevert = true }
                TextButton2(S.checkoutVersion) { confirmCheckout = true }
            }
            Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CoveTextField(
                    value = branchName,
                    onValueChange = { branchName = it },
                    label = S.branchOnCommit,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton2(S.create, enabled = branchName.isNotBlank()) {
                    vm.createBranch(branchName, commit.hash)
                    onDismiss()
                }
            }
        }
    }

    if (confirmRevert) {
        CoveAlertDialog(
            onDismiss = { confirmRevert = false },
            title = S.revertTitle,
            content = { Text(S.revertConfirm(commit.shortHash), style = MaterialTheme.typography.body2) },
            confirmLabel = S.revertAction,
            onConfirm = { confirmRevert = false; vm.revert(commit.hash); onDismiss() },
            dismissLabel = S.cancel
        )
    }
    if (confirmCheckout) {
        CoveAlertDialog(
            onDismiss = { confirmCheckout = false },
            title = S.checkoutOldTitle,
            content = { Text(S.checkoutConfirmText, style = MaterialTheme.typography.body2) },
            confirmLabel = S.checkout,
            onConfirm = { confirmCheckout = false; vm.checkoutDetached(commit.hash); onDismiss() },
            dismissLabel = S.cancel
        )
    }
}

@Composable
private fun TextButton2(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    androidx.compose.material.TextButton(onClick = onClick, enabled = enabled) { Text(text) }
}
