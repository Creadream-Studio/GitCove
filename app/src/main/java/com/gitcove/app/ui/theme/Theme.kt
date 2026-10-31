package com.gitcove.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme as M3MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import android.os.Build
import com.gitcove.app.ui.theme.Md2ShapesCompact
import com.gitcove.app.ui.theme.Md2ShapesRounded
import com.gitcove.app.ui.theme.Md2ShapesStandard

/**
 * GitCove 主题（MD2）：整体风格对齐 MP 管理器那类 Android 工具应用。
 *
 * - 颜色：MD2 双色系（primary / primaryVariant + secondary / secondaryVariant）+ 扩展令牌
 * - 字体：MD2 标尺（h6 / subtitle1 / body2 / caption / overline），略收紧行高
 * - 圆角：默认 4dp（MD2 规范），设置里可选紧凑 / 标准 / 圆润三档
 * - 强调色：MD2 无 Material You 动态取色，改为设置页显式选择强调色
 */

/** MD2 圆角风格：0 标准（2/4/4/8） / 1 圆润（4/8/12/16） / 2 紧凑（2/3/4/6） */
enum class ShapeStyle(val value: Int) {
    STANDARD(0), ROUNDED(1), COMPACT(2);

    companion object {
        fun from(value: Int) = entries.firstOrNull { it.value == value } ?: STANDARD
    }
}

/** 主题模式：0 跟随系统 / 1 深色 / 2 浅色（展示文案见 Strings.themeSystem/themeDark/themeLight） */
enum class ThemeMode(val value: Int) {
    SYSTEM(0), DARK(1), LIGHT(2);

    companion object {
        fun from(value: Int) = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

@Composable
private fun m2Shapes(style: ShapeStyle) = when (style) {
    ShapeStyle.STANDARD -> Md2ShapesStandard
    ShapeStyle.ROUNDED -> Md2ShapesRounded
    ShapeStyle.COMPACT -> Md2ShapesCompact
}

@Composable
fun GitCoveTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: Md2Accent = Md2Accent.BLUE,
    shapeStyle: ShapeStyle = ShapeStyle.STANDARD,
    /** 兼容旧调用：MD2 下由 [accent] 决定主色，动态取色不再生效 */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val palette = remember(dark, accent, dynamicColor) {
        when {
            // 兼容开关：Android 12+ 仍允许跟随壁纸取色（Material You），否则用 MD2 强调色
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> null
            dark -> darkPalette(accent)
            else -> lightPalette(accent)
        }
    }

    if (palette == null) {
        val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        M3MaterialTheme(colorScheme = scheme, typography = GitCoveTypography, shapes = m2Shapes(shapeStyle).toM3Shapes()) {
            val fallback = if (dark) darkPalette(accent) else lightPalette(accent)
            CompositionLocalProvider(LocalCovePalette provides fallback) {
                SystemBars(fallback)
                content()
            }
        }
        return
    }

    CompositionLocalProvider(LocalCovePalette provides palette) {
        SystemBars(palette)
        MaterialTheme(
            colors = palette.toMaterialColors(),
            typography = Md2Typography,
            shapes = m2Shapes(shapeStyle)
        ) {
            // ── 迁移期桥接：仍在使用 Material3 组件的界面用同一套 MD2 颜色渲染 ──
            M3MaterialTheme(
                colorScheme = palette.toM3Scheme(),
                typography = GitCoveTypography,
                shapes = m2Shapes(shapeStyle).toM3Shapes()
            ) {
                content()
            }
        }
    }
}

/**
 * 系统栏（MD2 规范：状态栏与顶部应用栏同色、导航栏跟随背景；不使用边缘到边缘，
 * 这是 MD2 与 Material You 全屏体验的主要差异之一）。
 */
@Composable
private fun SystemBars(palette: CovePalette) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = findActivity(view.context)?.window ?: return
    SideEffect {
        @Suppress("DEPRECATION")
        window.statusBarColor = palette.appBar.toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = palette.background.toArgb()
        // MD2 取消边缘到边缘绘制：内容不延伸到系统栏下方
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !palette.isDark && palette.appBar.luminance() > 0.5f
            isAppearanceLightNavigationBars = !palette.isDark && palette.background.luminance() > 0.5f
        }
    }
}

private tailrec fun findActivity(context: android.content.Context?): Activity? = when (context) {
    null -> null
    is Activity -> context
    is android.content.ContextWrapper -> findActivity(context.baseContext)
    else -> null
}

/** 按当前主题取差异对比配色 */
@Composable
fun diffColors(): DiffColors =
    if (CoveTheme.colors.isDark) DiffColors.dark() else DiffColors.light()

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
