package com.lifebutler.app.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.lifebutler.app.data.ButlerMemo
import com.lifebutler.app.data.ButlerStore
import com.lifebutler.app.data.MemoSort
import com.lifebutler.app.ui.components.ChipTone
import com.lifebutler.app.ui.components.LbCard
import com.lifebutler.app.ui.components.LbChip
import com.lifebutler.app.ui.components.LbConfirmDialog
import com.lifebutler.app.ui.components.LbDatePickerDialog
import com.lifebutler.app.ui.components.LbGhostButton
import com.lifebutler.app.ui.components.LbPlusButton
import com.lifebutler.app.ui.components.LbPrimaryButton
import com.lifebutler.app.ui.components.LbTwoActionDialog
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/* ── 备忘录:新建 / 编辑 / 删除 / 分类 / 搜索 / 排序 / 提醒,一页管完 ── */

/** 分类筛选里的「全部」哨兵值 */
private const val MEMO_ALL = "全部"

private val LB_MEMO_SORTS = listOf(
    MemoSort.Updated to "最近更新",
    MemoSort.Created to "创建时间",
    MemoSort.Remind to "提醒时间",
)

private val LB_WEEK_CN = listOf("日", "一", "二", "三", "四", "五", "六")

private fun lbClock(ms: Long): String = try {
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
} catch (e: Exception) {
    ""
}

/** 卡片上的短日期:今天 / 昨天 / M 月 d 日 + 时刻 */
private fun lbMemoTime(ms: Long): String {
    if (ms <= 0L) return ""
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val hm = lbClock(ms)
    return when {
        d.isEqual(today) -> "今天 $hm"
        d.isEqual(today.minusDays(1)) -> "昨天 $hm"
        else -> "${d.monthValue} 月 ${d.dayOfMonth} 日 $hm"
    }
}

/** 提醒用的紧凑写法:M-d HH:mm */
private fun lbShortDateTime(ms: Long): String {
    if (ms <= 0L) return ""
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${d.monthValue}-${d.dayOfMonth} ${lbClock(ms)}"
}

/** 提醒用的完整写法:M 月 d 日 HH:mm */
private fun lbFullDateTime(ms: Long): String {
    if (ms <= 0L) return ""
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${d.monthValue} 月 ${d.dayOfMonth} 日 ${lbClock(ms)}"
}

private fun lbDateCn(d: LocalDate): String =
    "${d.monthValue} 月 ${d.dayOfMonth} 日 · 周${LB_WEEK_CN[d.dayOfWeek.value % 7]}"

private fun lbMillisAt(d: LocalDate, hour: Int, minute: Int): Long =
    d.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
fun MemoScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }

    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(MEMO_ALL) }
    var sort by remember { mutableStateOf(MemoSort.Updated) }
    var editing by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var showCats by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(ctx, if (granted) "提醒已开启" else "没授权通知，到点不会弹出提醒", Toast.LENGTH_SHORT).show()
    }
    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, "android.permission.POST_NOTIFICATIONS") !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch("android.permission.POST_NOTIFICATIONS")
        }
    }

    val categories = store.memoCategories.value
    val counts = store.memos.groupingBy { it.category }.eachCount()
    val all = store.searchMemos(query)
    val shown = store.sortedMemos(all.filter { cat == MEMO_ALL || it.category == cat }, sort)
    val isSearching = query.trim().isNotEmpty() || cat != MEMO_ALL

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
                Text("备忘录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            LbPlusButton(
                onClick = { editId = null; editing = true },
                contentDescription = "新建备忘",
            )
        }

        // 关键词搜索:标题与正文一起找
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索标题或内容", fontSize = 13.sp, color = LbInk3) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            leadingIcon = {
                Icon(LbIcons.search, contentDescription = null, tint = LbInk3, modifier = Modifier.size(17.dp))
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Icon(
                        LbIcons.x,
                        contentDescription = "清空搜索",
                        tint = LbInk3,
                        modifier = Modifier
                            .size(17.dp)
                            .clickable { query = "" },
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LbAccent,
                unfocusedBorderColor = LbLine,
                cursorColor = LbAccent,
            ),
            textStyle = TextStyle(fontSize = 13.5.sp, color = LbInk),
        )

        // 分类筛选 + 分类管理入口
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                text = if (store.memos.isEmpty()) "全部" else "全部 ${store.memos.size}",
                selected = cat == MEMO_ALL,
                onClick = { cat = MEMO_ALL },
            )
            categories.forEach { c ->
                FilterChip(
                    text = if ((counts[c] ?: 0) > 0) "$c ${counts[c]}" else c,
                    selected = cat == c,
                    onClick = { cat = c },
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(LbSurface2)
                    .clickable { showCats = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(LbIcons.folders, contentDescription = null, tint = LbInk3, modifier = Modifier.size(13.dp))
                    Text(
                        "管理",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbInk2,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }

        // 排序方式 + 结果条数
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("排序", fontSize = 11.sp, color = LbInk3)
            LB_MEMO_SORTS.forEach { (key, label) ->
                FilterChip(text = label, selected = sort == key, onClick = { sort = key })
            }
            Spacer(Modifier.width(2.dp))
            Text("共 ${shown.size} 条", fontSize = 11.sp, color = LbInk3)
        }

        Spacer(Modifier.height(12.dp))

        if (shown.isEmpty()) {
            LbCard(contentPadding = 14.dp) {
                Text(
                    if (isSearching) {
                        "没有匹配的备忘。换个关键词，或把分类切回「全部」。"
                    } else {
                        "还没有备忘。点右上角「+」随手记一条：标题、内容、分类，还能设个提醒时间。"
                    },
                    fontSize = 12.sp,
                    color = LbInk3,
                    lineHeight = 19.sp,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                shown.forEach { m ->
                    MemoCard(
                        memo = m,
                        onOpen = { editId = m.id; editing = true },
                        onLong = { menuId = m.id },
                        onTogglePin = { store.toggleMemoPin(m.id) },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (editing) {
        val initial = editId?.let { id -> store.memos.firstOrNull { it.id == id } }
        MemoEditDialog(
            initial = initial,
            categories = categories,
            onAddCategory = { store.addMemoCategory(it) },
            onSave = { title, content, c, remindAt ->
                if (initial == null) store.addMemo(title, content, c, remindAt)
                else store.updateMemo(initial.id, title, content, c, remindAt)
                if (remindAt > System.currentTimeMillis()) ensureNotifPermission()
                editing = false
                editId = null
            },
            onDismiss = { editing = false; editId = null },
        )
    }

    menuId?.let { id ->
        val m = store.memos.firstOrNull { it.id == id }
        LbTwoActionDialog(
            title = m?.title ?: "这条备忘",
            text = "要改内容、分类或提醒时间，还是直接删除？",
            actionA = "编辑",
            actionB = "删除",
            onA = { editId = id; editing = true; menuId = null },
            onB = { deleteId = id; menuId = null },
            onDismiss = { menuId = null },
        )
    }

    deleteId?.let { id ->
        LbConfirmDialog(
            title = "删除这条备忘？",
            text = "删掉后就找不回来了；如果上面设了提醒，也会一并取消。",
            onDismiss = { deleteId = null },
            onConfirm = {
                store.removeMemo(id)
                deleteId = null
            },
        )
    }

    if (showCats) {
        MemoCategoryDialog(
            categories = categories,
            counts = counts,
            onAdd = { store.addMemoCategory(it) },
            onRemove = { store.removeMemoCategory(it) },
            onDismiss = { showCats = false },
        )
    }
}

/* ── 胶囊筛选按钮(选中=深玉绿) ── */

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) LbAccent else LbSurface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) LbOnAccent else LbInk2,
        )
    }
}

/* ── 一条备忘的卡片 ── */

@Composable
private fun MemoCard(
    memo: ButlerMemo,
    onOpen: () -> Unit,
    onLong: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val reminded = memo.remindAt > 0L && memo.remindAt <= now
    val futureRemind = memo.remindAt > now
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .lbPressable(onClick = onOpen, onLongClick = onLong),
        shape = RoundedCornerShape(18.dp),
        color = LbSurface,
        border = BorderStroke(1.dp, LbLine),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LbChip(memo.category, ChipTone.Green)
                if (futureRemind) {
                    Spacer(Modifier.width(6.dp))
                    LbChip("提醒 ${lbShortDateTime(memo.remindAt)}", ChipTone.Amber)
                } else if (reminded) {
                    Spacer(Modifier.width(6.dp))
                    LbChip("已提醒 ${lbShortDateTime(memo.remindAt)}", ChipTone.Soft)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (memo.pinned) LbAmberSoft else LbSurface2)
                        .clickable(onClick = onTogglePin),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        LbIcons.pin,
                        contentDescription = if (memo.pinned) "取消置顶" else "置顶",
                        tint = if (memo.pinned) LbAmber else LbInk3,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                memo.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LbInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (memo.content.isNotBlank()) {
                Text(
                    memo.content,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = LbInk2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text(
                lbMemoTime(memo.updatedAt).let { if (it.isEmpty()) "" else "更新于 $it" },
                fontSize = 11.sp,
                color = LbInk3,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/* ── 新建 / 编辑备忘 ── */

@Composable
private fun MemoEditDialog(
    initial: ButlerMemo?,
    categories: List<String>,
    onAddCategory: (String) -> Unit,
    onSave: (String, String, String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: ButlerStore.MEMO_UNCATEGORIZED) }
    var remind by remember { mutableStateOf(initial?.remindAt ?: 0L) }
    var showRemind by remember { mutableStateOf(false) }
    var showNewCat by remember { mutableStateOf(false) }
    var newCat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = LbSurface) {
            Column(
                Modifier
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Text(
                    if (initial == null) "新建备忘" else "编辑备忘",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LbInk,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = { Text("标题", fontSize = 12.sp) },
                    placeholder = { Text("如：周末采购清单", fontSize = 12.sp, color = LbInk3) },
                    singleLine = true,
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
                    textStyle = TextStyle(fontSize = 14.sp, color = LbInk),
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it; error = null },
                    label = { Text("内容", fontSize = 12.sp) },
                    placeholder = { Text("想记什么就写什么，可以换行", fontSize = 12.sp, color = LbInk3) },
                    minLines = 4,
                    maxLines = 9,
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
                    textStyle = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp, color = LbInk),
                )

                // 分类
                Text("分类", fontSize = 12.sp, color = LbInk3, modifier = Modifier.padding(top = 12.dp))
                val chips = categories + "＋新建"
                chips.chunked(4).forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { c ->
                            val isNew = c == "＋新建"
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (!isNew && cat == c) LbAccent else LbSurface2)
                                    .clickable {
                                        error = null
                                        if (isNew) showNewCat = true else cat = c
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    c,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (!isNew && cat == c) LbOnAccent else LbInk2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                if (showNewCat) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newCat,
                            onValueChange = { newCat = it },
                            placeholder = { Text("新分类名", fontSize = 12.sp, color = LbInk3) },
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
                                val n = newCat.trim()
                                if (n.isEmpty()) {
                                    error = "分类名不能为空"
                                } else {
                                    onAddCategory(n)
                                    cat = n
                                    newCat = ""
                                    showNewCat = false
                                }
                            },
                            modifier = Modifier.width(84.dp),
                        )
                    }
                }

                // 提醒时间
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, LbLine, RoundedCornerShape(12.dp))
                        .clickable { error = null; showRemind = true }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        LbIcons.bell,
                        contentDescription = null,
                        tint = if (remind > 0L) LbAmber else LbInk3,
                        modifier = Modifier.size(16.dp),
                    )
                    Column(
                        Modifier
                            .padding(start = 10.dp)
                            .weight(1f),
                    ) {
                        Text("提醒时间", fontSize = 11.sp, color = LbInk3)
                        Text(
                            if (remind > 0L) lbFullDateTime(remind) else "不提醒",
                            fontSize = 13.5.sp,
                            color = if (remind > 0L) LbInk else LbInk3,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                    if (remind > 0L) {
                        Text(
                            "清除",
                            fontSize = 11.5.sp,
                            color = LbRust,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { remind = 0L }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    } else {
                        Icon(LbIcons.chevronRight, contentDescription = null, tint = LbInk3, modifier = Modifier.size(15.dp))
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
                        "保存",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (title.isBlank() && content.isBlank()) {
                                error = "至少写个标题，或写点内容"
                            } else {
                                onSave(title, content, cat, remind)
                            }
                        },
                    )
                }
            }
        }
    }

    if (showRemind) {
        MemoRemindDialog(
            initial = remind,
            onPick = { remind = it; showRemind = false },
            onClear = { remind = 0L; showRemind = false },
            onDismiss = { showRemind = false },
        )
    }
}

/* ── 提醒时间选择:快捷 + 日历 + 时/分步进 ── */

@Composable
private fun MemoRemindDialog(
    initial: Long,
    onPick: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val hasInit = initial > 0L
    val initMoment = if (hasInit) Instant.ofEpochMilli(initial).atZone(zone) else null

    var date by remember { mutableStateOf(initMoment?.toLocalDate() ?: LocalDate.now()) }
    var hour by remember { mutableStateOf(initMoment?.hour ?: 9) }
    var minute by remember { mutableStateOf(((initMoment?.minute ?: 0) / 5) * 5) }
    var showDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picked = lbMillisAt(date, hour, minute)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = LbSurface) {
            Column(Modifier.padding(20.dp)) {
                Text("设置提醒时间", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)

                // 常用时间,一点就走
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val today = LocalDate.now()
                    listOf(
                        "今天 09:00" to (today to (9 to 0)),
                        "今天 20:00" to (today to (20 to 0)),
                        "明天 09:00" to (today.plusDays(1) to (9 to 0)),
                        "一周后" to (today.plusDays(7) to (9 to 0)),
                    ).forEach { (label, v) ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(LbAccentSoft)
                                .clickable {
                                    val at = lbMillisAt(v.first, v.second.first, v.second.second)
                                    if (at <= System.currentTimeMillis()) error = "这个时间已经过去了，换一个吧" else onPick(at)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LbAccent)
                        }
                    }
                }

                // 日期
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, LbLine, RoundedCornerShape(12.dp))
                        .clickable { showDate = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(lbDateCn(date), fontSize = 13.5.sp, color = LbInk, modifier = Modifier.weight(1f))
                    Icon(LbIcons.calendarEvent, contentDescription = null, tint = LbInk3, modifier = Modifier.size(16.dp))
                }

                // 时间
                Text("时间", fontSize = 12.sp, color = LbInk3, modifier = Modifier.padding(top = 12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepField(
                        value = hour.toString().padStart(2, '0'),
                        unit = "时",
                        onMinus = { hour = (hour + 23) % 24 },
                        onPlus = { hour = (hour + 1) % 24 },
                        modifier = Modifier.weight(1f),
                    )
                    Text(":", fontSize = 16.sp, color = LbInk3, modifier = Modifier.padding(horizontal = 6.dp))
                    StepField(
                        value = minute.toString().padStart(2, '0'),
                        unit = "分",
                        onMinus = { minute = (minute + 55) % 60 },
                        onPlus = { minute = (minute + 5) % 60 },
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    "将在 ${lbFullDateTime(picked)} 提醒你",
                    fontSize = 12.sp,
                    color = LbInk2,
                    modifier = Modifier.padding(top = 12.dp),
                )
                error?.let {
                    Text(it, fontSize = 12.sp, color = LbRust, modifier = Modifier.padding(top = 6.dp))
                }

                if (hasInit) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LbGhostButton("清除提醒", onClear, Modifier.weight(1f))
                        LbGhostButton("取消", onDismiss, Modifier.weight(1f))
                    }
                    LbPrimaryButton(
                        "确定",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        onClick = {
                            if (picked <= System.currentTimeMillis()) error = "这个时间已经过去了，换一个吧" else onPick(picked)
                        },
                    )
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LbGhostButton("取消", onDismiss, Modifier.weight(1f))
                        LbPrimaryButton(
                            "确定",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (picked <= System.currentTimeMillis()) error = "这个时间已经过去了，换一个吧" else onPick(picked)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showDate) {
        LbDatePickerDialog(
            initial = date.toString(),
            clearable = false,
            onPick = { iso ->
                try {
                    date = LocalDate.parse(iso)
                } catch (e: Exception) {
                }
                showDate = false
                error = null
            },
            onClear = { showDate = false },
            onDismiss = { showDate = false },
        )
    }
}

@Composable
private fun StepField(
    value: String,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        StepButton("−", onMinus)
        Column(
            Modifier.padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
            Text(unit, fontSize = 11.sp, color = LbInk3)
        }
        StepButton("+", onPlus)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(LbSurface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 17.sp, color = LbInk2)
    }
}

/* ── 分类管理:新增 / 删除(删分类不删备忘) ── */

@Composable
private fun MemoCategoryDialog(
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
                Text("分类管理", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                Text(
                    "删除分类不会删掉备忘，那些备忘会移到「未分类」。",
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
                                "${counts[c] ?: 0} 条",
                                fontSize = 11.sp,
                                color = LbInk3,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            if (c != ButlerStore.MEMO_UNCATEGORIZED) {
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
                        placeholder = { Text("新的分类名", fontSize = 12.sp, color = LbInk3) },
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
                        modifier = Modifier.width(84.dp),
                    )
                }

                error?.let {
                    Text(it, fontSize = 12.sp, color = LbRust, modifier = Modifier.padding(top = 8.dp))
                }

                LbGhostButton(
                    "完成",
                    onDismiss,
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                )
            }
        }
    }
}
