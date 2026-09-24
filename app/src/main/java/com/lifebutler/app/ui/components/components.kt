package com.lifebutler.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import com.lifebutler.app.R
import com.lifebutler.app.ui.icons.LbIcons
import com.lifebutler.app.ui.theme.LbAccent
import com.lifebutler.app.ui.theme.LbAccentSoft
import com.lifebutler.app.ui.theme.LbAmber
import com.lifebutler.app.ui.theme.LbAmberSoft
import com.lifebutler.app.ui.theme.LbBg
import com.lifebutler.app.ui.theme.LbInk
import com.lifebutler.app.ui.theme.LbInk2
import com.lifebutler.app.ui.theme.LbInk3
import com.lifebutler.app.ui.theme.LbLine
import com.lifebutler.app.ui.theme.LbLineStrong
import com.lifebutler.app.ui.theme.LbOnAccent
import com.lifebutler.app.ui.theme.LbOnDark
import com.lifebutler.app.ui.theme.LbRust
import com.lifebutler.app.ui.theme.LbRustSoft
import com.lifebutler.app.ui.theme.LbSurface
import com.lifebutler.app.ui.theme.LbSurface2
import kotlinx.coroutines.delay

@Composable
fun LbCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = LbSurface,
        border = BorderStroke(1.dp, LbLine),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

enum class ChipTone { Amber, Green, Soft, Rust, OnDark }

@Composable
fun LbChip(text: String, tone: ChipTone = ChipTone.Soft) {
    val bg: Color
    val fg: Color
    when (tone) {
        ChipTone.Amber -> { bg = LbAmberSoft; fg = LbAmber }
        ChipTone.Green -> { bg = LbAccentSoft; fg = LbAccent }
        ChipTone.Soft -> { bg = LbSurface2; fg = LbInk2 }
        ChipTone.Rust -> { bg = LbRustSoft; fg = LbRust }
        ChipTone.OnDark -> { bg = Color(0x29F6F5F0); fg = LbOnDark }
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.5.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
fun IconBadge(icon: ImageVector, bg: Color, fg: Color, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun SectionHeader(title: String, side: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        side()
    }
}

@Composable
fun HeroCard(
    painter: Painter,
    height: Dp,
    kicker: String,
    title: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(22.dp)),
    ) {
        Image(
            painter,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.36f to Color.Transparent,
                        1f to Color(0xC71E2A24),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(kicker, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xBCF6F5F0))
            Text(
                title,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = LbOnDark,
                modifier = Modifier.padding(top = 2.dp),
            )
            sub?.let {
                Text(it, fontSize = 11.sp, color = Color(0xC7F6F5F0), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
fun LbPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(90), label = "btnPress")
    Box(
        modifier
            // heightIn(min=) 而不是写死 height：系统字体放大到「大」时，
            // 按钮文字会占两行，写死高度会把它裁掉（长辈最容易撞上这个）。
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) LbAccent else LbLineStrong)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) LbOnAccent else LbInk3,
        )
    }
}

@Composable
fun LbGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(90), label = "ghostPress")
    Box(
        modifier
            // 同 LbPrimaryButton：不写死高度，字放大也不会被裁
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, LbLineStrong, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = LbInk)
    }
}

@Composable
fun TaskRow(checked: Boolean, onToggle: () -> Unit, title: String, sub: String? = null, onLongClick: (() -> Unit)? = null) {
    val haptic = LocalHapticFeedback.current
    val boxBg by animateColorAsState(if (checked) LbAccent else Color.Transparent, tween(180), label = "tboxBg")
    val boxLine by animateColorAsState(if (checked) LbAccent else LbLineStrong, tween(180), label = "tboxLine")
    val checkScale by animateFloatAsState(if (checked) 1f else 0.5f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "checkScale")
    val checkAlpha by animateFloatAsState(if (checked) 1f else 0f, tween(140), label = "checkAlpha")
    val titleColor by animateColorAsState(if (checked) LbInk3 else LbInk, tween(180), label = "titleColor")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .lbPressable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                },
                onLongClick = onLongClick,
            )
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(boxBg)
                .border(1.5.dp, boxLine, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checkAlpha > 0.01f) {
                Icon(
                    LbIcons.check,
                    contentDescription = null,
                    tint = LbOnAccent,
                    modifier = Modifier
                        .size(13.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                            alpha = checkAlpha
                        },
                )
            }
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = titleColor,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
            sub?.let {
                Text(it, fontSize = 11.5.sp, color = LbInk3, modifier = Modifier.padding(top = 1.dp))
            }
        }
    }
}

@Composable
fun CustodyCard(
    badge: @Composable () -> Unit,
    title: String,
    sub: String,
    chips: List<Pair<String, ChipTone>> = emptyList(),
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = LbSurface,
        border = BorderStroke(1.dp, LbLine),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                badge()
                Column(
                    Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                ) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                    Text(sub, fontSize = 12.sp, color = LbInk2, modifier = Modifier.padding(top = 2.dp))
                }
                Icon(LbIcons.chevronRight, contentDescription = null, tint = LbInk3, modifier = Modifier.size(16.dp))
            }
            if (chips.isNotEmpty()) {
                Row(
                    Modifier.padding(start = 52.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    chips.forEach { (t, tone) -> LbChip(t, tone) }
                }
            }
        }
    }
}

@Composable
fun LbListRow(
    leading: @Composable () -> Unit,
    title: String,
    sub: String? = null,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.lbPressable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
                } else Modifier,
            )
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(
            Modifier
                .padding(start = 11.dp)
                .weight(1f),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            sub?.let {
                Text(it, fontSize = 11.5.sp, color = LbInk3, modifier = Modifier.padding(top = 1.dp))
            }
        }
        trailing()
    }
}

/** 一个底部 tab:线性图标(`line`),或带 alpha 的位图图标(`raster`,3D 插图用)。 */
private data class LbTab(
    val label: String,
    val line: ImageVector,
    val raster: Int? = null,
)

/**
 * 位图图标「未选中」时压到多暗。
 *
 * **别按 LbInk3 的亮度去压**(那是给线性图标用的:线性图标是描边,压到近黑依然读得出形状;
 * 而位图机器人有一大块深色面罩,压到那个亮度整张就糊成一团黑,少帅直接反馈"不点它就是黑的,不对")。
 * 现在只去饱和 + 轻微压暗,读成"灰的机器人",形状和五官都还在。
 */
private const val RASTER_ICON_INACTIVE_K = 0.72f

/**
 * 位图图标「未选中」时的着色:去饱和 + 乘一个系数。
 *
 * 为什么不直接降 alpha:`Image` 降 alpha 是往**背景色**靠,而浅色主题的背景(LbBg #F4F3EF,
 * 亮度约 0.95)比图标的骨白头(约 0.82)还亮 —— 降 alpha 只会让它更白、更看不见。
 * 去饱和 + 压暗则在深浅两个主题下都读成「灰的」,同时保留 3D 的立体明暗。
 *
 * 一个 4x5 ColorMatrix 一次做完:前 3 行都取整幅图的亮度权重(0.299/0.587/0.114)再乘 k,
 * 等价于「先转灰度、再乘 k」;第 4 行原样透传 alpha。
 */
private fun inkTone(): ColorFilter {
    val k = RASTER_ICON_INACTIVE_K
    val r = 0.299f * k
    val g = 0.587f * k
    val b = 0.114f * k
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                r, g, b, 0f, 0f,
                r, g, b, 0f, 0f,
                r, g, b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

/**
 * 底栏标签的字号。
 *
 * 底栏文字是「导航 chrome」，不是正文 —— 它得在 5 等分的窄格里**始终保持一行**。
 * 系统字号开到 2.0×（无障碍放大）时，11sp 的「智能管家」会折成两行：那一格的图标被顶高，
 * 整条底栏高低不齐，反而更难读。所以这里把底栏标签的放大倍率封顶在 1.4×
 * （渲染出来仍大于默认的 11sp，只是不再线性放大）；其余文字照常跟随系统字号。
 */
@Composable
private fun lbNavLabelSize(): TextUnit {
    val fontScale = LocalDensity.current.fontScale
    return (11f / max(1f, fontScale / 1.4f)).sp
}

@Composable
fun LbBottomBar(current: String, onSelect: (String) -> Unit) {
    val items = listOf(
        LbTab("今日", LbIcons.home2),
        LbTab("守护", LbIcons.shieldCheck),
        // 「智能管家」用 3D 小机器人位图(design/butler3d/make_nav_icon.py 生成)。
        // 线性图标那套 tint 对彩色位图没用,所以选中/未选中改成「全彩 / 去饱和压暗」来区分。
        LbTab("智能管家", LbIcons.messageCircle, R.drawable.lb_nav_butler),
        LbTab("家庭", LbIcons.users),
        LbTab("我的", LbIcons.user),
    )
    Column(Modifier.fillMaxWidth().background(LbBg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LbLine))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp),
        ) {
            items.forEach { item ->
                val active = item.label == current
                val tint by animateColorAsState(if (active) LbAccent else LbInk3, tween(160), label = "tabTint")
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .lbPressable(onClick = { onSelect(item.label) })
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val raster = item.raster
                    if (raster != null) {
                        Image(
                            painter = painterResource(raster),
                            contentDescription = item.label,
                            colorFilter = if (active) null else inkTone(),
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Icon(
                            item.line,
                            contentDescription = item.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        item.label,
                        fontSize = lbNavLabelSize(),
                        color = tint,
                        // 必须保持一行：系统字号开到 2.0× 时「智能管家」会折成两行，
                        // 把这一格的图标顶上去，整条底栏就高低不齐了。
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

/** 点击 + 长按(删除等)统一手势入口(带按压缩放微动效) */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.lbPressable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "lbPress",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (onLongClick == null) {
                Modifier.clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = onClick,
                )
            } else {
                Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
            },
        )
}

/** 仅长按(删除等)的手势入口,不产生点击涟漪 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.lbLongPress(onLongClick: () -> Unit): Modifier =
    this.combinedClickable(
        interactionSource = null,
        indication = null,
        onLongClick = onLongClick,
        onClick = {},
    )

/** 小节标题右侧的小加号按钮 */
@Composable
fun LbPlusButton(onClick: () -> Unit, contentDescription: String = "添加") {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(LbSurface2)
            .lbPressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(LbIcons.plus, contentDescription = contentDescription, tint = LbInk2, modifier = Modifier.size(15.dp))
    }
}

/* ---------------------------------------------------------------------------
 * 「搜索跳过来的那一条」的落地工具
 *
 * 场景：跨模块搜索点了一条「3 月的水电费」，翻到记账页 —— 如果只是把页面切过去，
 * 用户看到的是一屏和一小时前一样的列表，还得自己从几十条里找，等于没跳。
 *
 * 做法：进来先滚到那一条、亮一会儿、然后自己退掉。这里必须用 bringIntoView，
 * 因为本项目的列表页几乎全是 `Column + verticalScroll`（不是 LazyColumn，没有 index 可用），
 * 只有 bringIntoView 能不去猜每一项的高度。
 * ------------------------------------------------------------------------ */

/** 等列表量完一遍再滚：这一帧还没测量的话，bringIntoView 会抛。 */
private const val LB_HIGHLIGHT_SETTLE_MS = 140L

/** 高亮停留多久。太短会「一闪而过」看着像闪屏；太长又像这一条被永久选中了。 */
private const val LB_HIGHLIGHT_HOLD_MS = 2400L

/** 列表项从 [lbItemHighlight] 拿到的东西。 */
class LbHighlightState internal constructor(
    /** 这一条是不是刚被搜到的目标 —— 用来决定要不要底色 */
    val active: Boolean,
    /** 挂到这一条的 Modifier 上（负责把它滚进可见区） */
    val modifier: Modifier,
)

/**
 * 列表项里调用一次，拿到「要不要高亮」和「要高亮就挂这个 Modifier」。
 *
 * [highlightId] 传页面级那个待定位的 id；[onConsumed] 在高亮结束时回调，
 * 外层把状态置空 —— 否则下次从别处再进这一页，它还会亮一次，用户会以为是刚加的。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun lbItemHighlight(id: String, highlightId: String?, onConsumed: () -> Unit): LbHighlightState {
    val active = highlightId != null && id.isNotEmpty() && id == highlightId
    val requester = remember { BringIntoViewRequester() }
    val consumedNow = rememberUpdatedState(onConsumed)
    LaunchedEffect(active, highlightId) {
        if (!active) return@LaunchedEffect
        delay(LB_HIGHLIGHT_SETTLE_MS)
        try {
            requester.bringIntoView()
        } catch (e: Exception) {
            // 这一条刚好不在组合里（比如被筛掉了），滚不过去就算了，底色照样亮 ——
            // 不能因为滚不动就把整页搞崩。
        }
        delay(LB_HIGHLIGHT_HOLD_MS)
        consumedNow.value()
    }
    return LbHighlightState(active, Modifier.bringIntoViewRequester(requester))
}

/** 高亮底色（淡入淡出，别硬切）。[active] 为假时是透明，可直接叠在任何底色上。 */
@Composable
fun lbHighlightBg(active: Boolean): Color =
    animateColorAsState(
        targetValue = if (active) LbAmberSoft else Color.Transparent,
        animationSpec = tween(260),
        label = "hlBg",
    ).value

/**
 * 页面内留痕的提示条 —— 用来**替掉 Toast**（D12 / C8）。
 *
 * 为什么不用 Toast：它转瞬即逝、页面不留痕、也没有「那怎么办」的下一步；
 * 用户一走神就不知道刚才发生了什么，更别说反悔。
 * 这条留在页面上直到用户点「知道了」，带上 [action] 时它就是那次操作的后悔药。
 *
 * @param action 可选的动作文字（如「撤销」）。给了就显示一个可点的动作按钮。
 */
@Composable
fun LbNoticeBar(
    text: String,
    action: String? = null,
    onAction: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LbAccentSoft)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(LbIcons.circleCheck, contentDescription = null, tint = LbAccent, modifier = Modifier.size(16.dp))
        Text(
            text,
            fontSize = 11.5.sp,
            color = LbInk,
            lineHeight = 16.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        if (action != null) {
            Text(
                action,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = LbAccent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        Text(
            "知道了",
            fontSize = 11.5.sp,
            color = LbInk3,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
