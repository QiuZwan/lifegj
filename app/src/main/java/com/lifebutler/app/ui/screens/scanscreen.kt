package com.lifebutler.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.lifebutler.app.data.ButlerStore
import com.lifebutler.app.data.SubScanner
import com.lifebutler.app.ui.components.ChipTone
import com.lifebutler.app.ui.components.IconBadge
import com.lifebutler.app.ui.components.LbCard
import com.lifebutler.app.ui.components.LbChip
import com.lifebutler.app.ui.components.LbGhostButton
import com.lifebutler.app.ui.components.LbPrimaryButton
import com.lifebutler.app.ui.components.SectionHeader
import com.lifebutler.app.ui.icons.LbIcons
import com.lifebutler.app.ui.theme.LbAccent
import com.lifebutler.app.ui.theme.LbAccentSoft
import com.lifebutler.app.ui.theme.LbInk
import com.lifebutler.app.ui.theme.LbInk2
import com.lifebutler.app.ui.theme.LbInk3
import com.lifebutler.app.ui.theme.LbLine
import com.lifebutler.app.ui.theme.LbRust
import com.lifebutler.app.ui.theme.LbRustSoft
import com.lifebutler.app.ui.theme.LbSurface
import com.lifebutler.app.ui.theme.LbSurface2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun ScanScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) } // 0=说明 1=扫描中 2=结果
    var smsGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    var candidates by remember { mutableStateOf<List<SubScanner.Candidate>>(emptyList()) }
    var apps by remember { mutableStateOf<List<String>>(emptyList()) }
    var scannedWithSms by remember { mutableStateOf(false) }
    var addedNow by remember { mutableStateOf(0) }
    var notifEnabled by remember { mutableStateOf(SubScanner.notificationsEnabled(ctx)) }

    fun addAsSub(name: String, amount: Double, nextDate: String = "") {
        store.addScannedSub(name, amount, "扫描", nextDate, force = true)
    }

    fun startScan() {
        step = 1
        scope.launch {
            delay(450)
            val sms = if (smsGranted) withContext(Dispatchers.IO) { SubScanner.scanSms(ctx) } else emptyList()
            scannedWithSms = smsGranted
            delay(350)
            val a = withContext(Dispatchers.IO) { SubScanner.installedSubApps(ctx) }
            val merged = withContext(Dispatchers.IO) { SubScanner.mergeCandidates(sms, SubScanner.notificationFindings(ctx)) }
            var added = 0
            merged.forEach { c ->
                if (store.subs.none { it.name == c.name } && !store.isDismissed(c.name)) {
                    val src = if (c.source.contains("通知")) "通知" else "扫描"
                    store.addScannedSub(c.name, c.amount ?: 0.0, src, c.nextDate)
                    added++
                }
                // 短信本身就是一条真实的扣费凭证 → 写进真实扣费流水
                if (c.source.contains("短信") && (c.amount ?: 0.0) > 0) {
                    val iso = SubScanner.fmtIso(c.dateMs)
                    if (store.charges.none { it.subName == c.name && it.date == iso }) {
                        store.addCharge(c.name, c.amount ?: 0.0, iso, "短信")
                    }
                }
            }
            addedNow = added
            candidates = merged
            apps = a
            step = 2
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        smsGranted = ok
        if (ok) startScan()
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
            Text("扫描本机", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
        }

        when (step) {
            0 -> {
                LbCard(modifier = Modifier.padding(top = 10.dp)) {
                    Text("一键扫描本机自动续费", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                    Text(
                        "扫描会查看三处：\n① 扣费短信（需要短信读取权限，只在本机分析、不上传）\n② 微信/支付宝的扣费通知（需开启「通知读取」，从开启后开始记录；到达时会自动进清单）\n③ 已安装应用列表（对照常见订阅类 App）",
                        fontSize = 12.5.sp,
                        color = LbInk2,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "扫描结果会**自动加入「守护」清单**；误加入的可在结果里一键移除（移除后不会再自动加回）。结果仅供参考，核对以平台账单为准。",
                        fontSize = 11.5.sp,
                        color = LbInk3,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    if (smsGranted) {
                        LbPrimaryButton("开始扫描", onClick = { startScan() }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LbPrimaryButton(
                            "授权读取短信并扫描",
                            onClick = { permLauncher.launch(Manifest.permission.READ_SMS) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "暂不授权，只扫描已安装应用",
                                fontSize = 12.sp,
                                color = LbAccent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { startScan() }
                                    .padding(8.dp),
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .height(1.dp)
                            .background(LbLine),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("通知读取（微信/支付宝扣费推送）", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                            Text(
                                if (notifEnabled) "已开启：扣费通知会在本机留档，扫描时一起呈现" else "未开启：只能从开启之后开始记录",
                                fontSize = 11.5.sp,
                                color = LbInk3,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                        if (notifEnabled) {
                            LbChip("已开启", ChipTone.Green)
                        } else {
                            MiniAction("去开启") {
                                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        }
                    }
                    Text(
                        "开启后回到本页，点这里刷新状态",
                        fontSize = 11.sp,
                        color = LbAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { notifEnabled = SubScanner.notificationsEnabled(ctx) }
                            .padding(top = 6.dp, bottom = 2.dp),
                    )
                }
            }

            1 -> {
                LbCard(modifier = Modifier.padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(LbIcons.scan, LbAccentSoft, LbAccent, size = 38.dp)
                        Column(Modifier.padding(start = 11.dp)) {
                            Text("正在扫描…", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                            Text(
                                if (smsGranted) "正在分析本机扣费短信、通知线索与已安装应用" else "正在检查通知线索与已安装应用",
                                fontSize = 12.sp,
                                color = LbInk3,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            else -> {
                if (scannedWithSms && candidates.isEmpty()) {
                    LbCard(modifier = Modifier.padding(top = 10.dp), contentPadding = 12.dp) {
                        Text("短信里没有发现明确的扣费线索。", fontSize = 12.5.sp, color = LbInk3)
                    }
                }
                if (addedNow > 0) {
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
                            Text("已自动加入 $addedNow 条到守护清单", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbAccent)
                            Text("误加入的点「移除」即可；移除后不会再自动加回", fontSize = 11.5.sp, color = LbInk2)
                        }
                    }
                }
                if (candidates.isNotEmpty()) {
                    SectionHeader("本机发现的扣费线索") {
                        Text("${candidates.size} 条", fontSize = 12.5.sp, color = LbInk3)
                    }
                    LbCard(contentPadding = 8.dp) {
                        candidates.forEach { c ->
                            val existing = store.subs.firstOrNull { it.name == c.name }
                            val warn = existing?.closing == true && c.dateMs > existing.closingAt
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 9.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(LbSurface2),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(c.name.take(1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LbInk2)
                                }
                                Column(
                                    Modifier
                                        .padding(start = 11.dp)
                                        .weight(1f),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(c.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                                        if (warn) {
                                            Spacer(Modifier.size(5.dp))
                                            LbChip("关闭后仍有扣费", ChipTone.Rust)
                                        }
                                    }
                                    Text(
                                        (c.amount?.let { "¥" + store.fmtMoney(it) } ?: "金额待补充") + " · " + SubScanner.fmtDate(c.dateMs) + " · 来源:" + c.source,
                                        fontSize = 11.5.sp,
                                        color = LbInk2,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                    Text(
                                        if (c.nextDate.isNotEmpty()) "下次扣费：${store.fmtCn(c.nextDate)}"
                                        else "下次扣费：原文里没有写明，加入后可在守护页补全",
                                        fontSize = 11.sp,
                                        color = if (c.nextDate.isNotEmpty()) LbInk2 else LbInk3,
                                        modifier = Modifier.padding(top = 1.dp),
                                    )
                                    Text(
                                        c.snippet,
                                        fontSize = 11.sp,
                                        color = LbInk3,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                when {
                                    existing != null && (existing.source == "扫描" || existing.source == "通知") -> MiniAction("移除") {
                                        store.removeSub(existing.id)
                                        store.dismissName(existing.name)
                                    }
                                    existing != null -> LbChip("已在守护", ChipTone.Green)
                                    else -> MiniAction("加入") { addAsSub(c.name, c.amount ?: 0.0, c.nextDate) }
                                }
                            }
                        }
                    }
                }

                if (apps.isNotEmpty()) {
                    SectionHeader("本机安装的相关应用") {
                        Text("${apps.size} 个", fontSize = 12.5.sp, color = LbInk3)
                    }
                    LbCard(contentPadding = 12.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            apps.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { a ->
                                        val exists = store.subs.any { it.name == a }
                                        AppChip(name = a, added = exists, modifier = Modifier.weight(1f)) { addAsSub(a, 0.0, "") }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                if (candidates.isEmpty() && apps.isEmpty()) {
                    LbCard(modifier = Modifier.padding(top = 10.dp), contentPadding = 12.dp) {
                        Text(
                            "没有发现线索。微信/支付宝的扣费可以通过「通知读取」积累（从开启后开始）；也可以稍后再试，或在「守护」页手动添加。",
                            fontSize = 12.5.sp,
                            color = LbInk3,
                            lineHeight = 19.sp,
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    LbGhostButton("重新扫描", onClick = { startScan() }, modifier = Modifier.weight(1f))
                    LbPrimaryButton("完成", onClick = onBack, modifier = Modifier.weight(1f))
                }
                Text(
                    "全部在本机完成，不上传；结果仅供参考，请以各平台账单为准。",
                    fontSize = 11.sp,
                    color = LbInk3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MiniAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(LbAccentSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = LbAccent)
    }
}

@Composable
private fun AppChip(name: String, added: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (added) LbAccentSoft else com.lifebutler.app.ui.theme.LbLineStrong, RoundedCornerShape(12.dp))
            .clickable(enabled = !added, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, fontSize = 12.5.sp, color = LbInk, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (added) "已加入" else "+ 加入", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (added) LbInk3 else LbAccent)
    }
}
