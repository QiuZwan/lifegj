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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.lifebutler.app.ui.components.LbGhostButton
import com.lifebutler.app.ui.components.LbPlusButton
import com.lifebutler.app.ui.components.LbPrimaryButton
import com.lifebutler.app.ui.components.LbTwoActionDialog
import com.lifebutler.app.ui.components.SectionHeader
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

val LB_EXPENSE_CATEGORIES = listOf("餐饮", "交通", "购物", "居家", "娱乐", "医疗", "人情", "其他")

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

@Composable
fun ExpenseScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    var showAdd by remember { mutableStateOf(false) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var editId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }

    val today = LocalDate.now()
    val todayStr = today.toString()
    val todayList = store.expenses.filter { it.date == todayStr }.sortedByDescending { it.at }
    val todayTotal = todayList.sumOf { it.amount }
    val catTotals = store.expenseCategoryTotals(todayList)
    val week = (0..6).map { i ->
        val d = today.minusDays(i.toLong())
        d to store.expenses.filter { it.date == d.toString() }.sumOf { it.amount }
    }
    val weekTotal = week.sumOf { it.second }
    val maxWeek = week.maxOfOrNull { it.second } ?: 0.0
    val groups = store.expenses.groupBy { it.date }.entries.sortedByDescending { it.key }

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
                Text("记账本", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            LbPlusButton(onClick = { showAdd = true }, contentDescription = "记一笔")
        }

        SectionHeader("今天") {
            Text("¥${store.fmtMoney(todayTotal)}", fontSize = 12.5.sp, color = if (todayList.isEmpty()) LbInk3 else LbInk2)
        }
        LbCard(contentPadding = 14.dp) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("¥${store.fmtMoney(todayTotal)}", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                    Text(
                        "${todayList.size} 笔",
                        fontSize = 12.sp,
                        color = LbInk3,
                        modifier = Modifier.padding(start = 8.dp, bottom = 5.dp),
                    )
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
            }
        }

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

        SectionHeader("全部记录") {
            Text("${store.expenses.size} 笔", fontSize = 12.5.sp, color = LbInk3)
        }
        if (groups.isEmpty()) {
            LbCard(contentPadding = 14.dp) {
                Text("还没有记录。今天花的钱，随手记一笔，月底就知道去哪了。", fontSize = 12.sp, color = LbInk3)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                groups.forEach { (date, list) ->
                    Column {
                        Text(
                            lbDateLabel(date),
                            fontSize = 11.5.sp,
                            color = LbInk3,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        )
                        LbCard(contentPadding = 6.dp) {
                            list.sortedByDescending { it.at }.forEach { e ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .lbPressable(onClick = { menuId = e.id })
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconBadge(
                                        lbExpenseIcon(e.category),
                                        if (e.category == "餐饮") LbAmberSoft else LbAccentSoft,
                                        if (e.category == "餐饮") LbAmber else LbAccent,
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
                                            fontSize = 10.5.sp,
                                            color = LbInk3,
                                            modifier = Modifier.padding(top = 1.dp),
                                        )
                                    }
                                    Text(
                                        "¥${store.fmtMoney(e.amount)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = LbInk,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showAdd) {
        ExpenseAddDialog(
            onSave = { a, c, n ->
                store.addExpense(a, c, n)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    editId?.let { id ->
        val e = store.expenses.firstOrNull { it.id == id }
        ExpenseAddDialog(
            initial = e,
            onSave = { a, c, n ->
                store.updateExpense(id, a, c, n)
                editId = null
            },
            onDismiss = { editId = null },
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

/** 记账弹窗:金额 + 分类胶囊 + 备注;也用于编辑初始值 */
@Composable
fun ExpenseAddDialog(
    initial: ButlerExpense? = null,
    onSave: (Double, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initAmount = initial?.let {
        if (it.amount % 1.0 == 0.0) it.amount.toInt().toString() else it.amount.toString()
    } ?: ""
    var amountText by remember { mutableStateOf(initAmount) }
    var cat by remember { mutableStateOf(initial?.category ?: "餐饮") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = LbSurface) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (initial == null) "记一笔" else "编辑这笔",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LbInk,
                )
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
                Text("分类", fontSize = 12.sp, color = LbInk3, modifier = Modifier.padding(top = 12.dp))
                LB_EXPENSE_CATEGORIES.chunked(4).forEach { row ->
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
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）", fontSize = 12.sp) },
                    placeholder = { Text("如：午饭 / 打车回家", fontSize = 12.sp, color = LbInk3) },
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
                                onSave(a, cat, note)
                            }
                        },
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
