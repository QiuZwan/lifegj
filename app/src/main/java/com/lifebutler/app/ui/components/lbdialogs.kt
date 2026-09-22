package com.lifebutler.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lifebutler.app.ui.icons.LbIcons
import com.lifebutler.app.ui.theme.LbAccent
import com.lifebutler.app.ui.theme.LbAccentSoft
import com.lifebutler.app.ui.theme.LbBg
import com.lifebutler.app.ui.theme.LbInk
import com.lifebutler.app.ui.theme.LbInk2
import com.lifebutler.app.ui.theme.LbInk3
import com.lifebutler.app.ui.theme.LbLine
import com.lifebutler.app.ui.theme.LbOnAccent
import com.lifebutler.app.ui.theme.LbRust
import com.lifebutler.app.ui.theme.LbSurface
import com.lifebutler.app.ui.theme.LbSurface2
import java.time.LocalDate
import java.time.YearMonth

/** 输入弹窗字段:label 为字段名;placeholder 为例示;numeric 时使用数字键盘;isDate 时使用日期选择器 */
data class LbField(
    val label: String,
    val placeholder: String = "",
    val numeric: Boolean = false,
    val isDate: Boolean = false,
    val dateClearable: Boolean = false,
)

/* ── 日期工具(弹窗内自足,兼容 10-21 与 2026-10-21 两种旧格式) ── */

private val LB_WEEK_CN = listOf("日", "一", "二", "三", "四", "五", "六")

private fun lbParse(raw: String): LocalDate? {
    var t = raw.trim()
    if (t.isEmpty()) return null
    t = t.replace("年", "-").replace("月", "-").replace("日", "").replace("/", "-").replace(".", "-")
    val parts = t.split("-").filter { it.isNotEmpty() }
    return try {
        when (parts.size) {
            3 -> LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            2 -> {
                var d = LocalDate.of(LocalDate.now().year, parts[0].toInt(), parts[1].toInt())
                if (d.isBefore(LocalDate.now())) d = d.plusYears(1)
                d
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

private fun lbDateLabel(raw: String): String {
    val d = lbParse(raw) ?: return raw
    return "${d.monthValue} 月 ${d.dayOfMonth} 日 · 周${LB_WEEK_CN[d.dayOfWeek.value % 7]}"
}

/**
 * 通用输入弹窗。
 * onConfirm 返回 null 表示通过并关闭;返回字符串则展示为错误并保持打开。
 * 日期字段统一使用日历选择器(带 今天/明天/一周后/下月今天 快捷)。
 */
@Composable
fun LbInputDialog(
    title: String,
    fields: List<LbField>,
    confirmText: String = "保存",
    initial: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> String?,
) {
    val values = remember { mutableStateOf(fields.mapIndexed { i, _ -> initial.getOrElse(i) { "" } }) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickerFor by remember { mutableStateOf<Int?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = LbSurface,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                fields.forEachIndexed { i, f ->
                    if (f.isDate) {
                        Column(Modifier.padding(top = 10.dp)) {
                            Text(f.label, fontSize = 12.sp, color = LbInk3)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, if (pickerFor == i) LbAccent else LbLine, RoundedCornerShape(12.dp))
                                    .clickable {
                                        error = null
                                        pickerFor = i
                                    }
                                    .padding(horizontal = 12.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (values.value[i].isEmpty()) {
                                        f.placeholder.ifEmpty { "点这里选择日期" }
                                    } else {
                                        lbDateLabel(values.value[i])
                                    },
                                    fontSize = 13.5.sp,
                                    color = if (values.value[i].isEmpty()) LbInk3 else LbInk,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    LbIcons.calendarEvent,
                                    contentDescription = null,
                                    tint = LbInk3,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = values.value[i],
                            onValueChange = { v ->
                                values.value = values.value.toMutableList().also { it[i] = v }
                                error = null
                            },
                            label = { Text(f.label, fontSize = 12.sp) },
                            placeholder = {
                                if (f.placeholder.isNotEmpty()) {
                                    Text(f.placeholder, fontSize = 12.sp, color = LbInk3)
                                }
                            },
                            singleLine = true,
                            keyboardOptions = if (f.numeric) {
                                KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            } else {
                                KeyboardOptions.Default
                            },
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
                            textStyle = TextStyle(fontSize = 13.5.sp, color = LbInk),
                        )
                    }
                }
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
                        confirmText,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val err = onConfirm(values.value.map { it.trim() })
                            if (err != null) error = err
                        },
                    )
                }
            }
        }
    }

    pickerFor?.let { idx ->
        LbDatePickerDialog(
            initial = values.value.getOrElse(idx) { "" },
            clearable = fields.getOrElse(idx) { LbField("") }.dateClearable,
            onPick = { iso ->
                values.value = values.value.toMutableList().also { it[idx] = iso }
                pickerFor = null
                error = null
            },
            onClear = {
                values.value = values.value.toMutableList().also { it[idx] = "" }
                pickerFor = null
                error = null
            },
            onDismiss = { pickerFor = null },
        )
    }
}

/** 日历选择器:快捷行 + 月份导航 + 星期表头 + 42 格网格,点选即返回(yyyy-MM-dd) */
@Composable
fun LbDatePickerDialog(
    initial: String,
    clearable: Boolean,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val initialDate = remember { lbParse(initial) }
    var month by remember { mutableStateOf(YearMonth.from(initialDate ?: today)) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = LbSurface) {
            Column(Modifier.padding(18.dp)) {
                Text("选择日期", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "今天" to today,
                        "明天" to today.plusDays(1),
                        "一周后" to today.plusDays(7),
                        "下月今天" to today.plusMonths(1),
                    ).forEach { (label, d) ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(LbAccentSoft)
                                .clickable { onPick(d.toString()) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LbAccent)
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(LbSurface2)
                            .clickable { month = month.minusMonths(1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(LbIcons.chevronLeft, contentDescription = "上个月", tint = LbInk2, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        "${month.year} 年 ${month.monthValue} 月",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbInk,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(LbSurface2)
                            .clickable { month = month.plusMonths(1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(LbIcons.chevronRight, contentDescription = "下个月", tint = LbInk2, modifier = Modifier.size(16.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    LB_WEEK_CN.forEach { w ->
                        Text(
                            w,
                            fontSize = 11.sp,
                            color = LbInk3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                val first = month.atDay(1)
                val lead = first.dayOfWeek.value % 7
                val days = month.lengthOfMonth()
                val prevMonth = month.minusMonths(1)
                val prevDays = prevMonth.lengthOfMonth()
                val rows = (lead + days + 6) / 7
                Column(Modifier.padding(top = 4.dp)) {
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth()) {
                            for (c in 0 until 7) {
                                val idx = r * 7 + c
                                val dayNum = idx - lead + 1
                                if (dayNum < 1) {
                                    val d = prevMonth.atDay(prevDays + dayNum)
                                    DayCell(Modifier.weight(1f), d, false, today, initialDate) { onPick(d.toString()) }
                                } else if (dayNum > days) {
                                    val d = month.plusMonths(1).atDay(dayNum - days)
                                    DayCell(Modifier.weight(1f), d, false, today, initialDate) { onPick(d.toString()) }
                                } else {
                                    val d = month.atDay(dayNum)
                                    DayCell(Modifier.weight(1f), d, true, today, initialDate) { onPick(d.toString()) }
                                }
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
                    if (clearable) {
                        LbGhostButton("清除日期", onClear, Modifier.weight(1f))
                    }
                    LbGhostButton("取消", onDismiss, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    modifier: Modifier,
    date: LocalDate,
    inMonth: Boolean,
    today: LocalDate,
    selected: LocalDate?,
    onClick: () -> Unit,
) {
    val isToday = date.isEqual(today)
    val isSelected = selected != null && date.isEqual(selected)
    val textColor = when {
        isSelected -> LbOnAccent
        !inMonth -> LbInk3
        isToday -> LbAccent
        else -> LbInk
    }
    Box(
        modifier
            .padding(vertical = 2.dp)
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) LbAccent else Color.Transparent)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(1.dp, LbAccent, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            fontSize = 13.sp,
            fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
        )
    }
}

/**
 * 弹窗正文的通用约束:**必须有高度上限、而且要能滚**。
 *
 * 为什么必须有这条 —— `Dialog` 自己不滚,正文多高就画多高,超出去的部分**直接被屏幕裁掉**,
 * 而底部那排按钮是排在正文后面的,于是会被一起顶到屏幕外面。用户看到的就是
 * 「弹窗里找不到按钮、也滑不动」(v2.12 真事:更新说明限定 800 字,实测约 1064dp,
 * 而整屏可用高度只有 640~720dp,「去下载」按钮根本没进过可视区)。
 *
 * 所以凡是**正文长度不由我们决定**的地方(更新说明、协议、服务端回来的文案)都必须挂上它。
 * 高度上限跟着屏幕走,不写死:矮屏(横屏/小屏)上固定 320dp 也可能装不下。
 */
@Composable
fun Modifier.lbDialogBody(): Modifier {
    val maxBody = (LocalConfiguration.current.screenHeightDp * 0.42f).dp.coerceAtMost(340.dp)
    return this
        .heightIn(max = maxBody)
        .verticalScroll(rememberScrollState())
}

/** 双动作弹窗:编辑 / 删除 等两级选择 */
@Composable
fun LbTwoActionDialog(
    title: String,
    text: String,
    actionA: String,
    actionB: String,
    onA: () -> Unit,
    onB: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = LbSurface,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                Text(
                    text,
                    fontSize = 13.sp,
                    color = LbInk3,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 6.dp).lbDialogBody(),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LbPrimaryButton(actionA, onClick = onA, modifier = Modifier.weight(1f))
                    BoxDangerButton(actionB, onB, Modifier.weight(1f))
                }
            }
        }
    }
}

/** 多行粘贴弹窗(备份恢复等) */
@Composable
fun LbPasteDialog(
    title: String,
    hint: String,
    initial: String = "",
    confirmText: String = "恢复",
    onDismiss: () -> Unit,
    onConfirm: (String) -> String?,
) {
    val value = remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = LbSurface,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                Text(
                    hint,
                    fontSize = 12.sp,
                    color = LbInk3,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 6.dp).lbDialogBody(),
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(150.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = LbBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LbLine),
                ) {
                    Box(Modifier.padding(10.dp)) {
                        if (value.value.isEmpty()) {
                            Text("在此粘贴备份内容…", fontSize = 12.sp, color = LbInk3)
                        }
                        BasicTextField(
                            value = value.value,
                            onValueChange = { value.value = it; error = null },
                            textStyle = TextStyle(fontSize = 11.5.sp, color = LbInk),
                            cursorBrush = SolidColor(LbAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                        )
                    }
                }
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
                        confirmText,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val err = onConfirm(value.value.trim())
                            if (err != null) error = err
                        },
                    )
                }
            }
        }
    }
}

/** 二次确认弹窗(删除 / 重置等) */
@Composable
fun LbConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "删除",
    danger: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = LbSurface,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                Text(
                    text,
                    fontSize = 13.sp,
                    color = LbInk3,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 6.dp).lbDialogBody(),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LbGhostButton("取消", onDismiss, Modifier.weight(1f))
                    if (danger) {
                        BoxDangerButton(confirmText, onConfirm, Modifier.weight(1f))
                    } else {
                        LbPrimaryButton(confirmText, onConfirm, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxDangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LbRust)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LbOnAccent)
    }
}
