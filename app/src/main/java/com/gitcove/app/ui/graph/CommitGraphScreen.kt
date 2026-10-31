package com.gitcove.app.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gitcove.app.data.git.CommitGraph
import com.gitcove.app.data.store.GitHubAccount
import com.gitcove.app.di.AppContainer
import com.gitcove.app.domain.model.Commit
import com.gitcove.app.domain.model.CommitStat
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.components.Avatar
import com.gitcove.app.ui.components.CoveBackButton
import com.gitcove.app.ui.components.CoveTopAppBar
import com.gitcove.app.ui.components.EmptyView
import com.gitcove.app.ui.repo.CommitActionsSheet
import com.gitcove.app.ui.repo.CommitDetailSheet
import com.gitcove.app.ui.repo.RepoViewModel
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.MonoFont
import com.gitcove.app.util.fullTime
import com.gitcove.app.util.relativeTime
import com.gitcove.app.util.vmFactory
import kotlinx.coroutines.delay

/**
 * 提交图谱独立页（4.5，从仓库页右上角工具栏进入）：
 * 图谱为核心——左侧泳道连线整页连续贯穿（每行画布顶格铺满行高、行间零间隙，
 * 曲线在行边界坐标精确对接，杜绝断层），每个提交节点右侧挂一张描边卡片框：
 * 引用徽章 + 提交摘要 + 作者头像/姓名 + 增删行统计。
 * 捏合缩放展开细节（完整提交信息 / 作者邮箱 / 完整哈希与时间）；
 * 点卡片 → 提交详情；长按 / 双击 → 操作菜单（复制哈希/信息/邮箱、发邮件、日历、分享补丁）。
 */
private val lanePalette = listOf(
    0xFF2E86DE, 0xFFE67E22, 0xFF27AE60, 0xFF8E44AD,
    0xFFE74C3C, 0xFF16A085, 0xFFF1C40F, 0xFF7F8C8D
)

private fun laneColor(l: Int) = Color(lanePalette[l % lanePalette.size])

@Composable
fun CommitGraphScreen(container: AppContainer, nav: androidx.navigation.NavHostController, repoId: Long) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val vm: RepoViewModel = viewModel(
        key = "repo_graph_$repoId",
        factory = vmFactory { RepoViewModel(container, repoId) }
    )
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
    // 首帧加载：本地 git 读取很快，稍等落地再判空，避免闪现空态
    var checkedEmpty by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (vm.commits.isEmpty() && System.currentTimeMillis() - start < 800) delay(50)
        checkedEmpty = true
    }

    Scaffold(
        topBar = {
            CoveTopAppBar(
                title = S.commitGraph,
                navigationIcon = { CoveBackButton { nav.popBackStack() } }
            )
        },
        backgroundColor = c.background
    ) { padding ->
        if (vm.commits.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (checkedEmpty) EmptyView(S.noCommits, icon = Icons.Filled.AccountTree)
                else CircularProgressIndicator()
            }
            return@Scaffold
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
                .padding(padding)
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
                GraphItem(
                    row = row,
                    laneCount = laneCount,
                    zoom = zoom,
                    expanded = expanded,
                    account = account,
                    vm = vm,
                    onDetail = { detailTarget = row.commit },
                    onActions = { actionTarget = row.commit }
                )
            }
        }
    }

    detailTarget?.let { target ->
        CommitDetailSheet(vm = vm, nav = nav, commit = target, onDismiss = { detailTarget = null })
    }

    actionTarget?.let { commit ->
        CommitActionsSheet(vm = vm, commit = commit, onDismiss = { actionTarget = null })
    }
}

/**
 * 图谱单行：泳道画布 matchParentSize 顶格铺满整行（行高由卡片决定，曲线在上下边界
 * 精确对接相邻行 → 整页连线连续无断层）；卡片框挂在本提交节点旁边，垂直居中。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GraphItem(
    row: CommitGraph.GraphRow,
    laneCount: Int,
    zoom: Float,
    expanded: Boolean,
    account: GitHubAccount?,
    vm: RepoViewModel,
    onDetail: () -> Unit,
    onActions: () -> Unit
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val commit = row.commit
    val laneW = (13 * zoom).dp
    val graphW: Dp = (laneW * laneCount).coerceAtMost(130.dp)

    var stat by remember(commit.hash) { mutableStateOf<CommitStat?>(null) }
    LaunchedEffect(commit.hash) {
        stat = vm.commitStats(commit.hash)
        if (stat == null) stat = CommitStat(0, 0, 0) // 失败不再反复请求
    }

    Box(Modifier.fillMaxWidth()) {
        GraphCanvas(row, laneCount, graphW, Modifier.matchParentSize())

        // 卡片框：上下对称留白 → 卡片中心 = 泳道节点圆点中心
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = graphW + 8.dp, top = 7.dp, bottom = 7.dp, end = 10.dp)
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .border(1.dp, c.outline, RoundedCornerShape(9.dp))
                    .background(c.surface, RoundedCornerShape(9.dp))
                    .combinedClickable(onClick = onDetail, onLongClick = onActions, onDoubleClick = onActions)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                            size = if (expanded) 22.dp else 18.dp,
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
                        val green = Color(0xFF27AE60)
                        val red = Color(0xFFE74C3C)
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
    }
}

/**
 * 泳道画布：宽度固定 graphW、高度铺满整行。行间零间隙 + 曲线端点落在行边界
 * （汇入曲线起点 x=上方出边终点 x；出边曲线终点 x=下方汇入起点 x）→ 跨行连续。
 */
@Composable
private fun GraphCanvas(
    row: CommitGraph.GraphRow,
    laneCount: Int,
    graphW: Dp,
    modifier: Modifier
) {
    val c = CoveTheme.colors
    Canvas(modifier) {
        val w = graphW.toPx()
        val laneW = w / laneCount
        fun lx(l: Int) = (l + 0.5f) * laneW
        val midY = size.height / 2f
        val strokeW = 1.8.dp.toPx()
        val dot = Offset(lx(row.lane), midY)

        // 纵向穿行（与本提交无关的其它线）：整行高度贯穿
        row.passLanes.forEach { l ->
            drawLine(
                laneColor(l), Offset(lx(l), 0f), Offset(lx(l), size.height),
                strokeWidth = strokeW, cap = StrokeCap.Round
            )
        }
        // 上方汇入本提交：同道直落（补齐圆点上方半行，消除主道断口），它道弯道汇入
        row.inLanes.forEach { l ->
            if (l == row.lane) {
                drawLine(
                    laneColor(l), Offset(lx(l), 0f), Offset(lx(l), midY),
                    strokeWidth = strokeW, cap = StrokeCap.Round
                )
            } else {
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
