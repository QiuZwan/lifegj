package com.lifebutler.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifebutler.app.data.AiButler
import com.lifebutler.app.data.AiConfig
import com.lifebutler.app.data.ButlerStore
import com.lifebutler.app.ui.icons.LbIcons
import com.lifebutler.app.ui.theme.LbAccent
import com.lifebutler.app.ui.theme.LbAccentSoft
import com.lifebutler.app.ui.theme.LbInk
import com.lifebutler.app.ui.theme.LbInk3
import com.lifebutler.app.ui.theme.LbLine
import com.lifebutler.app.ui.theme.LbOnAccent
import com.lifebutler.app.ui.theme.LbSurface
import com.lifebutler.app.ui.theme.LbSurface2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/* ── 悬浮小管家:底栏以外任何一页都能拖着走、点一下就地说话 ── */

/** 机器人本体的尺寸。序列帧是 380×640 的竖构图,这个宽高比差不多,机器人不会被压扁。 */
private val BUTLER_W = 62.dp
private val BUTLER_H = 100.dp

/** 就地弹出的那块小面板。 */
private val PANEL_W = 268.dp

/** 没量到真实高度之前的估值,只影响首次定位那一帧,随后 onSizeChanged 会纠正。 */
private val PANEL_ESTIMATE = 178.dp

/** 悬浮层的尺寸至少要这么大才算"一整层";比这还小说明是布局过程中的退化值,不能用。 */
private const val MIN_LAYER_PX = 320

/**
 * App 内全局悬浮的「小管家」。
 *
 * 少帅的原话:「把机器人小机器人拖到侧边,在侧边可以随意拖动到手机屏幕任何地方且点击进行互动」。
 * 所以它由三件事组成:
 *  1. 一个**一直在自转**的小机器人(复用智能管家页那 24 帧序列帧,见 [ButlerSpin]),默认贴右侧;
 *  2. **拖到哪儿就是哪儿**:自由二维拖动,松手把位置按**归一化坐标**存进 [ButlerStore] 下次还在那;
 *  3. **点一下就地说话**:旁边弹出一个小面板,直接打字发给管家,回复显示在机器人上方,
 *     不用先跳到「智能管家」页再打。写进本机的东西也会在这里如实列出来。
 *     v2.10.1 起**默认静止不转**,点一下(打开面板)它才转起来,再点一下(收起)停回静止 ——
 *     少帅要的是"平时别自己转"。停/转只决定推不推进帧,位置和大小一律不动;
 *     待机那点"呼吸"缩放一直留着,免得它看着像一张死图。
 *
 * 位置为什么存归一化分数而不存像素:可拖动范围会随设备/系统栏变化,存像素下次就可能跑到屏幕外。
 *
 * ⚠️ 拖动用的是逐帧累加位移,不是 [ButlerSpin] 里那套「手指绝对位置」映射——那边是把手指位置
 * 映射成旋转角度,这边是把手指的位移搬给机器人,语义不同,不能照抄。
 *
 * ⚠️ **位置换算的基准必须是"面板收起时"的层高**(见 [baseArea]):面板打开会加 [imePadding],
 * 层高被键盘压矮一大截,拿它当基准做比例换算的话,机器人会整体往上跳、收起又跳回来。
 */
@Composable
fun ButlerFloat(
    visible: Boolean,
    onOpenChat: () -> Unit = {},
    onOpenRoute: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val wPx = with(density) { BUTLER_W.toPx() }
    val hPx = with(density) { BUTLER_H.toPx() }
    val panelWPx = with(density) { PANEL_W.toPx() }
    val gapPx = with(density) { 10.dp.toPx() }
    val estimatePx = with(density) { PANEL_ESTIMATE.toPx() }

    /** 可拖动区域(已经扣掉状态栏/导航栏/底栏)。 */
    var area by remember { mutableStateOf(IntSize.Zero) }

    /**
     * 位置换算的**基准**尺寸 —— 只在面板收起时更新。
     *
     * 面板打开会给悬浮层加 [imePadding],层高被键盘压矮一大截(实测 1080×2047 → 1080×1227)。
     * 位置是「归一化分数 × 层高」,拿被压矮的层高去算,机器人会**整体往上跳**,收起面板又跳回来 ——
     * 在用户眼里就是"点一下它就不在原地了"。所以基准只认"面板收起时"那个尺寸,
     * 层变矮时只做「别被键盘挡住」的夹取,不做比例缩放。
     */
    var baseArea by remember { mutableStateOf(IntSize.Zero) }
    // 位置用归一化分数当唯一真源:区域尺寸一变(键盘弹起、转屏),像素位置自动跟着重算。
    var fx by remember { mutableFloatStateOf(if (store.butlerFx in 0f..1f) store.butlerFx else 1f) }
    var fy by remember { mutableFloatStateOf(if (store.butlerFy in 0f..1f) store.butlerFy else 0.6f) }
    var dragging by remember { mutableStateOf(false) }

    var panelOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    // 一进来就把管家最后一句摆出来,面板不会是空的
    var reply by remember { mutableStateOf(store.chat.lastOrNull { !it.fromUser }?.text) }
    var wrote by remember { mutableStateOf<List<String>>(emptyList()) }
    var navOffer by remember { mutableStateOf<String?>(null) }
    var panelH by remember { mutableIntStateOf(0) }

    val aiSource = remember { AiConfig.source(ctx) }
    val aiReady = aiSource != AiConfig.Source.NONE
    val focusRequester = remember { FocusRequester() }

    val base = if (baseArea.width > 0 && baseArea.height > 0) baseArea else area
    val maxX = (base.width - wPx).coerceAtLeast(0f)
    val maxY = (base.height - hPx).coerceAtLeast(0f)
    // 键盘弹起后的**可视**范围。位置只在这里面夹取:本来就在键盘上方的,一格都不动。
    val visMaxX = ((if (area.width > 0) area.width else base.width) - wPx).coerceAtLeast(0f)
    val visMaxY = ((if (area.height > 0) area.height else base.height) - hPx).coerceAtLeast(0f)
    val bx = if (visMaxX > 0f) (fx * maxX).coerceIn(0f, visMaxX) else fx * maxX
    val by = if (visMaxY > 0f) (fy * maxY).coerceIn(0f, visMaxY) else fy * maxY

    val panelHPx = if (panelH > 0) panelH.toFloat() else estimatePx
    val panelX = (bx + wPx / 2f - panelWPx / 2f).coerceIn(0f, (area.width - panelWPx).coerceAtLeast(0f))
    val above = by - panelHPx - gapPx
    val panelY =
        if (above >= 0f) {
            above
        } else {
            (by + hPx + gapPx).coerceAtMost((area.height - panelHPx).coerceAtLeast(0f))
        }

    // ⚠️ `bx`/`by` 是普通的 val,**不是 State**。手势 lambda 里直接读它们只会拿到"这个 lambda
    // 创建那一拍"的值 —— `pointerInput(Unit)` 不会因为重组而重启,于是它会一直用第一次组合的位置。
    // 第一版就是把 bx/by 直接打进日志,结果"点开面板前后"打出来一模一样,害我以为位置一点没动
    // (其实是键盘弹起后它真的让了位,只是日志在撒谎)。要打"点中那一刻"的真实位置,得过这层。
    val posNow by rememberUpdatedState(bx.roundToInt() to by.roundToInt())

    // 打开面板时把焦点给输入框,键盘自己弹出来,少点一下
    LaunchedEffect(panelOpen) {
        if (panelOpen) {
            delay(120)
            runCatching { focusRequester.requestFocus() }
        }
    }

    /** 发一句给管家。和「智能管家」页走的是同一条路,记录落进同一个 chat 列表,两边看到的是同一份对话。 */
    fun send(raw: String) {
        val t = raw.trim()
        if (t.isEmpty() || typing) return
        store.addChat(true, t)
        draft = ""
        typing = true
        wrote = emptyList()
        navOffer = null
        val history = store.chat.dropLast(1).map { it.fromUser to it.text }
        scope.launch {
            if (aiReady) {
                val r = AiButler.ask(ctx, store, t, history)
                typing = false
                store.addChat(false, r.text)
                reply = r.text
                wrote = r.actions
                navOffer = r.nav?.takeIf { it != "chat" }
            } else {
                delay(400)
                val r = store.reply(t)
                typing = false
                store.addChat(false, r)
                reply = r
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 只在面板开着的时候吃键盘内边距:否则别的地方一打字,这个机器人就会莫名其妙往上跳。
            .then(if (panelOpen) Modifier.imePadding() else Modifier)
            .onSizeChanged { sz ->
                // 只认「像一整层」的尺寸。实测见过一次宽度短暂退化成约等于机器人宽度:
                // 那一刻 maxX≈0,接着一次拖动就把归一化坐标直接压成 0(机器人被甩到屏幕最左边)。
                // 退化尺寸没有意义,直接忽略,宁可继续用上一次的真实尺寸。
                if (sz.width >= MIN_LAYER_PX && sz.height >= MIN_LAYER_PX) {
                    if (sz != area) {
                        store.debugFloat(
                            "层尺寸变化 ${area.width}x${area.height} -> ${sz.width}x${sz.height} panelOpen=$panelOpen",
                        )
                    }
                    area = sz
                    // 只有"面板收起时"量到的才是定位基准,键盘压矮后的尺寸不能当基准
                    if (!panelOpen) baseArea = sz
                }
            },
    ) {
        /* ① 机器人本体 */
        Box(
            Modifier
                .offset { IntOffset(bx.roundToInt(), by.roundToInt()) }
                .size(BUTLER_W, BUTLER_H)
                .scale(if (dragging) 1.08f else 1f)
                .pointerInput(maxX, maxY) {
                    if (maxX <= 0f || maxY <= 0f) return@pointerInput
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            store.debugFloat("end   area=${area.width}x${area.height} maxX=$maxX maxY=$maxY -> $fx $fy")
                            store.setButlerPos(fx, fy)
                        },
                        onDragCancel = {
                            dragging = false
                            // 手势被异常打断(不是用户抬手),**不落库**,并且回到上一次落过库的位置。
                            //
                            // 实测遇到过一次说不清来源的写入:用户拖完落在 bfx=0.30,后来盘上变成了
                            // bfx=0(机器人被甩到最左边)——中间的交互(点开面板/打字/发送/冷启动/切页)
                            // 逐条复现都不出。onDragCancel 恰好是"手势被外力扰动"的那条路,中间值不可信,
                            // 所以这里选择回退而不是记下来:被打断的拖动本来就不算"用户想把它放在这儿"。
                            store.debugFloat(
                                "cancel area=${area.width}x${area.height} maxX=$maxX maxY=$maxY " +
                                    "丢弃 $fx $fy 回退 ${store.butlerFx} ${store.butlerFy}",
                            )
                            if (store.butlerFx in 0f..1f) fx = store.butlerFx
                            if (store.butlerFy in 0f..1f) fy = store.butlerFy
                        },
                    ) { change, drag ->
                        change.consume()
                        // 区域尺寸正常才换算:maxX 趋近 0 时 nx/maxX 会把位置放大成 0 或无穷,
                        // 一次异常拖动就足以把机器人甩到边上。
                        if (maxX > 8f && maxY > 8f) {
                            // fx/fy 是 MutableState,在非 composition 的挂起块里读到的是**当前值**,不会读到旧快照
                            val nx = (fx * maxX + drag.x).coerceIn(0f, maxX)
                            val ny = (fy * maxY + drag.y).coerceIn(0f, maxY)
                            fx = nx / maxX
                            fy = ny / maxY
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        // 点一下:面板开合(**同时**让机器人原地停转 / 继续转,见下面的 spinning)。
                        store.debugFloat(
                            "点中机器人 panelOpen $panelOpen -> ${!panelOpen} " +
                                "pos=(${posNow.first},${posNow.second}) " +
                                "area=${area.width}x${area.height} base=${baseArea.width}x${baseArea.height}",
                        )
                        panelOpen = !panelOpen
                    }
                },
        ) {
            // interactive = false:拖动已经被外层用来搬位置了,这里只负责「转 / 停」。
            // v2.10.1:**默认就是静止不转**。点一下(打开面板)它才转起来,再点一下(收起)停回静止 ——
            // 少帅要的是"平时别自己转"(v2.10 及以前它一进 App 就一直在转,每一页都看得见)。
            ButlerSpin(Modifier.fillMaxSize(), interactive = false, spinning = panelOpen)
        }

        /* ② 就地弹出的对话面板(画在机器人之后,保证它压在上面) */
        if (panelOpen) {
            Column(
                Modifier
                    .offset { IntOffset(panelX.roundToInt(), panelY.roundToInt()) }
                    .width(PANEL_W)
                    .onSizeChanged { panelH = it.height }
                    .clip(RoundedCornerShape(18.dp))
                    .background(LbSurface)
                    .border(1.dp, LbLine, RoundedCornerShape(18.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "智能管家",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbInk,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .lbPressable(onClick = { panelOpen = false }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            LbIcons.x,
                            contentDescription = "收起",
                            tint = LbInk3,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                val shown = reply
                when {
                    typing -> Text(
                        "正在想…",
                        fontSize = 12.sp,
                        color = LbInk3,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    !shown.isNullOrBlank() -> Column(
                        Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(LbAccentSoft)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .heightIn(max = 148.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(shown, fontSize = 12.5.sp, color = LbInk, lineHeight = 18.sp)
                    }
                }

                if (wrote.isNotEmpty()) {
                    Text(
                        "已写进本机：" + wrote.joinToString("、"),
                        fontSize = 11.sp,
                        color = LbAccent,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                navOffer?.let { route ->
                    Row(
                        Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(LbSurface2)
                            .lbPressable(
                                onClick = {
                                    panelOpen = false
                                    onOpenRoute(route)
                                },
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("带我去", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                        Spacer(Modifier.weight(1f))
                        Icon(
                            LbIcons.arrowUpRight,
                            contentDescription = null,
                            tint = LbAccent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                Row(
                    Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = TextStyle(fontSize = 12.5.sp, color = LbInk),
                        cursorBrush = SolidColor(LbAccent),
                        maxLines = 3,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(LbSurface2)
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .focusRequester(focusRequester),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (draft.isEmpty()) {
                                    Text("跟管家说一句…", fontSize = 12.5.sp, color = LbInk3)
                                }
                                inner()
                            }
                        },
                    )
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(LbAccent)
                            .lbPressable(onClick = { send(draft) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            LbIcons.send,
                            contentDescription = "发送",
                            tint = LbOnAccent,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }

                Row(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (aiReady) {
                            "说的能直接写进本机"
                        } else {
                            "现在没接 AI，只能记事记账 · 去「我的 → AI 智能管家」打开"
                        },
                        fontSize = 11.sp,
                        color = LbInk3,
                        lineHeight = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "完整对话",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .lbPressable(
                                onClick = {
                                    panelOpen = false
                                    onOpenChat()
                                },
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
