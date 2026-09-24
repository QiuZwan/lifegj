package com.lifebutler.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.lifebutler.app.R
import com.lifebutler.app.data.A11yScanner
import com.lifebutler.app.data.ButlerClaim
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
import com.lifebutler.app.ui.components.LbNoticeBar
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
import kotlinx.coroutines.launch

/* ── 02 扣款守护 ── */

@Composable
fun GuardScreen(onOpenDetail: (String) -> Unit, onOpenDuties: () -> Unit, onOpenScan: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var showAdd by remember { mutableStateOf(false) }
    var deleteSubId by remember { mutableStateOf<String?>(null) }
    // 「已处理」（关闭中）那一组默认收起 —— 用户来守护页要看的是「还在扣钱的」。
    var showClosed by remember { mutableStateOf(false) }

    // 结论 chip 点一下要滚到「待处理」那一块。这里不用猜坐标：
    // 让那块自己 onGloballyPositioned 报位置，滚动量 = 当前滚动值 + 它在屏幕上的 y。
    var claimsY by remember { mutableStateOf(0f) }
    // 「要复核」那组（关了却还在扣费）的位置：结论 chip 在没有待认领线索时要滚到这里。
    var closedY by remember { mutableStateOf(0f) }
    // 「以后别再提」必须能撤销：dismissed 集合**只进不出**是 v2.18 前最大的一处不闭环。
    // 记住刚放进去的商户名，给一次 Undo（撤销就是把它移出集合，零成本）。
    var lastDismissed by remember { mutableStateOf<String?>(null) }
    // 页面内留痕的提示条，替掉转瞬即逝的 Toast（C8）。空 = 不显示。
    var notice by remember { mutableStateOf<String?>(null) }
    // ── D11 内联补全：缺金额或缺日期时，直接在那一行的那个词上点一下就补，
    // 不用「进详情 → 编辑 → 在三字段弹窗里小心别改错名称」（C7）。
    var fixSubId by remember { mutableStateOf<String?>(null) }
    var fixField by remember { mutableStateOf("") }

    // ⚠️ 全页的口径就是这三个:
    //   active  = 清单里列的、合计里算的、右上角数的「在用订阅」；
    //   closing = 已标记关闭的,单独收进「已处理」,不进合计;
    //   total   = 只加 active。
    // 原来合计用 active、而「N 笔」和列表用全部 subs,同一个数字在页面上两副面孔。
    val active = store.subs.filter { !it.closing }
    val closingList = store.subs.filter { it.closing }
    val total = active.sumOf { it.amount }
    val nearest = active
        .mapNotNull { s -> store.daysUntil(s.nextDate)?.let { s to it } }
        .minByOrNull { it.second }
    val closingCount = closingList.size
    val pendingObligations = store.obligations.count { !it.done }
    // 空态要说清是哪一种「空」:没开权限 / 开了没扫过 / 扫过确实没有。
    // 判据只能是「有没有任何一路现在还读得到」—— 不猜、不假定。
    val canScan = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED ||
        SubScanner.notificationAccessGranted(ctx) || A11yScanner.enabled(ctx)

    // ── D7：四路来源现在各自是什么状态。原来这些只在扫描页能看到，
    // 于是「我明明开了权限它却没记到」在守护页得不到任何线索（C1/C3）。
    val smsOn = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    val notifOn = SubScanner.notificationAccessGranted(ctx)
    val notifLive = SubScanner.notificationListenerConnected() != false
    val a11yOn = A11yScanner.enabled(ctx)
    val a11yLive = A11yScanner.connected != false

    // ── D1/D6：首屏要回答的两个钱数。
    // 「接下来要扣」只看 active + 有确定日期的；「本月已扣」只认真实扣费流水，不推算。
    val due = store.dueBeforeNextMonthEnd()
    val chargedThisMonth = store.chargedThisMonth()
    // 「要处理」= 待认领的线索 + 关了却还在扣费、需要回平台复核的。
    val toRecheck = store.subs.count { it.closing && store.hasChargeAfterClosing(it) }
    val toHandle = store.pendingClaims.size + toRecheck

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(top = 10.dp)) {
            Text("扣款守护", style = MaterialTheme.typography.labelSmall)
            Text("钱花在哪，一眼看清", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
        }

        // ── D7「数据从哪来」状态条 ──
        // 四路来源的开关态原来只在扫描页能看见。于是在守护页问「我的订阅怎么没自动出现」
        // 的人得不到任何线索 —— 而「我明明开了权限它却没记到」最常见的真相是
        // **服务被系统断开了**（C1/C3），这条恰好能一眼看出来。
        SourceStatusBar(
            smsOn = smsOn,
            notifOn = notifOn,
            notifLive = notifLive,
            a11yOn = a11yOn,
            a11yLive = a11yLive,
            lastScanAt = store.lastScanAt,
            lastScanText = if (store.lastScanAt > 0L) store.fmtCnAt(store.lastScanAt) else "",
            onOpenScan = onOpenScan,
        )

        // ── D12/C8：页面内留痕的提示条 ──
        // 原来这类反馈全靠 Toast：转瞬即逝、页面不留痕、也没有「那怎么办」的下一步。
        // 带「撤销」的时候它就是那次操作的唯一后悔药。
        notice?.let { msg ->
            LbNoticeBar(
                text = msg,
                action = if (lastDismissed != null) "撤销" else null,
                onAction = {
                    lastDismissed?.let { store.undismissName(it) }
                    lastDismissed = null
                    notice = null
                },
                onDismiss = { lastDismissed = null; notice = null },
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        // 扣费线索待确认。
        // 通知命中关键词只说明「可能扣了一笔」，判不出「这是不是一笔订阅」—— 关键词里
        // 「付款」「支出」这类词太宽。原来命中就直接写真实扣费流水 + 加进守护清单，
        // 用户会看到守护页凭空多出一个订阅、账目多出一笔，只觉得"这东西在乱记我的账"。
        // 现在先摆在这里，由他点「认得」才落库。
        val claims = store.pendingClaims
        if (claims.isNotEmpty()) {
            val c = claims.first()
            val rest = claims.size - 1
            LbCard(
                modifier = Modifier
                    .padding(top = 12.dp)
                    // 结论 chip 点一下要滚到这里。不用猜坐标：滚动量 = 当前滚动值 + 本卡的 y。
                    .onGloballyPositioned { claimsY = it.positionInRoot().y },
                contentPadding = 14.dp,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // ── B8：线索徽章**退出 amber** ──
                        // 线索是「等你判断」，不是「时间紧」。它不该和「这笔明天就要扣了」
                        // 抢同一个警告色，否则用户分不清哪个必须现在处理。
                        // 它是中性的待办：靠首屏位置 + 明确的「认得 / 以后别再提」引起注意。
                        // amber 从此只表示「时间紧，该动手了」（见 theme.kt 的语义分工注释）。
                        IconBadge(LbIcons.bell, LbSurface2, LbInk2, size = 32.dp)
                        Column(
                            Modifier
                                .padding(start = 10.dp)
                                .weight(1f),
                        ) {
                            Text(
                                if (rest > 0) "有 ${claims.size} 条线索 · 先处理这一条" else "最后一条线索",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LbInk,
                            )
                            Text(
                                "是订阅吗？你认了我才记账、才放进守护清单。签约的还没扣钱，金额留空不猜。",
                                fontSize = 11.5.sp,
                                color = LbInk3,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                    }
                    // 一次只摆一条：点完自动换成下一条，用户只需连点 N 次，
                    // 不用滚动、不用等列表重排。原来一次列 3 条，第 4 条给的提示是
                    // 「认掉前面几条就会露出来」—— 那是在让用户猜机制（D2）。
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
                                (if (c.amount > 0) "¥" + store.fmtMoney(c.amount) else "金额未知") +
                                    " · " + SubScanner.fmtDate(c.at) + " · " + claimSourceOf(ctx, c),
                                fontSize = 11.sp,
                                color = LbInk3,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                        ClaimBtn("认得", primary = true) {
                            store.confirmClaim(c.id)
                            lastDismissed = null
                            notice = "已认下「${c.name}」，写进扣费记录并放进守护清单"
                        }
                        Spacer(Modifier.size(6.dp))
                        // 按钮上写「以后别再提」而不是「不是我的」：后者听起来只是「这条不是」，
                        // 真实后果却是**这个商户永久不再自动加进来**。把后果说在按钮上，
                        // 并且给一次撤销（下面那条提示条）—— 原来这个名单只进不出（D2）。
                        ClaimBtn("以后别再提", primary = false) {
                            store.dismissClaim(c.id)
                            lastDismissed = c.name
                            notice = "「${c.name}」以后不再自动加进来（可在「我的 → 不再提示的商户」改回来）"
                        }
                    }
                    Text(
                        if (rest > 0) "处理完这条会自动换成下一条 · 还有 $rest 条"
                        else "处理完这条，就没有待认领的了",
                        fontSize = 11.sp,
                        color = LbInk3,
                        modifier = Modifier.padding(top = 8.dp),
                    )
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
                    // ── D1/D6：大数字改成「接下来要扣」，不再只是一个静态的「每月合计」。
                    // 用户看守护页问的是「我接下来要花多少」，而「每月合计」回答不了这个问题：
                    // 它把本月已经扣过的也算进去，月末看上去像还有一大笔要付。
                    // ⚠️ 口径：只算 active + 有确定日期的（dueBeforeNextMonthEnd 里定的两条）。
                    Text("接下来要扣（到下月底）", fontSize = 11.sp, color = LbOnDark2)
                    Text(
                        "¥${store.fmtMoney(due.first)}",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbOnDark,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    // 「本月已扣」只认真实扣费流水，绝不用订阅金额推算 ——
                    // 没扣过的钱不算花掉（B3 要的就是这个数，它以前只在记账页/今日页有）。
                    // 笔数口径仍是 active（与清单同源，见 B1 那次修复）。
                    Text(
                        buildString {
                            append("本月已扣 ¥").append(store.fmtMoney(chargedThisMonth))
                            append(" · 在用 ").append(active.size).append(" 笔")
                            if (due.first <= 0 && total > 0) append(" · 都还没填下次扣费日")
                        },
                        fontSize = 11.5.sp,
                        color = LbOnDark2,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // ── D1 的结论 chip：取代原来纯装饰的「M 月」。
                // 有东西要处理时它是「N 笔要处理」并且**点一下滚过去**；
                // 没有时退回「去扫描」（守护页最该做的事是找出忘关的订阅）。
                if (toHandle > 0) {
                    HeroScanChip("$toHandle 笔要处理") {
                        scope.launch {
                            val target = if (store.pendingClaims.isNotEmpty()) claimsY else closedY
                            scrollState.animateScrollTo((scrollState.value + target).toInt().coerceAtLeast(0))
                        }
                    }
                } else {
                    HeroScanChip("去扫描") { onOpenScan() }
                }
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
                    // 原来只写「读取扣费短信与已安装应用」—— 少说了两个来源,也完全没提
                    // v2.17 起才有的「代扣协议」(用户最难自己想到的那条路)。
                    Text("短信 · 通知 · 已装应用 · 支付宝/微信代扣协议，四处一起找", fontSize = 11.5.sp, color = LbInk3, modifier = Modifier.padding(top = 1.dp))
                    Text(
                        if (store.lastScanAt > 0L) "能自动帮你翻进那两家的续费页 · 上次扫描 " + store.fmtCnAt(store.lastScanAt)
                        else "能自动帮你翻进支付宝 / 微信的续费页，替你读那些忘关的",
                        fontSize = 11.5.sp,
                        color = LbAccent,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(LbIcons.chevronRight, contentDescription = null, tint = LbInk3, modifier = Modifier.size(15.dp))
            }
        }

        // ── 顶部这一条**只留给「时间紧」**（B8 之后 amber 的唯一语义）──
        // 原来这里是「关闭中」优先、临近扣费其次。问题是：只要有一笔在关闭中，
        // **「这笔今天就扣」这条真正急的提醒就永远不出现**了 —— 而"在关闭中"这件事
        // 下面本来就有一张常驻的「N 笔已标记关闭」卡在说。
        // 把位置让给时间紧的那条，同一件事也不用说两遍。
        if (nearest != null && nearest.second <= 3) {
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
                Text(
                    if (closingCount > 0) "${active.size} 笔在用 · $closingCount 笔已处理" else "${active.size} 笔在用",
                    fontSize = 12.5.sp,
                    color = LbInk3,
                )
                Spacer(Modifier.width(9.dp))
                LbPlusButton(onClick = { showAdd = true }, contentDescription = "添加订阅")
            }
        }
        LbCard(contentPadding = 8.dp) {
            if (active.isEmpty()) {
                // 空态分三种,各带一个能立刻做的动作。
                // 原来只有一句「还没有订阅。点右上角 + 记下第一笔」—— 用户刚扫完、什么都没扫到,
                // 看到的正是这句话,会以为扫坏了;而真正的原因(没开权限 / 没扫过 / 确实没有)三个字都没说。
                Column(Modifier.padding(12.dp)) {
                    Text(
                        when {
                            !canScan -> "还没开扫描要用的权限"
                            store.lastScanAt == 0L -> "还没扫过"
                            else -> "扫过了，四处来源都没发现订阅"
                        },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbInk,
                    )
                    Text(
                        when {
                            !canScan -> "短信里读「扣费 / 签约」要「读取短信」；读支付宝·微信那两页要「代扣协议读取」。开一路就能扫一路，不用全开。"
                            store.lastScanAt == 0L -> "四处一起找：扣费短信 · 扣费通知 · 已安装应用 · 支付宝/微信代扣协议。后两家那两页我能替你翻进去读。"
                            else -> "盖不到的地方我如实说：苹果 App Store 订阅、手机厂商商店（华为/小米等）、挂在话费里的代扣 —— 这三处只能你自己去看一眼。"
                        },
                        fontSize = 11.5.sp,
                        color = LbInk3,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ClaimBtn(if (store.lastScanAt == 0L) "去扫描" else "再扫一次", primary = true) { onOpenScan() }
                        Spacer(Modifier.width(8.dp))
                        ClaimBtn("手动记一笔", primary = false) { showAdd = true }
                    }
                }
            } else {
                // 只渲染「在用」的。关闭中的全部收进下面的「已处理」——
                // 原来两者混排,「关闭中」的也占着清单,于是合计/笔数/清单三处对不上。
                active.forEach { s ->
                    LbListRow(
                        leading = {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LbSurface2),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    s.name.take(1),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LbInk2,
                                )
                            }
                        },
                        title = s.name,
                        // 来源从「行尾的 chip」挪到「标题下面一行」，并且**带上解释**（D9/B5）：
                        // 原来 chip 上只写「扫描」「通知」「代扣页」三个字，用户看到
                        // 「代扣页」三个字无从理解 —— v2.17 新加的这个词等于白加。
                        sub = sourceHint(s.source),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 行尾**最多 1 个 chip**（D9）：只留「快扣了」这一个最重要的状态。
                                // ⚠️ 日期缺失时**别再放「待补全」chip**：右面那格日期列
                                // （store.dateLabel）本来就写「待补全」,两个一模一样的词会并排出现,
                                // 看上去像坏了（第一次实测的截图里就是「待补全 ¥25 待补全」）。
                                val d = store.daysUntil(s.nextDate)
                                if (d != null && d <= 3) {
                                    // ⚠️ `d == 0` 是「就是今天」，别把它并进 `d <= 1` 说成「明天扣」——
                                    // 实测就出了这个错：日期是今天，chip 却写着「明天扣」，两处各说各话。
                                    LbChip(
                                        when {
                                            d <= 0L -> "今天扣"
                                            d == 1L -> "明天扣"
                                            else -> "${store.daysText(s.nextDate)}扣"
                                        },
                                        ChipTone.Amber,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                // ── D11：缺的那个字段**点一下就补**，只弹这一个字段。
                                // 原来只能「进详情 → 编辑 → 在三个字段里小心别改错名称」（C7）。
                                if (s.amount <= 0) {
                                    Text(
                                        "金额待补",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = LbAmber,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { fixSubId = s.id; fixField = "amount" }
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                    )
                                } else {
                                    Text("¥" + store.fmtMoney(s.amount), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                                }
                                val noDate = d == null
                                Text(
                                    store.dateLabel(s.nextDate),
                                    fontSize = 11.5.sp,
                                    color = if (noDate) LbAmber else LbInk3,
                                    modifier = Modifier
                                        .width(52.dp)
                                        .padding(start = 8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(if (noDate) Modifier.clickable { fixSubId = s.id; fixField = "date" } else Modifier)
                                        .padding(vertical = 2.dp),
                                )
                            }
                        },
                        onClick = { onOpenDetail(s.id) },
                        onLongClick = { deleteSubId = s.id },
                    )
                }
            }
        }

        // 「已处理」= 已标记关闭的订阅。默认收起,点一下展开。
        // 原来这里只是一行统计文字(而且基数用的是历史累计 closedCount,与上面合计用的当前
        // closing 又是两套基数),关闭中的订阅则混在清单里 —— 现在归拢到这一处,基数统一。
        if (closingCount > 0) {
            LbCard(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .onGloballyPositioned { closedY = it.positionInRoot().y },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showClosed = !showClosed }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(LbIcons.circleCheck, LbAccentSoft, LbAccent, size = 32.dp)
                    Column(
                        Modifier
                            .padding(start = 11.dp)
                            .weight(1f),
                    ) {
                        Text("$closingCount 笔已标记关闭", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                        Text(
                            if (store.monthlySaved > 0) "每月少支出 ¥${store.fmtMoney(store.monthlySaved)} · 不计入上面的合计"
                            else "不计入上面的合计",
                            fontSize = 11.5.sp,
                            color = LbInk3,
                        )
                    }
                    Text(if (showClosed) "收起" else "查看", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LbAccent)
                }
                if (showClosed) {
                    Column(Modifier.padding(top = 8.dp)) {
                        closingList.forEach { s ->
                            LbListRow(
                                leading = {
                                    Box(
                                        Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(LbAccentSoft),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(LbIcons.circleCheck, contentDescription = null, tint = LbAccent, modifier = Modifier.size(17.dp))
                                    }
                                },
                                title = s.name,
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // 唯一还被允许留在行尾的提醒 chip:它是「你应该回头看一眼」的强信号。
                                        if (store.hasChargeAfterClosing(s)) {
                                            LbChip("关闭后仍有扣费", ChipTone.Rust)
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(if (s.amount <= 0) "金额待补" else "¥" + store.fmtMoney(s.amount), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                                    }
                                },
                                onClick = { onOpenDetail(s.id) },
                                onLongClick = { deleteSubId = s.id },
                            )
                        }
                        Text(
                            "点开可以核对「关闭后是否还在扣」，或长按删掉这一条。",
                            fontSize = 11.sp,
                            color = LbInk3,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                        )
                    }
                }
            }
        } else {
            LbCard(modifier = Modifier.padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(LbIcons.circleCheck, LbAccentSoft, LbAccent, size = 32.dp)
                    Column(Modifier.padding(start = 11.dp)) {
                        Text(
                            if (store.closedCount == 0) "还没有标记关闭的订阅"
                            else "历史累计标记关闭 ${store.closedCount} 笔",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LbInk,
                        )
                        Text(
                            if (store.closedCount == 0) "在订阅详情点「我已关闭」，这里会开始累计"
                            else "这些已不在清单里，也不再计入上面的合计",
                            fontSize = 11.5.sp,
                            color = LbInk3,
                        )
                    }
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
            // 删除是**不可逆**的，所以保留确认框（D12 的分级：不可逆才弹框）。
            // ⚠️ 「不再加回」那个副作用现在说成可撤销的 —— v2.18 之前它只进不出，
            // 而这句话写的是「之后也不会再自动加回」，等于把一次永久惩罚轻描淡写。
            text = if (sub != null && (sub.source == "扫描" || sub.source == "通知" || sub.source.startsWith("代扣页")))
                "删除后不再提醒扣费。这一条来自「${sub.source}」，之后扫描再读到它也不会自动加回 —— 想改回来，去「我的 → 不再提示的商户」。"
            else
                "删除后不再提醒扣费。这一笔是你手动记的，之后想恢复只能再记一次。",
            onDismiss = { deleteSubId = null },
            onConfirm = {
                store.removeSub(id)
                if (sub != null && (sub.source == "扫描" || sub.source == "通知" || sub.source.startsWith("代扣页"))) {
                    store.dismissName(sub.name)
                }
                deleteSubId = null
                notice = "已删除「$name」"
                lastDismissed = null
            },
        )
    }

    // ── D11 内联补全：只补缺的那一个字段，不进「编辑」那个三字段弹窗 ──
    fixSubId?.let { id ->
        val s = store.subs.firstOrNull { it.id == id }
        if (s != null && fixField == "amount") {
            LbInputDialog(
                title = "补全「${s.name}」的金额",
                fields = listOf(LbField("每月金额（元）", "如：25", numeric = true)),
                initial = listOf(if (s.amount > 0) store.fmtMoney(s.amount) else ""),
                onDismiss = { fixSubId = null },
                onConfirm = { v ->
                    val a = v.getOrElse(0) { "" }.toDoubleOrNull()
                    if (a == null || a <= 0) {
                        "金额填数字，比如 25"
                    } else {
                        // trialUntil / remindAhead 传 null = 这两项不动（updateSub 的约定）
                        store.updateSub(s.id, s.name, a, s.nextDate)
                        fixSubId = null
                        notice = "已补上「${s.name}」的金额：每月 ¥${store.fmtMoney(a)}"
                        null
                    }
                },
            )
        } else if (s != null) {
            LbInputDialog(
                title = "补全「${s.name}」的扣费日",
                fields = listOf(LbField("下次扣费日期", "点这里选择日期", isDate = true)),
                initial = listOf(s.nextDate),
                onDismiss = { fixSubId = null },
                onConfirm = { v ->
                    val d = v.getOrElse(0) { "" }
                    if (d.isBlank()) {
                        "选个日期吧"
                    } else {
                        store.updateSub(s.id, s.name, s.amount, d)
                        fixSubId = null
                        notice = "已补上「${s.name}」的下次扣费日：${store.fmtCn(d)}"
                        null
                    }
                },
            )
        }
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
    // 详情页里的删除入口。原来只有「回到清单长按那一行」一条路 —— 隐式手势，
    // 而且进了详情页反而找不到删除。这里把它明说出来。
    var showDelSub by remember { mutableStateOf(false) }
    // D12/C8：页面内留痕的提示条，替掉转瞬即逝的 Toast。
    var notice by remember { mutableStateOf<String?>(null) }
    // 「关闭中」是**可逆**操作，所以点完给一次撤销（D12 的分级：可逆的给 Undo、不弹框）。
    var undoClosing by remember { mutableStateOf(false) }
    // D11：金额没识别出来时**点一下就补**，只弹这一个字段，不进「编辑」那个三字段弹窗。
    var fixAmount by remember { mutableStateOf(false) }

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

        // D12/C8：反馈留在页面上，不再靠转瞬即逝的 Toast。
        notice?.let { msg ->
            LbNoticeBar(
                text = msg,
                action = if (undoClosing) "撤销" else null,
                onAction = {
                    store.unmarkSubClosing(sub.id)
                    undoClosing = false
                    notice = null
                },
                onDismiss = { undoClosing = false; notice = null },
                modifier = Modifier.padding(top = 10.dp),
            )
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
                    // ── D9：金额与日期并成**一行小字**，不再各占一个 chip ──
                    // 原来「每月 ¥25」是个 chip、日期又是一个 chip，一行最多挤 3 个，
                    // 2.0× 大字号必定折行。判据用 `days != null`（日期真的解析出来了），
                    // 只判非空会渲染成「每月 null 日」。
                    Text(
                        if (sub.amount <= 0 && days == null) "金额待补 · 扣费日待补全"
                        else if (sub.amount <= 0) "金额待补 · 每月 ${dayOfMonth} 日扣"
                        else if (days == null) "每月 ¥${store.fmtMoney(sub.amount)} · 扣费日待补全"
                        else "每月 ¥${store.fmtMoney(sub.amount)} · 每月 $dayOfMonth 日扣",
                        fontSize = 12.sp,
                        color = if (sub.amount <= 0 || days == null) LbAmber else LbInk3,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    // 来源从 chip 改成**带解释的小字**（D9 / B5）：
                    // 光秃秃一个「代扣页」三个字，用户无从理解。
                    sourceHint(sub.source)?.let { hint ->
                        Text(
                            hint,
                            fontSize = 11.sp,
                            color = LbInk3,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
            // 行上**只剩 1 个 chip**（D9）：只留最重要的那一个状态。
            // 金额与来源都已经挪到上面的小字里了。
            if (sub.closing || days != null) {
                Row(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (sub.closing) {
                        LbChip("关闭处理中", ChipTone.Green)
                    } else if (days != null) {
                        // 同上：`days == 0` 是「就是今天」，别说成「明天」
                        LbChip(
                            when {
                                days <= 0L -> "今天自动扣费"
                                days == 1L -> "明天自动扣费"
                                else -> "${store.daysText(sub.nextDate)}自动扣费"
                            },
                            if (days <= 3) ChipTone.Amber else ChipTone.Soft,
                        )
                    }
                }
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
                // ── D11：缺的字段**点一下就补**，只弹这一个 ──
                // 原来要「右上角编辑 → 在三字段弹窗里小心别改错名称」（C7）。
                Text(
                    "金额没识别出来 · 点这里补全",
                    fontSize = 11.5.sp,
                    color = LbAmber,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { fixAmount = true }
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                )
            }
        }

        // 关闭入口前置。原来这一块在页面最底 —— 前面横着「提醒」「下次扣费」
        // 「扣费记录」「手动补记」四块，想关掉一笔订阅得先滚过整页。而「怎么关掉它」
        // 恰恰是用户点进详情最想知道的事,所以提到主卡下面第一位。
        LbCard(contentPadding = 16.dp, modifier = Modifier.padding(top = 10.dp)) {
            if (!sub.closing) {
                CancelGuide(
                    store = store,
                    subId = sub.id,
                    onDone = { },
                    onNotice = { msg ->
                        notice = msg
                        // 「记下了…」是「我去关掉了」那一步的反馈：只有它是可逆的，
                        // 也只有它才该拿到撤销按钮（D12）。
                        undoClosing = msg.startsWith("记下了")
                    },
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(LbIcons.circleCheck, LbAccentSoft, LbAccent, size = 36.dp)
                    Column(
                        Modifier
                            .padding(start = 11.dp)
                            .weight(1f),
                    ) {
                        Text("已标记为「关闭中」", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                        // ── C6：把「怎样才算关干净了」说清楚 ──
                        // 用户真正的问题是「我关成功了没」，原来只说「之后若仍有扣费会提醒你复核」，
                        // 没说过了哪个日子就算停了 —— 那个日子才是他会等的那个信号。
                        Text(
                            if (days != null)
                                "过了 ${store.fmtCn(sub.nextDate)} 这个扣费日、且之后没有新的扣费记录，就算停干净了；若还有扣费，扫描时会提醒你复核。要彻底移出清单，点页面最下面的「删除这条订阅」。"
                            else
                                "取消需在平台完成；之后若仍有扣费，扫描时会提醒你复核。补上下次扣费日，我才能告诉你「过了哪天就算停干净」。要彻底移出清单，点页面最下面的「删除这条订阅」。",
                            fontSize = 11.5.sp,
                            color = LbInk3,
                            lineHeight = 17.sp,
                        )
                    }
                }
                // 「关闭中」是**可逆**的，所以给它一个明确的撤销，而不是弹确认框（D12）。
                Row(Modifier.padding(top = 10.dp)) {
                    LbGhostButton(
                        "撤销「关闭中」，放回在用清单",
                        onClick = {
                            store.unmarkSubClosing(sub.id)
                            undoClosing = false
                            notice = "已放回在用清单"
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                // ── C4：没开权限时不能说「会自动记下」—— 那是句假话 ──
                // 原来不管有没有授权都写「收到扣费短信或通知时这里会自动记下」，
                // 用户会以为功能在跑，其实一条都不会来。开没开要说准。
                // ⚠️ 这里是普通 Text（不是 RichText），别写 `**`，那会原样渲染成星号。
                val smsGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                val notifGranted = SubScanner.notificationAccessGranted(ctx)
                Text(
                    when {
                        smsGranted && notifGranted ->
                            "还没有这笔订阅的扣费记录。收到扣费短信或通知时这里会自动记下；也可以手动补记一笔。"
                        smsGranted ->
                            "还没有扣费记录。开了「读取短信」，收到扣费短信时会记到这里；通知那条还没开。也可以手动补记一笔。"
                        notifGranted ->
                            "还没有扣费记录。开了「通知读取」，收到扣费通知时会记到这里；短信那条还没开。也可以手动补记一笔。"
                        else ->
                            "还没有扣费记录，而且现在一条都不会自动进来：「读取短信」和「通知读取」两处都没开。" +
                                "去「守护 → 一键扫描」开一路，或先手动补记一笔。"
                    },
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

        // 删除入口。原来只有「回清单长按那一行」一条路：手势是隐式的，而且进了详情页反而没有。
        // 放在整页最底 —— 它是「少见但彻底」的动作，不该和上面的「去关闭」抢注意力。
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { showDelSub = true }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(LbIcons.trash, contentDescription = null, tint = LbRust, modifier = Modifier.size(15.dp))
            Text("删除这条订阅", fontSize = 12.5.sp, color = LbRust, modifier = Modifier.padding(start = 6.dp))
        }
        Text(
            "删除只是让管家不再盯着它（本机动作）。真要停止扣费，仍然得在支付宝 / 微信里关掉自动续费。",
            fontSize = 11.sp,
            color = LbInk3,
            lineHeight = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        )

        if (showDelSub) {
            LbConfirmDialog(
                title = "删除「${sub.name}」？",
                text = if (sub.source == "手动")
                    "删除后不再提醒扣费。这一笔是你手动记的，之后想恢复只能再记一次。"
                else
                    "删除后不再提醒扣费。这一条来自「${sub.source}」：删掉之后，扫描再读到它也不会自动加回 —— 这个「不再加回」是本机长期记住的，界面上没有撤销的地方。",
                onDismiss = { showDelSub = false },
                onConfirm = {
                    val nm = sub.name
                    val src = sub.source
                    store.removeSub(sub.id)
                    // 与「守护页清单长按删除」同一套规则：只有自动来源才记进「不再加回」。
                    if (src == "扫描" || src == "通知" || src == "代扣页") store.dismissName(nm)
                    showDelSub = false
                    // 不手动 onBack()：sub 立刻变 null，composable 开头那段
                    // `if (sub == null) { LaunchedEffect { onBack() } }` 会自动退回去。
                },
            )
        }

        // ── D11 内联补全：只补缺的那一个字段 ──
        if (fixAmount) {
            LbInputDialog(
                title = "补全「${sub.name}」的金额",
                fields = listOf(LbField("每月金额（元）", "如：25", numeric = true)),
                initial = listOf(if (sub.amount > 0) store.fmtMoney(sub.amount) else ""),
                onDismiss = { fixAmount = false },
                onConfirm = { v ->
                    val a = v.getOrElse(0) { "" }.toDoubleOrNull()
                    if (a == null || a <= 0) {
                        "金额填数字，比如 25"
                    } else {
                        store.updateSub(sub.id, sub.name, a, sub.nextDate)
                        fixAmount = false
                        notice = "已补上金额：每月 ¥${store.fmtMoney(a)}"
                        null
                    }
                },
            )
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

        // （关闭入口原来在这里 —— 已前置到主卡下面，见上面那段注释）
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
fun CancelGuide(
    store: ButlerStore,
    subId: String,
    onDone: () -> Unit,
    /**
     * D12 / C8：把反馈**留在页面上**的通道。
     * 不传就退回 Toast —— 但那样是转瞬即逝、页面不留痕、也没有「那怎么办」，
     * 所以调用方应当尽量传一个。
     */
    onNotice: ((String) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val sub = store.subs.firstOrNull { it.id == subId }
    if (sub == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val tell: (String) -> Unit = { msg ->
        if (onNotice != null) onNotice.invoke(msg) else Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
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
                        // 「那怎么办」必须跟着一起说 —— 只报一个失败等于把人晾在那儿。
                        tell("没打开「$name」。可直接在桌面找到它，或照下面的步骤自己进去。")
                    }
                }
            }
        }
        val mp = SubScanner.merchantPackage(sub.name)
        if (mp != null && SubScanner.isInstalled(ctx, mp)) {
            MiniGhost("打开${sub.name}") {
                if (!SubScanner.launchPackage(ctx, mp)) {
                    tell("没打开「${sub.name}」。可直接在桌面找到它，或照下面的步骤自己进去。")
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
        // 「复制步骤」是次要动作，降级成文字链接，不跟主按钮抢注意力（D3）。
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("关闭步骤", CANCEL_STEPS))
                    tell("关闭步骤已复制，粘到备忘里照着做就行")
                }
                .padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            Text("复制关闭步骤", fontSize = 12.sp, color = LbInk3)
        }
        // 文案改成带承诺的说法：「帮我复核」太虚，用户不知道点完会发生什么。
        // 这是**可逆**操作（「关闭中」能撤销），所以立即生效、**不弹确认框**（D12）。
        LbPrimaryButton(
            "我去关掉了，帮我盯着下一次",
            onClick = {
                store.markSubClosing(sub.id)
                tell("记下了。过了这个扣费日若还有扣费，扫描时会提醒你复核")
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

/**
 * D7「数据从哪来」状态条。
 *
 * 为什么需要它：四路来源开没开、服务有没有被系统掐掉，原来**只有扫描页能看到**（C1/C3）。
 * 于是在守护页问「我明明开了权限，我的订阅怎么没自动出现」的人得不到任何线索 ——
 * 而最常见的真相恰恰是「授权还在，服务被系统断开了」，这条恰好一眼能看出来。
 */
@Composable
private fun SourceStatusBar(
    smsOn: Boolean,
    notifOn: Boolean,
    notifLive: Boolean,
    a11yOn: Boolean,
    a11yLive: Boolean,
    lastScanAt: Long,
    lastScanText: String,
    onOpenScan: () -> Unit,
) {
    val ctx = LocalContext.current
    val anyOn = smsOn || notifOn || a11yOn
    // 「被系统断开」的名单：授权在、服务不在。这是最该说破的一种状态。
    val broken = buildList {
        if (notifOn && !notifLive) add("通知读取")
        if (a11yOn && !a11yLive) add("代扣协议读取")
    }
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(14.dp)),
        color = if (anyOn) LbSurface else LbAmberSoft,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 短信权限要在 App 内申请（不是系统设置页），所以点它进扫描页。
                SourceDot("短信", smsOn, true, onOpenScan)
                SourceDot("通知", notifOn, notifLive) {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                SourceDot("代扣页", a11yOn, a11yLive) {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                // 「已安装应用」不需要授权（只读白名单里的包名，不申请全量应用权限），
                // 所以它恒亮 —— 但它靠的是这次扫描有没有跑过，所以点它进扫描页。
                SourceDot("已装应用", true, true, onOpenScan)
            }
            if (broken.isNotEmpty()) {
                Text(
                    "「${broken.joinToString("、")}」授权还在，但服务被系统断开了 —— 点上面的名字重开一次。",
                    fontSize = 11.sp,
                    color = LbRust,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 5.dp, start = 2.dp),
                )
            }
            Text(
                if (!anyOn) "还没开启任何来源，所以这里可能一直是空的 —— 点上面的名字开一路就行，不用全开。"
                else if (lastScanAt > 0L) "上次扫描：$lastScanText"
                else "还没扫描过 —— 开了上面的来源就能扫",
                fontSize = 11.sp,
                color = if (!anyOn || lastScanAt == 0L) LbAmber else LbInk3,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 5.dp, start = 2.dp),
            )
        }
    }
}

/** 状态条上的一颗来源圆点。未开=灰，开了但服务断=rust，正常=accent。 */
@Composable
private fun RowScope.SourceDot(label: String, on: Boolean, live: Boolean, onClick: () -> Unit) {
    val broken = on && !live
    Row(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (!on) LbLine else if (broken) LbRust else LbAccent),
        )
        Text(
            label,
            fontSize = 11.sp,
            color = if (!on) LbInk3 else if (broken) LbRust else LbInk,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * 来源的**解释**（D9 / B5）。
 *
 * 原来 chip 上只写「扫描」「通知」「代扣页」三个字 —— "代扣页" 是 v2.17 才有的新词，
 * 用户看到三个字无从理解，那个来源等于白加。手动记的不解释（是他自己记的）。
 */
private fun sourceHint(source: String): String? = when {
    source.isBlank() || source == "手动" -> null
    source.startsWith("代扣页") -> "来源：$source（你在那一页签的协议）"
    source == "扫描" -> "来源：扣费短信"
    source == "通知" -> "来源：扣费通知"
    source == "演示" -> "来源：演示数据（不是你的真实订阅）"
    else -> "来源：$source"
}

/**
 * 线索的来源（B9：线索有 `pkg` 字段，却一直写死「来自通知」）。
 * 认不出是哪个 App 就如实退回「来自通知」—— 绝不拿包名去糊弄用户。
 */
private fun claimSourceOf(ctx: Context, c: ButlerClaim): String {
    val app = SubScanner.appNameOf(c.pkg)
    return if (app.isNotEmpty()) "来自 $app 的通知" else "来自通知"
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

/**
 * 深色「每月订阅合计」卡右上角的动作 chip。
 *
 * 原来那个位置是一个纯装饰的「N 月」,点不动。守护页第一件事就是「找出我忘关的订阅」,
 * 把它换成扫描入口 —— 不增加任何新概念,只是让本来闲着的地方能点。
 */
@Composable
private fun HeroScanChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x29F6F5F0))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = LbOnDark)
    }
}
