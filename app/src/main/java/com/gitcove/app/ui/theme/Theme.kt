package com.gitcove.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * GitCove 主题（开发文档 5.2）：
 * - 小圆角：4 / 6 / 8 / 12dp
 * - 紧凑排版
 * - 深湾蓝绿品牌色，支持跟随系统 / 强制深色 / 强制浅色
 */
private val GitCoveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp)
)

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

/** 主题模式：0 跟随系统 / 1 深色 / 2 浅色 */
enum class ThemeMode(val label: String, val value: Int) {
    SYSTEM("跟随系统", 0), DARK("深色", 1), LIGHT("浅色", 2);

    companion object {
        fun from(value: Int) = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

@Composable
fun GitCoveTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (dark) GitCoveDarkColors else GitCoveLightColors,
        typography = GitCoveTypography,
        shapes = GitCoveShapes,
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
