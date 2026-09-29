package com.lifebutler.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifebutler.app.data.ButlerStore
import com.lifebutler.app.ui.components.LbCard
import com.lifebutler.app.ui.components.LbChip
import com.lifebutler.app.ui.components.ChipTone
import com.lifebutler.app.ui.components.lbPressable
import com.lifebutler.app.ui.icons.LbIcons
import com.lifebutler.app.ui.theme.LbAccent
import com.lifebutler.app.ui.theme.LbInk
import com.lifebutler.app.ui.theme.LbInk2
import com.lifebutler.app.ui.theme.LbInk3
import com.lifebutler.app.ui.theme.LbLine
import com.lifebutler.app.ui.theme.LbSurface2
import kotlinx.coroutines.delay

/** 结果分组的先后顺序：常查的排前面。表里没有的类型（以后新加的）自动排到最后。 */
private val LB_SEARCH_ORDER = listOf(
    "订阅", "扣费流水", "到期事务", "待办", "备忘", "记账",
    "家人", "纪念日", "档案", "相册", "对话",
)

/**
 * 敲字停顿多久才算「这一句写完了」。
 *
 * 180ms 是两头的折中：往短了调，中文输入法的候选词每上屏一次都会触发一轮全表扫；
 * 往长了调，用户会觉得「我都停手了它还没反应」。
 */
private const val LB_SEARCH_DEBOUNCE_MS = 180L

/**
 * 一页最多画多少条结果。
 *
 * 全部画出来在极端数据下（比如搜个「的」字命中上千条）会把这一页的组合开销拉爆。
 * 超出的不丢：类目计数与总数照报，末尾提示缩小关键词。
 */
private const val LB_SEARCH_RENDER_LIMIT = 200

/**
 * 跨模块搜索页（overlay key = "search"）。
 *
 * 空查询**什么都不显示**（只给提示），不是把全部记录铺出来 ——
 * 一进来就是几百条，比空白更让人不知道该干嘛。
 *
 * [onOpen] 的第三个参数是「要定位到的那一条的 id」：订阅走 [subId] 落详情页，
 * 其余类型各自翻到对应模块并把那一条滚进可见区、短暂高亮（见 `lbItemHighlight`）。
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpen: (route: String, subId: String?, highlightId: String?) -> Unit,
) {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    val kb = LocalSoftwareKeyboardController.current
    var q by remember { mutableStateOf("") }
    // 防抖：真正拿去搜的是 settled（滞后于输入框一点点），q 只负责显示。
    var settled by remember { mutableStateOf("") }
    LaunchedEffect(q) {
        if (q.isBlank()) {
            settled = ""
            return@LaunchedEffect
        }
        delay(LB_SEARCH_DEBOUNCE_MS)
        settled = q
    }
    // 缓存键 = 关键字 + 「本机数据动过没有」。少了后面那个，边搜边改会给出过期结果。
    val stamp = store.dataStamp()
    val hits = remember(settled, stamp) { store.searchAll(settled) }
    val pending = q != settled
    // 分组与类目排序跟着 hits 一起 remember：敲一个字只该付一次「分组」的钱，
    // 不能之后每一帧重组（防抖、计数条变化）都重新 groupBy 一遍。
    val (grouped, kinds) = remember(settled, stamp) {
        val g = hits.groupBy { it.kind }
        val order = LB_SEARCH_ORDER.filter { g.containsKey(it) } +
            g.keys.filter { it !in LB_SEARCH_ORDER }.sorted()
        g to order
    }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .lbPressable(onClick = onBack)
                    .padding(6.dp),
            ) {
                Icon(LbIcons.chevronLeft, contentDescription = "返回", tint = LbInk2, modifier = Modifier.size(20.dp))
            }
            Text("搜一搜", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
        }

        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            placeholder = { Text("订阅、扣费、备忘、记账、档案…都能搜", fontSize = 12.5.sp, color = LbInk3) },
            singleLine = true,
            leadingIcon = { Icon(LbIcons.search, contentDescription = null, tint = LbInk3, modifier = Modifier.size(17.dp)) },
            trailingIcon = {
                if (q.isNotEmpty()) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .lbPressable(onClick = { q = "" })
                            .padding(6.dp),
                    ) {
                        Icon(LbIcons.x, contentDescription = "清空", tint = LbInk3, modifier = Modifier.size(15.dp))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .focusRequester(focus),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LbAccent,
                unfocusedBorderColor = LbLine,
                cursorColor = LbAccent,
            ),
            textStyle = TextStyle(fontSize = 14.sp, color = LbInk),
        )

        when {
            q.isBlank() -> {
                Spacer(Modifier.height(14.dp))
                LbCard(contentPadding = 14.dp) {
                    Text(
                        "输入两个字就能跨模块找。所有匹配都在本机完成，不联网。",
                        fontSize = 12.sp,
                        color = LbInk3,
                        lineHeight = 19.sp,
                    )
                }
            }
            hits.isEmpty() && pending -> {
                // 刚敲完、防抖还没到点：这时候说「没找到」是在撒谎（搜的还是上一个词），
                // 所以单给一行「正在找」。
                Spacer(Modifier.height(14.dp))
                LbCard(contentPadding = 14.dp) {
                    Text("正在找…", fontSize = 12.sp, color = LbInk3)
                }
            }
            hits.isEmpty() -> {
                Spacer(Modifier.height(14.dp))
                LbCard(contentPadding = 14.dp) {
                    Text("没有找到「$settled」。换个词试试，或者确认一下是不是还没记过。", fontSize = 12.sp, color = LbInk3)
                }
            }
            else -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (pending) "正在找…" else "共 ${hits.size} 条，分 ${kinds.size} 类",
                    fontSize = 11.5.sp,
                    color = LbInk3,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp, bottom = 2.dp),
                )
                var rendered = 0
                kinds.forEach { kind ->
                    if (rendered >= LB_SEARCH_RENDER_LIMIT) return@forEach
                    val list = grouped[kind] ?: return@forEach
                    val visible = list.take(LB_SEARCH_RENDER_LIMIT - rendered)
                    rendered += visible.size
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 6.dp, start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(kind, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                        Text(
                            "${list.size}",
                            fontSize = 11.sp,
                            color = LbInk3,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    LbCard(contentPadding = 6.dp) {
                        visible.forEachIndexed { i, h ->
                            if (i > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                        .height(1.dp)
                                        .background(LbLine),
                                )
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .lbPressable(onClick = {
                                        kb?.hide()
                                        // 前两个参数决定「翻到哪一页」，第三个决定「到了之后落在哪一条」。
                                        // 订阅是唯一有详情页的类型，它的 subId 与 id 是同一个值。
                                        onOpen(h.route, h.subId, h.id)
                                    })
                                    .padding(horizontal = 9.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        h.title.ifBlank { "（无标题）" },
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = LbInk,
                                        maxLines = 1,
                                    )
                                    if (h.sub.isNotBlank()) {
                                        Text(
                                            h.sub,
                                            fontSize = 11.sp,
                                            color = LbInk3,
                                            maxLines = 1,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                                LbChip(h.kind, ChipTone.Soft)
                                Icon(
                                    LbIcons.chevronRight,
                                    contentDescription = null,
                                    tint = LbInk3,
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .size(15.dp),
                                )
                            }
                        }
                    }
                }
                // 渲染兜底：类目计数与总数仍按全量报，超出的不画、末尾说清楚
                if (hits.size > rendered) {
                    Text(
                        "还有 ${hits.size - rendered} 条，请缩小关键词",
                        fontSize = 11.5.sp,
                        color = LbInk3,
                        modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
