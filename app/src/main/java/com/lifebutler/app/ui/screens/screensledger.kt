package com.lifebutler.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lifebutler.app.data.ButlerExpense
import com.lifebutler.app.data.ButlerStore
import com.lifebutler.app.ui.components.ChipTone
import com.lifebutler.app.ui.components.IconBadge
import com.lifebutler.app.ui.components.LbCard
import com.lifebutler.app.ui.components.LbChip
import com.lifebutler.app.ui.components.LbConfirmDialog
import com.lifebutler.app.ui.components.LbDatePickerDialog
import com.lifebutler.app.ui.components.LbField
import com.lifebutler.app.ui.components.LbGhostButton
import com.lifebutler.app.ui.components.LbInputDialog
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
import com.lifebutler.app.ui.theme.LbInk
import com.lifebutler.app.ui.theme.LbInk2
import com.lifebutler.app.ui.theme.LbInk3
import com.lifebutler.app.ui.theme.LbLine
import com.lifebutler.app.ui.theme.LbOnAccent
import com.lifebutler.app.ui.theme.LbRust
import com.lifebutler.app.ui.theme.LbSurface
import com.lifebutler.app.ui.theme.LbSurface2
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/* ── 记账本:今天花了多少、花在哪,一页看清 ── */

/**
 * 记账分类的**默认值**。
 *
 * 真正的分类列表在 `store.expenseCategories`（可增删，与备忘录分类同构）。
 * 这个名字留着只为「还没拿到 store 的地方」兜底，别再往里加硬编码分类。
 */
val LB_EXPENSE_CATEGORIES = ButlerStore.DEFAULT_EXPENSE_CATEGORIES

private val LB_WEEK = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun lbExpenseIcon(cat: String): ImageVector = when (cat) {
    "餐饮" -> LbIcons.cake
    "交通" -> LbIcons.car
    "购物" -> LbIcons.buildingStore
    "居家" -> LbIcons.home2
    "娱乐" -> LbIcons.sun
    "医疗" -> LbIcons.heart
    "人情" -> LbIcons.users
    else -> LbIcons.wallet
}

private fun lbDayLabel(d: LocalDate): String =
    if (d == LocalDate.now()) "今天" else LB_WEEK[d.dayOfWeek.value - 1]

private fun lbDateLabel(date: String): String {
    val d = try {
        LocalDate.parse(date)
    } catch (e: Exception) {
        return date
    }
    val today = LocalDate.now()
    return when {
        d == today -> "今天"
        d == today.minusDays(1) -> "昨天"
        else -> "${d.monthValue} 月 ${d.dayOfMonth} 日 · ${LB_WEEK[d.dayOfWeek.value - 1]}"
    }
}

private fun lbHm(at: Long): String = try {
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
} catch (e: Exception) {
    ""
}

/** 记一笔对话框里那一行用的短日期：今天 / 昨天 / 明天 / M 月 D 日 */
private fun lbDateShort(date: String): String {
    val d = try {
        LocalDate.parse(date)
    } catch (e: Exception) {
        return date
    }
    val today = LocalDate.now()
    return when (d) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        today.plusDays(1) -> "明天"
        else -> "${d.monthValue} 月 ${d.dayOfMonth} 日"
    }
}

/**
 * 「全部记录」那几个分组之前有多少个 item（标题行 / 4 个小标题 / 3 张卡片）。
 *
 * LazyColumn 只能按 **item 下标**滚，所以这个数必须跟上面的结构对齐：
 * 在「全部记录」前面加/删一个 item，这里就要跟着改 —— 改错了只是「跳过去差几屏」。
 */
private const val LB_LEDGER_GROUPS_START = 8

@Composable
fun ExpenseScreen(
    onBack: () -> Unit,
    /** 从搜索点进来时要落在哪一条 */
    highlightId: String? = null,
    onHighlightConsumed: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    var showAdd by remember { mutableStateOf(false) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var editId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var showBudget by remember { mutableStateOf(false) }
    var showCats by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val todayStr = today.toString()
    // **一次分组，全页共用。**
    // 原来「最近 7 天」是 7 次 `store.expenses.filter { it.date == d }` —— 也就是每重组一帧
    // 就把整张表扫 7 遍，再加上「今天」「全部记录」各一次。记满一年之后，这页光算数就卡。
    val byDate = remember(store.dataStamp()) { store.expenses.groupBy { it.date } }
    val todayList = (byDate[todayStr] ?: emptyList()).sortedByDescending { it.at }
    val todaySpend = store.spendOf(todayList)
    val todayIncome = store.incomeOf(todayList)
    val catTotals = store.expenseCategoryTotals(todayList)
    // 趋势条只看「花掉多少」：把收入画进同一根柱子里，看的人会以为那天花得特别多
    val week = (0..6).map { i ->
        val d = today.minusDays(i.toLong())
        d to store.spendOf(byDate[d.toString()] ?: emptyList())
    }
    val weekTotal = week.sumOf { it.second }
    val maxWeek = week.maxOfOrNull { it.second } ?: 0.0
    val groups = byDate.entries.sortedByDescending { it.key }

    val monthList = store.expensesInMonth(today.year, today.monthValue)
    val monthSpend = store.spendOf(monthList)
    val monthIncome = store.incomeOf(monthList)
    val budget = store.budgetStatus()

    val listState = rememberLazyListState()
    // 搜索跳过来的那一条：LazyColumn 根本不会组装屏幕外的内容，所以 `bringIntoView` 在这里没用，
    // 只能算出它落在第几个分组、按 item 下标滚过去。
    LaunchedEffect(highlightId, groups) {
        val id = highlightId ?: return@LaunchedEffect
        val gi = groups.indexOfFirst { (_, list) -> list.any { it.id == id } }
        if (gi >= 0) {
            listState.animateScrollToItem(LB_LEDGER_GROUPS_START + gi)
        } else {
            // 找不到（比如这条记录刚好被删了）：别把「待定位」一直挂着
            onHighlightConsumed()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
    ) {
        item {
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
                Text("记账本", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            LbPlusButton(onClick = { showAdd = true }, contentDescription = "记一笔")
        }
        }

        item {
        SectionHeader("今天") {
            Text(
                if (todayIncome > 0) "花 ¥${store.fmtMoney(todaySpend)} · 进 ¥${store.fmtMoney(todayIncome)}"
                else "¥${store.fmtMoney(todaySpend)}",
                fontSize = 12.5.sp,
                color = if (todayList.isEmpty()) LbInk3 else LbInk2,
            )
        }
        LbCard(contentPadding = 14.dp) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("¥${store.fmtMoney(todaySpend)}", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                    Text(
                        "${todayList.size} 笔",
                        fontSize = 12.sp,
                        color = LbInk3,
                        modifier = Modifier.padding(start = 8.dp, bottom = 5.dp),
                    )
                    if (todayIncome > 0) {
                        Text(
                            "另有收入 ¥${store.fmtMoney(todayIncome)}",
                            fontSize = 11.5.sp,
                            color = LbAccent,
                            modifier = Modifier.padding(start = 8.dp, bottom = 5.dp),
                        )
                    }
                }
                if (catTotals.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        catTotals.take(3).forEach { (c, v) ->
                            LbChip("$c ¥${store.fmtMoney(v)}", if (c == "餐饮") ChipTone.Amber else ChipTone.Soft)
                        }
                        if (catTotals.size > 3) {
                            LbChip("等 ${catTotals.size} 类", ChipTone.Soft)
                        }
                    }
                } else {
                    Text("今天还没记账，记第一笔吧。", fontSize = 12.sp, color = LbInk3, modifier = Modifier.padding(top = 6.dp))
                }
                LbPrimaryButton(
                    "记一笔",
                    onClick = { showAdd = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                // 分类管理入口：备忘录的分类早就能自己增删，记账却只能塞「其他」——
                // 有孩子的想加「教育」、养宠物的想加「宠物」、还贷的想加「房贷」，都无处可去。
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "分类管理（可自定义）",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showCats = true }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
        }

        }

        item {
        SectionHeader("本月") {
            Text("${today.monthValue} 月", fontSize = 12.5.sp, color = LbInk3)
        }
        LbCard(contentPadding = 14.dp) {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    MonthStat("支出", monthSpend, LbInk, Modifier.weight(1f))
                    MonthStat("收入", monthIncome, LbAccent, Modifier.weight(1f))
                    MonthStat(
                        "结余",
                        monthIncome - monthSpend,
                        if (monthIncome - monthSpend < 0) LbRust else LbInk,
                        Modifier.weight(1f),
                    )
                }
                if (budget == null) {
                    // 没设预算就不谈超支：凭空替用户定一个数再说他超了，是编造出来的焦虑
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "还没设月度预算",
                            fontSize = 12.sp,
                            color = LbInk3,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "设一个",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LbAccent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showBudget = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    val (spent, cap, over) = budget
                    Column(Modifier.padding(top = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "本月已花 ¥${store.fmtMoney(spent)} / 预算 ¥${store.fmtMoney(cap)}",
                                fontSize = 12.sp,
                                color = LbInk2,
                                modifier = Modifier.weight(1f),
                            )
                            if (over) LbChip("超出 ¥${store.fmtMoney(spent - cap)}", ChipTone.Rust)
                            Text(
                                "改",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LbAccent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showBudget = true }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 7.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(LbSurface2),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((spent / cap).toFloat().coerceIn(0.03f, 1f))
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (over) LbRust else LbAccent),
                            )
                        }
                    }
                }
                val dueSubs = store.subsDueInMonth(today.year, today.monthValue)
                if (dueSubs.isNotEmpty()) {
                    // 订阅与记账第一次联动,但口径必须写清楚:这是「预计、尚未发生」,
                    // 不算进上面的支出/结余 —— 把预期的钱当成已花的钱,就是编造。
                    val dueSum = dueSubs.sumOf { it.amount }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(LbSurface2)
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "订阅预计",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LbInk2,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "¥${store.fmtMoney(dueSum)}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LbInk,
                            )
                        }
                        Text(
                            "本月还有 ${dueSubs.size} 笔订阅要扣（尚未发生，不计入上面的支出）" +
                                "：${dueSubs.joinToString("、") { it.name }}",
                            fontSize = 11.sp,
                            color = LbInk3,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }

        }

        item {
        SectionHeader("最近 7 天") {
            Text("合计 ¥${store.fmtMoney(weekTotal)}", fontSize = 12.5.sp, color = LbInk3)
        }
        LbCard(contentPadding = 14.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                week.reversed().forEach { (d, amt) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            lbDayLabel(d),
                            fontSize = 11.5.sp,
                            color = if (d == today) LbInk else LbInk3,
                            modifier = Modifier.width(52.dp),
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(LbSurface2),
                        ) {
                            if (maxWeek > 0 && amt > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth((amt / maxWeek).toFloat().coerceIn(0.06f, 1f))
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(LbAccent),
                                )
                            }
                        }
                        Text(
                            "¥${store.fmtMoney(amt)}",
                            fontSize = 11.5.sp,
                            color = if (amt > 0) LbInk2 else LbInk3,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(64.dp),
                        )
                    }
                }
            }
        }

        }

        item {
            SectionHeader("全部记录") {
                Text("${store.expenses.size} 笔", fontSize = 12.5.sp, color = LbInk3)
            }
        }
        if (groups.isEmpty()) {
            item {
            LbCard(contentPadding = 14.dp) {
                Text("还没有记录。今天花的钱，随手记一笔，月底就知道去哪了。", fontSize = 12.sp, color = LbInk3)
            }
            }
        } else {
            // 一天一个 item：滚到哪儿才组装哪儿。原来这里是 `groups.forEach` 套在 Column 里，
            // 一页记满几百条就要一次性把它们（连同每行的图标）全建出来，是这一页发涩的主因。
            items(groups, key = { it.key }) { (date, list) ->
                Column(Modifier.padding(bottom = 10.dp)) {
                        Text(
                            lbDateLabel(date),
                            fontSize = 11.5.sp,
                            color = LbInk3,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        )
                        LbCard(contentPadding = 6.dp) {
                            list.sortedByDescending { it.at }.forEach { e ->
                                // 搜索跳过来的那一条：滚进来 + 亮一下
                                val hl = lbItemHighlight(e.id, highlightId, onHighlightConsumed)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .then(hl.modifier)
                                        .background(lbHighlightBg(hl.active))
                                        .lbPressable(onClick = { menuId = e.id })
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconBadge(
                                        if (e.income) LbIcons.arrowUpRight else lbExpenseIcon(e.category),
                                        if (e.income) LbAccentSoft else if (e.category == "餐饮") LbAmberSoft else LbAccentSoft,
                                        if (e.income) LbAccent else if (e.category == "餐饮") LbAmber else LbAccent,
                                        size = 32.dp,
                                    )
                                    Column(
                                        Modifier
                                            .padding(start = 10.dp)
                                            .weight(1f),
                                    ) {
                                        Text(
                                            e.note.ifEmpty { e.category },
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = LbInk,
                                        )
                                        Text(
                                            e.category + " · " + lbHm(e.at),
                                            fontSize = 11.sp,
                                            color = LbInk3,
                                            modifier = Modifier.padding(top = 1.dp),
                                        )
                                    }
                                    Text(
                                        (if (e.income) "+¥" else "¥") + store.fmtMoney(e.amount),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (e.income) LbAccent else LbInk,
                                    )
                                }
                            }
                        }
                }
        }
    }
    }

    if (showAdd) {
        ExpenseAddDialog(
            categories = store.expenseCategories.value,
            onSave = { a, c, n, income, date ->
                store.addExpense(a, c, n, income, date)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    editId?.let { id ->
        val e = store.expenses.firstOrNull { it.id == id }
        ExpenseAddDialog(
            initial = e,
            categories = store.expenseCategories.value,
            onSave = { a, c, n, income, date ->
                store.updateExpense(id, a, c, n, income, date)
                editId = null
            },
            onDismiss = { editId = null },
        )
    }

    if (showCats) {
        ExpenseCategoryDialog(
            categories = store.expenseCategories.value,
            counts = store.expenseCategories.value.associateWith { store.expenseCountOf(it) },
            onAdd = { store.addExpenseCategory(it) },
            onRemove = { store.removeExpenseCategory(it) },
            onDismiss = { showCats = false },
        )
    }

    if (showBudget) {
        LbInputDialog(
            title = "月度预算",
            fields = listOf(LbField("每月支出上限（元）", "如：3000 · 留空或填 0 表示不设", numeric = true)),
            initial = listOf(if (store.monthlyBudget.value > 0) store.fmtMoney(store.monthlyBudget.value) else ""),
            onDismiss = { showBudget = false },
            onConfirm = { v ->
                val raw = v.getOrElse(0) { "" }.trim()
                if (raw.isEmpty()) {
                    store.setMonthlyBudget(0.0)
                    showBudget = false
                    null
                } else {
                    val d = raw.toDoubleOrNull()
                    if (d == null || d < 0) {
                        "预算填数字，比如 3000"
                    } else {
                        store.setMonthlyBudget(d)
                        showBudget = false
                        null
                    }
                }
            },
        )
    }

    menuId?.let { id ->
        val e = store.expenses.firstOrNull { it.id == id }
        LbTwoActionDialog(
            title = e?.note?.ifEmpty { e.category } ?: "这笔",
            text = "要修改金额或分类，还是删除？",
            actionA = "编辑",
            actionB = "删除",
            onA = { editId = id; menuId = null },
            onB = { deleteId = id; menuId = null },
            onDismiss = { menuId = null },
        )
    }

    deleteId?.let { id ->
        LbConfirmDialog(
            title = "删除这笔花销？",
            text = "删除后不计入统计。",
            onDismiss = { deleteId = null },
            onConfirm = {
                store.removeExpense(id)
                deleteId = null
            },
        )
    }
}

/** 一笔账:金额 + 分类胶囊 + 日期 + 备注;也用于编辑初始值。[onSave] 最后一项 true = 收入，再后是日期 */
@Composable
fun ExpenseAddDialog(
    initial: ButlerExpense? = null,
    categories: List<String> = LB_EXPENSE_CATEGORIES,
    onSave: (Double, String, String, Boolean, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initAmount = initial?.let {
        if (it.amount % 1.0 == 0.0) it.amount.toInt().toString() else it.amount.toString()
    } ?: ""
    var amountText by remember { mutableStateOf(initAmount) }
    var cat by remember { mutableStateOf(initial?.category ?: "餐饮") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var income by remember { mutableStateOf(initial?.income ?: false) }
    // 日期：默认今天；编辑时用这笔**原来的**日期（别把旧账顺手改成今天）。
    // 「昨晚忘了记、今天早上补」是记账里最高频的场景，没有这一行就只能记成今天 ——
    // 于是今天的合计里混进昨天的钱、昨天显示 0，近 7 天趋势条整体错位。
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().toString()) }
    var showDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // 分类可能被删掉过（比如这份记录来自旧备份），把当前值补进去，免得选不中/看不出来
    val cats = remember(categories, cat) {
        if (categories.contains(cat)) categories else categories + cat
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = LbSurface) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (initial == null) "记一笔" else "编辑这笔",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LbInk,
                )
                // 收支开关放在最上面：先决定「这是花出去还是收进来」，再填金额和分类
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LbSurface2)
                        .padding(3.dp),
                ) {
                    listOf(false to "支出", true to "收入").forEach { (v, label) ->
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (income == v) LbAccent else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { income = v }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (income == v) LbOnAccent else LbInk2,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; error = null },
                    label = { Text("金额（元）", fontSize = 12.sp) },
                    placeholder = { Text("如：25", fontSize = 12.sp, color = LbInk3) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LbAccent,
                        unfocusedBorderColor = LbLine,
                        focusedLabelColor = LbAccent,
                        unfocusedLabelColor = LbInk3,
                        cursorColor = LbAccent,
                    ),
                    textStyle = TextStyle(fontSize = 15.sp, color = LbInk),
                )
                if (income) {
                    Text(
                        "收入分类固定记为「收入」，不用再选。",
                        fontSize = 11.5.sp,
                        color = LbInk3,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Text("分类", fontSize = 12.sp, color = LbInk3, modifier = Modifier.padding(top = 12.dp))
                    cats.chunked(4).forEach { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            row.forEach { c ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (cat == c) LbAccent else LbSurface2)
                                        .clickable { cat = c }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        c,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (cat == c) LbOnAccent else LbInk2,
                                    )
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                // 日期行：点开用项目里**同一个**日历选择器（今天/昨天/明天…+ 月份导航），不另写一套
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LbSurface2)
                        .clickable { showDate = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (income) "入账日期" else "消费日期",
                        fontSize = 12.sp,
                        color = LbInk3,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        lbDateShort(date),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbAccent,
                    )
                    Text("  更改", fontSize = 11.sp, color = LbInk3)
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）", fontSize = 12.sp) },
                    placeholder = {
                        Text(if (income) "如：八月工资 / 报销到账" else "如：午饭 / 打车回家", fontSize = 12.sp, color = LbInk3)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LbAccent,
                        unfocusedBorderColor = LbLine,
                        focusedLabelColor = LbAccent,
                        unfocusedLabelColor = LbInk3,
                        cursorColor = LbAccent,
                    ),
                    textStyle = TextStyle(fontSize = 13.5.sp, color = LbInk),
                )
                error?.let {
                    Text(it, fontSize = 12.sp, color = LbRust, modifier = Modifier.padding(top = 8.dp))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LbGhostButton("取消", onDismiss, Modifier.weight(1f))
                    LbPrimaryButton(
                        "保存",
                        {
                            val a = amountText.trim().toDoubleOrNull()
                            if (a == null || a <= 0) {
                                error = "金额填数字，比如 25"
                            } else {
                                onSave(a, if (income) "收入" else cat, note, income, date)
                            }
                        },
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
    if (showDate) {
        LbDatePickerDialog(
            initial = date,
            clearable = false,
            onPick = { date = it; showDate = false },
            onClear = { showDate = false },
            onDismiss = { showDate = false },
        )
    }
}

/** 本月三个数字（支出 / 收入 / 结余）里的一格 */
@Composable
private fun MonthStat(label: String, value: Double, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = LbInk3)
        Text(
            (if (value < 0) "-¥" else "¥") + fmtPlain(kotlin.math.abs(value)),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 这里只要「两位小数、去掉多余的 0」，不引用 store（本文件的对话框层没有 store 实例） */
private fun fmtPlain(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.getDefault(), "%.2f", v).trimEnd('0').trimEnd('.')

/* ── 记账分类管理：新增 / 删除（删分类**不删账**，账目改挂「其他」） ── */

@Composable
private fun ExpenseCategoryDialog(
    categories: List<String>,
    counts: Map<String, Int>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = LbSurface) {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Text("记账分类", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                Text(
                    "删掉一个分类不会删掉账目 —— 那些账会移到「其他」。「其他」是兜底分类，不能删除。",
                    fontSize = 12.sp,
                    color = LbInk3,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Column(Modifier.padding(top = 8.dp)) {
                    categories.forEach { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c, fontSize = 13.sp, color = LbInk, modifier = Modifier.weight(1f))
                            Text(
                                "${counts[c] ?: 0} 笔",
                                fontSize = 11.sp,
                                color = LbInk3,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            if (c != ButlerStore.EXPENSE_FALLBACK) {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(LbSurface2)
                                        .clickable { error = null; onRemove(c) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        LbIcons.trash,
                                        contentDescription = "删除分类 $c",
                                        tint = LbRust,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            } else {
                                Box(Modifier.size(28.dp))
                            }
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; error = null },
                        placeholder = { Text("新的分类名，如：教育 / 宠物 / 房贷", fontSize = 12.sp, color = LbInk3) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LbAccent,
                            unfocusedBorderColor = LbLine,
                            cursorColor = LbAccent,
                        ),
                        textStyle = TextStyle(fontSize = 13.sp, color = LbInk),
                    )
                    Spacer(Modifier.width(8.dp))
                    LbPrimaryButton(
                        "添加",
                        onClick = {
                            val n = text.trim()
                            when {
                                n.isEmpty() -> error = "分类名不能为空"
                                categories.any { it.equals(n, true) } -> error = "这个分类已经有了"
                                else -> {
                                    onAdd(n)
                                    text = ""
                                }
                            }
                        },
                    )
                }
                error?.let {
                    Text(it, fontSize = 12.sp, color = LbRust, modifier = Modifier.padding(top = 8.dp))
                }
                Text(
                    "分类只存在本机，只用于你自己的账目统计。",
                    fontSize = 11.sp,
                    color = LbInk3,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    LbGhostButton("完成", onDismiss, Modifier.weight(1f))
                }
            }
        }
    }
}
