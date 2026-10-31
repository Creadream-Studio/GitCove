package com.gitcove.app

import com.gitcove.app.data.git.CommitGraph
import com.gitcove.app.domain.model.Commit
import org.junit.Assert.assertEquals
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
        rows.forEach { assertTrue(it.inLanes.isEmpty() && it.passLanes.isEmpty()) }
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
        // x 从旁边泳道弯入 m 的道（outEdge 已验证），m 自身泳道为直线穿入
        assertTrue(rows[2].inLanes.isEmpty())
    }

    @Test
    fun `pass through lanes carry unrelated branch lines`() {
        // 干线 c<-b<-a，侧线 d 基于 a
        val commits = listOf(c("c", "b"), c("d", "a"), c("b", "a"), c("a"))
        val rows = CommitGraph.build(commits)
        assertEquals(listOf(0, 1, 0, 1), rows.map { it.lane })
        // b 行上 d 的线纵向穿过
        assertTrue(rows[2].passLanes.contains(1))
        // a 行：d 的线一直在道 1，a 沿该道直线落到圆点
        assertEquals(1, rows[3].lane)
        assertTrue(rows[3].inLanes.isEmpty())
    }

    @Test
    fun `octopus merge assigns separate lanes to extra parents`() {
        val commits = listOf(c("m", "a", "b", "c2"), c("a"), c("b", "a"), c("c2", "a"))
        val rows = CommitGraph.build(commits)
        assertEquals(3, rows[0].outEdges.size)
        // 三条出边的目标道互不相同
        assertEquals(3, rows[0].outEdges.map { it.second }.distinct().size)
    }
}
