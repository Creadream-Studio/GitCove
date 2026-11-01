package com.gitcove.app.data.git

import org.eclipse.jgit.diff.HistogramDiff
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator

/**
 * diff3 三方合并引擎（Working Copy 4.6 Resolve 工具的核心）。
 *
 * 以 base（共同祖先）为坐标，把 ours / theirs 两侧的编辑区间归组：
 * - 组内只有一侧编辑 → 单侧改动（OURS / THEIRS，可自动采用也可反悔）
 * - 两侧改动结果相同 → 双方一致（STABLE）
 * - 两侧改动重叠且不同 → 冲突（CONFLICT，需用户决定）
 *
 * 结果按 base 顺序输出，稳定区（双方未动）夹在其中作为上下文。
 * 纯函数，无 Android 依赖。
 */
object Diff3 {

    enum class Kind { STABLE, OURS, THEIRS, CONFLICT }

    /** 一个合并块：STABLE 时 ours==theirs==稳定内容；冲突时 ours/theirs 为两侧方案 */
    data class Chunk(
        val kind: Kind,
        val ours: List<String>,
        val theirs: List<String>
    ) {
        val lines: List<String> get() = ours
    }

    private data class Edit(val begin: Int, val end: Int, val repl: List<String>, val sideOurs: Boolean)

    fun merge(base: List<String>, ours: List<String>, theirs: List<String>): List<Chunk> {
        val all = (edits(base, ours, sideOurs = true) + edits(base, theirs, sideOurs = false))
            .sortedBy { it.begin }

        val chunks = mutableListOf<Chunk>()
        var i = 0
        var p = 0 // 已消费的 base 行游标

        while (i < all.size) {
            // 归组：与本组区间重叠或相邻（无上下文行隔开）的编辑并入同一组——
            // 与 git xdiff/xmerge.c 一致：相邻改动视为同处冲突；插入点重合亦冲突
            var hi = all[i].end
            val group = mutableListOf(all[i])
            var j = i + 1
            while (j < all.size && all[j].begin <= hi) {
                if (all[j].end > hi) hi = all[j].end
                group.add(all[j])
                j++
            }
            val lo = group.minOf { it.begin }

            if (lo > p) {
                val stable = base.subList(p, lo)
                chunks.add(Chunk(Kind.STABLE, stable, stable))
            }

            val ourRepl = applyGroup(group.filter { it.sideOurs }, lo, hi, base)
            val theirRepl = applyGroup(group.filter { !it.sideOurs }, lo, hi, base)
            val baseSlice = base.subList(lo, hi)
            val kind = when {
                ourRepl == theirRepl -> Kind.STABLE      // 双方改成一样
                ourRepl == baseSlice -> Kind.THEIRS      // 只有对方改了
                theirRepl == baseSlice -> Kind.OURS      // 只有我方改了
                else -> Kind.CONFLICT
            }
            chunks.add(Chunk(kind, ourRepl, theirRepl))
            p = hi
            i = j
        }
        if (p < base.size) {
            val stable = base.drop(p)
            chunks.add(Chunk(Kind.STABLE, stable, stable))
        }
        return chunks
    }

    /**
     * 按决策拼出最终文件内容。
     * 单侧改动块（OURS/THEIRS，双方只有一侧编辑）：git 语义为无冲突自动合并，
     * 默认采用改动的一侧；有显式决定（旧界面预设）则尊重之。
     * CONFLICT 块未决（null）输出空——由 UI 层守卫，不应走到这里。
     */
    fun assemble(chunks: List<Chunk>, decisions: Map<Int, Decision>): String {
        val out = StringBuilder()
        chunks.forEachIndexed { idx, chunk ->
            val lines: List<String> = when (chunk.kind) {
                Kind.STABLE -> chunk.ours
                Kind.OURS -> when (decisions[idx]) {
                    Decision.THEIRS -> chunk.theirs
                    Decision.BOTH -> chunk.ours + chunk.theirs
                    Decision.NONE -> emptyList()
                    else -> chunk.ours
                }
                Kind.THEIRS -> when (decisions[idx]) {
                    Decision.OURS -> chunk.ours
                    Decision.BOTH -> chunk.ours + chunk.theirs
                    Decision.NONE -> emptyList()
                    else -> chunk.theirs
                }
                Kind.CONFLICT -> when (decisions[idx]) {
                    Decision.OURS -> chunk.ours
                    Decision.THEIRS -> chunk.theirs
                    Decision.BOTH -> chunk.ours + chunk.theirs
                    Decision.NONE, null -> emptyList()
                }
            }
            lines.forEach { l -> out.append(l).append('\n') }
        }
        return out.toString()
    }

    enum class Decision { OURS, THEIRS, BOTH, NONE }

    // ── 内部 ──

    private fun edits(a: List<String>, b: List<String>, sideOurs: Boolean): List<Edit> {
        if (a == b) return emptyList()
        val diff = HistogramDiff().diff(
            RawTextComparator.DEFAULT,
            RawText(a.joinToString("\n").toByteArray()),
            RawText(b.joinToString("\n").toByteArray())
        ) ?: return emptyList()
        return diff.map { e ->
            Edit(e.beginA, e.endA, b.subList(e.beginB, e.endB), sideOurs)
        }
    }

    private fun applyGroup(edits: List<Edit>, lo: Int, hi: Int, base: List<String>): List<String> {
        if (edits.isEmpty()) return base.subList(lo, hi)
        val out = mutableListOf<String>()
        var p = lo
        for (e in edits.sortedBy { it.begin }) {
            if (e.begin > p) out += base.subList(p, e.begin)
            out += e.repl
            if (e.end > p) p = e.end
        }
        if (p < hi) out += base.subList(p, hi)
        return out
    }
}
