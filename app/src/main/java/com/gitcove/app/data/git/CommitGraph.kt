package com.gitcove.app.data.git

import com.gitcove.app.domain.model.Commit

/**
 * 提交图谱泳道算法（Working Copy 4.5 Commit Graph 的数据层）。
 *
 * 经典 git log --graph 布局：
 * - 每行一个提交，按时间倒序；
 * - 每个提交占一条泳道（lane），父子关系用泳道间的连线（含弯道）表达；
 * - 第一父提交继承当前泳道，其余父提交分配最右侧空闲泳道；
 * - 多条线汇入同一提交（合并 / 分支同头）时，提交落在其中最左侧泳道。
 *
 * 纯 Kotlin 实现，无 Android 依赖，可用 JVM 单元测试验证。
 */
object CommitGraph {

    /** 一行提交的绘制信息 */
    data class GraphRow(
        val commit: Commit,
        /** 提交圆点所在泳道（0 起，从左往右） */
        val lane: Int,
        /** 从上方汇入圆点的泳道（其它线 terminating 于本提交） */
        val inLanes: List<Int>,
        /** 纵向穿过本行、不与提交相接的泳道 */
        val passLanes: List<Int>,
        /** 从圆点向下伸出的边：(fromLane=圆点泳道, toLane=下方泳道) */
        val outEdges: List<Pair<Int, Int>>
    )

    private data class Active(val hash: String, val lane: Int)

    /** 图谱最大泳道数（防御异常仓库，超出后折叠到最右） */
    const val MAX_LANES = 12

    fun build(commits: List<Commit>): List<GraphRow> {
        val active = mutableListOf<Active>()
        val rows = mutableListOf<GraphRow>()

        fun freeLane(exclude: Int = -1): Int {
            val used = active.map { it.lane }.toMutableSet()
            for (l in 0 until MAX_LANES) {
                if (l !in used && l != exclude) return l
            }
            return MAX_LANES - 1
        }

        for (c in commits) {
            // 1. 汇入本提交的线（可能多条：合并提交 / 两分支同头）
            val incoming = active.filter { it.hash == c.hash }
            val lane = incoming.minOfOrNull { it.lane } ?: freeLane()
            active.removeAll(incoming)

            // 2. 处理父提交：第一父继承泳道，其余取空闲泳道；已存在的线则汇入
            val outEdges = mutableListOf<Pair<Int, Int>>()
            c.parents.forEachIndexed { idx, p ->
                val existing = active.filter { it.hash == p }
                val target = when {
                    existing.isNotEmpty() -> existing.minOf { it.lane }
                    idx == 0 -> {
                        active.add(Active(p, lane)); lane
                    }
                    else -> {
                        val l = freeLane(exclude = lane)
                        active.add(Active(p, l)); l
                    }
                }
                outEdges.add(lane to target)
            }

            val passLanes = active.map { it.lane }.filter { it != lane }.distinct()
            rows.add(
                GraphRow(
                    commit = c,
                    lane = lane,
                    inLanes = incoming.map { it.lane }.filter { it != lane }.distinct(),
                    passLanes = passLanes,
                    outEdges = outEdges.distinct()
                )
            )
        }
        return rows
    }
}
