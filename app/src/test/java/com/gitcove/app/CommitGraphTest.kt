package com.gitcove.app

import com.gitcove.app.data.git.CommitGraph
import com.gitcove.app.domain.model.Commit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 提交图谱泳道算法测试（纯 JVM，提交列表一律按 git log 惯例：最新在前） */
class CommitGraphTest {

    private fun c(hash: String, vararg parents: String) =
        Commit(hash, hash, "a", "a@x.com", 0L, parents.toList())

    @Test
    fun `linear history keeps single lane`() {
        val rows = CommitGraph.build(listOf(c("c", "b"), c("b", "a"), c("a")))
        assertEquals(listOf(0, 0, 0), rows.map { it.lane })
        // 中间行/末行有主道汇入：inLanes 含自身 lane → 绘制层补齐圆点上方半行
        assertTrue(rows[0].inLanes.isEmpty())
        assertEquals(listOf(0), rows[1].inLanes)
        assertEquals(listOf(0), rows[2].inLanes)
        rows.forEach { assertTrue(it.passLanes.isEmpty()) }
        // 每行一条向下边（末行无父）
        assertEquals(listOf(1, 1, 0), rows.map { it.outEdges.size })
    }

    @Test
    fun `branch forks to a new lane and merges back`() {
        // e 合并 d、b；c 也在 b 上；列表 = log 顺序（最新在前）
        val commits = listOf(
            c("e", "d", "b"),
            c("d", "b"),
            c("c", "b"),
            c("b", "a"),
            c("a")
        )
        val rows = CommitGraph.build(commits)
        assertEquals(0, rows[0].lane)            // e 占泳道 0
        assertEquals(0, rows[1].lane)            // 第一父 d 继承泳道 0
        // e 的两条出边：d 直下（0→0），b 侧移到新道 1
        assertEquals(2, rows[0].outEdges.size)
        assertTrue(rows[0].outEdges.any { it.first == 0 && it.second == 1 })
        // 出生行不得把新父线当穿行线：e 行 passLanes 为空
        // （否则合并行在新泳道凭空多一条贯穿全行的悬空竖线）
        assertTrue("出生行 passLanes 应为空，实际 ${rows[0].passLanes}", rows[0].passLanes.isEmpty())
    }

    @Test
    fun `two branches sharing same head converge into one dot`() {
        // x、y 两个分支头同指 m
        val commits = listOf(c("y", "m"), c("x", "m"), c("m"))
        val rows = CommitGraph.build(commits)
        assertEquals(0, rows[0].lane)
        assertEquals(1, rows[0].outEdges.size)   // y 直下
        assertEquals(1, rows[1].outEdges.size)   // x 从新道汇回 m 的道
        assertEquals(0, rows[1].outEdges[0].second)
        assertEquals(0, rows[2].lane)            // m 落在主干道
        // m 自身泳道为直线穿入：inLanes 含自身 lane（补齐圆点上方半行）
        assertEquals(listOf(0), rows[2].inLanes)
    }

    @Test
    fun `pass through lanes carry unrelated branch lines`() {
        // 干线 c<-b<-a，侧线 d 基于 a
        val commits = listOf(c("c", "b"), c("d", "a"), c("b", "a"), c("a"))
        val rows = CommitGraph.build(commits)
        assertEquals(listOf(0, 1, 0, 1), rows.map { it.lane })
        // b 行上 d 的线纵向穿过
        assertTrue(rows[2].passLanes.contains(1))
        // b 行有主道汇入（c 的线从上直落进圆点）
        assertEquals(listOf(0), rows[2].inLanes)
        // a 行：d 的线一直在道 1，a 沿该道直线落到圆点
        assertEquals(1, rows[3].lane)
        assertEquals(listOf(1), rows[3].inLanes)
    }

    @Test
    fun `octopus merge assigns separate lanes to extra parents`() {
        val commits = listOf(c("m", "a", "b", "c2"), c("a"), c("b", "a"), c("c2", "a"))
        val rows = CommitGraph.build(commits)
        assertEquals(3, rows[0].outEdges.size)
        // 三条出边的目标道互不相同
        assertEquals(3, rows[0].outEdges.map { it.second }.distinct().size)
        // 出生行（章鱼合并）passLanes 为空；后续行 b、c2 各自补齐主道上半行
        assertTrue(rows[0].passLanes.isEmpty())
        assertEquals(listOf(0), rows[1].inLanes)   // a：主道直落
        assertEquals(listOf(1), rows[2].inLanes)   // b：从道 1 直落到圆点
        assertEquals(listOf(2), rows[3].inLanes)   // c2：从道 2 直落到圆点
    }

    /**
     * 回归：一行内「消费入线（道 0）+ 穿行线（道 1）」并存时，
     * 出边汇入已有线（0→1 曲线并入道 1 竖线）。该行：
     *  - 上半行：道 0 直落进圆点（inLanes=[0]），道 1 竖线穿行；
     *  - 下半行：道 0 直落（第一父），道 0→1 曲线汇入道 1；
     * 任一道号都不得被错误地画成与本提交无关的贯穿竖线。
     */
    @Test
    fun `lane number reused within a row is not a pass through`() {
        // m（道 0）出边分两路：第一父 p 继承道 0、第二父 q 侧移道 1；
        // p 行消费道 0 入线，父 q join 已有道 1 线（0→1 出边曲线汇入）；
        // q 行消费道 1 入线。
        val commits = listOf(
            c("m", "p", "q"),   // m：第一父 p → 道 0；第二父 q → 空闲道 1
            c("p", "q"),        // p：消费道 0 入线；父 q 已存在（道 1）→ 出边 0→1
            c("q")              // q：消费道 1 入线
        )
        val rows = CommitGraph.build(commits)
        // 行 m：出生行，passLanes 必须为空（q 的新线只走 outEdges 曲线）
        assertTrue(rows[0].passLanes.isEmpty())
        // 行 p：incoming 道号线若被复用也不产生穿行竖线；
        // p 消费道 0 入线 → inLanes=[0]；q 的线（道 1）在 p 行穿行
        assertEquals(listOf(0), rows[1].inLanes)
        assertTrue(rows[1].passLanes.contains(1))
        // 行 q：消费道 1 入线
        assertEquals(listOf(1), rows[2].inLanes)
        // 全图任意一行：passLanes 不得包含该行 inLanes 或 lane 本身
        rows.forEach { r ->
            r.passLanes.forEach { p -> assertFalse("$p 不应既是穿行又是汇入/主道", p == r.lane || p in r.inLanes) }
        }
    }

    /**
     * 回归：合并行出生的新父线，在出生行只出现于下半行（outEdges），
     * 上一行行底不与任何新泳道竖线相接 —— 即出生行 passLanes 不含新线道号。
     */
    @Test
    fun `merge birth row has no phantom full height lane`() {
        // 经典两分支：e 合并 d、b；d、b 同基 a
        val commits = listOf(
            c("e", "d", "b"),
            c("d", "a"),
            c("b", "a"),
            c("a")
        )
        val rows = CommitGraph.build(commits)
        val birth = rows[0]
        assertEquals(setOf(0, 1), birth.outEdges.map { it.second }.toSet())
        // e 行（出生行）：d 的新线道 0 与 b 的新线道 1 都不得作为穿行竖线
        assertTrue(birth.passLanes.isEmpty())
        // d 行：主道上半行存在（inLanes=[0]），b 的线穿行
        assertEquals(listOf(0), rows[1].inLanes)
        assertTrue(rows[1].passLanes.contains(1))
        // b 行：消费道 1 入线；a 的线（道 0，d 的第一父继承）已在 d 行出生、
        // 于 b 行穿行
        assertEquals(listOf(1), rows[2].inLanes)
        assertTrue(rows[2].passLanes.contains(0))
        // a 行：消费道 0 入线
        assertEquals(listOf(0), rows[3].inLanes)
    }
}
