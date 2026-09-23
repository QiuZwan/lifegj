package com.lifebutler.app.ui.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifebutler.app.ui.icons.LbIcons
import com.lifebutler.app.ui.theme.LbAccent
import com.lifebutler.app.ui.theme.LbAccentSoft
import com.lifebutler.app.ui.theme.LbAmber
import com.lifebutler.app.ui.theme.LbAmberSoft
import com.lifebutler.app.ui.theme.LbBg
import com.lifebutler.app.ui.theme.LbInk
import com.lifebutler.app.ui.theme.LbInk2
import com.lifebutler.app.ui.theme.LbLine
import com.lifebutler.app.ui.theme.LbSurface

/**
 * 应用锁：进 App 先过一遍**系统锁屏**（密码 / 图案 / 指纹 / 人脸），从后台回来再锁一次。
 *
 * 三件刻意为之的事：
 *
 * 1. **不自己存密码。** 交给系统 Keyguard 校验，我们既不接触指纹也不存 PIN。本机设没设锁屏
 *    也能如实判断（`isDeviceSecure`），不会出现「以为锁上了其实谁都能进」。
 * 2. **本机没设锁屏时不硬锁。** 那种情况下根本没法解锁，硬锁等于把用户自己关在外面；
 *    这里如实告诉用户「应用锁现在不生效」并给一条去系统设置的路，外加一个「暂时进入」。
 *    宁可承认没保护，也不做一个假的锁屏。
 * 3. **`foregroundTick` 由 Activity 的 onResume 驱动**，而不是这里自己观察生命周期：
 *    发起解锁会跳出去再回来，若不分清「自己那次」就会陷入解锁→立刻又锁上的死循环，
 *    所以发起前让 Activity 记一个宽限窗口（onBeforeUnlock），回程那次不算「回到前台」。
 */
@Composable
fun ButlerLockGate(
    enabled: Boolean,
    foregroundTick: Int,
    onBeforeUnlock: () -> Unit,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val km = remember(ctx) { ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager }
    // ⚠️ 必须把 foregroundTick 一起做 key：原来只 remember(ctx)，于是"本机设没设锁屏"这件事
    // 只在进入组合时算了一次就永远缓存。用户按提示去系统里设好锁屏、切回 App —— 界面**依然**
    // 显示「应用锁现在不生效」，他会以为"我设了它没认"，反过来怀疑锁屏没设成功。
    // foregroundTick 本来就在 onResume 时自增（见本文件顶部第 3 条说明），拿它当 key
    // 恰好就是"回到前台重算一次"。
    val secure = remember(ctx, foregroundTick) { km?.isDeviceSecure == true }

    var locked by remember { mutableStateOf(enabled) }
    // 本机没设锁屏时用户选了「暂时进入」：本次进程内不再拦
    var bypassed by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) locked = false
    }

    // 回到前台重新锁上
    LaunchedEffect(foregroundTick) { if (enabled && !bypassed) locked = true }
    // 设置里关掉应用锁 → 立刻放行
    LaunchedEffect(enabled) { if (!enabled) { locked = false; bypassed = false } }

    if (!enabled || !locked || bypassed) {
        content()
        return
    }

    @Suppress("DEPRECATION")
    fun launchKeyguard() {
        val intent = km?.createConfirmDeviceCredentialIntent("生活管家", "解锁后查看本机记录")
        if (intent == null) {
            // 系统没得校验（没设锁屏，或厂商没实现这个入口）：只能放行，但说清楚为什么
            bypassed = true
            return
        }
        onBeforeUnlock()
        launcher.launch(intent)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LbBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(LbAccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    LbIcons.shieldLock,
                    contentDescription = null,
                    tint = LbAccent,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("生活管家已锁定", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
            Text(
                "本机记录只有你能看。用系统的锁屏密码 / 指纹解锁后继续。",
                fontSize = 13.sp,
                color = LbInk2,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (secure) {
                Spacer(Modifier.height(24.dp))
                LbPrimaryButton("解锁", onClick = { launchKeyguard() }, modifier = Modifier.fillMaxWidth())
            } else {
                // 诚实提示：现在这个锁保护不了任何东西，原因在本机系统设置，不在这个开关
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(LbAmberSoft)
                        .padding(14.dp),
                ) {
                    Text("应用锁现在不生效", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = LbAmber)
                    Text(
                        "本机还没有设置锁屏密码、图案或指纹，系统没得校验 —— 先去系统设置里设一个，回来就能真正锁上。",
                        fontSize = 12.5.sp,
                        color = LbInk2,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                LbPrimaryButton(
                    "打开系统安全设置",
                    onClick = {
                        val i = android.content.Intent(Settings.ACTION_SECURITY_SETTINGS)
                        if (ctx !is Activity) i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            ctx.startActivity(i)
                        } catch (e: Exception) {
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                LbGhostButton("暂时进入（不锁）", onClick = { bypassed = true }, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LbSurface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LbIcons.shieldCheck, contentDescription = null, tint = LbAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "校验由系统完成，生活管家不保存、也看不到你的密码或指纹。",
                    fontSize = 11.5.sp,
                    color = LbInk2,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}
