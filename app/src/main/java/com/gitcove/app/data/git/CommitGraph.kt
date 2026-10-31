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
        /**
         * 从上方汇入圆点的泳道（其它线 terminating 于本提交）。
         * [可以包含 lane 本身]：表示主道从行顶边界直落到圆点——绘制层靠它补齐
         * 圆点上方半行（否则主线一节一节断开，与上一行接不上）。
         */
        val inLanes: List<Int>,
        /**
         * 纵向穿过本行、不与提交相接的泳道：只含「进入本行前就已存在、
         * 且不在本行被消费」的线。本行刚出生的父提交新线 [不含在内]——
         * 它们只应通过 outEdges 曲线出现在圆点以下的半行，否则会在出生行
         * 凭空多出一条贯穿全行的竖线（上半段悬空断线）。
         */
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
            // 真正的穿行线：进入本行前就已存在、且不在本行被消费。
            // 必须先于 removeAll / 添加父线快照——否则会把本行刚出生的
            // 父提交新线误当作穿行线（合并行在新泳道凭空多出一条贯穿全行
            // 的竖线，上半段悬空断线）。
            val passThrough = active.filter { it.hash != c.hash }
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

            rows.add(
                GraphRow(
                    commit = c,
                    lane = lane,
                    // 含 lane 自身（若有主道汇入）：驱动绘制层补齐圆点上方半行
                    inLanes = incoming.map { it.lane }.distinct(),
                    passLanes = passThrough.map { it.lane }.filter { it != lane }.distinct(),
                    outEdges = outEdges.distinct()
                )
            )
        }
        return rows
    }
}
