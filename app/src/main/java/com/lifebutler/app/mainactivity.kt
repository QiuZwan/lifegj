package com.lifebutler.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifebutler.app.data.ButlerStore
import com.lifebutler.app.data.ReminderScheduler
import com.lifebutler.app.data.SubScanner
import com.lifebutler.app.ui.components.ButlerFloat
import com.lifebutler.app.ui.components.ButlerLockGate
import com.lifebutler.app.ui.components.LbBottomBar
import com.lifebutler.app.ui.components.LbGhostButton
import com.lifebutler.app.ui.components.LbPrimaryButton
import com.lifebutler.app.ui.components.lbPressable
import com.lifebutler.app.ui.icons.LbIcons
import com.lifebutler.app.ui.screens.AboutScreen
import com.lifebutler.app.ui.screens.ChatScreen
import com.lifebutler.app.ui.screens.ExpenseScreen
import com.lifebutler.app.ui.screens.FamilyScreen
import com.lifebutler.app.ui.screens.FeedbackScreen
import com.lifebutler.app.ui.screens.GuardScreen
import com.lifebutler.app.ui.screens.HelpScreen
import com.lifebutler.app.ui.screens.MemoScreen
import com.lifebutler.app.ui.screens.MineScreen
import com.lifebutler.app.ui.screens.MonthReportScreen
import com.lifebutler.app.ui.screens.ObligationsScreen
import com.lifebutler.app.ui.screens.PrivacyScreen
import com.lifebutler.app.ui.screens.ScanScreen
import com.lifebutler.app.ui.screens.SearchScreen
import com.lifebutler.app.ui.screens.StatesScreen
import com.lifebutler.app.ui.screens.SubscriptionDetailScreen
import com.lifebutler.app.ui.screens.TermsScreen
import com.lifebutler.app.ui.screens.TodayScreen
import com.lifebutler.app.ui.screens.VaultScreen
import com.lifebutler.app.ui.theme.LbAccent
import com.lifebutler.app.ui.theme.LbAccentSoft
import com.lifebutler.app.ui.theme.LbBg
import com.lifebutler.app.ui.theme.LbDark
import com.lifebutler.app.ui.theme.LbInk
import com.lifebutler.app.ui.theme.LbInk2
import com.lifebutler.app.ui.theme.LbInk3
import com.lifebutler.app.ui.theme.LbLine
import com.lifebutler.app.ui.theme.LbOnAccent
import com.lifebutler.app.ui.theme.LbOnDark
import com.lifebutler.app.ui.theme.LbSurface
import com.lifebutler.app.ui.theme.LifeButlerTheme
import com.lifebutler.app.widget.LbWidgetProvider
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val tabRequest = mutableStateOf<String?>(null)
    private val askRequest = mutableStateOf<String?>(null)

    /**
     * 「回到前台」的计数，应用锁靠它重新锁上。
     *
     * 为什么不能只看 onResume 就锁：发起解锁会跳到系统的锁屏校验界面，回来时**也是**一次 onResume，
     * 于是会解锁 → 立刻又锁上，反复弹。所以发起前先让出一段宽限窗口（[allowUnlockFlow]），
     * 窗口内那次 onResume 不算「用户从后台回来」。
     */
    private val foregroundTick = mutableStateOf(0)
    private var graceUntilMs = 0L

    private fun allowUnlockFlow() {
        graceUntilMs = SystemClock.elapsedRealtime() + 5000
    }

    override fun onResume() {
        super.onResume()
        if (SystemClock.elapsedRealtime() >= graceUntilMs) {
            foregroundTick.value = foregroundTick.value + 1
        }
        // 回到前台顺手把桌面小组件重画一次：App 里刚记完一笔，回桌面不该还看到半小时前的旧数字。
        // （系统规定 updatePeriodMillis 最快 30 分钟，光靠它跟不上一句话就记一笔的节奏。）
        LbWidgetProvider.refresh(this)
    }

    /**
     * 从 intent 里取「打开后应该落在哪里」。
     *
     * 三个来源：桌面小组件的 `open_tab` extra、通知的 `open_tab` extra、
     * 以及长按图标的快捷方式 —— 后者是静态 XML，**不支持 extras**，只能用一个自定义 action 名区分
     * （见 res/xml/lb_shortcuts.xml，改那边要同步这里）。
     */
    private fun routeFromIntent(i: android.content.Intent?): String? {
        if (i == null) return null
        return when (i.action) {
            ACTION_SHORTCUT_LEDGER -> "ledger"
            ACTION_SHORTCUT_ASK -> "智能管家"
            else -> null
        } ?: i.getStringExtra("open_tab")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ButlerStore.get(applicationContext)
        dumpIfAsked(store)
        probeNotifIfAsked()
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
        val initialTab = if (debugAsk != null) "智能管家" else routeFromIntent(intent)
        setContent {
            LifeButlerTheme(dark = store.darkMode.value) {
                ButlerLockGate(
                    enabled = store.appLockEnabled.value,
                    foregroundTick = foregroundTick.value,
                    onBeforeUnlock = { allowUnlockFlow() },
                ) {
                    LbApp(
                        tabRequest = tabRequest,
                        askRequest = askRequest,
                        initialTab = initialTab,
                        store = store,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // 让 this.intent 跟上,否则 onCreate 之外的路径(setContent 之后触发的重组)读不到这次带的 extra
        setIntent(intent)
        // 快捷方式走 action（静态 XML 带不了 extras），小组件 / 通知走 open_tab extra —— 两条都认
        routeFromIntent(intent)?.let { tabRequest.value = it }
        if (isDebuggable(this)) intent.getStringExtra("ask")?.let { askRequest.value = it }
        dumpIfAsked(ButlerStore.get(applicationContext))
        probeNotifIfAsked()
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

    /**
     * 自动化测试用的第三个深链：把**任意一条通知正文**喂给扣费 / 签约解析器，看它认不认。
     *
     *   adb shell am start -n com.lifebutler.app/.MainActivity \
     *     --es notif64 <通知正文 UTF-8 的 base64> \
     *     [--es notif_title64 <标题的 base64>] [--es notif_pkg com.eg.android.AlipayGphone]
     *
     * 为什么用 base64 而不是直接传中文：adb 这条链上给 CJK 的待遇很差
     * （`input text` 遇中文直接 NPE），base64 是纯 ASCII，绕开整条编码链。
     *
     * 结果打进 logcat 的 `LbState`（`adb logcat -s LbState:V`），而且**真的**走一遍
     * `recordNotification` —— 所以「待认领线索有没有 +1、商户名抓到什么」一起被验证。
     * 只在 debug 包生效：release 包的 debuggable 为 false，这段直接返回。
     */
    private fun probeNotifIfAsked() {
        if (!isDebuggable(this)) return
        val b64 = intent?.getStringExtra("notif64") ?: return
        val text = decodeB64(b64) ?: return
        val title = intent?.getStringExtra("notif_title64")?.let { decodeB64(it) } ?: ""
        val pkg = intent?.getStringExtra("notif_pkg") ?: "com.eg.android.AlipayGphone"
        val store = ButlerStore.get(applicationContext)
        val before = store.pendingClaims.size
        android.util.Log.d("LbState", "[notif] pkg=$pkg title=$title")
        android.util.Log.d("LbState", "[notif] ${SubScanner.debugPreview(title, text)}")
        SubScanner.recordNotification(this, pkg, title, text)
        android.util.Log.d(
            "LbState",
            "[notif] END 待认领 $before -> ${store.pendingClaims.size} " +
                store.pendingClaims.joinToString(" / ") { "${it.name}@${it.amount}" },
        )
    }

    /** base64(UTF-8) → 字符串；解不出来就返回 null（不猜、不硬塞） */
    private fun decodeB64(s: String): String? = try {
        String(android.util.Base64.decode(s, android.util.Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    private fun isDebuggable(ctx: android.content.Context): Boolean =
        (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    companion object {
        /** 与 res/xml/lb_shortcuts.xml 里那两个 action 必须一字不差 —— 改一边就要改另一边 */
        private const val ACTION_SHORTCUT_LEDGER = "com.lifebutler.app.SHORTCUT_LEDGER"
        private const val ACTION_SHORTCUT_ASK = "com.lifebutler.app.SHORTCUT_ASK"
    }
}

@Composable
fun LbApp(
    tabRequest: MutableState<String?> = mutableStateOf(null),
    askRequest: MutableState<String?> = mutableStateOf(null),
    initialTab: String? = null,
    store: ButlerStore? = null,
) {
    val ctx = LocalContext.current
    val st = store ?: remember { ButlerStore.get(ctx) }
    // 深链 / 快捷方式给出的目标既可能是一个底部 tab，也可能是一个浮层页
    // （小组件点进「记一笔」就是直接落在记账本这个浮层），所以两边都认。
    val overlayStart = if (initialTab != null && initialTab in OVERLAY_ROUTES) initialTab else null
    var tab by rememberSaveable { mutableStateOf(if (overlayStart != null) "今日" else (initialTab ?: "今日")) }
    var overlay by rememberSaveable { mutableStateOf(overlayStart) }
    var detailSubId by rememberSaveable { mutableStateOf<String?>(null) }
    // 搜索点了一条结果之后「要落在哪一条」。
    // 这里连**目标页**一起记（而不是只记一个 id）：只记 id 的话，「先搜一条待办、再从底栏
    // 翻去记账页」也会亮一下 —— 那条待办的 id 当然不在记账页里，用户看到的是「页面闪了一下」。
    // 页面自己认领（拿到的 id 与自己的 key 匹配才用），亮完回调清空。
    var highlightAt by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 待发送的一句(深链带进来的),发完就清掉;再由新的深链带下一句进来
    var pendingAsk by remember { mutableStateOf(askRequest.value) }
    var showGuide by remember { mutableStateOf(false) }

    // 首启三步引导：只在「没走过引导」且「本机真的一条记录都没有」时才弹。
    // 后一个条件是为了别挡老用户 —— 升级上来的人已经会用了，突然被教程拦一下只会烦。
    LaunchedEffect(st.onboarded.value) {
        if (!st.onboarded.value && !st.hasAnyRecord()) showGuide = true
    }

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
            // 浮层页(备忘录 / 记账本 / 档案库 / 义务 / 月报 / 扫描 / 系统状态)不是底部 tab，
            // 小组件和通知点进来都走这里
            if (want in OVERLAY_ROUTES) overlay = want else tab = want
            tabRequest.value = null
        }
    }

    BackHandler(enabled = overlay != null) {
        // 「关于管家」下面那几个子页(帮助/协议/反馈),返回键退到「关于管家」这一层,
        // 而不是一下子退到底栏 —— 从「我的」进来要按两下才回去,跟微信那边的层级感一致。
        overlay = if (overlay?.startsWith("about_") == true) "about" else null
    }

    /**
     * 管家给的 route 名字 → 真的翻页。两处调用(「智能管家」页内、悬浮小管家)共用这一份,
     * 免得两个名字表走散——加一页只改这里。
     */
    fun openRoute(route: String) {
        when (route) {
            "today" -> { overlay = null; tab = "今日" }
            "guard" -> { overlay = null; tab = "守护" }
            "family" -> { overlay = null; tab = "家庭" }
            "mine" -> { overlay = null; tab = "我的" }
            "chat" -> Unit
            else -> overlay = route
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LbBg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
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
                        "detail" -> SubscriptionDetailScreen(
                            subId = detailSubId,
                            onBack = { overlay = null },
                            highlightId = highlightAt?.takeIf { it.first == "detail" }?.second,
                            onHighlightConsumed = { highlightAt = null },
                        )
                        "scan" -> ScanScreen(onBack = { overlay = null })
                        "duties" -> ObligationsScreen(
                            onBack = { overlay = null },
                            highlightId = highlightAt?.takeIf { it.first == "duties" }?.second,
                            onHighlightConsumed = { highlightAt = null },
                        )
                        "ledger" -> ExpenseScreen(
                            onBack = { overlay = null },
                            highlightId = highlightAt?.takeIf { it.first == "ledger" }?.second,
                            onHighlightConsumed = { highlightAt = null },
                        )
                        "memo" -> MemoScreen(
                            onBack = { overlay = null },
                            highlightId = highlightAt?.takeIf { it.first == "memo" }?.second,
                            onHighlightConsumed = { highlightAt = null },
                        )
                        "search" -> SearchScreen(
                            onBack = { overlay = null },
                            // 点结果就真的翻过去：订阅带 subId 的要落到「哪一笔」的详情，
                            // 光给一个页面名会跳到列表顶部，等于没跳
                            onOpen = { route, subId, hl ->
                                if (subId != null) {
                                    detailSubId = subId
                                    overlay = "detail"
                                    // 订阅命中时 hl 就是它自己的 id；扣费流水命中时 hl 是**那一笔**的 id
                                    // （subId 指的是它归属的订阅），详情页靠这个把流水那一行滚出来。
                                    highlightAt = "detail" to (hl ?: subId)
                                } else {
                                    if (route == "chat") tab = "智能管家" else openRoute(route)
                                    if (route == "chat") overlay = null
                                    // 落定之后再写定位，免得被 openRoute 里可能的重置盖掉
                                    highlightAt = hl?.let { pageKeyOf(route) to it }
                                }
                            },
                        )
                        "report" -> MonthReportScreen(onBack = { overlay = null })
                        "states" -> StatesScreen(onBack = { overlay = null }, onOpenScan = { overlay = "scan" })
                        "vault" -> VaultScreen(
                            onOpenStates = { overlay = "states" },
                            highlightId = highlightAt?.takeIf { it.first == "vault" }?.second,
                            onHighlightConsumed = { highlightAt = null },
                        )
                        // 「关于管家」及其子页(帮助 / 服务协议 / 隐私协议 / 意见反馈)。
                        // 全走 overlay,底栏会自然收起,和别的浮层页一个待遇。
                        "about" -> AboutScreen(
                            onBack = { overlay = null },
                            onOpenHelp = { overlay = "about_help" },
                            onOpenTerms = { overlay = "about_terms" },
                            onOpenPrivacy = { overlay = "about_privacy" },
                            onOpenFeedback = { overlay = "about_feedback" },
                        )
                        "about_help" -> HelpScreen(onBack = { overlay = "about" })
                        "about_terms" -> TermsScreen(onBack = { overlay = "about" })
                        "about_privacy" -> PrivacyScreen(onBack = { overlay = "about" })
                        "about_feedback" -> FeedbackScreen(onBack = { overlay = "about" })
                        "今日" -> TodayScreen(
                            onOpenDuties = { overlay = "duties" },
                            onOpenGuard = { tab = "守护" },
                            onOpenLedger = { overlay = "ledger" },
                            onOpenReport = { overlay = "report" },
                            onOpenSearch = { overlay = "search" },
                            onOpenFamily = { tab = "家庭" },
                            highlightId = highlightAt?.takeIf { it.first == "今日" }?.second,
                            onHighlightConsumed = { highlightAt = null },
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
                            onOpen = { route -> openRoute(route) },
                            autoAsk = pendingAsk,
                            onAskConsumed = { pendingAsk = null },
                            highlightId = highlightAt?.takeIf { it.first == "智能管家" }?.second,
                            onHighlightConsumed = { highlightAt = null },
                        )
                        "家庭" -> FamilyScreen(
                            highlightId = highlightAt?.takeIf { it.first == "家庭" }?.second,
                            onHighlightConsumed = { highlightAt = null },
                        )
                        "我的" -> MineScreen(
                            onOpenVault = { overlay = "vault" },
                            onOpenFamily = { tab = "家庭" },
                            onOpenReport = { overlay = "report" },
                            onOpenMemo = { overlay = "memo" },
                            onOpenAbout = { overlay = "about" },
                        )
                        else -> {}
                    }
                }
            }
            if (overlay == null) {
                LbBottomBar(current = tab, onSelect = { tab = it })
            }
        }

        // 悬浮小管家:压在整套界面之上(App 内全局),默认贴右侧、拖到哪儿存哪儿、点一下就地说话。
        // 「智能管家」页不给它出场——那一页有自己那一台大字号的机器人,两个一起出现会看花眼,
        // 也分不清到底哪个能拖。
        ButlerFloat(
            visible = (overlay ?: tab) != "智能管家",
            onOpenChat = {
                overlay = null
                tab = "智能管家"
            },
            onOpenRoute = { route -> openRoute(route) },
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                // 底栏还在的时候给它留出位置,机器人不会被拖到按钮底下
                .padding(bottom = if (overlay == null) 60.dp else 0.dp),
        )

        /*
         * 删除撤销条：任何模块删了东西都在这**一处**弹，5 秒后自己收回。
         *
         * 为什么不放在各页自己管：删除入口散在待办 / 守护 / 订阅 / 扣费 / 证件 / 成员 / 相册 /
         * 档案 / 备忘 / 记账十来个界面里，每处都写一遍计时和收回，迟早有的一直挂着、有的忘了
         * 通知数据层丢弃还原动作（那就会「窗口早就过了但撤销还能生效」，比没有撤销更让人困惑）。
         */
        var undoVisible by remember { mutableStateOf(false) }
        LaunchedEffect(st.undoToken.value) {
            if (st.undoToken.value <= 0) return@LaunchedEffect
            undoVisible = true
            delay(UNDO_WINDOW_MS)
            undoVisible = false
            st.discardUndo()
        }
        AnimatedVisibility(
            visible = undoVisible,
            enter = slideInVertically { h -> h } + fadeIn(),
            exit = slideOutVertically { h -> h } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = if (overlay == null) 78.dp else 20.dp),
        ) {
            UndoBar(
                label = st.undoLabel.value,
                onUndo = {
                    st.undoLastDelete()
                    undoVisible = false
                },
            )
        }

        if (showGuide) {
            OnboardingGuide(
                onOpen = { want ->
                    showGuide = false
                    st.setOnboarded()
                    if (want in OVERLAY_ROUTES) overlay = want else tab = want
                },
                onDismiss = {
                    showGuide = false
                    st.setOnboarded()
                },
            )
        }
    }
}

/** 撤销窗口：5 秒。够看清删了什么，又不至于一直杵在屏幕上 */
private const val UNDO_WINDOW_MS = 5_000L

/**
 * 那些「不是底部 tab、而是浮层」的页面名。
 * 深链 / 小组件 / 通知带过来的目标只要落在这里面，就当成浮层打开。
 */
private val OVERLAY_ROUTES = setOf(
    "memo", "ledger", "report", "vault", "duties", "scan", "states", "about", "search", "detail",
)

/**
 * 搜索结果里的 route → 它在 `when(key)` 里对应的那个 key。
 *
 * 为什么要绕一层：搜索页给的 route 是「四个底部 tab + 一串浮层」两种东西混在一起的
 * （`guard` 是底栏的「守护」，`ledger` 是浮层的记账本），而定位状态是按 `when(key)` 的 key 存的。
 * 这份映射只有这里一处，加一页只改这里。
 */
private fun pageKeyOf(route: String): String = when (route) {
    "today" -> "今日"
    "guard" -> "守护"
    "family" -> "家庭"
    "chat" -> "智能管家"
    "mine" -> "我的"
    else -> route
}

/**
 * 首启三步引导。
 *
 * 三条规矩：
 * 1. **不塞任何演示数据** —— 守住「首次安装是干净的」这个约定，引导只告诉用户去哪儿做，不替他做；
 * 2. **每步都能跳** —— 用户想自己摸，就让他摸；
 * 3. 走完（或跳过）就写一个标记，不会第二次挡路。
 */
@Composable
private fun OnboardingGuide(onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val steps = listOf(
        Triple(
            "① 加一个订阅",
            "把每月的会员费记下来：多少钱、哪天扣。扣费日前我会在每日简报里提醒你，不用自己记。",
            "去守护页加一个" to "guard",
        ),
        Triple(
            "② 记一笔",
            "午饭、打车随手记一笔。月底的月报会告诉你钱花在哪、比上月多了还是少了，还能设一个月度预算。",
            "打开记账本" to "ledger",
        ),
        Triple(
            "③ 跟管家说一句话",
            "比如「记一下：明天交房租」或「记账：午饭 25」。它会真的写进本机；改和删会先问你一次。",
            "去跟它说一句" to "智能管家",
        ),
    )
    var step by remember { mutableStateOf(0) }
    val (title, body, action) = steps[step]
    val last = step == steps.lastIndex

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(24.dp),
            color = LbSurface,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("三步就能用起来", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LbAccent)
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk, modifier = Modifier.padding(top = 5.dp))
                Text(
                    body,
                    fontSize = 12.5.sp,
                    color = LbInk2,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "所有记录只存在这台手机上；这里不会自动填任何示例内容。",
                    fontSize = 11.sp,
                    color = LbInk3,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LbGhostButton(if (last) "先自己看看" else "跳过", onDismiss, Modifier.weight(1f))
                    LbPrimaryButton(
                        if (last) "去说一句" else "下一步",
                        {
                            if (last) {
                                onOpen(action.second)
                            } else {
                                step++
                            }
                        },
                        Modifier.weight(1f),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    steps.indices.forEach { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (i == step) LbAccent else LbLine),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UndoBar(label: String, onUndo: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LbDark)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(LbIcons.trash, contentDescription = null, tint = LbOnDark, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            fontSize = 13.sp,
            color = LbOnDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(LbAccent)
                .lbPressable(onClick = onUndo)
                .padding(horizontal = 13.dp, vertical = 6.dp),
        ) {
            Text("撤销", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LbOnAccent)
        }
    }
}
