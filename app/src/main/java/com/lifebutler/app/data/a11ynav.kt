package com.lifebutler.app.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

/**
 * 「帮我翻进去」—— 用无障碍**在支付宝 / 微信里逐级点导航**，替你走到那张代扣清单页，
 * 到了就交给 [A11yScanner] 把清单读下来。
 *
 * ## 为什么值得做
 *
 * 那份清单在人家服务器上，没接口可查（官方接口只允许**商家**查自己那一单）。
 * 但清单本身**是给人看的** —— 你手动点进去能看见，那读屏就也能看见。
 * 之前 v2.17 只做了「你打开那页我帮你抄下来」，等于还得你自己找路；
 * 这一版是「你自己找路」这件事也替你做掉。
 *
 * ## 安全底线（这段是整个功能的命门，别为了"翻得进去"把它调松）
 *
 * 我们在**别人的支付 App**里点东西。点错一下的代价可能是真的付出去一笔钱、
 * 或者把一个你还在用的订阅关掉。所以：
 *
 * 1. **只点配置里写死的那些字，一律精确相等**（去空白后 `text` 或 `contentDescription`
 *    完全等于候选词）。不做 `contains`、不做模糊匹配 —— 模糊匹配正是"点到关闭按钮"的来源。
 * 2. **[FORBIDDEN] 二次拦截**：哪怕是配置里写的候选词，只要含动作类词（关闭/解约/取消/付款…）
 *    也**拒绝点击**。这是防"以后有人往配置里加了一条危险路径"的保险。
 * 3. **只在前 N 步点导航**。一旦认出清单页就**立刻停手**，绝不在目标页上再点任何东西。
 * 4. **找不到就停**（滚 3 次还找不到 → 结束并给手动路径），**绝不碰运气乱点**。
 * 5. 全程有**总超时**（[TIMEOUT_MS]）和**步数上限**，超了自动收手。
 * 6. 只在目标包自己的窗口里动作（`pkg != payer.pkg` 直接返回）。
 *
 * ## 诚实边界
 *
 * 各家的层级路径与按钮文案**会改版**，而这份路径表是照公开信息写的、**没在真机上核过**
 * （手上没有真支付宝/微信的可用账号）。所以：
 * - 失败是**正常结局之一**，不是异常 —— 失败时界面要如实说"停在第几步、没找到哪个字"，
 *   并把手动路径顶上，而不是假装成功；
 * - [Payer.route] 是**可编辑配置**：改一行文字就能适配新版，不用改代码逻辑。
 */
object A11yNav {

    /** 一步导航：走到某个入口，靠"点这几个字里的某一个"完成 */
    data class Step(
        /** 给人看的这一步在干什么，如「进入「设置」」 */
        val title: String,
        /** 候选文字，任一**精确命中**即点击（多写几个是为了兼容不同版本的说法） */
        val candidates: List<String>,
    )

    /**
     * 绝不允许点击的词（动作 / 状态变更）。
     *
     * ⚠️ 词表里有两条**故意**的取舍，理由都是「别把自己要点的入口也拦掉」：
     *
     * 1. **不含「支付」**：「支付设置」是必须点的导航行，写进去就永远翻不进去。
     * 2. **不写「扣款」，也不写 `扣款$`**（这里原来就有 `扣款$`，是个**真 bug**）：
     *    支付宝那一级的入口名就叫 **「免密支付/自动扣款」**，它正好以「扣款」结尾 ——
     *    `扣款$` 会把它判成危险词，于是路径走到最后一步被规则自己拦下，整条路走不完。
     *    危险的是「立即扣款」「确认扣款」里的**前缀**，那两个词已经在表里了；
     *    「扣款」本身只是名词。
     *
     * 👉 因此这份表有一条**人工约定**：**改词表前先跟 `A11yScanner.PAYERS` 的 route 对一遍**，
     *    看有没有哪个候选词命中这里。[[A11yNav.start]] 里也放了一条运行时自检来兜这件事。
     */
    private val FORBIDDEN = Regex(
        "(关闭|解约|取消|终止|删除|移除|立即|确认|开通|签约|同意|授权|转账|提现|退出|解除|撤销|修改|编辑|解绑|绑定|付款|续期)",
    )

    /** 一步最多滚几次去找入口 */
    private const val MAX_SCROLL_PER_STEP = 3

    /**
     * 找不到入口时先别收工：按这个预算等真页面出来（开屏广告、转场都要时间）。
     *
     * ⚠️ 只对「读得到字」的页面生效。**一个字都读不到的窗**（图片广告窗）不受它管：
     * 那种窗上没有任何可点的东西，等是唯一正确的动作，所以一路等到总超时（[TIMEOUT_MS]）
     * 才收工 —— 实测有的开屏广告不止 20 秒，20 秒收工会把"广告还没放完"误判成"没入口"。
     */
    private const val STEP_BUDGET_MS = 20_000L

    /**
     * 点完 / 滚完之后的**闭眼期**。
     *
     * ⚠️ 这个值不是"稳一点"的保守参数，它治的是一个**实测真冒出来的 bug**：
     * 点完「我的」之后，紧接着到达的那条事件描述的**还是上一步那个页面**（点击会让旧页面
     * 再发一条 CONTENT_CHANGED），而旧页面上当然没有「设置」—— 于是当场判成
     * 「在『进入设置』这一步没找到入口」收工，用户看到的是「点了一下就没动静了」。
     *
     * 所以：刚动作过的这段时间里，收到的窗口事件一律**不判断、不点击、也不放弃**；
     * 等闭眼期结束再重新看一次当时的窗口（[recheck]）。
     */
    private const val CLICK_GRACE_MS = 900L

    /** 整趟导航的总超时（含启动 App 的时间） */
    private const val TIMEOUT_MS = 75_000L

    private const val MAX_NODES = 4000
    private const val MAX_DEPTH = 60

    enum class Phase { IDLE, RUNNING, ARRIVED, FAILED }

    /** 给界面看的实时进度（不是 Compose state：服务在另一个进程时序里写，界面轮询读） */
    data class Live(val payerTitle: String, val index: Int, val total: Int, val stepTitle: String) {
        fun text(): String = if (index >= total) "最后一步：$stepTitle" else "第 ${index + 1}/$total 步：$stepTitle"
    }

    @Volatile
    var live: Live? = null
        private set

    @Volatile
    private var phase: Phase = Phase.IDLE

    private var payer: A11yScanner.Payer? = null
    private var steps: List<Step> = emptyList()
    private var index = 0
    private var scrolls = 0
    private var toastedStep = -1
    private var stepDeadline = 0L
    private var deadline = 0L

    /**
     * 最近一次看过的页面的样子（[pageTexts] 的结果）。给超时文案用：
     * 总超时收工时不带"当页长什么样"，用户（和我们）就只能猜是卡在哪。
     */
    @Volatile
    private var lastPageDesc = "没看到它的页面"

    /** 到位后等外面解析一次，再由 [afterParse] 收尾 */
    private var awaitingParse = false

    /** [CLICK_GRACE_MS] 的截止时刻（uptimeMillis） */
    private var graceUntil = 0L

    /** 服务实例。闭眼期结束后要主动重看一次当前窗口，所以得留着它（bind 时拿到） */
    @Volatile
    private var svc: AccessibilityService? = null

    private val handler = Handler(Looper.getMainLooper())
    private val watchdog = Runnable { finish(false, timeoutNote()) }

    /** 总超时收工的统一说法：光说"等太久"没法定位，把最后看到的页面带上 */
    private fun timeoutNote(): String =
        "等太久了，先停下；当页可见：$lastPageDesc。请照下面的路径自己点"

    /**
     * 闭眼期结束后的补看。**必须把外面那条流程照抄一遍**：
     * 正常情况下窗口事件是被 [A11yScannerService.onAccessibilityEvent] 送进来的，
     * 而我们在这里主动调 [onWindow] —— 一旦它返回 false（= 到位了 / 不管这页），
     * 也得跟服务一样接着去解析，否则"到位"那一下会没人接手。
     */
    private val recheck = Runnable {
        val s = svc ?: return@Runnable
        val rootIn = try { s.rootInActiveWindow } catch (e: Exception) { null } ?: return@Runnable
        val pkg = try { rootIn.packageName?.toString().orEmpty() } catch (e: Exception) { "" }
        if (pkg != payer?.pkg) return@Runnable
        val root = pick(pkg, rootIn)
        if (onWindow(s, pkg, root)) return@Runnable
        val n = A11yScanner.handleWindow(s, pkg, root)
        afterParse(s, n)
    }

    val running: Boolean get() = phase == Phase.RUNNING

    /* ── 对外 ── */

    /**
     * 用户点了「帮我翻进去」。
     *
     * @return null 表示已开始（或已结束）；非 null 是一句**给人看的原因**，界面直接显示。
     */
    /** 自检：服务是不是带着最新配置在跑。XML 改了但服务没重开时，系统用的还是旧 flags
     *  （图标类入口、广告窗后面的真页面全都读不到，而表现只是"找不到入口"，查起来没头绪） */
    private fun hasFreshConfig(): Boolean {
        val s = svc ?: return true
        return try {
            val am = s.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager ?: return true
            val enabled = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
            val self = enabled.firstOrNull { it.id?.startsWith(s.packageName + "/") == true } ?: return true
            val need = android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            (self.flags and need) == need
        } catch (e: Exception) { true }
    }

    fun start(ctx: Context, payer: A11yScanner.Payer): String? {
        if (payer.route.isEmpty()) return "「${payer.title}」还没配好自动路径，请照下面的路径自己点。"
        // 配置自检：任何一个候选词被 FORBIDDEN 拦住，这条路就是**走不通**的。
        // 与其翻到一半停下（用户看到的是"忽然不动了"），不如现在就说清楚。
        // 这条自检同时是"以后有人往 route 里加词"的护栏 —— 加了危险词或撞了词表，当场暴露。
        payer.route.flatMap { it.candidates }.firstOrNull { FORBIDDEN.containsMatchIn(it) }?.let {
            return "路径配置里的「$it」被安全规则拦住了，这条自动路走不通，请照下面的路径自己点。"
        }
        if (!A11yScanner.enabled(ctx)) return "需先开启「自动扣款读取」才能自动导航。"
        if (A11yScanner.connected == false) return "无障碍服务被系统断开了，去重开一下再试。"
        if (!SubScanner.isInstalled(ctx, payer.pkg)) return "这台机器上没装「${payer.title}」。"
        if (!hasFreshConfig()) return "无障碍服务还在用旧配置跑（更新后没重开过）。到系统设置把这个服务关一次再开，否则广告窗后面的真页面和图标类入口都读不到。"

        this.payer = payer
        steps = payer.route
        index = 0
        scrolls = 0
        lastPageDesc = "没看到它的页面"
        stepDeadline = SystemClock.uptimeMillis() + STEP_BUDGET_MS
        // 还没到位就开始点，等于没在清单页上乱点 —— 但这一步不会发生：下面是先开 App
        awaitingParse = false
        deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        graceUntil = 0L
        phase = Phase.RUNNING
        live = Live(payer.title, 0, steps.size, steps[0].title)
        handler.removeCallbacks(watchdog)
        handler.removeCallbacks(recheck)
        handler.postDelayed(watchdog, TIMEOUT_MS)
        save(ctx, "正在打开「${payer.title}」…", ok = null)

        if (!SubScanner.launchPackage(ctx, payer.pkg)) {
            finish(false, "打不开「${payer.title}」（没装或被系统限制）")
            return "打不开「${payer.title}」，请照下面的路径自己点。"
        }
        return null
    }

    fun cancel(ctx: Context) {
        if (phase == Phase.RUNNING) finish(false, "已取消")
    }

    /**
     * 服务把**已经挑好的窗口**（见 [pick]）递进来。返回 **true = 这一步我在处理**（外面就别去解析了），
     * false = 我没在管这一页（外面照常解析 —— 到位那一下正好走这条路）。
     */
    fun onWindow(ctx: Context, pkg: String, root: AccessibilityNodeInfo): Boolean {
        val p = payer ?: return false
        if (phase != Phase.RUNNING) return false
        if (pkg != p.pkg) return false          // 只在自己那家的窗口里动作
        if (SystemClock.uptimeMillis() > deadline) {
            finish(false, timeoutNote())
            return false
        }

        // ① 先认"到位没"——一旦认出清单页立刻停手，再点一下都可能点到"关闭服务"
        if (A11yScanner.looksLikeListPage(root)) {
            awaitingParse = true
            live = Live(p.title, steps.size, steps.size, "已到清单页")
            handler.removeCallbacks(watchdog)
            // 不置 ARRIVED：等外面真的解析完（afterParse）才算数
            return false
        }

        // ①.5 闭眼期（见 [CLICK_GRACE_MS]）。
        //     注意**放在到位判定之后**：闭眼期里万一下一页已经出来了，那是好消息，
        //     上面那一条会立刻接住；只有"还没换页"这种情况才需要这儿挡一下。
        //     返回 true = 这页我在处理，别让外面去解析半路上的页面。
        if (SystemClock.uptimeMillis() < graceUntil) return true

        val step = steps.getOrNull(index)
        if (step == null) {
            finish(false, "路径走完了，但没看到那张清单 —— 可能这家改版了")
            return false
        }
        if (index != toastedStep) {
            toastedStep = index
            toast(svc, "第 ${index + 1}/${steps.size} 步：${step.title}")
        }
        live = Live(p.title, index, steps.size, step.title)

        // ② 找这一步要点的那个字
        val hit = findExact(root, step.candidates)
        if (hit != null) {
            val label = step.candidates.firstOrNull { it == hit.text } ?: hit.text
            if (FORBIDDEN.containsMatchIn(label)) {
                // 配置里写了危险词 —— 宁可翻不进去也不点
                finish(false, "安全规则拦下了「$label」这一步，请照下面的路径自己点")
                return false
            }
            if (!click(ctx, hit.node)) {
                finish(false, "点不动「$label」（找不到可点的位置）")
                return false
            }
            index++
            scrolls = 0
            stepDeadline = SystemClock.uptimeMillis() + STEP_BUDGET_MS
            handler.removeCallbacks(watchdog)
            handler.postDelayed(watchdog, TIMEOUT_MS)
            live = Live(p.title, index, steps.size, steps.getOrNull(index)?.title ?: "等页面出来")
            armGrace()
            return true
        }

        // ③ 没找到 → 往下滚着找（有限次）
        if (scrolls < MAX_SCROLL_PER_STEP && scrollForward(root)) {
            scrolls++
            armGrace()
            return true
        }
        // ④ 还找不到也不马上收工。先看清这一页有没有字：
        //    **一个字都没有**（开屏广告那种图片窗）不受 20 秒步预算管 —— 这种窗上没有任何
        //    可点的东西，等是唯一正确的动作，一路等到总超时为止（实测有的广告不止 20 秒，
        //    20 秒收工会把"广告还没放完"误判成"没入口"）；读得到字的页面才按步预算收工。
        val textless = !hasAnyText(root)
        lastPageDesc = pageTexts(root)
        val budgetEnd = if (textless) deadline else stepDeadline
        if (SystemClock.uptimeMillis() < budgetEnd) { armGrace(); return true }
        finish(false, "在「${step.title}」这一步没找到入口；当页可见：${lastPageDesc}。请照下面的路径自己点")
        return false
    }

    /**
     * 进入闭眼期，并在结束时补看一次。
     *
     * 光有闭眼期不够：新页面的那条事件很可能**正好落在闭眼期里**被我们丢掉，
     * 之后再没有事件进来 —— 表现是「一直卡着直到 75 秒超时」。所以必须自己排一次补看。
     */
    private fun armGrace() {
        graceUntil = SystemClock.uptimeMillis() + CLICK_GRACE_MS
        handler.removeCallbacks(recheck)
        handler.postDelayed(recheck, CLICK_GRACE_MS + 150)
    }

    /** 外面解析完那一页之后收尾。n = **这一次**读到几条（不是线索池总数）。 */
    fun afterParse(ctx: Context, n: Int) {
        if (!awaitingParse) return
        val p = payer ?: return
        awaitingParse = false
        // 这一趟也算一次「扫描」：走到了清单页、并且把页面读了一遍。
        // 不记的话，守护页空态会在「只翻过代扣页、没跑过短信扫描」时显示「还没扫过」—— 那是假的。
        try { ButlerStore.get(ctx).markScanned() } catch (e: Exception) {}
        // ⚠️ 只用 n。线索池里可能早就躺着上一次读到的条目，拿它当"这次成功了"的证据是假的。
        if (n > 0) {
            finish(true, "读到了 $n 条签约")
        } else {
            finish(false, "翻到了那一页，但一条都没认出来（可能这家改版了）")
        }
        try {
            android.widget.Toast.makeText(
                ctx,
                if (phase == Phase.ARRIVED) "生活管家：已读到 ${p.title}的清单，回 App 核对"
                else "生活管家：那一页没读出条目，回 App 看详情",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        } catch (e: Exception) {
        }
    }

    /* ── 收尾 ── */

    private fun finish(ok: Boolean, note: String) {
        val p = payer
        phase = if (ok) Phase.ARRIVED else Phase.FAILED
        live = null
        awaitingParse = false
        payer = null
        steps = emptyList()
        graceUntil = 0L
        handler.removeCallbacks(watchdog)
        // 补看也要撤 —— 不收掉的话，收工之后那一次补看会拿着 payer=null 又跑一遍
        handler.removeCallbacks(recheck)
        if (p != null) storeNote(p, ok, note)
        // 进度与失败原来只写在自家界面里，而此刻屏幕在对方 App 手里 —— 用户看到的就是"没下文"。
        // 失败必须当场弹出来，这是唯一能穿过前台 App 的反馈通道。
        if (!ok) toast(svc, note)
        toastedStep = -1
        stepDeadline = 0L
    }

    /* ── 树操作 ── */

    private class Hit(val node: AccessibilityNodeInfo, val text: String)

    /** 精确命中：去空白后 text 或 contentDescription 与候选词**完全相等** */
    private fun findExact(root: AccessibilityNodeInfo, cands: List<String>): Hit? {
        val byNorm = cands.associateBy { norm(it) }
        var found: Hit? = null
        var budget = MAX_NODES

        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (found != null || budget <= 0 || depth > MAX_DEPTH) return
            budget--
            val cand = n.text?.toString()?.let { t -> byNorm[norm(t)] }
                ?: n.contentDescription?.toString()?.let { d -> byNorm[norm(d)] }
            if (cand != null) {
                found = Hit(clickableAncestor(n) ?: n, cand)
                return
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                walk(c, depth + 1)
                if (found != null) return
            }
        }
        walk(root, 0)
        return found
    }

    private fun toast(ctx: Context?, msg: String) {
        if (ctx == null) return
        try { android.widget.Toast.makeText(ctx, "生活管家：$msg", android.widget.Toast.LENGTH_SHORT).show() } catch (e: Exception) {}
    }

    /**
     * 挑一个值得看的窗口。开屏广告是一扇无文字的图片窗，还可能恰好就是 rootInActiveWindow
     * （实测微信走到这里 pageTexts 报"没读到文本"）—— 换成同包名下有内容的窗口。
     *
     * 必须在 [onWindow] **和** 解析（[A11yScanner.handleWindow]）之前都做这件事：
     * 只在导航里换窗、解析还拿原来的广告窗，会出现"认出了清单页、解析的却是广告"的错位。
     *
     * ⚠️ 挑选只在这一个函数里发生，且**只碰包名 == 传入包的窗口** —— getWindows() 会把
     * 别的 App 的窗口也列出来，但这里一个节点都不会去读它们，隐私口径不变。
     */
    fun pick(pkg: String, rootIn: AccessibilityNodeInfo): AccessibilityNodeInfo {
        if (hasAnyText(rootIn)) return rootIn
        val s = svc ?: return rootIn
        return try {
            s.windows.asSequence()
                .mapNotNull { w -> w.root }
                .firstOrNull { w -> w.packageName?.toString() == pkg && hasAnyText(w) }
                ?: rootIn
        } catch (e: Exception) { rootIn }
    }

    private fun hasAnyText(n: AccessibilityNodeInfo): Boolean {
        var budget = 200
        fun walk(x: AccessibilityNodeInfo, d: Int): Boolean {
            if (budget <= 0 || d > 15) return false
            budget--
            val t = x.text?.toString()
            val c = x.contentDescription?.toString()
            if (!t.isNullOrEmpty() || !c.isNullOrEmpty()) return true
            for (i in 0 until x.childCount) {
                val ch = x.getChild(i) ?: continue
                if (walk(ch, d + 1)) return true
            }
            return false
        }
        return walk(n, 0)
    }

    /** 失败诊断：摘出当页前几条短文本。有了它，"翻不进去"不用再猜是哪个词没匹配上 */
    private fun pageTexts(root: AccessibilityNodeInfo): String {
        val seen = LinkedHashSet<String>()
        var budget = 600
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (seen.size >= 8 || budget <= 0 || depth > 20) return
            budget--
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrEmpty() && t.length <= 10) seen.add(t)
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { walk(it, depth + 1) }
                if (seen.size >= 8) return
            }
        }
        walk(root, 0)
        if (seen.isEmpty()) return "没读到文本（扫过 ${600 - budget} 个节点，多半是图片广告窗）"
        val list = seen.joinToString("、")
        // 实测签名：一页读到的字**全是**底部标签栏（外加未读数这种纯数字角标），
        // 说明正文是自绘/图片渲染，读屏根本看不到入口 —— 这不是"再等等"能解决的，
        // 要在失败那一刻就说破，别让用户以为换个时机再试就行。
        val onlyTabs = seen.all { it in TAB_WORDS || it.matches(Regex("\\d+")) }
        return if (onlyTabs) "$list（只有底部标签栏读得到字，正文多半是画出来的，这条自动路走不通）" else list
    }

    /** 两家底部标签栏的文字，给「只剩底栏可读」的判定用 */
    private val TAB_WORDS = setOf(
        "首页", "理财", "视频", "消息", "我的", "生活", "我",
        "服务", "钱包", "通讯录", "发现", "微信", "看一看", "听一听",
    )

    private fun norm(s: String): String = s.trim().replace("\\s+".toRegex(), "").replace("\u00A0", "")

    /** 命中文字的往往是行内的 TextView，真正能点的在它上面那一层 */
    private fun clickableAncestor(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = n
        var hops = 0
        while (cur != null && hops < 6) {
            if (cur.isClickable && cur.isEnabled) return cur
            cur = cur.parent
            hops++
        }
        return null
    }

    /** 点。先语义化点击，不行再按坐标发手势（需要 canPerformGestures）。 */
    private fun click(ctx: Context, node: AccessibilityNodeInfo): Boolean {
        val target = clickableAncestor(node) ?: node
        try {
            if (target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        } catch (e: Exception) {
        }
        // 兜底：按节点（或其可点祖先）的中心发一次点按手势
        val r = android.graphics.Rect()
        try {
            (clickableAncestor(node) ?: node).getBoundsInScreen(r)
        } catch (e: Exception) {
            return false
        }
        if (r.width() <= 0 || r.height() <= 0) return false
        val svc = ctx as? AccessibilityService ?: return false
        return try {
            val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 40))
                .build()
            svc.dispatchGesture(g, null, null)
        } catch (e: Exception) {
            false
        }
    }

    /** 往下滚一屏（找当前页里第一个可滚的容器） */
    private fun scrollForward(root: AccessibilityNodeInfo): Boolean {
        var budget = MAX_NODES
        fun walk(n: AccessibilityNodeInfo, depth: Int): Boolean {
            if (budget <= 0 || depth > MAX_DEPTH) return false
            budget--
            try {
                if (n.isScrollable && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
            } catch (e: Exception) {
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                if (walk(c, depth + 1)) return true
            }
            return false
        }
        return walk(root, 0)
    }

    /* ── 结果留痕（用户回到 App 时能看到上一步发生了什么） ── */

    private const val PREFS = "lifebutler_scan"
    private const val KEY = "nav_last"

    private fun storeNote(p: A11yScanner.Payer, ok: Boolean, note: String) {
        try {
            val ctx = lastCtx ?: return
            val o = JSONObject().apply {
                put("payer", p.title)
                put("ok", ok)
                put("note", note)
                put("ts", System.currentTimeMillis())
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
        } catch (e: Exception) {
        }
    }

    /** 只有 service 那边会调；用来让 [storeNote] 拿到 Context */
    @Volatile
    private var lastCtx: Context? = null

    fun bind(ctx: Context) {
        lastCtx = ctx.applicationContext
        // 留着服务本身：闭眼期结束后的补看要主动读 rootInActiveWindow
        svc = ctx as? AccessibilityService
    }

    private fun save(ctx: Context, note: String, ok: Boolean?) {
        lastCtx = ctx.applicationContext
        try {
            val o = JSONObject().apply {
                put("payer", payer?.title ?: "")
                put("ok", ok ?: false)
                put("running", ok == null)
                put("note", note)
                put("ts", System.currentTimeMillis())
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
        } catch (e: Exception) {
        }
    }

    /** 上次「帮我翻进去」的结果，给界面显示。没有就返回空串。 */
    fun lastNote(ctx: Context): String = try {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return ""
        val o = JSONObject(s)
        val who = o.optString("payer")
        val ts = o.optLong("ts")
        if (who.isEmpty() || ts <= 0) "" else
            // ⚠️ ButlerStore 是 class(单例靠 companion 的 get(ctx)),不是 object ——
            // fmtCnAt 是实例方法,写成 ButlerStore.fmtCnAt(...) 编译不过。
            "$who · ${o.optString("note")} · " + ButlerStore.get(ctx).fmtCnAt(ts)
    } catch (e: Exception) {
        ""
    }

    /** 进程重启后残留的 RUNNING 状态清掉 */
    fun sweep(ctx: Context) {
        if (phase == Phase.RUNNING) return
        phase = Phase.IDLE
        live = null
    }
}
