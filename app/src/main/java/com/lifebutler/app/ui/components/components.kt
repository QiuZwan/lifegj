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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            Text(kicker, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xBCF6F5F0))
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
            .height(48.dp)
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
            ),
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
            .height(48.dp)
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
            ),
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

@Composable
fun LbBottomBar(current: String, onSelect: (String) -> Unit) {
    val items = listOf(
        "今日" to LbIcons.home2,
        "守护" to LbIcons.shieldCheck,
        "对话" to LbIcons.messageCircle,
        "家庭" to LbIcons.users,
        "我的" to LbIcons.user,
    )
    Column(Modifier.fillMaxWidth().background(LbBg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LbLine))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp),
        ) {
            items.forEach { (label, icon) ->
                val active = label == current
                val tint by animateColorAsState(if (active) LbAccent else LbInk3, tween(160), label = "tabTint")
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .lbPressable(onClick = { onSelect(label) })
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        label,
                        fontSize = 10.5.sp,
                        color = tint,
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
