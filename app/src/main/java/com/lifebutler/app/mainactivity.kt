package com.lifebutler.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lifebutler.app.data.ButlerStore
import com.lifebutler.app.data.ReminderScheduler
import com.lifebutler.app.ui.components.LbBottomBar
import com.lifebutler.app.ui.screens.ChatScreen
import com.lifebutler.app.ui.screens.ExpenseScreen
import com.lifebutler.app.ui.screens.FamilyScreen
import com.lifebutler.app.ui.screens.GuardScreen
import com.lifebutler.app.ui.screens.MemoScreen
import com.lifebutler.app.ui.screens.MineScreen
import com.lifebutler.app.ui.screens.MonthReportScreen
import com.lifebutler.app.ui.screens.ObligationsScreen
import com.lifebutler.app.ui.screens.ScanScreen
import com.lifebutler.app.ui.screens.StatesScreen
import com.lifebutler.app.ui.screens.SubscriptionDetailScreen
import com.lifebutler.app.ui.screens.TodayScreen
import com.lifebutler.app.ui.screens.VaultScreen
import com.lifebutler.app.ui.theme.LbBg
import com.lifebutler.app.ui.theme.LifeButlerTheme

class MainActivity : ComponentActivity() {
    private val tabRequest = mutableStateOf<String?>(null)
    private val askRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ButlerStore.get(applicationContext)
        dumpIfAsked(store)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { _ -> store.darkMode.value },
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { _ -> store.darkMode.value },
        )
        ReminderScheduler.ensureScheduled(applicationContext)
        // 自动化测试用的深链(把 app 装到模拟器上,用 adb 发一句中文过来)。
        // 只在 debug 包生效:release 包的 debuggable 是 false,这段永远是 null,装给用户的包没有这个入口。
        // (不用 BuildConfig:AGP 8 默认不生成那个类,为一行测试代码去开 buildConfig 不划算)
        val debugAsk = if (isDebuggable(this)) intent?.getStringExtra("ask") else null
        askRequest.value = debugAsk
        val initialTab = if (debugAsk != null) "智能管家" else intent?.getStringExtra("open_tab")
        setContent {
            LifeButlerTheme(dark = store.darkMode.value) {
                LbApp(tabRequest = tabRequest, askRequest = askRequest, initialTab = initialTab)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // 让 this.intent 跟上,否则 onCreate 之外的路径(setContent 之后触发的重组)读不到这次带的 extra
        setIntent(intent)
        intent.getStringExtra("open_tab")?.let { tabRequest.value = it }
        if (isDebuggable(this)) intent.getStringExtra("ask")?.let { askRequest.value = it }
        dumpIfAsked(ButlerStore.get(applicationContext))
    }

    /**
     * 自动化测试用的另一个深链:把当前本机记录打进 logcat,由 `adb logcat -s LbState:V` 读走。
     *
     * 为什么绕这么大弯,不直接读 shared_prefs/lifebutler.xml:
     * ButlerStore 用 apply()(异步落盘),实测冷启动后连等 36 秒、按 HOME 退到后台,
     * 那个文件都还是空的,而界面上明明已经有记录了 —— 脚本按文件读会拿到空数据,
     * 于是「有没有出现新回复」整体错位一格(第一版端到端就是这么废掉的)。
     * 为什么不用写文件:写了 cache/dump_state.json,adb run-as 那边看不到(试过,盘上确实没落)。
     * logcat 是唯一一条 adb 一定读得到、又不需要等落盘的通道。
     *
     * 分片输出是因为 logcat 单条消息有长度上限;分片之间用 BEGIN/END 夹住,脚本取最后一段。
     * 只在 debug 包生效:release 包 debuggable 为 false,这段永远不会执行,
     * 用户的包里不会有任何把数据倒出来的入口。
     */
    private fun dumpIfAsked(store: ButlerStore) {
        if (!isDebuggable(this)) return
        val nonce = intent?.getStringExtra("dump") ?: return
        val s = store.exportState()
        val parts = s.chunked(900)
        android.util.Log.d("LbState", "PROBE ${store.debugProbe()}")
        // 带上这次请求带过来的 nonce:脚本按它认领属于自己那一段。
        // 不然脚本读到的是 logcat 里**最后**一段 dump,可能是上一轮或上一次调用的残留,
        // 表现是「基线里混进了上一次的数据」(实测:清空数据后基线里还留着上轮的订阅和深色开关)。
        android.util.Log.d("LbState", "BEGIN ${parts.size} ${s.length} $nonce")
        parts.forEachIndexed { i, p -> android.util.Log.d("LbState", "$i|$p") }
        android.util.Log.d("LbState", "END")
    }

    private fun isDebuggable(ctx: android.content.Context): Boolean =
        (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

@Composable
fun LbApp(
    tabRequest: MutableState<String?> = mutableStateOf(null),
    askRequest: MutableState<String?> = mutableStateOf(null),
    initialTab: String? = null,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab ?: "今日") }
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }
    var detailSubId by rememberSaveable { mutableStateOf<String?>(null) }
    // 待发送的一句(深链带进来的),发完就清掉;再由新的深链带下一句进来
    var pendingAsk by remember { mutableStateOf(askRequest.value) }

    LaunchedEffect(askRequest.value) {
        askRequest.value?.let {
            pendingAsk = it
            askRequest.value = null
        }
    }

    LaunchedEffect(tabRequest.value) {
        tabRequest.value?.let { want ->
            overlay = null
            detailSubId = null
            // 备忘录是浮层页(不是底部 tab),通知点进来也走这里
            if (want == "memo") overlay = "memo" else tab = want
            tabRequest.value = null
        }
    }

    BackHandler(enabled = overlay != null) { overlay = null }

    Column(
        Modifier
            .fillMaxSize()
            .background(LbBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = overlay ?: tab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(120))
                },
                label = "screen",
                modifier = Modifier.fillMaxSize(),
            ) { key ->
                when (key) {
                    "detail" -> SubscriptionDetailScreen(subId = detailSubId, onBack = { overlay = null })
                    "scan" -> ScanScreen(onBack = { overlay = null })
                    "duties" -> ObligationsScreen(onBack = { overlay = null })
                    "ledger" -> ExpenseScreen(onBack = { overlay = null })
                    "memo" -> MemoScreen(onBack = { overlay = null })
                    "report" -> MonthReportScreen(onBack = { overlay = null })
                    "states" -> StatesScreen(onBack = { overlay = null }, onOpenScan = { overlay = "scan" })
                    "vault" -> VaultScreen(onOpenStates = { overlay = "states" })
                    "今日" -> TodayScreen(
                        onOpenDuties = { overlay = "duties" },
                        onOpenGuard = { tab = "守护" },
                        onOpenLedger = { overlay = "ledger" },
                        onOpenReport = { overlay = "report" },
                    )
                    "守护" -> GuardScreen(
                        onOpenDetail = { id ->
                            detailSubId = id
                            overlay = "detail"
                        },
                        onOpenDuties = { overlay = "duties" },
                        onOpenScan = { overlay = "scan" },
                    )
                    "智能管家" -> ChatScreen(
                        // 管家说要带用户去哪一页时,由这里真的翻过去
                        onOpen = { route ->
                            when (route) {
                                "today" -> { overlay = null; tab = "今日" }
                                "guard" -> { overlay = null; tab = "守护" }
                                "family" -> { overlay = null; tab = "家庭" }
                                "mine" -> { overlay = null; tab = "我的" }
                                "chat" -> Unit
                                else -> overlay = route
                            }
                        },
                        autoAsk = pendingAsk,
                        onAskConsumed = { pendingAsk = null },
                    )
                    "家庭" -> FamilyScreen()
                    "我的" -> MineScreen(
                        onOpenVault = { overlay = "vault" },
                        onOpenFamily = { tab = "家庭" },
                        onOpenReport = { overlay = "report" },
                        onOpenMemo = { overlay = "memo" },
                    )
                    else -> {}
                }
            }
        }
        if (overlay == null) {
            LbBottomBar(current = tab, onSelect = { tab = it })
        }
    }
}
