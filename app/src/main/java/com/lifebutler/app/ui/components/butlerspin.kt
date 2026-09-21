package com.lifebutler.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.lifebutler.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 没有硬件 GPU 的设备(模拟器等)上的「可旋转管家」。
 *
 * 为什么是序列帧而不是 3D 引擎：Filament 在 SwiftShader 上会在着色器编译期 panic 把进程带走
 * (见 [ButlerScene] 的注释),而这些设备上用户照样要看到"立在那里、能转"的机器人。
 * 于是事先用 `design/butler3d/render_spin.py`(纯 CPU 光栅器)把同一个 glb 渲染成
 * [FRAME_COUNT] 张绕 Y 轴平均分布的角度图,运行时把角度映射成"显示第几帧"。
 *
 * 三种交互(少帅反馈过"机器人是死的,动不了没互动",所以**静止时也必须自己转**):
 *  - 空闲:每 [IDLE_STEP_MS] 前进一帧,24 帧一圈约 17 秒 —— 远远看就知道这是个立着的活物;
 *  - 横向拖动:1:1 跟手转,松手继续自转;
 *  - 点一下:自己转一整圈。
 *
 * ⚠️ 手势挂在外层 Box 上,**不要挂到 Image 上**:Image 的布局高度由位图固有尺寸决定,
 * 不等于这块可视区,手指落在图上方的空白里就什么都收不到(实测:在图上拖能转、在图上方的
 * 空白拖毫无反应)。另外只收**横向**拖动([detectHorizontalDragGestures]),纵向留给页面滚动。
 */
private const val FRAME_COUNT = 24
private const val FRAME_DIR = "butler_spin"
private const val DEG_PER_FRAME = 360f / FRAME_COUNT

/** 横向拖动多少像素转一整圈。屏幕宽 360dp 左右时,一个来回刚好一圈多,手感合适。 */
private const val DRAG_PX_PER_TURN = 620f

/** 空闲自转:每帧间隔。[FRAME_COUNT] × 700ms ≈ 17 秒转一圈。 */
private const val IDLE_STEP_MS = 700L

/** 手一碰就停,松手 [IDLE_RESUME_MS] 之后才慢慢转起来(别跟用户抢控制权)。 */
private const val IDLE_RESUME_MS = 1600L

/** 点一下自转一整圈用多久。 */
private const val TAP_TURN_MS = 900L

/** 缓存 12 帧(约 11 MB)。自转是一帧一帧往前走的,12 帧刚好覆盖可见的邻近角度。 */
private val frameCache = object : LinkedHashMap<Int, ImageBitmap>(12, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>?): Boolean = size > 12
}

private fun decodeFrame(ctx: Context, index: Int): ImageBitmap? = try {
    val name = String.format("%s/f%02d.webp", FRAME_DIR, index)
    ctx.assets.open(name).use { stream ->
        BitmapFactory.decodeStream(stream)?.asImageBitmap()
    }
} catch (_: Throwable) {
    null
}

/** 角度(度) → 帧号,任意角度都能取到合法帧(负数也算对)。 */
private fun frameIndex(deg: Float): Int {
    val i = (deg / DEG_PER_FRAME).roundToInt() % FRAME_COUNT
    return if (i < 0) i + FRAME_COUNT else i
}

/** 归一化到 [0, 360)。 */
private fun norm(deg: Float): Float {
    val m = deg % 360f
    return if (m < 0f) m + 360f else m
}

/**
 * @param interactive 是否自己接管横向拖动/点击手势。
 *   默认 true（智能管家页那一台大字号的，自己管旋转和点击）。
 *   悬浮小管家传 false：它要的是「一直自己转，但拖动交给外层去搬位置」——
 *   如果这里也挂 [detectHorizontalDragGestures]，手指一按就变成「原地转」而不是「把机器人搬走」，
 *   两个手势会抢同一个事件。
 */
@Composable
fun ButlerSpin(modifier: Modifier = Modifier, interactive: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var angle by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    /** 上次手指碰它的时间。空闲自转靠它判断"现在该不该转"。 */
    var lastTouch by remember { mutableLongStateOf(0L) }

    // 取帧:用 conflate 而不是 collectLatest —— 快速拖动时中间帧直接跳过,
    // 但**正在解码的那一帧不会被掐断**(collectLatest 会把每一帧都取消掉,结果一帧都显示不出来)。
    LaunchedEffect(Unit) {
        coroutineScope {
            snapshotFlow { frameIndex(angle) }
                .conflate()
                .collect { i ->
                    val hit = synchronized(frameCache) { frameCache[i] }
                    val bmp = hit ?: withContext(Dispatchers.IO) { decodeFrame(ctx, i) }?.also {
                        synchronized(frameCache) { frameCache[i] = it }
                    }
                    if (bmp != null) frame = bmp
                    launch {
                        // 预取相邻帧,和"显示当前帧"互不阻塞
                        withContext(Dispatchers.IO) {
                            for (d in intArrayOf(1, -1, 2, -2)) {
                                val n = frameIndex(DEG_PER_FRAME * (i + d))
                                if (synchronized(frameCache) { frameCache[n] } == null) {
                                    decodeFrame(ctx, n)?.let { b ->
                                        synchronized(frameCache) { frameCache[n] = b }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    // 空闲自转。**别改成 Animatable**:它的 MutatorMutex 会把这个 LaunchedEffect 的 Job 本身
    // 取消掉(拖一下就永久停转,还查不出来),直接改状态最稳。
    LaunchedEffect(Unit) {
        while (true) {
            delay(IDLE_STEP_MS)
            if (SystemClock.uptimeMillis() - lastTouch >= IDLE_RESUME_MS) {
                angle = norm(angle + DEG_PER_FRAME)
            }
        }
    }

    // 待机时轻微"呼吸",让它即使刚好停在某一帧上也不是死的
    val breathe = rememberInfiniteTransition(label = "butlerBreathe")
    val phase by breathe.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "butlerBreathePhase",
    )

    // 手势只在 interactive 时挂上。悬浮小管家要的是「一直自己转，拖动交给外层搬位置」：
    // 这里若也挂 detectHorizontalDragGestures，手指一按就变成「原地转」而不是「把它搬走」，
    // 两个手势会抢同一个事件。
    val gestures =
        if (!interactive) {
            Modifier
        } else {
            Modifier
                .pointerInput(Unit) {
                    // 只认横向拖动:纵向留给页面的滚动,不然机器人这块会把上滑手势吃掉。
                    // ⚠️ `onHorizontalDrag` 的第二个参数是 **Float(已经就是横向位移)**,
                    // 不是 `detectDragGestures` 那样的 `Offset`——写成 `drag.x` 编译不过。
                    //
                    // ⚠️ 角度按**手指绝对位置**算,不要把每次的位移累加。指针事件是会丢的
                    // (这台软件渲染的模拟器上实测一次 400px 的滑动只送到 ~200px 的量,
                    // 累加法于是只转了半圈),而绝对位置法永远等于手指当前位置,丢多少都不影响结果。
                    var startX = 0f
                    var startAngle = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { off ->
                            lastTouch = SystemClock.uptimeMillis()
                            startX = off.x
                            startAngle = angle
                        },
                        onDragEnd = { lastTouch = SystemClock.uptimeMillis() },
                        onDragCancel = { lastTouch = SystemClock.uptimeMillis() },
                    ) { change, _ ->
                        change.consume()
                        lastTouch = SystemClock.uptimeMillis()
                        angle = norm(
                            startAngle - (change.position.x - startX) / DRAG_PX_PER_TURN * 360f,
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        lastTouch = SystemClock.uptimeMillis()
                        val from = angle
                        scope.launch {
                            // 用 withFrameNanos 跟着 Compose 的帧钟走,比 delay(16) 稳
                            val t0 = withFrameNanos { it }
                            while (true) {
                                val now = withFrameNanos { it }
                                val p = ((now - t0) / 1_000_000f / TAP_TURN_MS).coerceIn(0f, 1f)
                                lastTouch = SystemClock.uptimeMillis()
                                angle = norm(from + 360f * FastOutSlowInEasing.transform(p))
                                if (p >= 1f) break
                            }
                        }
                    }
                }
        }

    Box(
        modifier = modifier.then(gestures),
        contentAlignment = Alignment.Center,
    ) {
        val shown = frame
        if (shown == null) {
            // 第一帧还没解出来(或资源缺失):先用静态图垫着,不让这块空着
            Image(
                painter = painterResource(R.drawable.lb_butler3d),
                contentDescription = "智能管家",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                bitmap = shown,
                contentDescription = "智能管家，可拖动旋转",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1f + 0.008f * phase
                        scaleX = s
                        scaleY = s
                        translationY = -6f * phase * density
                    },
            )
        }
    }
}
