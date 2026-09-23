package com.lifebutler.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifebutler.app.R
import com.lifebutler.app.data.ButlerStore
import com.lifebutler.app.data.SubScanner
import com.lifebutler.app.ui.components.ChipTone
import com.lifebutler.app.ui.components.HeroCard
import com.lifebutler.app.ui.components.IconBadge
import com.lifebutler.app.ui.components.LbCard
import com.lifebutler.app.ui.components.LbChip
import com.lifebutler.app.ui.components.LbConfirmDialog
import com.lifebutler.app.ui.components.LbField
import com.lifebutler.app.ui.components.LbGhostButton
import com.lifebutler.app.ui.components.LbInputDialog
import com.lifebutler.app.ui.components.LbListRow
import com.lifebutler.app.ui.components.LbPlusButton
import com.lifebutler.app.ui.components.LbPrimaryButton
import com.lifebutler.app.ui.components.LbTwoActionDialog
import com.lifebutler.app.ui.components.SectionHeader
import com.lifebutler.app.ui.components.lbHighlightBg
import com.lifebutler.app.ui.components.lbItemHighlight
import com.lifebutler.app.ui.components.lbPressable
import com.lifebutler.app.ui.icons.LbIcons
import com.lifebutler.app.ui.theme.LbAccent
import com.lifebutler.app.ui.theme.LbAccentSoft
import com.lifebutler.app.ui.theme.LbAmber
import com.lifebutler.app.ui.theme.LbAmberSoft
import com.lifebutler.app.ui.theme.LbDark
import com.lifebutler.app.ui.theme.LbInk
import com.lifebutler.app.ui.theme.LbInk2
import com.lifebutler.app.ui.theme.LbInk3
import com.lifebutler.app.ui.theme.LbLine
import com.lifebutler.app.ui.theme.LbLineStrong
import com.lifebutler.app.ui.theme.LbOnAccent
import com.lifebutler.app.ui.theme.LbOnDark
import com.lifebutler.app.ui.theme.LbOnDark2
import com.lifebutler.app.ui.theme.LbRust
import com.lifebutler.app.ui.theme.LbSurface
import com.lifebutler.app.ui.theme.LbSurface2

/* ── 02 扣款守护 ── */

@Composable
fun GuardScreen(onOpenDetail: (String) -> Unit, onOpenDuties: () -> Unit, onOpenScan: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteSubId by remember { mutableStateOf<String?>(null) }

    val active = store.subs.filter { !it.closing }
    val total = active.sumOf { it.amount }
    val nearest = active
        .mapNotNull { s -> store.daysUntil(s.nextDate)?.let { s to it } }
        .minByOrNull { it.second }
    val closingCount = store.subs.count { it.closing }
    val pendingObligations = store.obligations.count { !it.done }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(top = 10.dp)) {
            Text("扣款守护", style = MaterialTheme.typography.labelSmall)
            Text("钱花在哪，一眼看清", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
        }

        // 扣费线索待确认。
        // 通知命中关键词只说明「可能扣了一笔」，判不出「这是不是一笔订阅」—— 关键词里
        // 「付款」「支出」这类词太宽。原来命中就直接写真实扣费流水 + 加进守护清单，
        // 用户会看到守护页凭空多出一个订阅、账目多出一笔，只觉得"这东西在乱记我的账"。
        // 现在先摆在这里，由他点「认得」才落库。
        val claims = store.pendingClaims
        if (claims.isNotEmpty()) {
            LbCard(modifier = Modifier.padding(top = 12.dp), contentPadding = 14.dp) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(LbIcons.bell, LbAmberSoft, LbAmber, size = 32.dp)
                        Column(
                            Modifier
                                .padding(start = 10.dp)
                                .weight(1f),
                        ) {
                            Text(
                                "收到 ${claims.size} 条扣费线索",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LbInk,
                            )
                            Text(
                                "是订阅吗？你认了我才记账、才放进守护清单。",
                                fontSize = 11.5.sp,
                                color = LbInk3,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                    }
                    claims.take(3).forEach { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    c.name,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LbInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    (if (c.amount > 0) "¥" + store.fmtMoney(c.amount) + " · " else "") +
                                        SubScanner.fmtDate(c.at) + " · 来自通知",
                                    fontSize = 11.sp,
                                    color = LbInk3,
                                    modifier = Modifier.padding(top = 1.dp),
                                )
                            }
                            ClaimBtn("认得", primary = true) { store.confirmClaim(c.id) }
                            Spacer(Modifier.size(6.dp))
                            ClaimBtn("不是我的", primary = false) { store.dismissClaim(c.id) }
                        }
                    }
                    if (claims.size > 3) {
                        Text(
                            "还有 ${claims.size - 3} 条 —— 认掉前面几条就会露出来",
                            fontSize = 11.sp,
                            color = LbInk3,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        Surface(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(22.dp)),
            color = LbDark,
            shape = RoundedCornerShape(22.dp),
        ) {
            Row(Modifier.padding(18.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("每月订阅合计", fontSize = 11.sp, color = LbOnDark2)
                    Text(
                        "¥${store.fmtMoney(total)}",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbOnDark,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        when {
                            store.subs.isEmpty() -> "还没有订阅记录"
                            nearest != null -> "${store.subs.size} 笔订阅 · 最近一笔在${store.daysText(nearest.first.nextDate)}"
                            else -> "${store.subs.size} 笔订阅 · 全部在处理中"
                        },
                        fontSize = 11.5.sp,
                        color = LbOnDark2,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                LbChip("${java.time.LocalDate.now().monthValue} 月", ChipTone.OnDark)
            }
        }

        // 一键扫描入口
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenScan),
            shape = RoundedCornerShape(16.dp),
            color = LbSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, LbLine),
        ) {
            Row(
                Modifier.padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(LbIcons.scan, LbAccentSoft, LbAccent, size = 34.dp)
                Column(
                    Modifier
                        .padding(start = 11.dp)
                        .weight(1f),
                ) {
                    Text("一键扫描本机自动续费", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                    Text("读取扣费短信与已安装应用，找出你忘记的订阅", fontSize = 11.5.sp, color = LbInk3, modifier = Modifier.padding(top = 1.dp))
                }
                Icon(LbIcons.chevronRight, contentDescription = null, tint = LbInk3, modifier = Modifier.size(15.dp))
            }
        }

        // 动态提醒条:优先显示「关闭进行中」,其次是临近扣费
        if (closingCount > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(LbAccentSoft)
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(LbIcons.circleCheck, LbSurface, LbAccent, size = 34.dp)
                Column(
                    Modifier
                        .padding(start = 11.dp)
                        .weight(1f),
                ) {
                    Text("$closingCount 笔已在关闭中", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbAccent)
                    Text("若之后仍有扣费，点开这条核对一下", fontSize = 11.5.sp, color = LbInk2)
                }
            }
        } else if (nearest != null && nearest.second <= 3) {
            val s = nearest.first
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(LbAmberSoft)
                    .clickable { onOpenDetail(s.id) }
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(LbIcons.alertTriangle, LbAmberSoft, LbAmber, size = 34.dp)
                Column(
                    Modifier
                        .padding(start = 11.dp)
                        .weight(1f),
                ) {
                    Text("「${s.name}」${store.daysText(s.nextDate)}扣费", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbAmber)
                    Text("要关闭的话，现在还来得及", fontSize = 11.5.sp, color = LbAmber)
                }
                LbChip("处理", ChipTone.Amber)
            }
        }

        SectionHeader("订阅清单") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${store.subs.size} 笔", fontSize = 12.5.sp, color = LbInk3)
                Spacer(Modifier.width(9.dp))
                LbPlusButton(onClick = { showAdd = true }, contentDescription = "添加订阅")
            }
        }
        LbCard(contentPadding = 8.dp) {
            if (store.subs.isEmpty()) {
                Text(
                    "还没有订阅。点右上角 + 记下第一笔，我会在扣费前提醒你。",
                    fontSize = 12.sp,
                    color = LbInk3,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(12.dp),
                )
            } else {
                store.subs.forEach { s ->
                    LbListRow(
                        leading = {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (s.closing) LbAccentSoft else LbSurface2),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    s.name.take(1),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (s.closing) LbAccent else LbInk2,
                                )
                            }
                        },
                        title = s.name,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (s.source != "手动") {
                                    LbChip(s.source, if (s.source == "演示") ChipTone.Amber else ChipTone.Soft)
                                    Spacer(Modifier.width(8.dp))
                                }
                                if (store.hasChargeAfterClosing(s)) {
                                    LbChip("关闭后仍有扣费", ChipTone.Rust)
                                    Spacer(Modifier.width(8.dp))
                                }
                                if (s.closing) {
                                    LbChip("关闭中", ChipTone.Green)
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    val d = store.daysUntil(s.nextDate)
                                    when {
                                        d == null -> {
                                            LbChip("待补全", ChipTone.Soft)
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        d <= 1 -> {
                                            LbChip("明天扣", ChipTone.Amber)
                                            Spacer(Modifier.width(8.dp))
                                        }
                                    }
                                }
                                Text(if (s.amount <= 0) "金额待补" else "¥" + store.fmtMoney(s.amount), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                                Text(
                                    store.dateLabel(s.nextDate),
                                    fontSize = 11.5.sp,
                                    color = LbInk3,
                                    modifier = Modifier
                                        .width(46.dp)
                                        .padding(start = 8.dp),
                                )
                            }
                        },
                        onClick = { onOpenDetail(s.id) },
                        onLongClick = { deleteSubId = s.id },
                    )
                }
            }
        }

        LbCard(modifier = Modifier.padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(LbIcons.circleCheck, LbAccentSoft, LbAccent, size = 32.dp)
                Column(Modifier.padding(start = 11.dp)) {
                    Text(
                        if (store.closedCount == 0) "还没有标记关闭的订阅"
                        else "已标记关闭 ${store.closedCount} 笔订阅",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbInk,
                    )
                    Text(
                        if (store.monthlySaved > 0) "每月少支出 ¥${store.fmtMoney(store.monthlySaved)}"
                        else "在订阅详情点「我已关闭」，这里会开始累计",
                        fontSize = 11.5.sp,
                        color = LbInk3,
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenDuties)
                .background(LbSurface)
                .border(1.dp, LbLine, RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(LbIcons.calendarEvent, LbAccentSoft, LbAccent)
            Column(
                Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            ) {
                Text("义务时间线", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                Text(
                    if (pendingObligations > 0) "$pendingObligations 项到期事务已排好队" else "把要到期的事放进来，我帮你数日子",
                    fontSize = 12.sp,
                    color = LbInk2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(LbIcons.chevronRight, contentDescription = null, tint = LbInk3, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showAdd) {
        LbInputDialog(
            title = "添加订阅",
            fields = listOf(
                LbField("名称", "如：视频会员"),
                LbField("每月金额（元）", "如：25", numeric = true),
                LbField("下次扣费日期", "点这里选择日期", isDate = true),
            ),
            onDismiss = { showAdd = false },
            onConfirm = { v ->
                val name = v.getOrElse(0) { "" }
                val amountStr = v.getOrElse(1) { "" }
                val dateStr = v.getOrElse(2) { "" }
                val amount = amountStr.toDoubleOrNull()
                when {
                    name.isEmpty() -> "写下订阅名称吧"
                    amount == null || amount <= 0 -> "金额填数字，比如 25"
                    dateStr.isEmpty() -> "选个下次扣费日期吧"
                    else -> {
                        store.addSub(name, amount, dateStr)
                        showAdd = false
                        null
                    }
                }
            },
        )
    }

    deleteSubId?.let { id ->
        val sub = store.subs.firstOrNull { it.id == id }
        val name = sub?.name ?: "这笔订阅"
        LbConfirmDialog(
            title = "删除「$name」？",
            text = "删除后不再提醒扣费；如果是扫描/通知加入的，之后也不会再自动加回。",
            onDismiss = { deleteSubId = null },
            onConfirm = {
                store.removeSub(id)
                if (sub != null && (sub.source == "扫描" || sub.source == "通知")) {
                    store.dismissName(sub.name)
                }
                deleteSubId = null
            },
        )
    }
}

/* ── 03 订阅详情 ── */

@Composable
fun SubscriptionDetailScreen(
    subId: String?,
    onBack: () -> Unit,
    /** 从搜索点进来的话：订阅命中就是本订阅的 id，扣费流水命中就是**那一笔**的 id */
    highlightId: String? = null,
    onHighlightConsumed: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    val sub = store.subs.firstOrNull { it.id == subId }

    if (sub == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var showEdit by remember { mutableStateOf(false) }
    var showAddCharge by remember { mutableStateOf(false) }
    var chargeDeleteId by remember { mutableStateOf<String?>(null) }
    var showTrial by remember { mutableStateOf(false) }

    // 订阅命中时 highlightId 就是**本订阅自己**的 id：整页就是它，没有「某一行」要滚，
    // 直接算定位完成 —— 不然这个待定位状态会一直挂着，下次再进来还会亮一下。
    LaunchedEffect(highlightId) {
        if (highlightId != null && highlightId == sub.id) onHighlightConsumed()
    }

    val hasDate = sub.nextDate.isNotBlank()
    val dayOfMonth = store.parseDate(sub.nextDate)?.dayOfMonth
    val days = store.daysUntil(sub.nextDate)

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
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LbIcons.chevronLeft, contentDescription = "返回", tint = LbInk2, modifier = Modifier.size(20.dp))
                Text("订阅详情", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            MiniGhost("编辑") { showEdit = true }
        }

        LbCard(contentPadding = 16.dp, modifier = Modifier.padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(LbAmberSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(sub.name.take(1), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbAmber)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(sub.name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                    Text(
                        if (hasDate) "自动续费 · 每月 $dayOfMonth 日" else "自动续费 · 扣费日待补全",
                        fontSize = 12.sp,
                        color = if (hasDate) LbInk3 else LbAmber,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (sub.closing) {
                    LbChip("关闭处理中", ChipTone.Green)
                } else if (days == null) {
                    LbChip("扣费日待补全", ChipTone.Soft)
                } else {
                    LbChip(
                        if (days <= 1) "明天自动扣费" else "${store.daysText(sub.nextDate)}自动扣费",
                        if (days <= 3) ChipTone.Amber else ChipTone.Soft,
                    )
                }
                LbChip("每月 ¥${store.fmtMoney(sub.amount)}", ChipTone.Soft)
                if (sub.source != "手动") LbChip(sub.source, if (sub.source == "演示") ChipTone.Amber else ChipTone.Soft)
            }
            if (sub.source == "演示") {
                Text(
                    "这是演示数据，不是你的真实订阅；长按清单里的这一条可以删掉。",
                    fontSize = 11.5.sp,
                    color = LbAmber,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (store.hasChargeAfterClosing(sub)) {
                Text(
                    "关闭后仍收到过「${sub.name}」的扣费记录，建议回平台再核对一次是否真的取消了。",
                    fontSize = 11.5.sp,
                    color = LbRust,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (sub.amount <= 0) {
                Text(
                    "金额未识别，点右上角「编辑」补全",
                    fontSize = 11.5.sp,
                    color = LbAmber,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // 单条提醒设置。订阅之间金额差得远（6 元的 iCloud 和 200 多的会员），
        // 统一阈值总有人不合适，所以每条都能自己定提前量。
        LbCard(contentPadding = 16.dp, modifier = Modifier.padding(top = 10.dp)) {
            Column {
                Text("提醒", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                Text(
                    "这一笔提前几天进每日简报。「默认」= 跟随「我的 → 提醒与免打扰」里的设置。",
                    fontSize = 11.5.sp,
                    color = LbInk3,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                LbRemindAheadRow(
                    label = "提前",
                    value = sub.remindAhead,
                    options = listOf(0, 1, 3, 7, 15),
                    onPick = { store.setSubRemindAhead(sub.id, it) },
                )
                val trial = store.trialDaysLeft(sub)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("试用截止日", fontSize = 12.5.sp, color = LbInk)
                        Text(
                            when {
                                sub.trialUntil.isBlank() ->
                                    "没设。很多会员是「免费 7 天，之后自动续费」—— 记上到期日，我提前一天提醒你，免得忘了取消。"
                                trial == null -> "${store.fmtCn(sub.trialUntil)}（已过）"
                                else -> "还剩 $trial 天 · ${store.fmtCn(sub.trialUntil)}"
                            },
                            fontSize = 11.5.sp,
                            color = if (trial != null && trial <= 1) LbAmber else LbInk3,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    MiniGhost(if (sub.trialUntil.isBlank()) "设置" else "改") { showTrial = true }
                }
                // 涨价：只在**真的有两笔扣费可比**时才说。没有任何扣费记录时这里什么都不显示，
                // 免得看起来像「它知道要涨价」—— 那是推算，不是记录。
                val jump = store.priceJumpOf(sub.name)
                if (jump != null) {
                    Text(
                        "最近一笔比上一笔贵了 ¥${store.fmtMoney(jump)}（都来自你记的扣费记录）。",
                        fontSize = 11.5.sp,
                        color = LbRust,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        Surface(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(22.dp)),
            color = LbDark,
            shape = RoundedCornerShape(22.dp),
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("下次扣费", fontSize = 11.sp, color = LbOnDark2)
                    Text(
                        "¥%.2f".format(sub.amount),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbOnDark,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        if (hasDate) "${store.fmtCn(sub.nextDate)} · 每年累计 ¥${store.fmtMoney(sub.amount * 12)}"
                        else "扣费日还没补全 · 每年累计 ¥${store.fmtMoney(sub.amount * 12)}",
                        fontSize = 11.5.sp,
                        color = LbOnDark2,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                LbChip(if (hasDate) store.daysText(sub.nextDate) else "待补全", ChipTone.OnDark)
            }
        }

        val subCharges = store.chargesOf(sub.name)
        SectionHeader("扣费记录") {
            Text(
                if (subCharges.isEmpty()) "暂无记录" else "共 ${subCharges.size} 笔",
                fontSize = 12.sp,
                color = LbInk3,
            )
        }
        LbCard(contentPadding = 8.dp) {
            if (subCharges.isEmpty()) {
                Text(
                    "还没有这笔订阅的扣费记录。收到扣费短信或通知时这里会自动记下；也可以手动补记一笔。",
                    fontSize = 12.sp,
                    color = LbInk3,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(12.dp),
                )
            } else {
                subCharges.forEach { c ->
                    // 搜索点的是「这一笔扣费」的话，把这一行滚进来并亮一下
                    val hl = lbItemHighlight(c.id, highlightId, onHighlightConsumed)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .then(hl.modifier)
                            .background(lbHighlightBg(hl.active))
                            .lbPressable(onClick = { }, onLongClick = { chargeDeleteId = c.id })
                            .padding(vertical = 9.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(store.fmtCn(c.date), fontSize = 12.sp, color = LbInk2)
                        if (c.source != "手动") {
                            Spacer(Modifier.width(6.dp))
                            LbChip(c.source, ChipTone.Soft)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("¥%.2f".format(c.amount), fontSize = 12.sp, color = LbInk)
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            LbGhostButton("手动补记一笔扣费", onClick = { showAddCharge = true }, modifier = Modifier.fillMaxWidth())
        }

        if (showEdit) {
        LbInputDialog(
            title = "编辑订阅",
            fields = listOf(
                LbField("名称", "如：视频会员"),
                LbField("每月金额（元）", "如：25", numeric = true),
                LbField("下次扣费日期", "点这里选择日期", isDate = true),
            ),
            initial = listOf(sub.name, if (sub.amount > 0) store.fmtMoney(sub.amount) else "", sub.nextDate),
            onDismiss = { showEdit = false },
            onConfirm = { v ->
                val name = v.getOrElse(0) { "" }
                val amountStr = v.getOrElse(1) { "" }
                val amount = if (amountStr.isEmpty()) 0.0 else (amountStr.toDoubleOrNull() ?: -1.0)
                val date = v.getOrElse(2) { "" }
                when {
                    name.isEmpty() -> "写下名称吧"
                    amount < 0 -> "金额填数字，比如 25（可留空）"
                    else -> {
                        store.updateSub(sub.id, name, amount, date.ifEmpty { sub.nextDate })
                        showEdit = false
                        null
                    }
                }
            },
        )
    }

    LbCard(modifier = Modifier.padding(top = 10.dp), contentPadding = 16.dp) {
            if (!sub.closing) {
                CancelGuide(store = store, subId = sub.id, onDone = { })
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(LbIcons.circleCheck, LbAccentSoft, LbAccent, size = 36.dp)
                    Column(
                        Modifier
                            .padding(start = 11.dp)
                            .weight(1f),
                    ) {
                        Text("已标记为「关闭中」", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                        Text("取消需在平台完成；之后若仍有扣费，扫描时会提醒你复核", fontSize = 11.5.sp, color = LbInk3)
                    }
                }
            }
        }
        if (showAddCharge) {
            LbInputDialog(
                title = "补记一笔扣费",
                fields = listOf(
                    LbField("金额（元）", "如：25", numeric = true),
                    LbField("扣费日期", "点这里选择日期", isDate = true),
                ),
                initial = listOf(if (sub.amount > 0) store.fmtMoney(sub.amount) else "", java.time.LocalDate.now().toString()),
                onDismiss = { showAddCharge = false },
                onConfirm = { v ->
                    val a = v.getOrElse(0) { "" }.toDoubleOrNull()
                    val d = v.getOrElse(1) { "" }
                    when {
                        a == null || a <= 0 -> "金额填数字，比如 25"
                        d.isEmpty() -> "选个日期吧"
                        else -> {
                            store.addCharge(sub.name, a, d, "手动")
                            showAddCharge = false
                            null
                        }
                    }
                },
            )
        }

        chargeDeleteId?.let { id ->
            val c = store.charges.firstOrNull { it.id == id }
            LbConfirmDialog(
                title = "删除这条扣费记录？",
                text = if (c != null) "${store.fmtCn(c.date)} · ¥${store.fmtMoney(c.amount)}；删除后不再计入统计。" else "删除后不再计入统计。",
                onDismiss = { chargeDeleteId = null },
                onConfirm = {
                    store.removeCharge(id)
                    chargeDeleteId = null
                },
            )
        }

        if (showTrial) {
            LbInputDialog(
                title = "试用截止日",
                fields = listOf(
                    LbField(
                        "到期日",
                        "留空表示不是试用",
                        isDate = true,
                        dateClearable = true,
                    ),
                ),
                initial = listOf(sub.trialUntil),
                onDismiss = { showTrial = false },
                onConfirm = { v ->
                    // 清空 = 取消试用标记（不是「今天到期」）—— 这两个意思差很远，别混
                    store.setSubTrial(sub.id, v.getOrElse(0) { "" })
                    showTrial = false
                    null
                },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/* ── 通用:诚实取消引导(任何第三方 App 都无法代你取消,这里带你到入口并复核) ── */

private const val CANCEL_STEPS = "关闭自动续费步骤:1) 支付宝:我的→设置→支付设置→免密支付/自动扣款;2) 微信:我→服务→钱包→支付设置→自动续费;3) 商家 App:我的→会员/自动续费管理。找到本服务并关闭即可。"

@Composable
fun CancelGuide(store: ButlerStore, subId: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val sub = store.subs.firstOrNull { it.id == subId }
    if (sub == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    Text("去对应平台关闭自动续费", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
    Text(
        "① 打开对应平台（支付宝 / 微信 / 商家 App）\n② 找到「自动扣款」或「自动续费管理」\n③ 关闭本服务",
        fontSize = 12.5.sp,
        color = LbInk2,
        lineHeight = 20.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        "取消只能由你在平台完成；管家做的是把步骤带到手，并在之后帮你复核是否真的停了。",
        fontSize = 11.5.sp,
        color = LbInk3,
        lineHeight = 17.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SubScanner.installedPlatforms(ctx).forEach { (name, installed) ->
            if (installed) {
                MiniGhost(name) {
                    val pkg = if (name == "支付宝") "com.eg.android.AlipayGphone" else "com.tencent.mm"
                    if (!SubScanner.launchPackage(ctx, pkg)) {
                        Toast.makeText(ctx, "未安装或无法打开，可直接在桌面找到该应用", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        val mp = SubScanner.merchantPackage(sub.name)
        if (mp != null && SubScanner.isInstalled(ctx, mp)) {
            MiniGhost("打开${sub.name}") {
                if (!SubScanner.launchPackage(ctx, mp)) {
                    Toast.makeText(ctx, "未安装或无法打开该应用", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        LbGhostButton(
            "复制关闭步骤",
            onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("关闭步骤", CANCEL_STEPS))
                Toast.makeText(ctx, "关闭步骤已复制", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f),
        )
        LbPrimaryButton(
            "我已关闭，帮我复核",
            onClick = {
                store.markSubClosing(sub.id)
                onDone()
            },
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun MiniGhost(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, com.lifebutler.app.ui.theme.LbLineStrong, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 12.sp, color = LbInk)
    }
}

/* ── 04 义务时间线 ── */

@Composable
fun ObligationsScreen(
    onBack: () -> Unit,
    /** 从搜索点进来时要落在哪一条 */
    highlightId: String? = null,
    onHighlightConsumed: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var editId by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("全部") }

    val allTags = listOf("全部") + store.obligations.map { it.tag }.filter { it.isNotEmpty() }.distinct()
    val list = store.obligations
        .sortedWith(compareBy({ it.done }, { store.daysUntil(it.date) ?: Long.MAX_VALUE }))
        .filter { filter == "全部" || it.tag == filter }
    val nearestPending = store.obligations.filter { !it.done }
        .mapNotNull { store.daysUntil(it.date) }
        .minOrNull()

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
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LbIcons.chevronLeft, contentDescription = "返回", tint = LbInk2, modifier = Modifier.size(20.dp))
                Text("义务时间线", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            LbPlusButton(onClick = { showAdd = true }, contentDescription = "添加义务")
        }

        HeroCard(
            painter = painterResource(R.drawable.archive_papers),
            height = 130.dp,
            kicker = "义务时间线",
            title = "该办的事，排好了队",
            sub = if (nearestPending != null) "离最近的一件，还有 $nearestPending 天" else "都处理完了，享受当下",
            modifier = Modifier.padding(top = 10.dp),
        )

        Row(
            Modifier
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            allTags.forEach { tag ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { filter = tag },
                ) {
                    LbChip(tag, if (filter == tag) ChipTone.Green else ChipTone.Soft)
                }
            }
        }

        LbCard(contentPadding = 14.dp, modifier = Modifier.padding(top = 10.dp)) {
            if (list.isEmpty()) {
                Text(
                    if (store.obligations.isEmpty()) "还没有排期。点右上角 + 记下第一件要到期的事。" else "这个分类下暂时没有事项。",
                    fontSize = 12.sp,
                    color = LbInk3,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(6.dp),
                )
            } else {
                list.forEachIndexed { i, d ->
                    val days = store.daysUntil(d.date)
                    // 搜索跳过来的那一条：滚进来 + 亮一下
                    val hl = lbItemHighlight(d.id, highlightId, onHighlightConsumed)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .then(hl.modifier)
                            .background(lbHighlightBg(hl.active))
                            .lbPressable(
                                onClick = { store.toggleObligation(d.id) },
                                onLongClick = { menuId = d.id },
                            ),
                    ) {
                        Column(
                            Modifier.width(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val dotColor = when {
                                d.done -> LbInk3
                                days != null && days <= 14 -> LbAmber
                                else -> LbAccent
                            }
                            Box(
                                Modifier
                                    .padding(top = 6.dp)
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(if (d.done || (days != null && days <= 14)) dotColor else Color.Transparent)
                                    .border(2.5.dp, dotColor, CircleShape),
                            )
                            if (i < list.size - 1) {
                                Box(
                                    Modifier
                                        .width(2.dp)
                                        .height(44.dp)
                                        .background(LbLineStrong),
                                )
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 6.dp, bottom = 12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (d.done) "${d.title} · 已完成" else "${d.title} · ${store.daysText(d.date)}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (d.done) LbInk3 else LbInk,
                                    modifier = Modifier.weight(1f),
                                )
                                LbChip(
                                    when {
                                        d.done -> "已完成"
                                        days != null && days <= 14 -> "临近"
                                        else -> d.tag
                                    },
                                    when {
                                        d.done -> ChipTone.Soft
                                        days != null && days <= 14 -> ChipTone.Amber
                                        else -> ChipTone.Soft
                                    },
                                )
                            }
                            if (d.note.isNotEmpty()) {
                                Text(d.note, fontSize = 11.5.sp, color = LbInk3, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
        }

        LbCard(modifier = Modifier.padding(top = 10.dp), contentPadding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(LbIcons.bell, LbAccentSoft, LbAccent, size = 30.dp)
                Text(
                    "点一下标记完成，长按删除；到期前会自动提醒",
                    fontSize = 12.sp,
                    color = LbInk2,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }

        // 逐条提前量：默认吃全局设置，重要的（车险续保、体检预约）可以单条提前更久
        LbRemindAheadSection(
            title = "提前多久提醒我",
            hint = "「默认」= 跟随「我的 → 提醒与免打扰」里的「到期提前」。" +
                "像车险续保这种要留出比价时间的，可以单独调到 15 / 30 天。",
            items = store.obligations.filter { !it.done }
                .map { Triple(it.id, it.title, it.remindAhead) },
            onPick = { id, days -> store.setObligationRemindAhead(id, days) },
        )
        Spacer(Modifier.height(16.dp))
    }

    if (showAdd) {
        LbInputDialog(
            title = "添加义务",
            fields = listOf(
                LbField("事项", "如：驾照换证"),
                LbField("到期日期", "点这里选择日期", isDate = true),
                LbField("备注（可选）", "如：体检任意网点可做"),
                LbField("分类（可选）", "证件 / 保单 / 车辆 / 宠物"),
            ),
            onDismiss = { showAdd = false },
            onConfirm = { v ->
                val title = v.getOrElse(0) { "" }
                val date = v.getOrElse(1) { "" }
                val note = v.getOrElse(2) { "" }
                val tag = v.getOrElse(3) { "" }
                when {
                    title.isEmpty() -> "写下事项名称吧"
                    date.isEmpty() -> "选个到期日期吧"
                    else -> {
                        store.addObligation(title, date, note, tag.ifEmpty { "其他" })
                        showAdd = false
                        null
                    }
                }
            },
        )
    }

    menuId?.let { id ->
        val d = store.obligations.firstOrNull { it.id == id }
        LbTwoActionDialog(
            title = d?.title ?: "这条义务",
            text = "要修改到期日或备注，还是删除？",
            actionA = "编辑",
            actionB = "删除",
            onA = { editId = id; menuId = null },
            onB = { deleteId = id; menuId = null },
            onDismiss = { menuId = null },
        )
    }

    editId?.let { id ->
        val d = store.obligations.firstOrNull { it.id == id }
        LbInputDialog(
            title = "编辑义务",
            fields = listOf(
                LbField("事项", "如：驾照换证"),
                LbField("到期日期", "点这里选择日期", isDate = true),
                LbField("备注（可选）", "如：体检任意网点可做"),
                LbField("分类（可选）", "证件 / 保单 / 车辆 / 宠物"),
            ),
            initial = listOf(d?.title ?: "", d?.date ?: "", d?.note ?: "", d?.tag ?: ""),
            onDismiss = { editId = null },
            onConfirm = { v ->
                val title = v.getOrElse(0) { "" }
                val date = v.getOrElse(1) { "" }
                val note = v.getOrElse(2) { "" }
                val tag = v.getOrElse(3) { "" }
                when {
                    title.isEmpty() -> "写下事项名称吧"
                    date.isEmpty() -> "选个到期日期吧"
                    else -> {
                        store.updateObligation(id, title, date, note, tag)
                        editId = null
                        null
                    }
                }
            },
        )
    }

    deleteId?.let { id ->
        LbConfirmDialog(
            title = "删除这条义务？",
            text = "删除后不再倒计时提醒。",
            onDismiss = { deleteId = null },
            onConfirm = {
                store.removeObligation(id)
                deleteId = null
            },
        )
    }
}

/** 待认领线索上的小按钮：认得 / 不是我的 */
@Composable
private fun ClaimBtn(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (primary) LbAccentSoft else LbSurface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) LbAccent else LbInk2,
        )
    }
}
