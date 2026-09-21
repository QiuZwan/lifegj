package com.lifebutler.app.ui.components

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.google.android.filament.IndirectLight
import com.lifebutler.app.R
import io.github.sceneview.SceneView
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * 智能管家页的 3D 小机器人。
 *
 * v2.6 起这里是**真的 3D 模型**（assets/models/butler3d.glb，腾讯混元图生 3D 出的），
 * 不再是一张渲染图。用户可以拖动旋转、双指缩放（[rememberCameraManipulator] 自带的轨道相机），
 * 另外 v2.8 起**静止时它自己也会慢慢转**（[SPIN_PERIOD_MS] 一圈）——少帅反馈过"机器人是死的,
 * 动不了没互动",光靠"能拖"是不够的:你得先知道它能拖。
 *
 * 为什么环境光用球谐常数而不是 HDR 文件：
 * Filament 的 PBR 材质必须有一份 IndirectLight（间接光），否则整个模型发黑。
 * 常规做法是打包一张 .hdr 环境贴图（1~2MB），但管家页只要"均匀的室内环境光"这一个效果，
 * 用 `IndirectLight.Builder().irradiance(1, sh)` 给一组球谐常数就够了——零资源、零体积。
 * [SH_R] 里的三个数就是 (R,G,B) 的环境光强度。
 *
 * 两层回退，都不会空白或崩溃：
 *  1. 软件渲染的设备（GPU 名带 "SwiftShader"，典型是模拟器）：Filament 的 blitLow 着色器
 *     在 SwiftShader 的 GLSL 编译器上编译不过（结构体构造不被支持），引擎会直接 native
 *     panic 把进程带走——所以在进 Filament **之前**就拦下，交给 [ButlerSpin]：
 *     事先 CPU 渲染好的 24 张角度序列帧，空闲自转 + 横向拖动转圈 + 点一下转一整圈。
 *     真机 GPU（Adreno/Mali/PowerVR）不受影响，走下面的真 3D。
 *  2. 模型加载失败（instance == null）：静态图垫底。
 */
private const val SH_R = 1.00f
private const val SH_G = 0.98f
private const val SH_B = 0.94f

/** 环境光强度（lux）。Filament 的间接光默认 30000，这里略调亮让陶瓷更通透。 */
private const val IBL_INTENSITY = 50_000f

/** 机器人摆到多大规模。glb 里模型本身的单位很随意，用 scaleToUnits 归一到 1 个世界单位。 */
private const val MODEL_SCALE_TO_UNITS = 1.0f

/**
 * 静止时自转一圈要多久。故意慢：这是"它还活着"的暗示，不是让你盯着看的风扇。
 * （模拟器那条路的 [ButlerSpin] 是 24 帧序列帧，约 17 秒一圈。）
 */
private const val SPIN_PERIOD_MS = 30_000

/**
 * SwiftShader = 模拟器/软件渲染的 GPU。Filament 的 blitLow 着色器在它的 GLSL 编译器上
 * 编译不过（结构体构造不被支持），引擎会 native panic 直接带走进程——进 Filament 前拦下。
 *
 * 拿 GPU 名字需要 GL_RENDERER，而它必须有一个当前生效的 GL 上下文才读得到。
 * 这里用 EGL 建一个 1x1 的 pbuffer 上下文（离屏、不闪屏、用完即毁），读一次缓存住。
 * 读不到（EGL 初始化失败等）按「非软件渲染」处理——宁可让真机去跑 3D，也别误伤。
 */
private var cachedGpuRenderer: String? = null

private fun queryGpuRenderer(ctx: Context): String {
    cachedGpuRenderer?.let { return it }
    var renderer = ""
    try {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display != EGL14.EGL_NO_DISPLAY) {
            val ver = IntArray(2)
            if (EGL14.eglInitialize(display, ver, 0, ver, 1)) {
                val cfgAttribs = intArrayOf(
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE,
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val num = IntArray(1)
                if (EGL14.eglChooseConfig(display, cfgAttribs, 0, configs, 0, 1, num, 0) && num[0] > 0) {
                    val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                    val glCtx = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
                    if (glCtx != EGL14.EGL_NO_CONTEXT) {
                        val pbAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
                        val surface = EGL14.eglCreatePbufferSurface(display, configs[0], pbAttribs, 0)
                        if (surface != EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display, surface, surface, glCtx)) {
                            renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
                            EGL14.eglMakeCurrent(
                                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
                            )
                        }
                        EGL14.eglDestroySurface(display, surface)
                        EGL14.eglDestroyContext(display, glCtx)
                    }
                }
                EGL14.eglTerminate(display)
            }
        }
    } catch (_: Throwable) {
        // 读不到就当真机处理；真要跑不动,后面 instance==null 那层兜底还在。
    }
    cachedGpuRenderer = renderer
    return renderer
}

private fun isSoftwareGpu(ctx: Context): Boolean {
    val r = queryGpuRenderer(ctx)
    return r.contains("SwiftShader", ignoreCase = true) ||
        r.contains("Android Emulator", ignoreCase = true) ||
        r.contains("Software", ignoreCase = true)
}

@Composable
fun ButlerScene(modifier: Modifier) {
    val context = LocalContext.current
    if (isSoftwareGpu(context)) {
        // 软件渲染(模拟器等):Filament 会崩,改用事先 CPU 渲染好的角度序列帧,
        // 空闲自转 + 横向拖动转圈 + 点一下自转一整圈 —— 不是一张贴死的图。
        ButlerSpin(modifier = modifier)
        return
    }
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val instance = rememberModelInstance(modelLoader, "models/butler3d.glb")

    if (instance == null) {
        // 模型还没加载完（或加载失败）。静态图垫底,避免空一块。
        Image(
            painter = painterResource(R.drawable.lb_butler3d),
            contentDescription = "智能管家",
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
        return
    }

    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = rememberEnvironment(environmentLoader) {
        val sh = floatArrayOf(SH_R, SH_G, SH_B)
        val indirect = IndirectLight.Builder()
            .irradiance(1, sh)
            .intensity(IBL_INTENSITY)
            .build(engine)
        environmentLoader.createEnvironment(indirect, null, null)
    }

    // 静止自转。走节点的 rotation 而不是相机:相机是给用户拖的,别跟用户抢。
    val spin = rememberInfiniteTransition(label = "butlerSpin")
    val yaw by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(SPIN_PERIOD_MS, easing = LinearEasing)),
        label = "butlerYaw",
    )

    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        environment = environment,
        cameraManipulator = rememberCameraManipulator(),
    ) {
        ModelNode(
            modelInstance = instance,
            scaleToUnits = MODEL_SCALE_TO_UNITS,
            rotation = Rotation(y = yaw),
        )
    }
}
