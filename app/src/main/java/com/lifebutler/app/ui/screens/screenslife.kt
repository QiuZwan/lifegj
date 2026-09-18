package com.lifebutler.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lifebutler.app.R
import com.lifebutler.app.data.ButlerMember
import com.lifebutler.app.data.ButlerStore
import com.lifebutler.app.data.Notifier
import com.lifebutler.app.data.ReminderScheduler
import com.lifebutler.app.data.Weather
import com.lifebutler.app.ui.components.ChipTone
import com.lifebutler.app.ui.components.HeroCard
import com.lifebutler.app.ui.components.IconBadge
import com.lifebutler.app.ui.components.LbCard
import com.lifebutler.app.ui.components.LbChip
import com.lifebutler.app.ui.components.LbConfirmDialog
import com.lifebutler.app.ui.components.LbField
import com.lifebutler.app.ui.components.LbGhostButton
import com.lifebutler.app.ui.components.LbInputDialog
import com.lifebutler.app.ui.components.LbPasteDialog
import com.lifebutler.app.ui.components.LbPlusButton
import com.lifebutler.app.ui.components.LbPrimaryButton
import com.lifebutler.app.ui.components.SectionHeader
import com.lifebutler.app.ui.components.lbLongPress
import com.lifebutler.app.ui.components.lbPressable
import com.lifebutler.app.ui.icons.LbIcons
import com.lifebutler.app.ui.theme.LbAccent
import com.lifebutler.app.ui.theme.LbAccentSoft
import com.lifebutler.app.ui.theme.LbAmber
import com.lifebutler.app.ui.theme.LbAmberSoft
import com.lifebutler.app.ui.theme.LbBg
import com.lifebutler.app.ui.theme.LbDark
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/* ── 05 对话管家(消息持久化,「记一下」写入今日待办) ── */

@Composable
fun ChatScreen() {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    val listState = rememberScrollState()
    var draft by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val path = store.saveChatPhoto(uri)
            if (path != null) {
                store.addChat(true, draft.trim(), path)
                draft = ""
            } else {
                Toast.makeText(ctx, "图片保存失败，换一张试试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(top = 10.dp)) {
            Text("对话管家", style = MaterialTheme.typography.labelSmall)
            Text("说出来，就有人接住", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(listState)
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            store.chat.forEach { m ->
                if (m.photoPath.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start,
                    ) {
                        Column(
                            Modifier
                                .clip(
                                    if (m.fromUser) RoundedCornerShape(16.dp, 16.dp, 6.dp, 16.dp)
                                    else RoundedCornerShape(16.dp, 16.dp, 16.dp, 6.dp),
                                )
                                .background(LbDark)
                                .padding(5.dp),
                        ) {
                            LocalImage(
                                path = m.photoPath,
                                modifier = Modifier
                                    .width(196.dp)
                                    .height(132.dp)
                                    .clip(RoundedCornerShape(11.dp)),
                            )
                            if (m.text.isNotEmpty()) {
                                Text(
                                    m.text,
                                    fontSize = 11.sp,
                                    color = Color(0xCCF2F1EC),
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 3.dp),
                                )
                            }
                        }
                    }
                } else {
                    ChatBubble(m.fromUser, m.text)
                }
            }
            if (typing) {
                Row(Modifier.fillMaxWidth()) {
                    TypingBubble()
                }
            }
        }

        LaunchedEffect(store.chat.size, typing) {
            listState.animateScrollTo(listState.maxValue)
        }

        LbCard(contentPadding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    Image(
                        painterResource(R.drawable.focus_desk),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(
                    Modifier
                        .padding(start = 11.dp)
                        .weight(1f),
                ) {
                    Text("一句话收件箱", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                    Text("说「记一下：……」直接写进今日待办", fontSize = 11.5.sp, color = LbInk3, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        Surface(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = LbSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, LbLine),
        ) {
            Row(
                Modifier.padding(start = 13.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    LbIcons.camera,
                    contentDescription = "发一张图",
                    tint = LbInk2,
                    modifier = Modifier
                        .size(23.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .padding(3.dp),
                )
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.5.sp, color = LbInk),
                    cursorBrush = SolidColor(LbAccent),
                    modifier = Modifier
                        .padding(start = 9.dp)
                        .weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) {
                                Text("说点什么，或点左边发一张图…", fontSize = 12.sp, color = LbInk3)
                            }
                            inner()
                        }
                    },
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (draft.isBlank()) LbSurface2 else LbAccent)
                        .clickable(enabled = draft.isNotBlank() && !typing) {
                            val t2 = draft.trim()
                            if (t2.isNotEmpty() && !typing) {
                                store.addChat(true, t2)
                                draft = ""
                                typing = true
                                scope.launch {
                                    delay(800)
                                    val r = store.reply(t2)
                                    typing = false
                                    store.addChat(false, r)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        LbIcons.send,
                        contentDescription = "发送",
                        tint = if (draft.isBlank()) LbInk3 else LbOnAccent,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(isUser: Boolean, text: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .clip(
                    if (isUser) RoundedCornerShape(16.dp, 16.dp, 6.dp, 16.dp)
                    else RoundedCornerShape(16.dp, 16.dp, 16.dp, 6.dp),
                )
                .background(if (isUser) LbDark else LbSurface)
                .then(
                    if (isUser) Modifier
                    else Modifier.border(1.dp, LbLine, RoundedCornerShape(16.dp, 16.dp, 16.dp, 6.dp)),
                )
                .padding(horizontal = 13.dp, vertical = 11.dp),
        ) {
            Text(
                text,
                fontSize = 12.5.sp,
                color = if (isUser) LbOnDark else LbInk,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 6.dp))
            .background(LbSurface)
            .border(1.dp, LbLine, RoundedCornerShape(16.dp, 16.dp, 16.dp, 6.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(0.35f, 0.6f, 0.85f).forEach { a ->
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(LbInk3.copy(alpha = a)),
            )
        }
    }
}

/* ── 06 家庭守护(成员与日期可记录) ── */

private fun memberPhotoRes(photo: String): Int = when (photo) {
    "mom" -> R.drawable.avatar_mom
    "dad" -> R.drawable.avatar_dad
    "cat" -> R.drawable.avatar_cat
    else -> 0
}

@Composable
fun FamilyScreen() {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    var showAddMember by remember { mutableStateOf(false) }
    var showAddDate by remember { mutableStateOf(false) }
    var memberMenu by remember { mutableStateOf<ButlerMember?>(null) }
    var deleteKeyId by remember { mutableStateOf<String?>(null) }
    var memberEdit by remember { mutableStateOf<ButlerMember?>(null) }
    var photoTargetId by remember { mutableStateOf<String?>(null) }
    val memberPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = photoTargetId
        photoTargetId = null
        if (id != null && uri != null) {
            val ok = store.setMemberPhoto(id, uri)
            Toast.makeText(ctx, if (ok) "照片已更新" else "照片保存失败", Toast.LENGTH_SHORT).show()
        }
    }
    var showEmergency by remember { mutableStateOf(false) }

    val nearestMember = store.members
        .mapNotNull { m -> store.daysUntil(m.date)?.let { m to it } }
        .minByOrNull { it.second }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(top = 10.dp)) {
            Text("家庭守护", style = MaterialTheme.typography.labelSmall)
            Text("家人都好，你就安心", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
        }

        Box(Modifier.padding(top = 12.dp)) {
            HeroCard(
                painter = painterResource(R.drawable.family_home),
                height = 170.dp,
                kicker = "给家人的下一件事",
                title = if (nearestMember != null)
                    "${nearestMember.first.name}的${nearestMember.first.label} · ${store.fmtCn(nearestMember.first.date)}"
                else "把家人的重要日子记下来",
                sub = if (nearestMember != null)
                    "${store.daysText(nearestMember.first.date)} · 提前一天会再提醒你"
                else "点下面家人区右上角，添加第一位家人",
            )
            if (nearestMember != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    LbChip("已记录", ChipTone.OnDark)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("家人 · ${store.members.size} 位", fontSize = 11.5.sp, color = LbInk3, modifier = Modifier.weight(1f))
            LbPlusButton(onClick = { showAddMember = true }, contentDescription = "添加家人")
        }
        if (store.members.isEmpty()) {
            Text(
                "还没有家人记录，点右上角 + 添加第一人。",
                fontSize = 12.sp,
                color = LbInk3,
                modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            Column(
                Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                store.members.chunked(3).forEach { rowMembers ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        rowMembers.forEach { m ->
                            MemberCard(
                                name = m.name,
                                sub = if (m.date.isEmpty()) m.label else "${m.label} · ${store.daysText(m.date)}",
                                photoRes = memberPhotoRes(m.photo),
                                filePath = if (store.isLocalPhoto(m.photo)) m.photo else "",
                                modifier = Modifier.weight(1f),
                                onClick = { memberMenu = m },
                            )
                        }
                        repeat((3 - rowMembers.size).coerceAtLeast(0)) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        val localPhotos = store.members.filter { store.isLocalPhoto(it.photo) }
        SectionHeader("家人照片") {
            Text(
                if (localPhotos.isEmpty()) "还没有" else "${localPhotos.size} 张",
                fontSize = 12.sp,
                color = LbInk3,
            )
        }
        if (localPhotos.isEmpty()) {
            LbCard(contentPadding = 12.dp) {
                Text(
                    "还没有家人照片。点任意一位家人 →「换一张照片」，图片会压缩后只存在这台手机上。",
                    fontSize = 12.sp,
                    color = LbInk3,
                    lineHeight = 18.sp,
                )
            }
        } else {
            val shown = localPhotos.take(2)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                shown.forEach { m ->
                    StripPhoto(m.photo, "${m.name} · ${m.label}", Modifier.weight(1f))
                }
                repeat((2 - shown.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }

        SectionHeader("关键日期") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("提前一周提醒", fontSize = 12.sp, color = LbInk3)
                Spacer(Modifier.width(9.dp))
                LbPlusButton(onClick = { showAddDate = true }, contentDescription = "添加日期")
            }
        }
        LbCard(contentPadding = 8.dp) {
            if (store.keyDates.isEmpty()) {
                Text("还没有要记的日子。生日、纪念日、复诊都可以放这里。", fontSize = 12.sp, color = LbInk3, modifier = Modifier.padding(12.dp))
            } else {
                store.keyDates
                    .sortedBy { store.daysUntil(it.date) ?: Long.MAX_VALUE }
                    .forEach { k ->
                        val d = store.daysUntil(k.date)
                        LbListRowMini(
                            icon = { IconBadge(LbIcons.cake, if (d != null && d <= 14) LbAmberSoft else LbAccentSoft, if (d != null && d <= 14) LbAmber else LbAccent, size = 32.dp) },
                            title = k.title,
                            sub = if (k.note.isNotEmpty()) k.note else store.fmtCn(k.date),
                            chip = store.daysText(k.date),
                            chipTone = if (d != null && d <= 14) ChipTone.Amber else ChipTone.Soft,
                            onLongClick = { deleteKeyId = k.id },
                        )
                    }
            }
        }

        Surface(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable { showEmergency = true },
            color = LbDark,
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color(0x1FF6F5F0)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LbIcons.shieldLock, contentDescription = null, tint = LbOnDark, modifier = Modifier.size(17.dp))
                }
                Column(
                    Modifier
                        .padding(start = 11.dp)
                        .weight(1f),
                ) {
                    Text("家庭应急卡", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbOnDark)
                    Text(
                        emergencySummary(store),
                        fontSize = 11.sp,
                        color = Color(0x9EF6F5F0),
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                Icon(LbIcons.chevronRight, contentDescription = null, tint = Color(0x9EF6F5F0), modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showAddMember) {
        LbInputDialog(
            title = "添加家人",
            fields = listOf(
                LbField("称呼", "如：外婆"),
                LbField("要记的事", "如：复诊 / 生日"),
                LbField("日期", "点这里选择日期（可留空）", isDate = true, dateClearable = true),
            ),
            onDismiss = { showAddMember = false },
            onConfirm = { v ->
                val name = v.getOrElse(0) { "" }
                val label = v.getOrElse(1) { "" }
                val date = v.getOrElse(2) { "" }
                when {
                    name.isEmpty() -> "写下称呼吧"
                    else -> {
                        store.addMember(name, label.ifEmpty { "提醒" }, date)
                        showAddMember = false
                        null
                    }
                }
            },
        )
    }

    if (showAddDate) {
        LbInputDialog(
            title = "添加关键日期",
            fields = listOf(
                LbField("名称", "如：爸妈结婚纪念日"),
                LbField("日期", "点这里选择日期", isDate = true),
                LbField("备注（可选）", "如：去年订了蛋糕"),
            ),
            onDismiss = { showAddDate = false },
            onConfirm = { v ->
                val title = v.getOrElse(0) { "" }
                val date = v.getOrElse(1) { "" }
                val note = v.getOrElse(2) { "" }
                when {
                    title.isEmpty() -> "写下名称吧"
                    date.isEmpty() -> "选个日期吧"
                    else -> {
                        store.addKeyDate(title, date, note)
                        showAddDate = false
                        null
                    }
                }
            },
        )
    }

    memberMenu?.let { m ->
        MemberMenuDialog(
            name = m.name,
            onPhoto = {
                photoTargetId = m.id
                memberMenu = null
                memberPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onEdit = {
                memberEdit = m
                memberMenu = null
            },
            onRemove = {
                store.removeMember(m.id)
                memberMenu = null
                Toast.makeText(ctx, "已从家人列表移除", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { memberMenu = null },
        )
    }

    memberEdit?.let { m ->
        LbInputDialog(
            title = "编辑家人",
            fields = listOf(
                LbField("称呼", "如：外婆"),
                LbField("要记的事", "如：复诊 / 生日"),
                LbField("日期", "点这里选择日期（可留空）", isDate = true, dateClearable = true),
            ),
            initial = listOf(m.name, m.label, m.date),
            onDismiss = { memberEdit = null },
            onConfirm = { v ->
                val name = v.getOrElse(0) { "" }
                val label = v.getOrElse(1) { "" }
                val date = v.getOrElse(2) { "" }
                when {
                    name.isEmpty() -> "写下称呼吧"
                    else -> {
                        store.updateMember(m.id, name, label.ifEmpty { "提醒" }, date)
                        memberEdit = null
                        null
                    }
                }
            },
        )
    }

    deleteKeyId?.let { id ->
        LbConfirmDialog(
            title = "删除这个日期？",
            text = "删除后不再提醒。",
            onDismiss = { deleteKeyId = null },
            onConfirm = {
                store.removeKeyDate(id)
                deleteKeyId = null
            },
        )
    }

    if (showEmergency) {
        LbInputDialog(
            title = "家庭应急卡",
            fields = listOf(
                LbField("血型", "如：O 型"),
                LbField("常用药 / 过敏", "如：降压药，青霉素过敏"),
                LbField("紧急联系人", "如：小满 138****"),
            ),
            initial = listOf(store.bloodType.value, store.meds.value, store.emergencyContact.value),
            onDismiss = { showEmergency = false },
            onConfirm = { v ->
                store.setEmergency(v.getOrElse(0) { "" }, v.getOrElse(1) { "" }, v.getOrElse(2) { "" })
                showEmergency = false
                null
            },
        )
    }
}

private fun emergencySummary(store: ButlerStore): String {
    val parts = listOfNotNull(
        store.bloodType.value.takeIf { it.isNotEmpty() }?.let { "血型 $it" },
        store.meds.value.takeIf { it.isNotEmpty() },
        store.emergencyContact.value.takeIf { it.isNotEmpty() }?.let { "联系人 $it" },
    )
    return if (parts.isEmpty()) "血型、用药、紧急联系人，点一下填写" else parts.joinToString(" · ")
}

@Composable
private fun LbListRowMini(
    icon: @Composable () -> Unit,
    title: String,
    sub: String,
    chip: String,
    chipTone: ChipTone,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .lbLongPress(onLongClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(
            Modifier
                .padding(start = 11.dp)
                .weight(1f),
        ) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
            Text(sub, fontSize = 11.5.sp, color = LbInk3, modifier = Modifier.padding(top = 1.dp))
        }
        LbChip(chip, chipTone)
    }
}

@Composable
private fun MemberCard(
    name: String,
    sub: String,
    photoRes: Int,
    filePath: String = "",
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.lbPressable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = LbSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, LbLine),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (filePath.isNotEmpty()) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp)),
                ) {
                    LocalPhoto(
                        path = filePath,
                        fallbackRes = if (photoRes != 0) photoRes else R.drawable.avatar_user,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else if (photoRes != 0) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp)),
                ) {
                    Image(
                        painterResource(photoRes),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            } else {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(LbSurface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name.take(1), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = LbInk2)
                }
            }
            Text(name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbInk, modifier = Modifier.padding(top = 7.dp))
            Text(sub, fontSize = 10.5.sp, color = LbInk3, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
private fun MemberMenuDialog(
    name: String,
    onPhoto: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = LbSurface) {
            Column(Modifier.padding(20.dp)) {
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                Text("想为这位家人做什么？", fontSize = 12.sp, color = LbInk3, modifier = Modifier.padding(top = 4.dp))
                MenuRow(LbIcons.camera, "换一张照片", onPhoto, Modifier.padding(top = 12.dp))
                MenuRow(LbIcons.pencil, "编辑称呼与日期", onEdit, Modifier.padding(top = 8.dp))
                MenuRow(LbIcons.trash, "从列表移除", onRemove, Modifier.padding(top = 8.dp), danger = true)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "取消",
                        fontSize = 12.5.sp,
                        color = LbInk3,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LbBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) LbRust else LbAccent, modifier = Modifier.size(16.dp))
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (danger) LbRust else LbInk,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun StripPhoto(path: String, cap: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(104.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        LocalImage(path, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color(0xB31E2A24),
                    ),
                ),
        )
        Text(
            cap,
            fontSize = 10.5.sp,
            color = Color(0xFFF6F5F0),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 8.dp),
        )
    }
}

/* ── 07 我的(统计来自真实数据) ── */

@Composable
fun MineScreen(onOpenVault: () -> Unit, onOpenFamily: () -> Unit, onOpenReport: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    var showRename by remember { mutableStateOf(false) }
    var showData by remember { mutableStateOf(false) }
    var showDemo by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val ok = store.saveAvatar(ctx, uri)
            Toast.makeText(ctx, if (ok) "头像已更新" else "照片保存失败", Toast.LENGTH_SHORT).show()
        }
    }
    var showReminder by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    var restoreInitial by remember { mutableStateOf("") }
    var restorePending by remember { mutableStateOf<String?>(null) }
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(ctx, if (granted) "通知权限已开启" else "未授权通知，提醒将无法显示", Toast.LENGTH_SHORT).show()
    }
    fun ensureNotifPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, "android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch("android.permission.POST_NOTIFICATIONS")
        }
    }

    var showWeather by remember { mutableStateOf(false) }
    var weatherOn by remember { mutableStateOf(Weather.enabled(ctx)) }
    var weatherCache by remember { mutableStateOf(Weather.cached(ctx)) }
    var weatherBusy by remember { mutableStateOf(false) }
    val weatherScope = rememberCoroutineScope()

    fun refreshWeather(manual: Boolean) {
        if (weatherBusy) return
        if (!Weather.hasLocation(ctx)) return
        weatherBusy = true
        weatherScope.launch {
            val info = withContext(Dispatchers.IO) { Weather.refresh(ctx) }
            weatherBusy = false
            if (info != null) {
                weatherCache = info
                if (manual) Toast.makeText(ctx, "已更新：${Weather.describe(info.code)} ${info.temp}°（今日 ${info.low}°~${info.high}°）", Toast.LENGTH_SHORT).show()
            } else if (manual) {
                Toast.makeText(ctx, "天气获取失败，检查网络后再试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val weatherPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            Weather.setEnabled(ctx, true)
            Weather.setAsked(ctx)
            weatherOn = true
            refreshWeather(manual = false)
            Toast.makeText(ctx, "桌面天气已开启", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(ctx, "未获取位置权限，天气未开启", Toast.LENGTH_SHORT).show()
        }
    }

    val managedCount = store.tasks.count { !it.done } + store.obligations.count { !it.done } + store.subs.count { !it.closing }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(top = 10.dp)) {
            Text("我的", style = MaterialTheme.typography.labelSmall)
            Text("你忙你的，剩下的我来", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
        }

        Column(
            Modifier
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(LbSurface)
                .border(1.dp, LbLine, RoundedCornerShape(20.dp)),
        ) {
            Image(
                painterResource(R.drawable.review_journal),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp),
                contentScale = ContentScale.Crop,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    Modifier
                        .offset(y = (-26).dp)
                        .size(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(3.dp, LbSurface, RoundedCornerShape(18.dp))
                        .clickable {
                            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                ) {
                    LocalPhoto(
                        path = store.avatarPath.value,
                        fallbackRes = R.drawable.avatar_user,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showRename = true }
                        .padding(vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(store.profileName.value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                        Spacer(Modifier.width(5.dp))
                        Icon(LbIcons.pencil, contentDescription = "改称呼", tint = LbInk3, modifier = Modifier.size(13.dp))
                    }
                    Text("生活管家 · 第 ${store.dayCount()} 天", fontSize = 11.5.sp, color = LbInk3, modifier = Modifier.padding(top = 1.dp))
                }
                Box(Modifier.padding(bottom = 5.dp)) {
                    val totalOpen = store.tasks.size + store.obligations.size
                    val allDone = totalOpen > 0 && store.tasks.all { it.done } && store.obligations.all { it.done }
                    LbChip(
                        when {
                            totalOpen == 0 -> "还没开始"
                            allDone -> "都处理完了"
                            else -> "进行中"
                        },
                        if (allDone) ChipTone.Green else ChipTone.Soft,
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            StatTile(
                if (store.monthlySaved > 0) "¥${store.fmtMoney(store.monthlySaved)}" else "—",
                "每月省下",
                Modifier.weight(1f),
            )
            StatTile("${store.archiveFileCount()} 份", "已归档", Modifier.weight(1f))
            StatTile("$managedCount 项", "托管理中", Modifier.weight(1f))
        }

        Surface(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenVault),
            shape = RoundedCornerShape(20.dp),
            color = LbSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, LbLine),
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp)),
                ) {
                    Image(
                        painterResource(R.drawable.vault_shelf),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(
                    Modifier
                        .padding(start = 11.dp)
                        .weight(1f),
                ) {
                    Text("家庭档案库", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                    Text("${store.archive.size} 组档案 · 随时导出", fontSize = 11.sp, color = LbInk3, modifier = Modifier.padding(top = 2.dp))
                }
                Icon(LbIcons.chevronRight, contentDescription = null, tint = LbInk3, modifier = Modifier.size(15.dp))
            }
        }

        Surface(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = LbSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, LbLine),
        ) {
            Column {
                val rows = listOf(
                    Triple(LbIcons.bell, "提醒与免打扰", "每日简报 · 扣费/到期提醒"),
                    Triple(LbIcons.users, "家庭守护设置", "连接到「家庭」页"),
                    Triple(LbIcons.moon, "深色模式", if (store.darkMode.value) "已开启" else "已关闭"),
                    Triple(LbIcons.cloud, "桌面天气", if (weatherOn) "已开启" else "未开启（不联网）"),
                    Triple(LbIcons.fileText, "本月月报", "花销 · 订阅 · 省下"),
                    Triple(LbIcons.shieldLock, "数据与隐私", "全部保存在本机"),
                    Triple(LbIcons.download, "导出家庭档案", "一键整理成文本"),
                    Triple(LbIcons.deviceFloppy, "备份与恢复", "换机不丢数据"),
                    Triple(LbIcons.eye, "载入演示数据", "用示例内容预览"),
                    Triple(LbIcons.trash, "清空全部数据", "从零开始记录"),
                )
                rows.forEachIndexed { i, r ->
                    if (i > 0) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(LbLine))
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (r.second) {
                                    "提醒与免打扰" -> showReminder = true
                                    "家庭守护设置" -> onOpenFamily()
                                    "深色模式" -> {
                                        store.setDarkMode(!store.darkMode.value)
                                        (ctx as? ComponentActivity)?.let { act ->
                                            act.enableEdgeToEdge(
                                                statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { _ -> store.darkMode.value },
                                                navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { _ -> store.darkMode.value },
                                            )
                                        }
                                    }
                                    "桌面天气" -> showWeather = true
                                    "本月月报" -> onOpenReport()
                                    "数据与隐私" -> showData = true
                                    "载入演示数据" -> showDemo = true
                                    "清空全部数据" -> showClear = true
                                    "备份与恢复" -> showBackup = true
                                    "导出家庭档案" -> {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, buildExport(store))
                                        }
                                        ctx.startActivity(Intent.createChooser(sendIntent, "导出家庭档案"))
                                    }
                                    else -> { }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(r.first, LbAccentSoft, LbAccent, size = 30.dp)
                        Text(
                            r.second,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = LbInk,
                            modifier = Modifier
                                .padding(start = 11.dp)
                                .weight(1f),
                        )
                        Text(r.third, fontSize = 11.sp, color = LbInk3, modifier = Modifier.padding(end = 8.dp))
                        Icon(LbIcons.chevronRight, contentDescription = null, tint = LbInk3, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Surface(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onOpenReport),
            shape = RoundedCornerShape(20.dp),
            color = LbDark,
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color(0x1FF6F5F0)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LbIcons.fileText, contentDescription = null, tint = LbOnDark, modifier = Modifier.size(17.dp))
                }
                Column(
                    Modifier
                        .padding(start = 11.dp)
                        .weight(1f),
                ) {
                    val now = LocalDate.now()
                    val monthSpend = store.expensesInMonth(now.year, now.monthValue).sumOf { it.amount }
                    Text("${now.monthValue} 月月报", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbOnDark)
                    Text(
                        "花销 ¥${store.fmtMoney(monthSpend)} · 每月订阅 ¥${store.fmtMoney(store.subs.filter { !it.closing }.sumOf { it.amount })} · 本月已省 ¥${store.fmtMoney(store.savedThisMonth)}",
                        fontSize = 11.sp,
                        color = Color(0x9EF6F5F0),
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                Icon(LbIcons.arrowUpRight, contentDescription = null, tint = Color(0x9EF6F5F0), modifier = Modifier.size(15.dp))
            }
        }

        Text(
            "生活管家 · v1.9.0",
            fontSize = 10.5.sp,
            color = LbInk3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 6.dp),
        )
    }

    if (showRename) {
        LbInputDialog(
            title = "改个称呼",
            fields = listOf(LbField("称呼", "如：小满")),
            initial = listOf(store.profileName.value),
            onDismiss = { showRename = false },
            onConfirm = { v ->
                val n = v.getOrElse(0) { "" }
                if (n.isEmpty()) "写个称呼吧" else {
                    store.renameProfile(n)
                    showRename = false
                    null
                }
            },
        )
    }

    if (showData) {
        val totalRecords = store.tasks.size + store.subs.size + store.obligations.size +
            store.members.size + store.keyDates.size + store.archive.size +
            store.chat.size + store.expenses.size + store.charges.size + store.archiveFileCount()
        LbConfirmDialog(
            title = "数据与隐私",
            text = "所有数据只保存在本机，不联网上传，卸载即清除（天气是可选下载项，可在「桌面天气」里关闭）。\n\n当前共 $totalRecords 条记录，其中已归档文件 ${store.archiveFileCount()} 份。",
            confirmText = "知道了",
            onDismiss = { showData = false },
            onConfirm = { showData = false },
        )
    }

    if (showDemo) {
        LbConfirmDialog(
            title = "载入演示数据？",
            text = "会先清空当前记录，再写入一套示例内容（订阅、义务、家人、记账等），方便你先看看界面长什么样。\n\n演示的订阅都带「演示」角标，一眼能认出来；之后可以随时清空重来。",
            confirmText = "载入演示",
            onDismiss = { showDemo = false },
            onConfirm = {
                store.loadDemo()
                showDemo = false
                Toast.makeText(ctx, "已载入演示数据", Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (showClear) {
        LbConfirmDialog(
            title = "清空全部数据？",
            text = "将删除全部记录（待办 / 订阅 / 对话 / 家人 / 日期），从零开始；此操作不可恢复。",
            confirmText = "清空",
            onDismiss = { showClear = false },
            onConfirm = {
                store.clearAll()
                showClear = false
                Toast.makeText(ctx, "已清空，从今天开始记录", Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (showReminder) {
        val notifGranted = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, "android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED
        ReminderDialog(
            enabled = store.reminderEnabled.value,
            hour = store.reminderHour.value,
            notifGranted = notifGranted,
            onSave = { en, h ->
                store.setReminder(en, h)
                if (en) {
                    ensureNotifPermission()
                    ReminderScheduler.ensureScheduled(ctx)
                    val passed = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) >= h
                    Toast.makeText(
                        ctx,
                        if (passed) "已开启，明天 %02d:00 起发简报".format(h) else "已开启，今天 %02d:00 发简报".format(h),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    ReminderScheduler.cancel(ctx)
                    Toast.makeText(ctx, "已关闭提醒", Toast.LENGTH_SHORT).show()
                }
                showReminder = false
            },
            onTest = {
                ensureNotifPermission()
                Notifier.postTest(ctx)
                Toast.makeText(ctx, "测试通知已发送（若未显示请检查通知权限）", Toast.LENGTH_SHORT).show()
            },
            onFixPermission = { ensureNotifPermission() },
            onDismiss = { showReminder = false },
        )
    }

    if (showWeather) {
        WeatherDialog(
            enabled = weatherOn,
            info = weatherCache,
            hasPermission = Weather.hasLocation(ctx),
            onSave = { en ->
                Weather.setEnabled(ctx, en)
                Weather.setAsked(ctx)
                weatherOn = en
                showWeather = false
                if (en && !Weather.hasLocation(ctx)) {
                    weatherPermLauncher.launch("android.permission.ACCESS_COARSE_LOCATION")
                } else if (en) {
                    refreshWeather(manual = true)
                } else {
                    Toast.makeText(ctx, "已关闭：天气不再联网", Toast.LENGTH_SHORT).show()
                }
            },
            onRefresh = {
                if (Weather.hasLocation(ctx)) refreshWeather(manual = true)
                else weatherPermLauncher.launch("android.permission.ACCESS_COARSE_LOCATION")
            },
            onDismiss = { showWeather = false },
        )
    }

    if (showBackup) {
        BackupDialog(
            onCopy = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("生活管家备份", store.exportState()))
                Toast.makeText(ctx, "备份已复制，保存到安全的地方即可", Toast.LENGTH_SHORT).show()
            },
            onRestore = {
                restoreInitial = try {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val t = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    if (t.contains("\"tasks\"") || t.length > 80) t else ""
                } catch (e: Exception) {
                    ""
                }
                showBackup = false
                showRestore = true
            },
            onDismiss = { showBackup = false },
        )
    }

    if (showRestore) {
        LbPasteDialog(
            title = "恢复备份",
            hint = "把之前复制的备份内容粘贴到下面（含头像与家人照片）。",
            initial = restoreInitial,
            onDismiss = { showRestore = false },
            onConfirm = { raw ->
                when {
                    raw.isEmpty() -> "先粘贴备份内容"
                    raw.length > 2_000_000 -> "内容过大，不像是备份文本（备份一般几十到几百 KB）"
                    !store.isValidBackup(raw) -> "内容不是有效的备份，请检查后再试"
                    else -> {
                        restorePending = raw
                        restoreInitial = ""
                        showRestore = false
                        null
                    }
                }
            },
        )
    }

    restorePending?.let { raw ->
        LbConfirmDialog(
            title = "恢复这份备份？",
            text = "将覆盖当前全部数据（待办 / 订阅 / 对话 / 家人 / 照片），无法撤销。",
            confirmText = "覆盖恢复",
            onDismiss = { restorePending = null },
            onConfirm = {
                val ok = store.importState(raw)
                restorePending = null
                if (ok) {
                    ReminderScheduler.ensureScheduled(ctx)
                    Toast.makeText(ctx, "备份已恢复", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "恢复失败，备份内容可能已损坏", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun LocalPhoto(path: String, fallbackRes: Int, modifier: Modifier = Modifier) {
    val bmp = remember(path) {
        if (path.isEmpty()) null else android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap()
    }
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Image(painterResource(fallbackRes), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    }
}

/** 只渲染本机真实存在的图片;取不到就什么都不画,不拿占位图顶替 */
@Composable
private fun LocalImage(path: String, modifier: Modifier = Modifier) {
    val bmp = remember(path) {
        if (path.isEmpty()) null else android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap()
    }
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(
            modifier.background(LbSurface2),
            contentAlignment = Alignment.Center,
        ) {
            Text("图片已失效", fontSize = 11.sp, color = LbInk3)
        }
    }
}

@Composable
private fun WeatherDialog(
    enabled: Boolean,
    info: Weather.Info?,
    hasPermission: Boolean,
    onSave: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    var en by remember { mutableStateOf(enabled) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = LbSurface) {
            Column(Modifier.padding(20.dp)) {
                Text("桌面天气", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                Text(
                    "开启后只做一件事：联网下载你所在城市的天气（不上传任何数据）。需要「大致位置」权限；关闭后完全不联网。",
                    fontSize = 12.sp,
                    color = LbInk3,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("首页天气", fontSize = 13.sp, color = LbInk, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (en) LbAccentSoft else LbSurface2)
                            .clickable { en = !en }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            if (en) "已开启" else "已关闭",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (en) LbAccent else LbInk3,
                        )
                    }
                }
                Text(
                    when {
                        !hasPermission && en -> "位置权限：未授权（开启天气需要）"
                        info != null -> "当前：" + (if (info.city.isNotEmpty()) info.city + " · " else "") + Weather.describe(info.code) + " " + info.temp + "° · 更新于 " + lbFmtTime(info.at)
                        else -> "当前：尚未获取天气"
                    },
                    fontSize = 11.5.sp,
                    color = LbInk3,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    LbGhostButton("立即刷新", onRefresh, Modifier.weight(1f))
                    LbPrimaryButton("保存", { onSave(en) }, Modifier.weight(1f))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "取消",
                        fontSize = 12.5.sp,
                        color = LbInk3,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

private fun lbFmtTime(ts: Long): String {
    return try {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
    } catch (e: Exception) {
        ""
    }
}

@Composable
private fun ReminderDialog(
    enabled: Boolean,
    hour: Int,
    notifGranted: Boolean,
    onSave: (Boolean, Int) -> Unit,
    onTest: () -> Unit,
    onFixPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    var en by remember { mutableStateOf(enabled) }
    var h by remember { mutableStateOf(hour) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = LbSurface) {
            Column(Modifier.padding(20.dp)) {
                Text("每日简报与提醒", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                Text(
                    "每天一条简报：临近的扣费、到期的事务、家人的重要日期；内容只在本机生成。",
                    fontSize = 12.sp,
                    color = LbInk3,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("开启提醒", fontSize = 13.sp, color = LbInk, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (en) LbAccentSoft else LbSurface2)
                            .clickable { en = !en }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            if (en) "已开启" else "已关闭",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (en) LbAccent else LbInk3,
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("发送时间", fontSize = 13.sp, color = LbInk, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(8, 9, 12).forEach { hh ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (h == hh) LbAccent else LbSurface2)
                                    .clickable { h = hh }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    "%02d:00".format(hh),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (h == hh) LbOnAccent else LbInk2,
                                )
                            }
                        }
                    }
                }
                val passedToday = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) >= h
                Text(
                    if (!en) "提醒已关闭" else if (passedToday) "下次发送：明天 %02d:00".format(h) else "下次发送：今天 %02d:00".format(h),
                    fontSize = 11.5.sp,
                    color = LbInk3,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (notifGranted) {
                        Text("通知权限：已开启", fontSize = 11.5.sp, color = LbAccent)
                    } else {
                        Text(
                            "通知权限未开启 · 点我开启",
                            fontSize = 11.5.sp,
                            color = LbAmber,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onFixPermission)
                                .padding(vertical = 2.dp),
                        )
                    }
                }
                Text(
                    "提示：如果手机系统限制了后台运行，简报可能延迟；可在系统设置允许「生活管家」自启动与后台运行。",
                    fontSize = 11.sp,
                    color = LbInk3,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    LbGhostButton("发送测试", onTest, Modifier.weight(1f))
                    LbPrimaryButton("保存", { onSave(en, h) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BackupDialog(onCopy: () -> Unit, onRestore: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = LbSurface) {
            Column(Modifier.padding(20.dp)) {
                Text("备份与恢复", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                Text(
                    "备份会复制一段本机数据文本（含头像与家人照片），保存到聊天记录或文件即可；换机后粘贴即可恢复（覆盖当前数据）。",
                    fontSize = 12.sp,
                    color = LbInk3,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    LbGhostButton("复制备份", onCopy, Modifier.weight(1f))
                    LbPrimaryButton("粘贴恢复", onRestore, Modifier.weight(1f))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "取消",
                        fontSize = 12.5.sp,
                        color = LbInk3,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

private fun buildExport(store: ButlerStore): String {
    val sb = StringBuilder()
    sb.append("生活管家 · 家庭档案导出\n")
    sb.append("导出时间:${LocalDate.now()}\n\n")
    sb.append("【家人】\n")
    store.members.forEach { sb.append("- ${it.name}:${it.label} ${it.date}（${store.daysText(it.date)}）\n") }
    sb.append("\n【关键日期】\n")
    store.keyDates.forEach { sb.append("- ${it.title}:${it.date}（${store.daysText(it.date)}）\n") }
    sb.append("\n【订阅】\n")
    store.subs.forEach { sb.append("- ${it.name}:¥${store.fmtMoney(it.amount)}/月${if (it.closing) "（关闭处理中）" else ""}\n") }
    sb.append("\n【义务】\n")
    store.obligations.forEach { sb.append("- ${it.title}:${store.daysText(it.date)}${if (it.done) "（已完成）" else ""}\n") }
    sb.append("\n【档案】\n")
    store.archive.forEach { sb.append("- ${it.title}:${it.files.size} 个文件 · ${it.note}\n") }
    return sb.toString()
}

@Composable
private fun StatTile(v: String, k: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = LbSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, LbLine),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(v, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
            Text(k, fontSize = 10.sp, color = LbInk3, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

/* ── 08 档案库(可检索、可存真实文件) ── */

/** 取系统给的原始文件名;拿不到就退回 Uri 末段 */
private fun displayNameOf(ctx: android.content.Context, uri: android.net.Uri): String {
    try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) {
                val n = c.getString(i)
                if (!n.isNullOrBlank()) return n
            }
        }
    } catch (e: Exception) {
    }
    return uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "file" }
}

private fun isImageName(name: String): Boolean {
    val n = name.lowercase()
    return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
        n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp")
}

private fun mimeOf(name: String): String {
    val n = name.lowercase()
    return when {
        n.endsWith(".jpg") || n.endsWith(".jpeg") -> "image/jpeg"
        n.endsWith(".png") -> "image/png"
        n.endsWith(".webp") -> "image/webp"
        n.endsWith(".gif") -> "image/gif"
        n.endsWith(".pdf") -> "application/pdf"
        n.endsWith(".txt") -> "text/plain"
        n.endsWith(".doc") || n.endsWith(".docx") -> "application/msword"
        n.endsWith(".xls") || n.endsWith(".xlsx") -> "application/vnd.ms-excel"
        n.endsWith(".ppt") || n.endsWith(".pptx") -> "application/vnd.ms-powerpoint"
        n.endsWith(".mp4") -> "video/mp4"
        n.endsWith(".mp3") || n.endsWith(".m4a") -> "audio/*"
        else -> "*/*"
    }
}

/** 把 App 私有目录里的档案文件以只读方式交给系统查看器打开 */
private fun openLocalFile(ctx: android.content.Context, name: String) {
    try {
        val f = ButlerStore.get(ctx).fileOf(name)
        if (!f.exists()) {
            Toast.makeText(ctx, "文件已不在本机", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeOf(name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "用其他应用打开"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "没有能打开它的应用", Toast.LENGTH_SHORT).show()
    }
}

/** 点一下执行 onTap,长按执行 onLong(单个 combinedClickable,避免手势互抢) */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.lbTapOrLong(onTap: () -> Unit, onLong: () -> Unit): Modifier =
    this.combinedClickable(onClick = onTap, onLongClick = onLong)

/** 档案文件在磁盘上的名字形如 arch_<组id>_<时间戳>_<原名>,这里还原出可读的文件名 */
private fun niceFileName(archiveId: String, stored: String): String {
    val prefix = "arch_${archiveId}_"
    val rest = if (stored.startsWith(prefix)) stored.substring(prefix.length) else stored
    return rest.substringAfter('_', rest)
}

@Composable
fun VaultScreen(onOpenStates: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    var deleteFile by remember { mutableStateOf<Pair<String, String>?>(null) }
    var uploadTarget by remember { mutableStateOf("") }

    fun importUris(uris: List<android.net.Uri>) {
        val id = uploadTarget
        if (id.isEmpty() || uris.isEmpty()) return
        val ok = ArrayList<String>()
        var bad = 0
        uris.forEach { u ->
            val n = store.importArchiveFile(u, id, displayNameOf(ctx, u))
            if (n != null) ok.add(n) else bad++
        }
        if (ok.isNotEmpty()) store.addArchiveFiles(id, ok)
        Toast.makeText(
            ctx,
            when {
                ok.isEmpty() -> "没能存入：单份可能超过 4 MB，或文件读不到"
                bad > 0 -> "已存入 ${ok.size} 个，另有 $bad 个读不到或过大"
                else -> "已存入 ${ok.size} 个文件"
            },
            Toast.LENGTH_SHORT,
        ).show()
    }

    // 照片走系统相册选择器;文件走文档选择器(可多选)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        importUris(uris)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        importUris(uris)
    }

    val filtered = store.archive.filter {
        query.isEmpty() || it.title.contains(query, true) || it.note.contains(query, true)
    }
    val totalDocs = store.archive.sumOf { it.files.size }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(top = 10.dp)) {
            Text("档案库", style = MaterialTheme.typography.labelSmall)
            Text("重要的纸，都在这里", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Surface(
                Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = LbSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, LbLine),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(LbIcons.search, contentDescription = null, tint = LbInk3, modifier = Modifier.size(15.dp))
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .weight(1f),
                    ) {
                        if (query.isEmpty()) {
                            Text("搜证件、保单、报告", fontSize = 12.sp, color = LbInk3)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp, color = LbInk),
                            cursorBrush = SolidColor(LbAccent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LbAccentSoft)
                    .clickable { showAdd = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(LbIcons.plus, contentDescription = "添加档案", tint = LbAccent, modifier = Modifier.size(18.dp))
            }
        }

        Box(Modifier.padding(top = 10.dp)) {
            HeroCard(
                painter = painterResource(R.drawable.vault_shelf),
                height = 120.dp,
                kicker = "家庭档案",
                title = "${store.archive.size} 组档案 · 共 $totalDocs 份",
            )
        }

        if (filtered.isEmpty()) {
            LbCard(modifier = Modifier.padding(top = 10.dp), contentPadding = 16.dp) {
                if (store.archive.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconBadge(LbIcons.folders, LbSurface2, LbInk2, size = 40.dp)
                        Text(
                            "还没有档案",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LbInk,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            "照片、证件扫描件、保单 PDF 都能存进来，长期留在本机。\n先建一个档案组，再往里传。",
                            fontSize = 11.5.sp,
                            color = LbInk3,
                            lineHeight = 17.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        LbPrimaryButton(
                            "新建档案组",
                            { showAdd = true },
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                        )
                    }
                } else {
                    Text("没有匹配「$query」的档案。", fontSize = 12.sp, color = LbInk3)
                }
            }
        } else {
            Column(
                Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                filtered.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { v ->
                            LbCard(modifier = Modifier
                                .weight(1f)
                                .lbTapOrLong({ openId = v.id }, { deleteId = v.id }), contentPadding = 12.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconBadge(LbIcons.folders, LbSurface2, LbInk2, size = 26.dp)
                                    Text(
                                        v.title,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = LbInk,
                                        maxLines = 1,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                                val imgs = v.files.filter { isImageName(it) }
                                if (imgs.isNotEmpty()) {
                                    Row(
                                        Modifier.padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        imgs.take(3).forEach { name ->
                                            LocalImage(
                                                store.fileOf(name).absolutePath,
                                                Modifier
                                                    .size(30.dp)
                                                    .clip(RoundedCornerShape(7.dp)),
                                            )
                                        }
                                    }
                                }
                                Text(
                                    if (v.files.isEmpty()) "还没有文件" else "${v.files.size} 个文件 · 点开可管理",
                                    fontSize = 10.5.sp,
                                    color = LbInk3,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                if (v.note.isNotEmpty()) {
                                    Text(
                                        v.note,
                                        fontSize = 10.sp,
                                        color = LbInk3,
                                        maxLines = 1,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(LbAccentSoft)
                                        .clickable {
                                            uploadTarget = v.id
                                            filePicker.launch("*/*")
                                        }
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(LbIcons.plus, contentDescription = null, tint = LbAccent, modifier = Modifier.size(13.dp))
                                    Text(
                                        "上传照片 / 文件",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = LbAccent,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LbSurface2)
                .clickable(onClick = onOpenStates)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(LbIcons.listCheck, LbSurface, LbInk2, size = 30.dp)
                Text(
                    "系统状态 · 权限、天气与本机数据一览",
                    fontSize = 12.sp,
                    color = LbInk2,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showAdd) {
        LbInputDialog(
            title = "新建档案组",
            fields = listOf(
                LbField("名称", "如：保单"),
                LbField("备注（可选）", "如：续保可提前比价"),
            ),
            onDismiss = { showAdd = false },
            onConfirm = { v ->
                val title = v.getOrElse(0) { "" }
                val note = v.getOrElse(1) { "" }
                if (title.isBlank()) "写下名称吧" else {
                    store.addArchive(title, note)
                    showAdd = false
                    // 建完直接打开这一组,让用户马上就能加照片/文件
                    store.archive.lastOrNull()?.let { openId = it.id }
                    null
                }
            },
        )
    }

    openId?.let { id ->
        ArchiveDetailDialog(
            store = store,
            archiveId = id,
            onAddPhotos = {
                uploadTarget = id
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onAddFiles = {
                uploadTarget = id
                filePicker.launch("*/*")
            },
            onOpenFile = { name -> openLocalFile(ctx, name) },
            onDeleteFile = { name -> deleteFile = id to name },
            onDismiss = { openId = null },
        )
    }

    deleteFile?.let { (aid, name) ->
        LbConfirmDialog(
            title = "从这组档案里移除？",
            text = "会同时删掉本机保存的「${niceFileName(aid, name)}」，无法恢复。",
            confirmText = "移除",
            onDismiss = { deleteFile = null },
            onConfirm = {
                store.removeArchiveFile(aid, name)
                deleteFile = null
            },
        )
    }

    deleteId?.let { id ->
        val arc = store.archive.firstOrNull { it.id == id }
        val title = arc?.title ?: "这组档案"
        val n = arc?.files?.size ?: 0
        LbConfirmDialog(
            title = "删除「$title」？",
            text = if (n > 0) "组里的 $n 个文件也会一起删掉，无法恢复。" else "删除后不再出现在档案库。",
            onDismiss = { deleteId = null },
            onConfirm = {
                store.removeArchive(id)
                deleteId = null
            },
        )
    }
}

@Composable
private fun ArchiveDetailDialog(
    store: ButlerStore,
    archiveId: String,
    onAddPhotos: () -> Unit,
    onAddFiles: () -> Unit,
    onOpenFile: (String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val arc = store.archive.firstOrNull { it.id == archiveId } ?: return
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = LbSurface) {
            Column(
                Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(arc.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                        Text(
                            if (arc.files.isEmpty()) "还没有文件" else "${arc.files.size} 个文件 · 都存在本机",
                            fontSize = 11.sp,
                            color = LbInk3,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(LbSurface2)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(LbIcons.x, contentDescription = "关闭", tint = LbInk3, modifier = Modifier.size(16.dp))
                    }
                }
                if (arc.note.isNotEmpty()) {
                    Text(
                        arc.note,
                        fontSize = 11.5.sp,
                        color = LbInk3,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    LbPrimaryButton("加照片", onAddPhotos, Modifier.weight(1f))
                    LbGhostButton("加文件", onAddFiles, Modifier.weight(1f))
                }
                Text(
                    "照片选完自动压缩后保存;其他文件单份上限 4 MB。全部只存在本机。",
                    fontSize = 10.5.sp,
                    color = LbInk3,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Spacer(Modifier.height(12.dp))

                if (arc.files.isEmpty()) {
                    LbCard(contentPadding = 14.dp) {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            IconBadge(LbIcons.inbox, LbSurface2, LbInk2, size = 38.dp)
                            Text(
                                "空档案组",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LbInk,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                "上面的按钮能把照片或 PDF 存进来，长期留底。",
                                fontSize = 11.sp,
                                color = LbInk3,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        arc.files.forEach { name ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(LbSurface2)
                                    .lbTapOrLong({ onOpenFile(name) }, { onDeleteFile(name) })
                                    .padding(9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isImageName(name)) {
                                    LocalImage(
                                        store.fileOf(name).absolutePath,
                                        Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                    )
                                } else {
                                    IconBadge(LbIcons.fileText, LbSurface, LbInk2, size = 42.dp)
                                }
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp),
                                ) {
                                    Text(
                                        niceFileName(arc.id, name),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = LbInk,
                                        maxLines = 1,
                                    )
                                    Text(
                                        "点开查看 · 长按移除",
                                        fontSize = 10.sp,
                                        color = LbInk3,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                Icon(LbIcons.chevronRight, contentDescription = null, tint = LbInk3, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ── 09 系统状态(真实的本机运行状态) ── */

@Composable
private fun StatusRow(
    title: String,
    desc: String,
    ok: Boolean,
    okText: String,
    offText: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = LbInk)
            Text(desc, fontSize = 10.5.sp, color = LbInk3, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            Modifier
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (ok) LbSurface2 else LbAccentSoft)
                .clickable(onClick = onAction)
                .padding(horizontal = 11.dp, vertical = 6.dp),
        ) {
            Text(
                if (ok) okText else actionText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (ok) LbInk3 else LbAccent,
            )
        }
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.5.sp, color = LbInk2, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = LbInk)
    }
}

@Composable
fun StatesScreen(onBack: () -> Unit, onOpenScan: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var sizeText by remember { mutableStateOf("结算中…") }

    val smsOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    val notifOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val locOk = Weather.hasLocation(ctx)
    val listenerOk = remember(tick) { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }
    val weatherOn = remember(tick) { Weather.enabled(ctx) }
    val winfo = remember(tick) { Weather.cached(ctx) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    LaunchedEffect(tick) {
        sizeText = withContext(Dispatchers.IO) {
            val bytes = runCatching {
                ctx.filesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }.getOrDefault(0L)
            when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else -> "${bytes / (1024 * 1024)} MB"
            }
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
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onBack)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(LbIcons.chevronLeft, contentDescription = "返回", tint = LbInk2, modifier = Modifier.size(20.dp))
            Text("系统状态", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
        }
        Text("本机运行状态", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
        Text("下面每一条都来自这台手机的真实状态,不是示例", fontSize = 12.5.sp, color = LbInk3, modifier = Modifier.padding(top = 4.dp))

        val pending = listOf(smsOk, listenerOk, notifOk).count { !it }
        LbCard(modifier = Modifier.padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    if (pending == 0) LbIcons.circleCheck else LbIcons.alertTriangle,
                    if (pending == 0) LbAccentSoft else LbAmberSoft,
                    if (pending == 0) LbAccent else LbAmber,
                    size = 38.dp,
                )
                Column(Modifier.padding(start = 11.dp)) {
                    Text(
                        if (pending == 0) "核心功能都已就绪" else "还有 $pending 项没开启",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LbInk,
                    )
                    Text(
                        if (pending == 0) "扫描、自动扣费识别、每日简报都能正常用"
                        else "下面未开启的项目打开后,对应功能才会生效",
                        fontSize = 11.sp,
                        color = LbInk3,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        LbCard(modifier = Modifier.padding(top = 10.dp)) {
            Text("本机数据", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LbInk3)
            Column(Modifier.padding(top = 6.dp)) {
                DataRow("订阅", "${store.subs.size} 条")
                DataRow("扣费流水", "${store.charges.size} 笔")
                DataRow("记账", "${store.expenses.size} 笔")
                DataRow("待办", "${store.tasks.count { !it.done }} 件待处理 · 共 ${store.tasks.size} 件")
                DataRow("义务", "${store.obligations.count { !it.done }} 件待处理 · 共 ${store.obligations.size} 件")
                DataRow("家人", "${store.members.size} 位")
                DataRow("档案", "${store.archive.size} 组 · ${store.archiveFileCount()} 个文件")
                DataRow("对话", "${store.chat.size} 条")
                DataRow("本机文件占用", sizeText)
                if (store.subs.isEmpty()) {
                    LbGhostButton(
                        "去扫描本机续费",
                        onOpenScan,
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        }

        LbCard(modifier = Modifier.padding(top = 10.dp)) {
            Text("权限与开关", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LbInk3)
            Column(Modifier.padding(top = 4.dp)) {
                StatusRow(
                    "短信读取",
                    "一键扫描扣费短信;只在本机分析,不外传",
                    smsOk,
                    okText = "已授权",
                    offText = "未授权",
                    actionText = "去授权",
                ) { permLauncher.launch(Manifest.permission.READ_SMS) }
                StatusRow(
                    "通知使用权",
                    "自动捕获微信 / 支付宝 / 银行的扣费通知",
                    listenerOk,
                    okText = "已开启",
                    offText = "未开启",
                    actionText = "去开启",
                ) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                StatusRow(
                    "通知权限",
                    "每日简报提醒(Android 13 起需手动允许)",
                    notifOk,
                    okText = "已允许",
                    offText = "未允许",
                    actionText = "去允许",
                ) { permLauncher.launch("android.permission.POST_NOTIFICATIONS") }
                StatusRow(
                    "大致位置",
                    "只用来取所在城市的天气,关闭不影响其他功能",
                    locOk,
                    okText = "已允许",
                    offText = "未允许",
                    actionText = "去允许",
                ) { permLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
            }
        }

        LbCard(modifier = Modifier.padding(top = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("桌面天气", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LbInk3, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (weatherOn) LbAccentSoft else LbSurface2)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (weatherOn) "已开启" else "未开启",
                        fontSize = 10.5.sp,
                        color = if (weatherOn) LbAccent else LbInk3,
                    )
                }
            }
            if (!weatherOn) {
                Text(
                    "在「我的 → 桌面天气」里打开后,这里会显示上次同步结果。",
                    fontSize = 11.5.sp,
                    color = LbInk3,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                val timeText = winfo?.let {
                    java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it.at))
                }
                Text(
                    if (winfo == null) "还没有成功同步过"
                    else "${winfo.city.ifEmpty { "所在城市" }} ${Weather.describe(winfo.code)} ${winfo.temp}° · $timeText",
                    fontSize = 12.5.sp,
                    color = LbInk,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    when {
                        !locOk -> "缺少位置权限,暂时拉不到天气"
                        Weather.isStale(ctx) -> "数据已过 1 小时,建议刷新"
                        else -> "数据新鲜,无需刷新"
                    },
                    fontSize = 11.sp,
                    color = LbInk3,
                    modifier = Modifier.padding(top = 3.dp),
                )
                LbPrimaryButton(
                    if (busy) "正在刷新…" else "立即刷新",
                    {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { Weather.refresh(ctx) }
                                busy = false
                                tick++
                                Toast.makeText(
                                    ctx,
                                    if (r != null) "天气已更新" else "这次没取到,稍后再试",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    enabled = !busy && locOk,
                )
            }
        }

        LbCard(modifier = Modifier.padding(top = 10.dp)) {
            Text("数据去向", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LbInk3)
            Text(
                "订阅、扣费、记账、档案、对话等全部只写在本机私有存储里,卸载即清空。" +
                    "唯一联网的功能是桌面天气,而且只在开启后、为了下载天气才联网。",
                fontSize = 11.5.sp,
                color = LbInk2,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
