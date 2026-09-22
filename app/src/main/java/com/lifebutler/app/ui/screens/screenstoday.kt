package com.lifebutler.app.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lifebutler.app.R
import com.lifebutler.app.data.ButlerStore
import com.lifebutler.app.data.ButlerSub
import com.lifebutler.app.data.Weather
import com.lifebutler.app.ui.components.ChipTone
import com.lifebutler.app.ui.components.CustodyCard
import com.lifebutler.app.ui.components.HeroCard
import com.lifebutler.app.ui.components.IconBadge
import com.lifebutler.app.ui.components.LbCard
import com.lifebutler.app.ui.components.LbChip
import com.lifebutler.app.ui.components.LbConfirmDialog
import com.lifebutler.app.ui.components.LbField
import com.lifebutler.app.ui.components.LbGhostButton
import com.lifebutler.app.ui.components.LbInputDialog
import com.lifebutler.app.ui.components.LbPlusButton
import com.lifebutler.app.ui.components.LbPrimaryButton
import com.lifebutler.app.ui.components.LbTwoActionDialog
import com.lifebutler.app.ui.components.SectionHeader
import com.lifebutler.app.ui.components.TaskRow
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
import com.lifebutler.app.ui.theme.LbSurface
import com.lifebutler.app.ui.theme.LbSurface2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private fun Modifier.dashedBorder(): Modifier = this.drawWithContent {
    drawContent()
    drawRoundRect(
        color = LbLineStrong,
        style = Stroke(
            width = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(38f, 38f),
    )
}

@Composable
fun TodayScreen(
    onOpenDuties: () -> Unit,
    onOpenGuard: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenSearch: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    var sheetSubId by remember { mutableStateOf<String?>(null) }
    var showAddTask by remember { mutableStateOf(false) }
    var deleteTaskId by remember { mutableStateOf<String?>(null) }
    var taskMenuId by remember { mutableStateOf<String?>(null) }
    var editTaskId by remember { mutableStateOf<String?>(null) }
    var showWeatherIntro by remember { mutableStateOf(false) }
    var weatherOn by remember { mutableStateOf(Weather.enabled(ctx)) }
    var weatherInfo by remember { mutableStateOf(Weather.cached(ctx)) }
    var weatherBusy by remember { mutableStateOf(false) }
    var showQuickExpense by remember { mutableStateOf(false) }
    val weatherScope = rememberCoroutineScope()

    fun fetchWeather(manual: Boolean) {
        if (weatherBusy) return
        if (!Weather.hasLocation(ctx)) {
            if (manual) Toast.makeText(ctx, "需要「大致位置」权限才能获取天气", Toast.LENGTH_SHORT).show()
            return
        }
        weatherBusy = true
        weatherScope.launch {
            val info = withContext(Dispatchers.IO) { Weather.refresh(ctx) }
            weatherBusy = false
            if (info != null) {
                weatherInfo = info
                if (manual) Toast.makeText(ctx, "已更新：${Weather.describe(info.code)} ${info.temp}°（今日 ${info.low}°~${info.high}°）", Toast.LENGTH_SHORT).show()
            } else if (manual) {
                Toast.makeText(ctx, "天气获取失败，检查网络后再试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val locPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            Weather.setEnabled(ctx, true)
            Weather.setAsked(ctx)
            weatherOn = true
            fetchWeather(manual = false)
            Toast.makeText(ctx, "桌面天气已开启", Toast.LENGTH_SHORT).show()
        } else {
            Weather.setEnabled(ctx, false)
            weatherOn = false
            Toast.makeText(ctx, "未获取位置权限，天气未开启", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Weather.enabled(ctx)) {
            weatherOn = true
            if (Weather.isStale(ctx) || weatherInfo == null) fetchWeather(manual = false)
        } else if (!Weather.asked(ctx)) {
            showWeatherIntro = true
        }
    }

    val today = LocalDate.now()
    val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val dateLine = "%d 月 %d 日 · %s".format(today.monthValue, today.dayOfMonth, weekdays[today.dayOfWeek.value - 1])

    val doneCount = store.tasks.count { it.done }
    val allDone = store.tasks.isNotEmpty() && doneCount == store.tasks.size
    // 有明确扣费日的取最近一笔;都没有日期时,退而提醒第一笔「扣费日待补全」的订阅
    val nextSub: Pair<ButlerSub, Long?>? = store.subs.filter { !it.closing }
        .mapNotNull { s -> store.daysUntil(s.nextDate)?.let { s to it } }
        .minByOrNull { it.second }
        ?: store.subs.firstOrNull { !it.closing && store.daysUntil(it.nextDate) == null }?.let { it to null }
    val nextOb = store.obligations.filter { !it.done }
        .mapNotNull { o -> store.daysUntil(o.date)?.let { o to it } }
        .minByOrNull { it.second }
    val pendingObligations = store.obligations.count { !it.done }
    val watchCount = (if (nextSub != null) 1 else 0) + (if (nextOb != null) 1 else 0)

    val wInfo = weatherInfo
    val chipText: String
    val chipIcon: ImageVector
    if (weatherOn && wInfo != null) {
        chipText = (if (wInfo.city.isNotEmpty()) wInfo.city + " · " else "") + Weather.describe(wInfo.code) + " " + wInfo.temp + "°"
        chipIcon = when (Weather.iconKey(wInfo.code)) {
            "sun" -> LbIcons.sun
            "cloud" -> LbIcons.cloud
            else -> LbIcons.cloudRain
        }
    } else if (weatherOn) {
        chipText = "天气获取中…"
        chipIcon = LbIcons.cloud
    } else {
        chipText = "本机记录 · 第 ${store.dayCount()} 天"
        chipIcon = LbIcons.mapPin
    }

    val expenseTodayList = store.expenses.filter { it.date == today.toString() }
    // 首页那个数字只算**花掉的**：把收入加进来会得到一个没有意义的和
    val expenseTodayTotal = store.spendOf(expenseTodayList)
    val expenseTodayCount = expenseTodayList.size
    val expenseCats = store.expenseCategoryTotals(expenseTodayList)

    val monthExpense = store.spendOf(store.expensesInMonth(today.year, today.monthValue))
    val monthSub = store.subs.filter { !it.closing }.sumOf { it.amount }

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
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(dateLine, style = MaterialTheme.typography.labelSmall)
                Text(
                    "早上好，${store.profileName.value}",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // 搜一搜：跨模块检索的入口放在最常打开的那一页的右上角，
            // 因为「找东西」这件事总是从「我现在在首页」开始
            Box(
                Modifier
                    .padding(end = 7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(LbSurface)
                    .border(1.dp, LbLine, RoundedCornerShape(999.dp))
                    .lbPressable(onClick = onOpenSearch)
                    .padding(horizontal = 9.dp, vertical = 7.dp),
            ) {
                Icon(LbIcons.search, contentDescription = "搜一搜", tint = LbInk2, modifier = Modifier.size(15.dp))
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(LbSurface)
                    .border(1.dp, LbLine, RoundedCornerShape(999.dp))
                    .clickable {
                        if (weatherOn) {
                            fetchWeather(manual = true)
                        } else {
                            showWeatherIntro = true
                        }
                    }
                    .padding(horizontal = 11.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(chipIcon, contentDescription = null, tint = LbInk2, modifier = Modifier.size(13.dp))
                Text(chipText, fontSize = 12.sp, color = LbInk2, modifier = Modifier.padding(start = 5.dp))
            }
        }

        HeroCard(
            painter = painterResource(R.drawable.hero_morning),
            height = 176.dp,
            kicker = "今天的第一件事",
            title = if (store.tasks.isEmpty()) "先记下今天要做的一件事" else "还剩 ${store.tasks.count { !it.done }} 件没做完",
            sub = if (store.tasks.isEmpty()) "点下方「+」记第一件，我帮你盯着" else "勾掉一件少一件，都完成后我来给你报个平安",
            modifier = Modifier.padding(top = 12.dp),
        )

        SectionHeader("今天要做的事") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$doneCount / ${store.tasks.size}",
                    fontSize = 12.5.sp,
                    color = if (allDone) LbAccent else LbInk3,
                )
                Spacer(Modifier.width(9.dp))
                LbPlusButton(onClick = { showAddTask = true })
            }
        }
        LbCard(contentPadding = 6.dp) {
            if (store.tasks.isEmpty()) {
                Text(
                    "还没有事项。点右上角 + 添加，或在对话里说「记一下：……」",
                    fontSize = 12.sp,
                    color = LbInk3,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(12.dp),
                )
            } else {
                store.tasks.forEach { t ->
                    TaskRow(
                        checked = t.done,
                        onToggle = { store.toggleTask(t.id) },
                        title = t.text,
                        sub = t.meta.ifEmpty { null },
                        onLongClick = { taskMenuId = t.id },
                    )
                }
            }
        }

        SectionHeader("今天花的钱") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¥${store.fmtMoney(expenseTodayTotal)}", fontSize = 12.5.sp, color = if (expenseTodayCount == 0) LbInk3 else LbInk2)
                Spacer(Modifier.width(9.dp))
                LbPlusButton(onClick = { showQuickExpense = true }, contentDescription = "记一笔")
            }
        }
        LbCard(contentPadding = 12.dp) {
            if (expenseTodayCount == 0) {
                Text(
                    "今天还没记账。午饭、打车随手一记，月底就知道钱花哪了。",
                    fontSize = 12.sp,
                    color = LbInk3,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(4.dp),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("¥${store.fmtMoney(expenseTodayTotal)}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                        Text("共 $expenseTodayCount 笔", fontSize = 11.5.sp, color = LbInk3, modifier = Modifier.padding(top = 2.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        expenseCats.take(2).forEach { (c, v) ->
                            Text("$c ¥${store.fmtMoney(v)}", fontSize = 11.5.sp, color = LbInk2, modifier = Modifier.padding(top = 1.dp))
                        }
                    }
                }
                Row(
                    Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenLedger)
                        .padding(vertical = 2.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("看全部明细", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = LbAccent)
                    Spacer(Modifier.width(4.dp))
                    Icon(LbIcons.arrowUpRight, contentDescription = null, tint = LbAccent, modifier = Modifier.size(14.dp))
                }
            }
        }

        SectionHeader("替你盯着的") {
            Text("$watchCount 项", fontSize = 12.5.sp, color = LbInk3)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (nextSub != null) {
                val s = nextSub.first
                val days = nextSub.second
                CustodyCard(
                    badge = { IconBadge(LbIcons.creditCard, LbAmberSoft, LbAmber) },
                    title = "${s.name} · ¥${store.fmtMoney(s.amount)}/月",
                    sub = when {
                        days == null -> "扣费日还没补全，去守护页补上更稳妥"
                        days <= 1 -> "明天自动扣费，还来得及拦"
                        else -> "${store.daysText(s.nextDate)}自动扣费（${store.fmtCn(s.nextDate)}）"
                    },
                    chips = listOf(
                        (if (days == null) "扣费日待补全" else if (days <= 1) "明天扣费" else "${days} 天后扣费") to
                            (if (days == null || days <= 3) ChipTone.Amber else ChipTone.Soft),
                        "看关闭步骤" to ChipTone.Soft,
                    ),
                    onClick = { sheetSubId = s.id },
                )
            } else {
                CustodyCard(
                    badge = { IconBadge(LbIcons.creditCard, LbSurface2, LbInk2) },
                    title = "还没有订阅记录",
                    sub = "去「守护」页添加第一笔，我来替你盯着",
                    onClick = onOpenGuard,
                )
            }

            if (nextOb != null) {
                val o = nextOb.first
                val days = nextOb.second
                CustodyCard(
                    badge = { IconBadge(LbIcons.calendarEvent, LbAccentSoft, LbAccent) },
                    title = "${o.title} · ${if (days >= 0) "还有 $days 天" else store.daysText(o.date)}",
                    sub = o.note.ifEmpty { "到期前会提前提醒" },
                    chips = listOf(
                        (if (days <= 14) "临近" else "已排期") to (if (days <= 14) ChipTone.Amber else ChipTone.Green),
                        o.tag to ChipTone.Soft,
                    ),
                    onClick = onOpenDuties,
                )
            } else if (store.obligations.isEmpty() || pendingObligations == 0) {
                CustodyCard(
                    badge = { IconBadge(LbIcons.calendarEvent, LbAccentSoft, LbAccent) },
                    title = if (store.obligations.isEmpty()) "还没有排期的事务" else "义务都处理完了",
                    sub = "把要到期的事放进时间线，我帮你数日子",
                    onClick = onOpenDuties,
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenDuties)
                .padding(9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (pendingObligations > 0) "查看全部 $pendingObligations 项待办义务" else "打开义务时间线",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = LbAccent,
                )
                Spacer(Modifier.width(5.dp))
                Icon(LbIcons.arrowUpRight, contentDescription = null, tint = LbAccent, modifier = Modifier.size(15.dp))
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenReport)
                .clip(RoundedCornerShape(20.dp))
                .dashedBorder()
                .padding(11.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(LbIcons.fileText, LbSurface2, LbInk2, size = 34.dp)
                Column(
                    Modifier
                        .padding(start = 11.dp)
                        .weight(1f),
                ) {
                    Text("${today.monthValue} 月月报", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbInk2)
                    Text(
                        "花销 ¥${store.fmtMoney(monthExpense)} · 订阅 ¥${store.fmtMoney(monthSub)} · 已省 ¥${store.fmtMoney(store.savedThisMonth)}",
                        fontSize = 11.5.sp,
                        color = LbInk3,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                Icon(LbIcons.arrowUpRight, contentDescription = null, tint = LbInk3, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showAddTask) {
        LbInputDialog(
            title = "添加一件事",
            fields = listOf(
                LbField("内容", "如：给妈妈回电话", false),
                LbField("备注（可选）", "如：她昨晚发过语音，还没回"),
            ),
            onDismiss = { showAddTask = false },
            onConfirm = { values ->
                val t = values.getOrElse(0) { "" }
                val note = values.getOrElse(1) { "" }
                if (t.isEmpty()) "写点内容吧" else {
                    store.addTask(t, note.ifEmpty { "手动添加" })
                    showAddTask = false
                    null
                }
            },
        )
    }

    taskMenuId?.let { id ->
        val t = store.tasks.firstOrNull { it.id == id }
        LbTwoActionDialog(
            title = t?.text ?: "这件事",
            text = "要修改内容 / 备注，还是删除？",
            actionA = "编辑",
            actionB = "删除",
            onA = { editTaskId = id; taskMenuId = null },
            onB = { deleteTaskId = id; taskMenuId = null },
            onDismiss = { taskMenuId = null },
        )
    }

    editTaskId?.let { id ->
        val t = store.tasks.firstOrNull { it.id == id }
        LbInputDialog(
            title = "编辑这件事",
            fields = listOf(
                LbField("内容", "如：给妈妈回电话"),
                LbField("备注（可选）", "如：她昨晚发过语音，还没回"),
            ),
            initial = listOf(t?.text ?: "", t?.meta ?: ""),
            onDismiss = { editTaskId = null },
            onConfirm = { v ->
                val text = v.getOrElse(0) { "" }
                val note = v.getOrElse(1) { "" }
                if (text.isEmpty()) "写点内容吧" else {
                    store.updateTask(id, text, note)
                    editTaskId = null
                    null
                }
            },
        )
    }

    deleteTaskId?.let { id ->
        LbConfirmDialog(
            title = "删除这件事？",
            text = "删除后不可恢复；之后也可以在对话里重新记一遍。",
            onDismiss = { deleteTaskId = null },
            onConfirm = {
                store.removeTask(id)
                deleteTaskId = null
            },
        )
    }

    if (showWeatherIntro) {
        WeatherIntroDialog(
            onEnable = {
                showWeatherIntro = false
                Weather.setAsked(ctx)
                locPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onSkip = {
                showWeatherIntro = false
                Weather.setAsked(ctx)
            },
        )
    }

    if (showQuickExpense) {
        ExpenseAddDialog(
            onSave = { a, c, n, income ->
                store.addExpense(a, c, n, income)
                showQuickExpense = false
            },
            onDismiss = { showQuickExpense = false },
        )
    }

    sheetSubId?.let { id ->
        PaySheet(store = store, subId = id, onDismiss = { sheetSubId = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaySheet(store: ButlerStore, subId: String, onDismiss: () -> Unit) {
    val sub = store.subs.firstOrNull { it.id == subId }
    if (sub == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var step by remember(subId) { mutableStateOf(if (sub.closing) 2 else 0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dayOfMonth = store.parseDate(sub.nextDate)?.dayOfMonth

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LbSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            if (step == 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(LbAmberSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            sub.name.take(1),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LbAmber,
                        )
                    }
                    Column(
                        Modifier
                            .padding(start = 12.dp)
                            .weight(1f),
                    ) {
                        Text(sub.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                        Text(
                            if (dayOfMonth != null) "自动续费 · 每月 $dayOfMonth 日" else "自动续费 · 扣费日待补全",
                            fontSize = 12.sp,
                            color = if (dayOfMonth != null) LbInk3 else LbAmber,
                        )
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(LbSurface2)
                        .padding(14.dp),
                ) {
                    Text("¥%.2f".format(sub.amount), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                    Text(
                        if (dayOfMonth != null) "下次扣费：${store.fmtCn(sub.nextDate)}（${store.daysText(sub.nextDate)}）"
                        else "下次扣费日期还没补全，去「守护 → 编辑」补上更稳妥",
                        fontSize = 12.sp,
                        color = LbInk2,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Text(
                    "取消只能在对应平台完成；管家把步骤带到手，并在之后帮你复核是否真的停了。",
                    fontSize = 12.5.sp,
                    color = LbInk2,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LbGhostButton("保留订阅", onClick = onDismiss, modifier = Modifier.weight(1f))
                    LbPrimaryButton(
                        "去关闭（看步骤）",
                        onClick = { step = 1 },
                        modifier = Modifier.weight(1.35f),
                    )
                }
            } else if (step == 1) {
                Column(Modifier.padding(top = 4.dp)) {
                    CancelGuide(store = store, subId = sub.id, onDone = { step = 2 })
                }
            } else {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconBadge(LbIcons.circleCheck, LbAccentSoft, LbAccent, size = 60.dp)
                    Text(
                        "已标记为「关闭中」",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbInk,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        "取消需在平台完成；之后若仍有扣费，扫描时会提醒你复核。月费 ¥${store.fmtMoney(sub.amount)} 已计入「每月少支出」。",
                        fontSize = 13.sp,
                        color = LbInk2,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    LbPrimaryButton(
                        "完成",
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherIntroDialog(onEnable: () -> Unit, onSkip: () -> Unit) {
    Dialog(onDismissRequest = onSkip) {
        Surface(shape = RoundedCornerShape(24.dp), color = LbSurface) {
            Column(Modifier.padding(20.dp)) {
                Text("在首页显示真实天气？", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                Text(
                    "开启后只做一件事：联网下载你所在城市的天气（不上传任何数据）。需要「大致位置」权限；不想开的话，关闭状态下完全不联网。",
                    fontSize = 12.5.sp,
                    color = LbInk3,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "数据源：Open-Meteo · 结果缓存 1 小时 · 随时可在「我的」里关闭",
                    fontSize = 11.sp,
                    color = LbInk3,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LbGhostButton("不用了", onSkip, Modifier.weight(1f))
                    LbPrimaryButton("好，开启", onEnable, Modifier.weight(1f))
                }
            }
        }
    }
}
