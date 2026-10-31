package com.gitcove.app

import com.gitcove.app.data.git.Diff3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** diff3 三方合并引擎测试（纯 JVM） */
class Diff3Test {

    private fun lines(vararg s: String) = s.toList()

    @Test
    fun `no changes yields single stable chunk`() {
        val base = lines("a", "b", "c")
        val chunks = Diff3.merge(base, base, base)
        assertEquals(1, chunks.size)
        assertEquals(Diff3.Kind.STABLE, chunks[0].kind)
    }

    @Test
    fun `ours only change is auto-ours`() {
        val base = lines("a", "b", "c")
        val ours = lines("a", "B2", "c")
        val chunks = Diff3.merge(base, ours, base)
        assertEquals(listOf(Diff3.Kind.STABLE, Diff3.Kind.OURS, Diff3.Kind.STABLE), chunks.map { it.kind })
        assertEquals(listOf("B2"), chunks[1].ours)
    }

    @Test
    fun `theirs only change is auto-theirs`() {
        val base = lines("a", "b", "c")
        val theirs = lines("a", "b2", "c")
        val chunks = Diff3.merge(base, base, theirs)
        assertEquals(Diff3.Kind.THEIRS, chunks[1].kind)
        assertEquals(listOf("b2"), chunks[1].theirs)
    }

    @Test
    fun `overlapping different edits conflict`() {
        val base = lines("a", "b", "c")
        val ours = lines("a", "OURS", "c")
        val theirs = lines("a", "THEIRS", "c")
        val chunks = Diff3.merge(base, ours, theirs)
        val conflict = chunks.filter { it.kind == Diff3.Kind.CONFLICT }
        assertEquals(1, conflict.size)
        assertEquals(listOf("OURS"), conflict[0].ours)
        assertEquals(listOf("THEIRS"), conflict[0].theirs)
    }

    @Test
    fun `same edit on both sides is stable`() {
        val base = lines("a", "b", "c")
        val both = lines("a", "SAME", "c")
        val chunks = Diff3.merge(base, both, both)
        // 稳定区 a / 同改块 / 稳定区 c，三块全为 STABLE
        assertTrue(chunks.all { it.kind == Diff3.Kind.STABLE })
        assertEquals(listOf("SAME"), chunks[1].ours)
    }

    @Test
    fun `non overlapping edits stay separate`() {
        val base = lines("a", "b", "c", "d", "e")
        val ours = lines("A2", "b", "c", "d", "e")     // 改首行
        val theirs = lines("a", "b", "c", "d", "E2")   // 改末行
        val chunks = Diff3.merge(base, ours, theirs)
        assertEquals(listOf(Diff3.Kind.OURS, Diff3.Kind.STABLE, Diff3.Kind.THEIRS), chunks.map { it.kind })
    }

    @Test
    fun `assemble with decisions produces merged file`() {
        val base = lines("a", "b", "c", "d")
        val ours = lines("A2", "b", "OURS", "d")
        val theirs = lines("a", "b", "THEIRS", "D2")
        val chunks = Diff3.merge(base, ours, theirs)
        // 相邻改动按 git 语义归入同一冲突组：[OURS(首行), STABLE(b), CONFLICT(中段+末行)]
        assertEquals(listOf(Diff3.Kind.OURS, Diff3.Kind.STABLE, Diff3.Kind.CONFLICT), chunks.map { it.kind })
        val decisions = mapOf(0 to Diff3.Decision.OURS, 2 to Diff3.Decision.BOTH)
        val merged = Diff3.assemble(chunks, decisions)
        assertEquals("A2\nb\nOURS\nd\nTHEIRS\nD2\n", merged)
    }

    @Test
    fun `conflict appended lines from both sides`() {
        // 双方在同一位置各自追加不同行
        val base = lines("head", "tail")
        val ours = lines("head", "ours-line", "tail")
        val theirs = lines("head", "theirs-line", "tail")
        val chunks = Diff3.merge(base, ours, theirs)
        assertTrue(chunks.any { it.kind == Diff3.Kind.CONFLICT })
        val conflict = chunks.first { it.kind == Diff3.Kind.CONFLICT }
        assertEquals(listOf("ours-line"), conflict.ours)
        assertEquals(listOf("theirs-line"), conflict.theirs)
    }
}
