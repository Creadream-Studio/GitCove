package com.gitcove.app.ui.resolve

/**
 * 词级差异高亮（VS Code 冲突编辑器的核心可读性来源）。
 *
 * 对冲突块的我方/对方两侧做词序列 LCS：不在公共序列里的词 =
 * 该侧真正发生变化的部分，渲染时加深一档背景——两侧整块底色
 * 之上，用户一眼就能看出「到底改了哪几个词」。
 *
 * 纯计算，无 Android 依赖；超出保险上限（任一侧 >600 词或
 * LCS 矩阵 >40 万格）时放弃高亮退回整块展示，绝不拖慢 UI。
 */
object WordDiff {

    private val word = Regex("\\S+")

    /** 每条线的“变化词”字符区间列表（按行分组） */
    data class Result(
        val ours: List<List<IntRange>>,
        val theirs: List<List<IntRange>>
    )

    fun lineHighlights(oursLines: List<String>, theirsLines: List<String>): Result {
        val a = flatTokens(oursLines)
        val b = flatTokens(theirsLines)
        if (a.isEmpty() && b.isEmpty()) return Result(emptyList(), emptyList())
        if (a.size > 600 || b.size > 600) return Result(emptyList(), emptyList())
        if (a.size.toLong() * b.size > 400_000L) return Result(emptyList(), emptyList())

        val n = a.size
        val m = b.size
        val w = m + 1
        // LCS DP（自底向上填表，回溯时未走公共序列的词即“变化”
        val dp = IntArray((n + 1) * (m + 1))
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            dp[i * w + j] = if (a[i] == b[j]) dp[(i + 1) * w + j + 1] + 1
            else maxOf(dp[(i + 1) * w + j], dp[i * w + j + 1])
        }
        val changedA = BooleanArray(n)
        val changedB = BooleanArray(m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { i++; j++ }
                dp[(i + 1) * w + j] >= dp[i * w + j + 1] -> { changedA[i] = true; i++ }
                else -> { changedB[j] = true; j++ }
            }
        }
        while (i < n) { changedA[i] = true; i++ }
        while (j < m) { changedB[j] = true; j++ }
        return Result(mapLines(oursLines, changedA), mapLines(theirsLines, changedB))
    }

    /** 词序列（与行序一致，mapLines 按同序消费） */
    private fun flatTokens(lines: List<String>): List<String> =
        lines.flatMap { l -> word.findAll(l).map { it.value } }

    /** 全局词索引 → 每行的变化词字符区间 */
    private fun mapLines(lines: List<String>, changed: BooleanArray): List<List<IntRange>> {
        var k = 0
        return lines.map { l ->
            val ranges = mutableListOf<IntRange>()
            word.findAll(l).forEach { mt ->
                if (k < changed.size && changed[k]) ranges.add(mt.range)
                k++
            }
            ranges
        }
    }
}
