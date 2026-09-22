package com.lifebutler.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifebutler.app.data.ButlerStore
import com.lifebutler.app.ui.components.ChipTone
import com.lifebutler.app.ui.components.IconBadge
import com.lifebutler.app.ui.components.LbCard
import com.lifebutler.app.ui.components.LbChip
import com.lifebutler.app.ui.components.SectionHeader
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
import com.lifebutler.app.ui.theme.LbOnDark
import com.lifebutler.app.ui.theme.LbRust
import com.lifebutler.app.ui.theme.LbSurface
import com.lifebutler.app.ui.theme.LbSurface2
import java.time.LocalDate
import java.time.YearMonth

/* ── 月报:只统计有真实时间戳的数据(记账、扣费流水、关闭历史) ── */

@Composable
fun MonthReportScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    var back by remember { mutableStateOf(0) }

    val month: YearMonth = YearMonth.now().minusMonths(back.toLong())
    val prev: YearMonth = month.minusMonths(1)

    val monthExpenses = store.expensesInMonth(month.year, month.monthValue)
    // 月报的三行口径：支出 / 收入 / 结余。都只由列表复算，不做任何推算
    val monthTotal = store.spendOf(monthExpenses)
    val monthIncome = store.incomeOf(monthExpenses)
    val prevTotal = store.spendOf(store.expensesInMonth(prev.year, prev.monthValue))
    val cats = store.expenseCategoryTotals(monthExpenses)
    val maxCat = cats.maxOfOrNull { it.second } ?: 0.0

    val monthCharges = store.charges.filter { c ->
        val d = store.parseDate(c.date)
        d != null && d.year == month.year && d.monthValue == month.monthValue
    }.sortedByDescending { it.at }

    val closedThisMonth = store.closedInMonth(month.year, month.monthValue)

    val activeSubs = store.subs.filter { !it.closing }
    val monthSubTotal = activeSubs.sumOf { it.amount }

    val delta = when {
        prevTotal <= 0 && monthTotal <= 0 -> ""
        prevTotal <= 0 -> "上月没有记账，无法比较"
        else -> {
            val pct = ((monthTotal - prevTotal) / prevTotal * 100)
            val sign = if (pct >= 0) "+" else ""
            "%s%.0f%% 对比上月".format(sign, pct)
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
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LbIcons.chevronLeft, contentDescription = "返回", tint = LbInk2, modifier = Modifier.size(20.dp))
                Text("月报", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            LbChip("${month.year} 年 ${month.monthValue} 月", ChipTone.Soft)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (back == 0) "本月" else "${month.monthValue} 月",
                fontSize = 11.5.sp,
                color = LbInk3,
                modifier = Modifier.weight(1f),
            )
            Text(
                "上一月",
                fontSize = 11.5.sp,
                color = if (back > 0) LbAccent else LbInk3,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { if (back > 0) back-- }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                "下一月",
                fontSize = 11.5.sp,
                color = if (back < 0) LbAccent else LbInk3,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { if (back < 0) back++ }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        SectionHeader("花钱") {
            Text(if (delta.isEmpty()) "尚无对比" else delta, fontSize = 12.sp, color = LbInk3)
        }
        LbCard(contentPadding = 14.dp) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("¥${store.fmtMoney(monthTotal)}", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                    Text(
                        "${monthExpenses.size} 笔",
                        fontSize = 12.sp,
                        color = LbInk3,
                        modifier = Modifier.padding(start = 8.dp, bottom = 5.dp),
                    )
                }
                if (prevTotal > 0) {
                    Text(
                        "上月 ¥${store.fmtMoney(prevTotal)}",
                        fontSize = 11.5.sp,
                        color = LbInk3,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // 有收入才显示这两行：一笔收入都没记的时候，
                // 「收入 ¥0 / 结余 -¥820」只会让人以为算错了
                if (monthIncome > 0) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Column {
                            Text("收入", fontSize = 11.sp, color = LbInk3)
                            Text(
                                "¥${store.fmtMoney(monthIncome)}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LbAccent,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                        Column {
                            Text("结余", fontSize = 11.sp, color = LbInk3)
                            val bal = monthIncome - monthTotal
                            Text(
                                (if (bal < 0) "-¥" else "¥") + store.fmtMoney(kotlin.math.abs(bal)),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (bal < 0) LbRust else LbInk,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                    }
                }
                if (cats.isEmpty()) {
                    Text(
                        "这个月还没有记账。随手记一笔，月底就知道钱去哪了。",
                        fontSize = 12.sp,
                        color = LbInk3,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Column(
                        Modifier.padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        cats.take(5).forEach { (c, v) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c, fontSize = 11.5.sp, color = LbInk2, modifier = Modifier.width(42.dp))
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(LbSurface2),
                                ) {
                                    if (maxCat > 0) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth((v / maxCat).toFloat().coerceIn(0.05f, 1f))
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(if (c == "餐饮") LbAmber else LbAccent),
                                        )
                                    }
                                }
                                Text(
                                    "¥${store.fmtMoney(v)}",
                                    fontSize = 11.5.sp,
                                    color = LbInk2,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(66.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionHeader("扣费流水") {
            Text(
                if (monthCharges.isEmpty()) "本月无记录" else "${monthCharges.size} 笔 · ¥${store.fmtMoney(monthCharges.sumOf { it.amount })}",
                fontSize = 12.sp,
                color = LbInk3,
            )
        }
        LbCard(contentPadding = 10.dp) {
            if (monthCharges.isEmpty()) {
                Text(
                    "本月还没有扣费记录。开启「通知读取」后，扣费通知会在这里自动留档。",
                    fontSize = 12.sp,
                    color = LbInk3,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(6.dp),
                )
            } else {
                monthCharges.take(8).forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(store.fmtCn(c.date), fontSize = 12.sp, color = LbInk2)
                        Text(
                            c.subName,
                            fontSize = 12.sp,
                            color = LbInk,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .weight(1f),
                        )
                        if (c.source != "手动") {
                            LbChip(c.source, ChipTone.Soft)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("¥${store.fmtMoney(c.amount)}", fontSize = 12.sp, color = LbInk)
                    }
                }
                if (monthCharges.size > 8) {
                    Text(
                        "另有 ${monthCharges.size - 8} 笔，去对应订阅详情看全部",
                        fontSize = 11.sp,
                        color = LbInk3,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }
            }
        }

        SectionHeader("订阅")
        LbCard(contentPadding = 14.dp) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("当前每月订阅合计", fontSize = 11.5.sp, color = LbInk3)
                        Text(
                            "¥${store.fmtMoney(monthSubTotal)}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LbInk,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Text("${activeSubs.size} 笔生效中", fontSize = 11.5.sp, color = LbInk3)
                }
                val noDate = activeSubs.count { it.nextDate.isBlank() }
                if (noDate > 0) {
                    Text(
                        "其中 $noDate 笔还没补全扣费日，去「守护」页补上",
                        fontSize = 11.5.sp,
                        color = LbAmber,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp)),
                color = LbAccentSoft,
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.padding(13.dp)) {
                    Text("本月标记关闭", fontSize = 11.5.sp, color = LbAccent)
                    Text(
                        "${closedThisMonth.size} 笔",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbAccent,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Surface(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp)),
                color = LbSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, LbLine),
            ) {
                Column(Modifier.padding(13.dp)) {
                    Text("本月少支出", fontSize = 11.5.sp, color = LbInk3)
                    Text(
                        "¥${store.fmtMoney(closedThisMonth.sumOf { it.amount })}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbInk,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        SectionHeader("当前进度")
        LbCard(contentPadding = 14.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressRow("待办事项", store.tasks.count { it.done }, store.tasks.size)
                ProgressRow("到期义务", store.obligations.count { it.done }, store.obligations.size)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(LbIcons.wallet, LbAccentSoft, LbAccent, size = 30.dp)
                    Text(
                        "累计记账 ${store.expenses.size} 笔",
                        fontSize = 12.5.sp,
                        color = LbInk,
                        modifier = Modifier.padding(start = 11.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(LbIcons.fileText, LbAccentSoft, LbAccent, size = 30.dp)
                    Text(
                        "已归档文件 ${store.archiveFileCount()} 份",
                        fontSize = 12.5.sp,
                        color = LbInk,
                        modifier = Modifier.padding(start = 11.dp),
                    )
                }
                if (store.closedCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(LbIcons.circleCheck, LbAccentSoft, LbAccent, size = 30.dp)
                        Text(
                            "历史累计标记关闭 ${store.closedCount} 笔 · 每月少支出 ¥${store.fmtMoney(store.monthlySaved)}",
                            fontSize = 12.5.sp,
                            color = LbInk,
                            modifier = Modifier.padding(start = 11.dp),
                        )
                    }
                }
            }
        }
        Text(
            "月报只统计有真实时间的数据（记账、扣费流水、关闭记录）；「当前进度」是此刻的快照。",
            fontSize = 11.sp,
            color = LbInk3,
            lineHeight = 17.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun ProgressRow(label: String, done: Int, total: Int) {
    val ratio = if (total <= 0) 0f else done.toFloat() / total
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(
            if (ratio >= 1f && total > 0) LbIcons.circleCheck else LbIcons.calendarEvent,
            if (ratio >= 1f && total > 0) LbAccentSoft else LbSurface2,
            if (ratio >= 1f && total > 0) LbAccent else LbInk2,
            size = 30.dp,
        )
        Column(
            Modifier
                .padding(start = 11.dp)
                .weight(1f),
        ) {
            Text(label, fontSize = 12.5.sp, color = LbInk)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(LbSurface2),
            ) {
                if (total > 0 && done > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth(ratio.coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(LbAccent),
                    )
                }
            }
        }
        Text(
            if (total == 0) "暂无" else "$done / $total",
            fontSize = 11.5.sp,
            color = if (total == 0) LbInk3 else LbInk2,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
