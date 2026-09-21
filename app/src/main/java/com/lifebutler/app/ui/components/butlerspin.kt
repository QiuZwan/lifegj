package com.lifebutler.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
 *  - 点一下:**原地停转**(再点一下继续转)。v2.10 改的,见下面 [spinning] 的说明。
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
 *   悬浮小管家传 false：它要的是「自己转，但拖动交给外层去搬位置」——
 *   如果这里也挂 [detectHorizontalDragGestures]，手指一按就变成「原地转」而不是「把机器人搬走」，
 *   两个手势会抢同一个事件。
 * @param spinning 外部控制「还转不转」。false = **原地停转**：帧一帧都不再推进，但位置/大小都不动。
 *   悬浮小管家用它实现"点开面板时它就不转了"。
 *   ⚠️ 这是普通参数,协程里直接读只会拿到进来那一刻的值(而且不报错、只是照旧转,极难查),
 *   所以协程内一律读 [rememberUpdatedState] 包过的那个。
 *
 * interactive 那一台的「点一下」= **原地停转 / 再点继续转**(v2.10 由"点一下转一整圈"改过来,
 * 少帅要的是"点一下它就在原地不转了,再点一下才转")。
 */
@Composable
fun ButlerSpin(
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    spinning: Boolean = true,
) {
    val ctx = LocalContext.current

    var angle by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    /** 上次手指碰它的时间。空闲自转靠它判断"现在该不该转"。 */
    var lastTouch by remember { mutableLongStateOf(0L) }
    /** interactive 那一台自己点出来的暂停:点一下停,再点一下转。 */
    var tapPaused by remember { mutableStateOf(false) }

    val wantSpin by rememberUpdatedState(spinning)
    val dbg = remember { com.lifebutler.app.data.ButlerStore.get(ctx) }
    DisposableEffect(Unit) {
        dbg.debugFloat("spin 进入组合 interactive=$interactive spinning=$spinning")
        onDispose { dbg.debugFloat("spin 离开组合 interactive=$interactive") }
    }
    LaunchedEffect(wantSpin, tapPaused) {
        dbg.debugFloat(
            "spin 状态 wantSpin=$wantSpin tapPaused=$tapPaused → " +
                (if (wantSpin && !tapPaused) "转" else "停"),
        )
    }

    // 取帧:用 conflate 而不是 collectLatest —— 快速拖动时中间帧直接跳过,
    // 但**正在解码的那一帧不会被掐断**(collectLatest 会把每一帧都取消掉,结果一帧都显示不出来)。
    LaunchedEffect(Unit) {
        coroutineScope {
            snapshotFlow { frameIndex(angle) }
                .conflate()
                .collect { i ->
                    val bmp = synchronized(frameCache) { frameCache[i] }
                        ?: withContext(Dispatchers.IO) { decodeFrame(ctx, i) }?.also {
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
        var tick = 0
        while (true) {
            delay(IDLE_STEP_MS)
            // 停转时**原地不动,一帧都不推进**——只是不推进,不是把角度归零或者换个姿势。
            if (!wantSpin || tapPaused) continue
            val dt = SystemClock.uptimeMillis() - lastTouch
            if (dt >= IDLE_RESUME_MS) {
                angle = norm(angle + DEG_PER_FRAME)
                if (tick++ % 4 == 0) dbg.debugFloat("spin 自转中 angle=$angle frame=${frameIndex(angle)}")
            }
        }
    }

    // 待机时轻微"呼吸",让它即使刚好停在某一帧上也不是死的。
    // 停转时刻意**保留**它:少帅要的是"不转了",不是"冻成一张死图"(v2.8 他抱怨过机器人是死的)。
    val breathe = rememberInfiniteTransition(label = "butlerBreathe")
    val phase by breathe.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "butlerBreathePhase",
    )

    // 手势只在 interactive 时挂上。悬浮小管家要的是「自己转，拖动交给外层搬位置」：
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
                    // 点一下 = 原地停转 / 再点继续转。**不做任何角度变更**:停了就是停在这一帧上。
                    detectTapGestures { tapPaused = !tapPaused }
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
