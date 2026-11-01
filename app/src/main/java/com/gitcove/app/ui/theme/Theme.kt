package com.gitcove.app.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GitCoveTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: Md2Accent = Md2Accent.BLUE,
    shapeStyle: ShapeStyle = ShapeStyle.STANDARD,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    // MD2：不再跟随壁纸取色（Material You 属于 M3 设计语言），强调色由设置页显式选择
    val palette = remember(dark, accent) {
        if (dark) darkPalette(accent) else lightPalette(accent)
    }

    // 关闭 Android 12+ 的拉伸过度滚动（stretch overscroll）：列表滑到边缘时内容被
    // 整体拉长、露出一大片窗口底色，长内容页上看着像“滑出一片空白”；工具型应用
    // （MP 管理器一类）也不使用该效果，全局禁用
    CompositionLocalProvider(
        LocalCovePalette provides palette,
        LocalOverscrollConfiguration provides null
    ) {
        SystemBars(palette)
        MaterialTheme(
            colors = palette.toMaterialColors(),
            typography = Md2Typography,
            shapes = m2Shapes(shapeStyle),
            content = content
        )
    }
}

/**
 * 系统栏（MD2 规范：状态栏与顶部应用栏同色、导航栏跟随背景）。
 *
 * v0.4.8 起全版本统一 edge-to-edge（见 MainActivity）：
 * - Android 15+：statusBarColor / navigationBarColor 为空操作（系统强制透明），
 *   状态栏透出 CoveTopAppBar 铺上去的 appBar 底色；
 * - Android 14-：颜色设置仍生效，配合 CoveTopAppBar 的 safeDrawing 顶部避让，
 *   同样呈现"状态栏与应用栏同色"的 MD2 效果。
 * 内容避让统一由 WindowInsets 处理（safeDrawing / imePadding / Scaffold insets）。
 * 注意：这里绝不能再调 setDecorFitsSystemWindows(true) —— SideEffect 每次重组都会
 * 重跑，会把 MainActivity 设的 edge-to-edge(false) 改回去；正是此前"部分系统上
 * 顶部工具栏又被状态栏挡住"的根因（ROM 半生效时 insets 被清零）。
 */
@Composable
private fun SystemBars(palette: CovePalette) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = findActivity(view.context)?.window ?: return
    SideEffect {
        @Suppress("DEPRECATION")
        window.statusBarColor = palette.appBarElevatedColor().toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = palette.background.toArgb()
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
