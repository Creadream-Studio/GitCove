package com.gitcove.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本更新检查器单元测试（核心判定逻辑，纯 JVM，无需网络/Android SDK）：
 * - 只有 **正式** Release（排除 Prerelease/Draft）才会生成 [UpdateState.Available]
 * - 版本比较正确：v0.5.2 与 0.5.2 不提示，v0.6.0 提示
 */
class UpdateCheckerTest {

    private fun r(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        publishedAt: String = "2026-11-12T10:00:00Z",
        body: String? = null
    ) = GitHubRelease(tag, prerelease, draft, publishedAt, "http://x/$tag", body)

    @Test
    fun `prerelease release never promotes update`() {
        assertTrue(stableReleases(listOf(r("v0.5.0", prerelease = true))).isEmpty())
    }

    @Test
    fun `draft release never promotes update`() {
        assertTrue(stableReleases(listOf(r("v0.5.0", draft = true))).isEmpty())
    }

    @Test
    fun `only formal releases are returned`() {
        val list = listOf(
            r("v0.5.0"),
            r("v0.6.0", prerelease = true),
            r("v0.6.1", draft = true),
            r("v0.7.0")
        )
        assertEquals(listOf("v0.5.0", "v0.7.0"), stableReleases(list).map { it.tagName })
    }

    @Test
    fun `empty release list is no update`() {
        assertTrue(stableReleases(emptyList()).isEmpty())
    }

    @Test
    fun `formal release newer than current version triggers prompt`() {
        val list = listOf(
            r("v0.5.2", publishedAt = "2026-11-06T10:00:00Z", body = "current"),
            r("v0.6.0", publishedAt = "2026-11-12T10:00:00Z", body = "new")
        )
        val stable = stableReleases(list)
        assertEquals(2, stable.size)
        // 最新正式版：时间最晚 + 版本排序最高
        assertEquals("v0.6.0", stable.maxByOrNull { it.publishedAt ?: 0L }!!.tagName)
        assertTrue(isVersion("v0.6.0", "0.5.2"))
        assertFalse(isVersion("v0.6.0", "0.6.1"))
        assertFalse(isVersion("v0.6.0", "v0.6.0"))
    }

    @Test
    fun `formal release same as current version does not trigger prompt`() {
        val list = listOf(r("v0.6.0", body = "same"))
        assertFalse(isVersion("v0.6.0", list[0].tag_name))
        assertFalse(isVersion(list[0].tag_name, "v0.6.0"))
    }

    @Test
    fun `version parsing handles v prefix prerelease suffix and padding`() {
        assertEquals(listOf(0L, 5L, 2L), parseVersion("v0.5.2"))
        assertEquals(listOf(0L, 5L, 2L), parseVersion("0.5.2-alpha"))
        assertEquals(listOf(0L, 5L, 20L), parseVersion("0.5.20"))
    }

    @Test
    fun `version comparison compares segments`() {
        assertTrue(compareVersions("0.6.0", "0.5.2") > 0)
        assertTrue(compareVersions("0.5.3", "0.5.2") > 0)
        assertTrue(compareVersions("1.0.0", "0.5.2") > 0)
        assertTrue(compareVersions("0.5.2", "0.5.2") == 0)
        assertTrue(compareVersions("0.4.0", "0.5.2") < 0)
    }
}
