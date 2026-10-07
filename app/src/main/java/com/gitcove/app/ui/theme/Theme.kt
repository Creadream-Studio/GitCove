package com.gitcove.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * GitCove 主题（开发文档 5.2）：
 * - 紧凑排版
 * - 深湾蓝绿品牌色，支持跟随系统 / 强制深色 / 强制浅色
 * - MD3 风格可在设置中修改：圆角风格（标准 / 圆润 / 紧凑）+ Android 12+ 动态取色
 */

/** MD3 圆角风格：0 标准（4/6/8/12） / 1 圆润（8/12/16/24） / 2 紧凑（2/4/6/8） */
enum class ShapeStyle(val value: Int) {
    STANDARD(0), ROUNDED(1), COMPACT(2);

    companion object {
        fun from(value: Int) = entries.firstOrNull { it.value == value } ?: STANDARD
    }
}

private fun shapesFor(style: ShapeStyle): Shapes = when (style) {
    ShapeStyle.STANDARD -> Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp)
    )
    ShapeStyle.ROUNDED -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp)
    )
    ShapeStyle.COMPACT -> Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(8.dp)
    )
}

private val GitCoveDarkColors = darkColorScheme(
    primary = CovePrimaryDark,
    onPrimary = CoveOnPrimaryDark,
    primaryContainer = CovePrimaryContainerDark,
    onPrimaryContainer = CoveOnPrimaryContainerDark,
    secondary = CoveSecondaryDark,
    onSecondary = CoveOnSecondaryDark,
    tertiary = CoveTertiaryDark,
    onTertiary = CoveOnTertiaryDark,
    background = CoveBackgroundDark,
    onBackground = CoveOnBackgroundDark,
    surface = CoveSurfaceDark,
    onSurface = CoveOnSurfaceDark,
    surfaceVariant = CoveSurfaceVariantDark,
    onSurfaceVariant = CoveOnSurfaceVariantDark,
    outline = CoveOutlineDark,
    error = CoveErrorDark,
    onError = CoveOnErrorDark,
    errorContainer = CoveErrorContainerDark,
    onErrorContainer = CoveOnErrorContainerDark,
    inverseSurface = CoveInverseSurfaceDark,
    inverseOnSurface = CoveInverseOnSurfaceDark
)

private val GitCoveLightColors = lightColorScheme(
    primary = CovePrimaryLight,
    onPrimary = CoveOnPrimaryLight,
    primaryContainer = CovePrimaryContainerLight,
    onPrimaryContainer = CoveOnPrimaryContainerLight,
    secondary = CoveSecondaryLight,
    onSecondary = CoveOnSecondaryLight,
    tertiary = CoveTertiaryLight,
    onTertiary = CoveOnTertiaryLight,
    background = CoveBackgroundLight,
    onBackground = CoveOnBackgroundLight,
    surface = CoveSurfaceLight,
    onSurface = CoveOnSurfaceLight,
    surfaceVariant = CoveSurfaceVariantLight,
    onSurfaceVariant = CoveOnSurfaceVariantLight,
    outline = CoveOutlineLight,
    error = CoveErrorLight,
    onError = CoveOnErrorLight,
    errorContainer = CoveErrorContainerLight,
    onErrorContainer = CoveOnErrorContainerLight
)

/** 主题模式：0 跟随系统 / 1 深色 / 2 浅色（展示文案见 Strings.themeSystem/themeDark/themeLight） */
enum class ThemeMode(val value: Int) {
    SYSTEM(0), DARK(1), LIGHT(2);

    companion object {
        fun from(value: Int) = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

@Composable
fun GitCoveTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    shapeStyle: ShapeStyle = ShapeStyle.STANDARD,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    // MD3 动态取色（Material You）：Android 12+ 可跟随壁纸配色，否则回退品牌色
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> GitCoveDarkColors
        else -> GitCoveLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = GitCoveTypography,
        shapes = shapesFor(shapeStyle),
        content = content
    )
}

/** 按当前主题取差异对比配色 */
@Composable
fun diffColors(): DiffColors =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DiffColors.dark() else DiffColors.light()

data class DiffColors(
    val addBg: Color,
    val delBg: Color,
    val addFg: Color,
    val delFg: Color,
    val hunkFg: Color,
    val metaFg: Color
) {
    companion object {
        fun dark() = DiffColors(DiffAddBgDark, DiffDelBgDark, DiffAddFgDark, DiffDelFgDark, DiffHunkFgDark, DiffMetaFgDark)
        fun light() = DiffColors(DiffAddBgLight, DiffDelBgLight, DiffAddFgLight, DiffDelFgLight, DiffHunkFgLight, DiffMetaFgLight)
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
