package com.gitcove.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gitcove.app.data.store.GitHubAccount
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

/**
 * 圆形作者头像：
 * 1. 已登录 GitHub 账号（邮箱或用户名匹配时直接用其头像）
 * 2. https://github.com/<name>.png（作者名恰为 GitHub 用户名）
 * 3. Gravatar（邮箱 MD5，d=404：无头像时返回 404 进入下一级）
 * 4. 兜底：首字符圆标（颜色由邮箱哈希决定，稳定不闪变）
 */
@Composable
fun Avatar(
    email: String,
    name: String,
    size: Dp,
    account: GitHubAccount? = null,
    modifier: Modifier = Modifier
) {
    val candidates = remember(email, name, account) {
        buildList {
            account?.avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
                val emailHit = !account.email.isNullOrBlank() &&
                    account.email.equals(email.trim(), ignoreCase = true)
                val nameHit = account.login.isNotBlank() &&
                    account.login.equals(name.trim(), ignoreCase = true)
                if (emailHit || nameHit) add(url)
            }
            if (name.isNotBlank()) add("https://github.com/${android.net.Uri.encode(name.trim())}.png")
            if (email.isNotBlank() && email.contains('@')) {
                add("https://www.gravatar.com/avatar/${md5Hex(email.trim().lowercase(Locale.ROOT))}?d=404&s=96")
            }
        }.distinct()
    }

    // 当前生效的候选层级：加载失败自动降级
    var level by remember(candidates) { mutableIntStateOf(0) }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // 兜底首字符圆标：加载中/全部失败时露出
        InitialBadge(name = name, email = email, size = size)
        if (level < candidates.size) {
            AsyncImage(
                model = candidates[level],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                error = null,
                onError = { level++ }
            )
        }
    }
}

/** 首字符圆标：颜色取邮箱哈希 */
@Composable
private fun InitialBadge(name: String, email: String, size: Dp) {
    val seed = if (email.isNotBlank()) email else name
    val color = palette(abs(seed.hashCode()) % palette.size)
    val letter = (name.trim().ifBlank { email.trim() })
        .firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter,
            style = androidx.compose.material.MaterialTheme.typography.caption,
            color = Color.White
        )
    }
}

/** 头像兜底色板（MD2 强调色系） */
private val palette = listOf(
    Color(0xFF0288D1), Color(0xFF00897B), Color(0xFF3949AB), Color(0xFFE53935),
    Color(0xFFFB8C00), Color(0xFF6D4C41), Color(0xFF7B1FA2), Color(0xFF546E7A)
)

private fun palette(i: Int): Color = palette[i % palette.size]

private fun md5Hex(text: String): String =
    MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { String.format("%02x", it) }
