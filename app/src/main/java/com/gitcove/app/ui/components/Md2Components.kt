package com.gitcove.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.ui.theme.appBarElevatedColor
import com.gitcove.app.ui.theme.luminance

/**
 * GitCove 的 MD2 组件层。
 *
 * 全部界面统一用这里的基础件拼装，保证"工具型 MD2"的一致性：
 * 56dp 主色应用栏 + 4dp 投影、分组标题条、细分隔线、4dp 圆角卡片、
 * 密集文本字段（filler 风格）、MD2 Tab / Chip / 分段按钮 / 底部抽屉。
 */

/** MD2 应用栏：56dp，appBar 底色 + 4dp 投影，支持副标题（返回键可选） */
@Composable
fun CoveTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = CoveTheme.colors
    Surface(
        color = c.appBar,
        contentColor = c.onAppBar,
        elevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = if (navigationIcon != null) 4.dp else 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigationIcon?.invoke()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp, horizontal = if (navigationIcon != null) 4.dp else 0.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.h6,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.caption,
                        color = subtitleColor ?: c.onAppBar.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            actions()
        }
    }
}

/** MD2 返回键（应用栏内使用，自动按 onAppBar 着色） */
@Composable
fun CoveBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            modifier = Modifier.size(22.dp)
        )
    }
}

/** MD2 分组标题条：surfaceVariant 底 + overline 主色小标题 */
@Composable
fun CoveSectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val c = CoveTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.overline,
            color = c.primary,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(text = trailing, style = MaterialTheme.typography.caption, color = c.onSurfaceVariant)
        }
    }
}

/** MD2 列表分隔线 */
@Composable
fun CoveDivider(modifier: Modifier = Modifier, insetStart: Int = 0) {
    Divider(
        color = CoveTheme.colors.outline.copy(alpha = 0.45f),
        thickness = 1.dp,
        startIndent = insetStart.dp,
        modifier = modifier
    )
}

/** MD2 列表行：图标 + 主标题 + 副标题 + 尾部信息，48dp 起，紧凑不留白 */
@Composable
fun CoveListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    iconTint: Color? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    titleColor: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val c = CoveTheme.colors
    val interactive = Modifier.fillMaxWidth().then(
        when {
            onClick != null && onLongClick != null -> Modifier.combinedRowClickable(onClick, onLongClick)
            onClick != null -> Modifier.clickable(onClick = onClick)
            else -> Modifier
        }
    )
    Row(
        modifier = modifier.then(interactive).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            CompositionLocalProvider(androidx.compose.material.LocalContentColor provides (iconTint ?: c.primary)) {
                icon()
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle2,
                color = titleColor ?: c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.caption,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.let {
            Row(verticalAlignment = Alignment.CenterVertically) { it() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedRowClickable(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)

/** MD2 Tab 行：应用栏同色背景 + 2dp 指示条 */
@Composable
fun CoveTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    val c = CoveTheme.colors
    val indicator = if (c.isDark) c.primary else c.onAppBar
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
        backgroundColor = c.appBarElevatedColor(),
        contentColor = c.onAppBar,
        indicator = { positions ->
            Box(
                Modifier
                    .tabIndicatorOffset(positions[selectedIndex])
                    .height(2.dp)
                    .background(indicator)
            )
        },
        divider = {}
    ) {
        tabs.forEachIndexed { index, label ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                text = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.button,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                selectedContentColor = if (c.isDark) c.primary else c.onAppBar,
                unselectedContentColor = (if (c.isDark) c.onAppBar else c.onAppBar).copy(alpha = 0.65f)
            )
        }
    }
}

/** MD2 Chip（选择/过滤）：32dp 药丸，选中主色填充 */
@Composable
fun CoveChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val c = CoveTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) c.primary.copy(alpha = 0.14f) else Color.Transparent,
        contentColor = if (selected) c.primary else c.onSurfaceVariant,
        border = BorderStroke(1.dp, if (selected) c.primary else c.outline),
        elevation = 0.dp,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.button,
            modifier = Modifier
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** MD2 分段按钮（替代 M3 SegmentedButton）：4dp 圆角分段，选中段主色填充 */
@Composable
fun CoveSegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    val c = CoveTheme.colors
    Row(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val shape = when {
                options.size == 1 -> RoundedCornerShape(4.dp)
                index == 0 -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                index == options.lastIndex -> RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                else -> RoundedCornerShape(0.dp)
            }
            Surface(
                shape = shape,
                color = if (selected) c.primary else Color.Transparent,
                contentColor = if (selected) c.onPrimary else c.onSurface,
                border = BorderStroke(1.dp, if (selected) c.primary else c.outline),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .clickable { onSelect(index) }
                        .heightIn(min = 34.dp)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.button,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** MD2 密集文本字段（filler 风格：surfaceVariant 底 + 主色下划线），支持 minLines / 支持文本 */
@Composable
fun CoveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val c = CoveTheme.colors
    Column(modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle ?: MaterialTheme.typography.body1,
            label = label?.let { { Text(it, style = MaterialTheme.typography.caption) } },
            placeholder = placeholder?.let {
                { Text(it, style = MaterialTheme.typography.body2, color = c.onSurfaceVariant) }
            },
            trailingIcon = trailingIcon,
            leadingIcon = leadingIcon,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = c.surfaceVariant,
                focusedIndicatorColor = c.primary,
                unfocusedIndicatorColor = c.outline,
                errorIndicatorColor = c.error,
                cursorColor = c.primary,
                textColor = c.onSurface,
                focusedLabelColor = c.primary,
                unfocusedLabelColor = c.onSurfaceVariant,
                errorLabelColor = c.error,
                placeholderColor = c.onSurfaceVariant
            )
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.caption,
                color = if (isError) c.error else c.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

/** MD2 主按钮（contained，2dp 投影） */
@Composable
fun CoveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    danger: Boolean = false
) {
    val c = CoveTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = if (danger) ButtonDefaults.buttonColors(backgroundColor = c.error, contentColor = c.onError)
        else ButtonDefaults.buttonColors(backgroundColor = c.primary, contentColor = c.onPrimary),
        shape = RoundedCornerShape(4.dp),
        elevation = ButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.button)
    }
}

/** MD2 描边按钮 */
@Composable
fun CoveOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    val c = CoveTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, if (enabled) c.outline else c.outline.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.primary),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.button)
    }
}

/** MD2 进度条（不确定态 / 确定态） */
@Composable
fun CoveProgressBar(modifier: Modifier = Modifier, progress: Float? = null) {
    val c = CoveTheme.colors
    if (progress == null) {
        androidx.compose.material.LinearProgressIndicator(
            modifier = modifier.fillMaxWidth().height(2.dp),
            color = c.primary,
            backgroundColor = c.surfaceVariant
        )
    } else {
        androidx.compose.material.LinearProgressIndicator(
            progress = progress,
            modifier = modifier.fillMaxWidth().height(2.dp),
            color = c.primary,
            backgroundColor = c.surfaceVariant
        )
    }
}

/** 居中 Loading */
@Composable
fun CoveLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = CoveTheme.colors.primary,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(32.dp)
        )
    }
}

/** MD2 底部抽屉（Dialog 实现，避免依赖 M3 ModalBottomSheet；点击外部关闭） */
@Composable
fun CoveBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = CoveTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Surface(
                modifier = modifier.fillMaxWidth(),
                color = c.surface,
                contentColor = c.onSurface,
                elevation = 8.dp,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // 顶部抓握条
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .background(c.outline, RoundedCornerShape(2.dp))
                        )
                    }
                    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), content = content)
                }
            }
        }
    }
}

/** 徽章（角标） */
@Composable
fun CoveBadge(count: Int, color: Color, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .background(color, CircleShape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.overline,
            color = CoveTheme.colors.onPrimary
        )
    }
}

/** 简易键值行（设置页 / 详情页） */
@Composable
fun CoveKeyValueRow(key: String, value: String, modifier: Modifier = Modifier) {
    val c = CoveTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(key, style = MaterialTheme.typography.body2, color = c.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(
            value,
            style = MaterialTheme.typography.body2,
            color = c.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 空列表占位（MD2：居中说明 + 可选操作） */
@Composable
fun CoveEmptyState(text: String, modifier: Modifier = Modifier, icon: (@Composable () -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    val c = CoveTheme.colors
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon?.let {
            CompositionLocalProvider(androidx.compose.material.LocalContentColor provides c.onSurfaceVariant.copy(alpha = 0.6f)) {
                it()
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            color = c.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        action?.let { Spacer(Modifier.height(12.dp)); it() }
    }
}

/** MD2 通用对话框：4dp 圆角、24dp 投影、surface 底 + 标题/内容/双按钮 */
@Composable
fun CoveAlertDialog(
    onDismiss: () -> Unit,
    title: String,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissLabel: String? = null,
    onDismissClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val c = CoveTheme.colors
    androidx.compose.material.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(4.dp),
        backgroundColor = c.surface,
        contentColor = c.onSurface,
        title = { Text(title, style = MaterialTheme.typography.subtitle1) },
        text = content?.let { body -> { Column { body() } } },
        confirmButton = {
            if (confirmLabel != null && onConfirm != null) {
                androidx.compose.material.TextButton(onClick = onConfirm) {
                    Text(confirmLabel, style = MaterialTheme.typography.button, color = c.primary)
                }
            }
        },
        dismissButton = if (dismissLabel != null) {
            {
                androidx.compose.material.TextButton(onClick = onDismissClick ?: onDismiss) {
                    Text(dismissLabel, style = MaterialTheme.typography.button, color = c.onSurfaceVariant)
                }
            }
        } else null
    )
}

/** MD2 强调色选择器：一排圆形色块，选中项加外圈与对勾 */
@Composable
fun CoveAccentPicker(
    selected: com.gitcove.app.ui.theme.Md2Accent,
    onSelect: (com.gitcove.app.ui.theme.Md2Accent) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CoveTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        com.gitcove.app.ui.theme.Md2Accent.entries.forEach { accent ->
            val swatch = if (c.isDark) accent.darkPrimary else accent.lightPrimary
            val isSelected = accent == selected
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(34.dp)
                    .background(
                        if (isSelected) swatch.copy(alpha = 0.22f) else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(swatch, CircleShape)
                        .clickable { onSelect(accent) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (swatch.luminance() > 0.5f) Color.Black.copy(alpha = 0.75f) else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
