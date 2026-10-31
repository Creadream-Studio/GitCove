package com.gitcove.app.ui.file

import android.media.MediaPlayer
import android.net.Uri
import android.graphics.BitmapFactory
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.gitcove.app.i18n.LocalStrings
import com.gitcove.app.ui.theme.CoveTheme
import com.gitcove.app.util.fileSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// ─────────────────────────── 图片 ───────────────────────────

/** 手势变换状态；双图对比时两幅图共用同一实例，实现拖动/缩放同步 */
class SyncTransform {
    var scale by mutableStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    fun apply(zoom: Float, pan: Offset) {
        scale = (scale * zoom).coerceIn(1f, 12f)
        offset += pan
    }
    fun reset() { scale = 1f; offset = Offset.Zero }
}

/** 解码结果（inSampleSize 采样控制内存，单边不超过约 2048px） */
private data class DecodedImage(val bitmap: ImageBitmap, val w: Int, val h: Int)

private fun decodeImage(file: File): DecodedImage? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2048) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
    DecodedImage(bmp.asImageBitmap(), bounds.outWidth, bounds.outHeight)
}.getOrNull()

/**
 * 可拖动缩放的图片。
 * - clipToBounds：拖出自身视口的部分必须裁掉，否则双图对比时右图会叠画到左半屏上
 * - 触摸：双指缩放/拖动；鼠标：拖动平移 + 滚轮缩放（PointerEventType.Scroll）
 */
@Composable
private fun Zoomable(bitmap: ImageBitmap, t: SyncTransform, modifier: Modifier = Modifier) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ -> t.apply(zoom, pan) }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { t.reset() })
            }
            .pointerInput(Unit) {
                // 鼠标滚轮缩放（触摸事件 scrollDelta 恒为 0，不会误触发）
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (dy != 0f) {
                                t.apply(1f - dy * 0.1f, Offset.Zero)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = t.scale
                scaleY = t.scale
                translationX = t.offset.x
                translationY = t.offset.y
            }
    )
}

/** 图片查看：双击/双指缩放 + 底部尺寸信息 */
@Composable
fun ImagePane(file: File, modifier: Modifier = Modifier) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    var result by remember { mutableStateOf<DecodedImage?>(null) }
    var failed by remember { mutableStateOf(false) }
    val t = remember { SyncTransform() }

    LaunchedEffect(file.path) {
        withContext(Dispatchers.IO) {
            result = decodeImage(file)
            failed = result == null
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        when {
            result != null -> Zoomable(result!!.bitmap, t, Modifier.fillMaxSize())
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(S.imgOpenFailed, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.body2)
                Text(
                    fileSizeText(file.length()),
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.caption
                )
            }
            else -> CircularProgressIndicator(color = Color.White, modifier = Modifier.size(30.dp))
        }
        result?.let {
            Text(
                S.pixels(it.w, it.h) + " · " + fileSizeText(file.length()),
                style = MaterialTheme.typography.caption,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            )
        }
    }
}

// ─────────────────────────── 双图对比（差异页） ───────────────────────────

/**
 * 图片差异：新旧两图并排，拖动/缩放任一张，另一张同步跟随（共用 SyncTransform）。
 * 一侧缺失（新增/删除）时只显示存在的一侧 + 顶部说明条。
 */
@Composable
fun ImageDiffView(
    oldFile: File?,
    newFile: File?,
    banner: String?,
    modifier: Modifier = Modifier
) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val t = remember { SyncTransform() }
    var oldImg by remember { mutableStateOf<DecodedImage?>(null) }
    var newImg by remember { mutableStateOf<DecodedImage?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(oldFile?.path, newFile?.path) {
        withContext(Dispatchers.IO) {
            oldImg = oldFile?.let { decodeImage(it) }
            newImg = newFile?.let { decodeImage(it) }
            loaded = true
        }
    }

    Column(modifier.fillMaxSize()) {
        if (banner != null) {
            Text(
                banner,
                style = MaterialTheme.typography.caption,
                color = c.onTertiaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.tertiaryContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.primary, modifier = Modifier.size(30.dp))
            }
            return@Column
        }
        // 新增/删除：只有一侧有图 → 单图全宽显示，避免同图重复误导
        if (oldImg == null || newImg == null) {
            val only = newImg ?: oldImg
            if (only == null) {
                // 两侧都解码失败
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(S.imgOpenFailed, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.body2)
                }
                return@Column
            }
            Column(Modifier.fillMaxSize()) {
                Zoomable(
                    only.bitmap,
                    t,
                    Modifier.fillMaxWidth().weight(1f).background(Color.Black)
                )
                SideLabel(
                    (if (newImg != null) S.sideNewFull else S.sideOldFull) +
                        " · " + S.pixels(only.w, only.h)
                )
            }
            Text(
                S.zoomGestureHint,
                style = MaterialTheme.typography.caption,
                color = c.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            )
            return@Column
        }
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // 旧图（不存在时用新图占位；两侧都缺失则留空黑底）
            val leftBmp = oldImg?.bitmap ?: newImg?.bitmap
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (leftBmp != null) {
                    Zoomable(
                        leftBmp,
                        t,
                        Modifier.fillMaxWidth().weight(1f).background(Color.Black)
                    )
                } else {
                    Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black))
                }
                SideLabel(
                    if (oldImg != null) S.sideOldFull + " · " + S.pixels(oldImg!!.w, oldImg!!.h)
                    else if (newImg != null) S.sideOldFull else ""
                )
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.outline))
            // 新图（不存在时用旧图占位）
            val rightBmp = newImg?.bitmap ?: oldImg?.bitmap
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (rightBmp != null) {
                    Zoomable(
                        rightBmp,
                        t,
                        Modifier.fillMaxWidth().weight(1f).background(Color.Black)
                    )
                } else {
                    Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black))
                }
                SideLabel(
                    if (newImg != null) S.sideNewFull + " · " + S.pixels(newImg!!.w, newImg!!.h)
                    else if (oldImg != null) S.sideNewFull else ""
                )
            }
        }
        Text(
            S.zoomGestureHintSync,
            style = MaterialTheme.typography.caption,
            color = c.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SideLabel(text: String) {
    val c = CoveTheme.colors
    Box(
        Modifier.fillMaxWidth().background(c.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.caption,
            color = c.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
        )
    }
}

// ─────────────────────────── 视频 ───────────────────────────

private fun mmss(ms: Int): String {
    val s = ms / 1000
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}

/** 视频播放：VideoView + 播放/暂停 + 进度条 + 时间；宽高比按实际视频自适应 */
@Composable
fun VideoPane(file: File, modifier: Modifier = Modifier) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val context = LocalContext.current
    val videoView = remember(file.path) { VideoView(context) }
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    var position by remember { mutableStateOf(0) }
    var videoAspect by remember { mutableStateOf(16f / 9f) }

    DisposableEffect(file.path) {
        videoView.setVideoURI(Uri.fromFile(file))
        videoView.setOnPreparedListener { mp ->
            prepared = true
            if (mp.videoWidth > 0 && mp.videoHeight > 0) videoAspect = mp.videoWidth.toFloat() / mp.videoHeight
            videoView.start()
            playing = true
        }
        videoView.setOnCompletionListener { playing = false }
        videoView.setOnErrorListener { _, _, _ -> failed = true; true }
        onDispose { videoView.stopPlayback() }
    }

    LaunchedEffect(playing, prepared) {
        while (playing && prepared) {
            position = videoView.currentPosition
            delay(300)
        }
    }

    Column(modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when {
                failed -> Text(S.cannotPlayMedia, color = Color.White.copy(alpha = 0.85f))
                !prepared -> CircularProgressIndicator(color = Color.White, modifier = Modifier.size(30.dp))
                else -> AndroidView(
                    factory = { videoView },
                    // 不能先 fillMaxSize 再 aspectRatio（宽度高度都被固定后比例失效导致拉伸）；
                    // aspectRatio 在松约束下自适应到最大的同比例尺寸，VideoView 自身也会保比例
                    modifier = Modifier.aspectRatio(videoAspect)
                )
            }
        }
        // 控制条（黑底白字）
        Row(
            Modifier.fillMaxWidth().background(Color(0xE6121212)).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material.IconButton(onClick = {
                when {
                    videoView.isPlaying -> { videoView.pause(); playing = false }
                    prepared -> { videoView.start(); playing = true }
                }
            }) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Slider(
                value = if (duration > 0) position.toFloat() / duration else 0f,
                onValueChange = {
                    position = (it * duration).toInt()
                    videoView.seekTo(position)
                },
                enabled = prepared,
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material.SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White.copy(alpha = 0.9f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
            Text(
                "${mmss(position)} / ${mmss(duration)}",
                style = MaterialTheme.typography.caption,
                color = Color.White.copy(alpha = 0.8f)
            )
            androidx.compose.material.IconButton(onClick = {
                if (prepared) { videoView.seekTo(0); videoView.start(); playing = true }
            }) {
                Icon(Icons.Filled.Replay, contentDescription = null, tint = Color.White)
            }
        }
    }
}

// ─────────────────────────── 音频 ───────────────────────────

/** 音频播放：MediaPlayer + 大播放键 + 进度条 + 时间 */
@Composable
fun AudioPane(file: File, modifier: Modifier = Modifier) {
    val S = LocalStrings.current
    val c = CoveTheme.colors
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    var position by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf(false) }

    DisposableEffect(file.path) {
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener {
                prepared = true
                duration = it.duration
                it.start()
                playing = true
            }
            mp.setOnCompletionListener {
                playing = false
                position = duration
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            failed = true
        }
        onDispose {
            mp.release()
            player = null
        }
    }

    LaunchedEffect(playing, prepared) {
        while (playing && prepared) {
            if (!dragging) position = player?.currentPosition ?: 0
            delay(300)
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (failed) {
            Text(S.cannotPlayMedia, color = c.onSurfaceVariant, style = MaterialTheme.typography.body2)
            return@Column
        }
        Box(
            Modifier.size(120.dp).background(c.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = c.onPrimaryContainer, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(28.dp))
        Slider(
            value = if (duration > 0) position.coerceAtMost(duration).toFloat() / duration else 0f,
            onValueChange = { dragging = true; position = (it * duration).toInt() },
            onValueChangeFinished = {
                player?.seekTo(position)
                dragging = false
            },
            enabled = prepared,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(mmss(position), style = MaterialTheme.typography.caption, color = c.onSurfaceVariant)
            Text(mmss(duration), style = MaterialTheme.typography.caption, color = c.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        FloatingActionButton(
            onClick = {
                val mp = player ?: return@FloatingActionButton
                when {
                    mp.isPlaying -> { mp.pause(); playing = false }
                    prepared -> { mp.start(); playing = true }
                }
            },
            backgroundColor = c.primary,
            contentColor = c.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            file.name,
            style = MaterialTheme.typography.caption,
            color = c.onSurfaceVariant,
            maxLines = 1
        )
    }
}
