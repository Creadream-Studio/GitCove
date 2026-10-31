package com.gitcove.app.ui.theme

import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Material Design 2（MD2）主题内核。
 *
 * 设计取向对齐 MP 管理器（AbdurazaaqMohammed/MP-Manager）那类工具型 MD2 应用：
 * - 深色默认：深蓝黑底 + 蓝灰抬升面 + 天蓝强调色
 * - 密集列表、分组标题、细分隔线
 * - 4dp 小圆角、2dp 卡片投影（靠阴影而非浅色填充表达层级）
 * - MD2 字体标尺（h6 / subtitle1 / body2 / caption / overline）
 */

/** MD2 强调色方案（设置页可选；MD2 没有 Material You 动态取色，改为显式强调色） */
enum class Md2Accent(
    val value: Int,
    val darkPrimary: Color,
    val lightPrimary: Color
) {
    /** 天蓝：默认，贴近 MP 管理器的主色 */
    BLUE(0, Color(0xFF76D1FF), Color(0xFF14678F)),
    /** 湾青：GitCove 品牌色 */
    TEAL(1, Color(0xFF4DD8C0), Color(0xFF00695C)),
    /** 靛紫：MD2 经典主色 */
    INDIGO(2, Color(0xFFB69CFF), Color(0xFF4F3BA8)),
    /** 珊瑚：暖色强调 */
    CORAL(3, Color(0xFFFF9E8A), Color(0xFFB23B27)),
    /** 琥珀 */
    AMBER(4, Color(0xFFF2C46B), Color(0xFF7A5400));

    companion object {
        fun from(value: Int) = entries.firstOrNull { it.value == value } ?: BLUE
    }
}

/**
 * GitCove 的颜色令牌：MD2 官方 12 色 + 迁移期需要的扩展令牌
 * （surfaceVariant / outline / container 系列在 MD2 里没有，用近似语义补齐）。
 */
@Immutable
data class CovePalette(
    val primary: Color,
    val primaryVariant: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryVariant: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    /** 顶部应用栏背景/前景（浅色主题走主色，深色主题走抬升面，均符合 MD2 规范） */
    val appBar: Color,
    val onAppBar: Color,
    val isDark: Boolean
) {
    /** 转换到 MD2 MaterialTheme.colors */
    fun toMaterialColors() = if (isDark) {
        darkColors(
            primary = primary,
            primaryVariant = primaryVariant,
            secondary = secondary,
            secondaryVariant = secondaryVariant,
            background = background,
            surface = surface,
            error = error,
            onPrimary = onPrimary,
            onSecondary = onSecondary,
            onBackground = onBackground,
            onSurface = onSurface,
            onError = onError
        )
    } else {
        lightColors(
            primary = primary,
            primaryVariant = primaryVariant,
            secondary = secondary,
            secondaryVariant = secondaryVariant,
            background = background,
            surface = surface,
            error = error,
            onPrimary = onPrimary,
            onSecondary = onSecondary,
            onBackground = onBackground,
            onSurface = onSurface,
            onError = onError
        )
    }
}

/**
 * MD2 抬升色：深色主题下按规范用白色叠加表达高度（Surface elevation 覆盖）。
 * 状态栏要与 4dp 抬升后的应用栏同色，才不会出现色带断层。
 */
fun CovePalette.appBarElevatedColor(elevationDp: Float = 4f): Color {
    if (!isDark) return appBar
    // 与 Compose MD2 的 DefaultElevationOverlay 完全一致的算法：
    // alpha = (4.5 * ln(elevation + 1) + 2) / 100，叠加色为 contentColorFor(background)
    val alpha = ((4.5f * kotlin.math.ln(elevationDp + 1f)) + 2f) / 100f
    val base = appBar
    val tint = onAppBar
    fun blend(from: Float, to: Float): Float = ((from + (to - from) * alpha) * 255f).roundToInt() / 255f
    return Color(red = blend(base.red, tint.red), green = blend(base.green, tint.green), blue = blend(base.blue, tint.blue), alpha = 1f)
}

/** 取色：深色主题用亮色强调，浅色主题用深色强调 */
private fun accentOn(primary: Color): Color =
    if (primary.luminance() > 0.5f) primary.darken(0.86f) else Color.White

fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

private fun Color.darken(factor: Float): Color =
    Color(red * factor, green * factor, blue * factor, alpha)

private fun Color.mix(other: Color, ratio: Float): Color =
    Color(
        red + (other.red - red) * ratio,
        green + (other.green - green) * ratio,
        blue + (other.blue - blue) * ratio,
        alpha
    )

/** 深色（默认）：黑蓝底 + 蓝灰抬升面 + 强调色 */
fun darkPalette(accent: Md2Accent): CovePalette {
    val primary = accent.darkPrimary
    val onPrimary = accentOn(primary)
    return CovePalette(
        primary = primary,
        primaryVariant = primary.mix(Color.Black, 0.35f),
        onPrimary = onPrimary,
        primaryContainer = primary.mix(Color.Black, 0.78f),
        onPrimaryContainer = primary.mix(Color.White, 0.55f),
        secondary = Color(0xFF8ED8C0),
        secondaryVariant = Color(0xFF4E9B85),
        onSecondary = Color(0xFF00382C),
        secondaryContainer = Color(0xFF123128),
        onSecondaryContainer = Color(0xFFA8E6D2),
        tertiary = Color(0xFFF2C46B),
        onTertiary = Color(0xFF3A2A00),
        tertiaryContainer = Color(0xFF3A2E12),
        onTertiaryContainer = Color(0xFFFFE3AE),
        background = Color(0xFF101418),
        onBackground = Color(0xFFE2E6E9),
        surface = Color(0xFF1A2126),
        onSurface = Color(0xFFE2E6E9),
        surfaceVariant = Color(0xFF232B31),
        onSurfaceVariant = Color(0xFFA3AEB5),
        outline = Color(0xFF3B464C),
        error = Color(0xFFFF8A80),
        onError = Color(0xFF4A0B06),
        errorContainer = Color(0xFF4E1512),
        onErrorContainer = Color(0xFFFFD9D4),
        appBar = Color(0xFF1A2126),
        onAppBar = Color(0xFFE2E6E9),
        isDark = true
    )
}

/** 浅色：MD2 蓝灰白底 + 主色应用栏 */
fun lightPalette(accent: Md2Accent): CovePalette {
    val primary = accent.lightPrimary
    return CovePalette(
        primary = primary,
        primaryVariant = primary.mix(Color.Black, 0.3f),
        onPrimary = Color.White,
        primaryContainer = primary.mix(Color.White, 0.82f),
        onPrimaryContainer = primary.mix(Color.Black, 0.45f),
        secondary = Color(0xFF0F7A65),
        secondaryVariant = Color(0xFF0A5B4B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFC9F0E4),
        onSecondaryContainer = Color(0xFF00332A),
        tertiary = Color(0xFF8A6100),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFE7B3),
        onTertiaryContainer = Color(0xFF2C2000),
        background = Color(0xFFF6F8FA),
        onBackground = Color(0xFF12181C),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF12181C),
        surfaceVariant = Color(0xFFE9EEF2),
        onSurfaceVariant = Color(0xFF4A555B),
        outline = Color(0xFFC3CBD1),
        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        appBar = primary,
        onAppBar = Color.White,
        isDark = false
    )
}

val LocalCovePalette = staticCompositionLocalOf { darkPalette(Md2Accent.BLUE) }

/** 主题令牌访问入口：`CoveTheme.colors.primary`（MD2 超集，含 surfaceVariant/outline 等扩展令牌） */
object CoveTheme {
    val colors: CovePalette
        @ReadOnlyComposable
        @androidx.compose.runtime.Composable
        get() = LocalCovePalette.current
}

/** MD2 字体标尺：比 MD3 默认更紧凑，贴合工具型高信息密度界面 */
val Md2Typography = Typography(
    h1 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 88.sp, lineHeight = 100.sp, letterSpacing = -1.5.sp),
    h2 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 56.sp, lineHeight = 64.sp, letterSpacing = -0.5.sp),
    h3 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    h4 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.25.sp),
    h5 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
    h6 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    subtitle1 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    subtitle2 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    body1 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.15.sp),
    body2 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp),
    button = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    caption = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 15.sp, letterSpacing = 0.25.sp),
    overline = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 0.8.sp)
)

/** MD2 圆角：默认 4dp 小圆角；沿用设置里的三档密度调节 */
val Md2ShapesCompact = androidx.compose.material.Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
)

val Md2ShapesStandard = androidx.compose.material.Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
)

val Md2ShapesRounded = androidx.compose.material.Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
)
