package com.lifebutler.app.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 「代扣协议读取」—— 用无障碍读屏，把你**自己打开**的那张「免密支付 / 自动续费」清单读下来。
 *
 * ## 为什么是这几页，而不是"逐个 App 进去看"
 *
 * 国内 App **没有支付牌照**，它想每月自动从你账户扣钱，就必须在某一家「收单方」那里
 * 和你签一份代扣协议。所以你手机上所有自动续费，几乎都只签在下面这几家：
 *
 *   ① 支付宝（免密支付 / 自动扣款）  ② 微信支付（自动续费）
 *   ③ 苹果 App Store（Apple 账号）  ④ 华为 / 小米 / OPPO / vivo 应用商店
 *   ⑤ 运营商话费代扣                ⑥ 少数银行直连
 *
 * 也就是说：**不存在"爱奇艺自己的续费"这种独立的东西** —— 爱奇艺那笔续费本身
 * 就躺在支付宝或微信的清单里。读这两页，等于一次拿到绝大多数 App 的续费。
 * ③~⑥ 每家入口不一样、版式也杂，所以 [PAYERS] 做成**加一条配置就多一家**的结构，
 * 以后想加谁都不用改别的地方。
 *
 * ## 隐私底线（都在这里，别绕过）
 *
 * 1. **只接收白名单收单方的事件**：限定写在 `res/xml/lb_a11y_config.xml` 的 `packageNames` 里，
 *    系统层面就不会把别的 App 的事件递过来 —— 不是"我们不读"，是根本收不到。
 * 2. **页面特征不命中就一个字都不留**（[PAGE_MARK]）。微信一打开是聊天列表，
 *    用户在聊天里提到"自动续费"也不该被抓 —— 所以还要 [looksLikeList] 再挡一道。
 * 3. **跳过输入框**（EditText）：输密码、输账号的那些框一律不看。
 * 4. 只在本机解析、不上传；解析出来的每一条都要用户自己核对。
 */
object A11yScanner {

    /** 一家「收单方」的目标页。加一家 = 加一条，不用改别处。 */
    data class Payer(
        /** 存进线索池时用的来源标记 */
        val key: String,
        /** 展示名 */
        val title: String,
        /** 包名 */
        val pkg: String,
        /** 那一页叫什么（展示用） */
        val pageName: String,
        /** 手动怎么走过去（用户找不到时照着点） */
        val manualPath: String,
        /** 能直达就直达；多数 App 没开放，所以可能是 null */
        val deepLink: String? = null,
    )

    val PAYERS = listOf(
        Payer(
            key = "alipay", title = "支付宝", pkg = "com.eg.android.AlipayGphone",
            pageName = "免密支付 / 自动扣款",
            manualPath = "我的 → 设置 → 支付设置 → 免密支付/自动扣款",
        ),
        Payer(
            key = "wechat", title = "微信", pkg = "com.tencent.mm",
            pageName = "自动续费",
            manualPath = "我 → 服务 → 钱包 → 支付设置 → 自动续费",
        ),
    )

    /** 服务只监听这些包（必须和 `lb_a11y_config.xml` 的 `packageNames` 一致） */
    val WATCHED_PKGS: Set<String> = PAYERS.map { it.pkg }.toSet()

    fun payerOf(pkg: String): Payer? = PAYERS.firstOrNull { it.pkg == pkg }

    /* ── 页面判据 ── */

    /**
     * 命中这些词才认为"这可能就是那张清单"。
     *
     * 只用来认**整页**：光靠它不够 —— 微信里聊到"自动续费"四个字也会命中，
     * 所以还要再看这一页有没有真切出条目（[extract] + [looksLikeList]）。
     */
    private val PAGE_MARK = Regex("(免密支付|自动扣款|自动续费|扣费服务|委托代扣|代扣协议|签约管理|已签约)")

    /**
     * 一条**详情行**该有的样子：周期词或扣款日。
     *
     * ⚠️ 这里**故意不含**「免密支付 / 自动扣款 / 自动续费」这类**页面级**词。
     * 踩过一次：主题自带标题栏会渲染出 App 名（真机上是「支付宝」），它紧挨着页面标题
     * 「免密支付/自动扣款」—— 于是文本流配对把它们配成一条，凭空多出一个商户叫
     * 「支付宝」、没有金额的签约。页面级词只能用来看"这一页是不是清单"（[PAGE_MARK]），
     * 不能用来认"这一行是不是一条签约"。
     */
    private val CYCLE = Regex(
        "(每月|每年|每季|每周|每\\d+天|每\\d+个月|扣款日|扣款时间|下次扣款|下次扣费|下次续费" +
            "|已签约|签约成功|扣费方式|连续包月|连续包年)",
    )

    private val AMT = Regex("(?:[¥￥]\\s*(\\d+(?:\\.\\d{1,2})?))|(?:(\\d+(?:\\.\\d{1,2})?)\\s*元)")

    /** 明显不是商户名的行 */
    private val NAME_BAD = Regex(
        "(已签约|未签约|添加|管理|全部|更多|免密支付|自动扣款|自动续费|扣费服务|委托代扣|签约管理" +
            "|我的|设置|返回|首页|确定|取消|下一步|关闭服务|解除|解约|帮助|客服|说明|详情|查看|展开)",
    )

    /** 一条线索 */
    data class Row(val name: String, val detail: String, val amount: Double?, val nextDate: String)

    /* ── 解析器 ── */

    private fun amountOf(s: String): Double? {
        val m = AMT.find(s) ?: return null
        val raw = m.groupValues[1].ifEmpty { m.groupValues[2] }
        val v = raw.toDoubleOrNull() ?: return null
        return if (v > 0 && v <= 3000) v else null
    }

    /** 像不像"商户名"这一行 */
    private fun isName(t: String): Boolean {
        val s = t.trim()
        if (s.length !in 2..20) return false
        if (NAME_BAD.containsMatchIn(s)) return false
        // 含金额 / 纯数字 / 含日期 的都不是名字
        if (AMT.containsMatchIn(s)) return false
        if (Regex("^[\\d\\s.·:：/%-]+$").containsMatchIn(s)) return false
        if (Regex("\\d{4}[-/年]\\d{1,2}").containsMatchIn(s)) return false
        // 冒号结尾的多半是小标题
        if (s.endsWith("：") || s.endsWith(":")) return false
        return true
    }

    /**
     * 从一页里切出条目。
     *
     * 两条路，优先走结构：
     * ① [groups] —— 无障碍树里「一个可点容器 = 一个列表项」，它的后代文本天然就是一组。
     *    这是最准的，但有的版式整页只有一个大容器，切不出来。
     * ② 退化成**文本流配对**：名称行 + 紧邻的详情行（含金额或周期词）。
     *    支付宝那页实测就是「网易云音乐」/「每月 ¥15.00 · 下次扣款 …」两行一组。
     *
     * 两条都拿不到东西就**返回空**，由上层如实说「这一页没读到能认的条目」——
     * 宁可说没读到，也不要拿页面上随便几行文字硬凑成订阅。
     */
    fun extract(texts: List<String>, groups: List<List<String>>): List<Row> {
        val out = LinkedHashMap<String, Row>()

        // ① 按可点条目分组
        groups.forEach { g ->
            val name = g.firstOrNull { isName(it) } ?: return@forEach
            val detail = g.filter { it != name }.joinToString(" · ").trim()
            if (detail.isEmpty()) return@forEach
            if (!CYCLE.containsMatchIn(detail) && amountOf(detail) == null) return@forEach
            val r = Row(name.trim(), detail, amountOf(detail), SubScanner.parseDueDate(detail))
            out.putIfAbsent(r.name, r)
        }

        // ② 文本流配对（结构切不出来时才需要，但两路都跑一遍也无害：按名字去重）
        var i = 0
        while (i < texts.size) {
            val t = texts[i].trim()
            if (isName(t)) {
                val d = texts.getOrNull(i + 1)?.trim().orEmpty()
                if (CYCLE.containsMatchIn(d) || amountOf(d) != null) {
                    out.putIfAbsent(t, Row(t, d, amountOf(d), SubScanner.parseDueDate(d)))
                    i += 2
                    continue
                }
            }
            i++
        }
        return out.values.toList()
    }

    /** 这一页像不像"一张带金额/周期的清单"。不像就整页丢弃。 */
    private fun looksLikeList(rows: List<Row>): Boolean = rows.isNotEmpty()

    /* ── 从无障碍树取文本 ── */

    private const val MAX_NODES = 1500
    private const val MAX_DEPTH = 40

    private fun isEditable(n: AccessibilityNodeInfo): Boolean =
        n.className?.toString()?.contains("EditText") == true

    /** 整页文本流（跳过输入框） */
    private fun collectTexts(n: AccessibilityNodeInfo, out: MutableList<String>, budget: IntArray, depth: Int) {
        if (budget[0] <= 0 || depth > MAX_DEPTH) return
        budget[0]--
        if (!isEditable(n)) {
            n.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            collectTexts(c, out, budget, depth + 1)
        }
    }

    /**
     * 「可点容器」的文本组。只收**最靠上**的那一层可点节点：
     * 一个列表项通常整体可点，它的子节点也可点 —— 不收父就会把一条拆成好几条。
     */
    private fun collectGroups(
        n: AccessibilityNodeInfo,
        out: MutableList<List<String>>,
        budget: IntArray,
        depth: Int,
    ) {
        if (budget[0] <= 0 || depth > MAX_DEPTH) return
        budget[0]--
        val clickableHere = n.isClickable && n.childCount > 0
        val self = n.text?.toString()?.trim()
        val parts = mutableListOf<String>()
        if (clickableHere) {
            // 这一层就是条目：把它的后代文本全收进来，且**不再往下找条目**
            val texts = mutableListOf<String>()
            val b2 = intArrayOf(budget[0])
            collectTexts(n, texts, b2, 0)
            budget[0] = b2[0]
            if (texts.size >= 2) {
                out.add(texts)
                return
            }
        }
        if (!self.isNullOrEmpty() && !isEditable(n)) parts.add(self)
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            collectGroups(c, out, budget, depth + 1)
        }
    }

    /* ── 连接状态 ── */

    /** 服务是否已连上（和通知监听那边同一套：授权还在 ≠ 服务在跑） */
    @Volatile
    var connected: Boolean? = null

    /**
     * 无障碍服务在系统里开着没。
     * [AccessibilityManager] 在个别 ROM 上会漏报，所以再直接读一次 Settings 兜底。
     */
    fun enabled(ctx: Context): Boolean {
        return try {
            val expected = ComponentName(ctx, A11yScannerService::class.java)
            val flat = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            flat.split(':').any { it.equals(expected.flattenToString(), true) || it.equals(expected.flattenToShortString(), true) }
        } catch (e: Exception) {
            false
        }
    }

    /** 真的在跑：开着的 且 服务没被系统断开 */
    fun active(ctx: Context): Boolean = enabled(ctx) && connected != false

    /* ── 发现池（和通知那条共用 `lifebutler_scan` 这份 prefs，key 分开） ── */

    private const val PREFS = "lifebutler_scan"
    private const val KEY = "a11y_findings"
    private const val MAX_KEEP = 200

    /** 上一个被处理过的页面指纹：同一页反复触发事件时不要重复解析 */
    @Volatile
    private var lastPageSig: String = ""
    @Volatile
    private var lastAtMs: Long = 0L

    /**
     * 服务把窗口交给这里。返回这次新记下的条数（0 = 页面不像清单 / 没读到 / 已处理过）。
     */
    fun handleWindow(ctx: Context, pkg: String, root: AccessibilityNodeInfo): Int {
        val payer = payerOf(pkg) ?: return 0
        // ⚠️ 节流**要在走树之前**：滚动/刷新一秒钟能触发几十次事件，而走一遍无障碍树
        // （最多 1500 个节点）比判断"是不是同一页"贵得多。原来把 lastAtMs 放在解析成功
        // 之后才更新，等于"页面不是我想要的"时完全不节流 —— 微信聊天列表那种场景会白烧 CPU。
        val now = SystemClock.uptimeMillis()
        if (now - lastAtMs < 600) return 0
        lastAtMs = now

        val texts = mutableListOf<String>()
        collectTexts(root, texts, intArrayOf(MAX_NODES), 0)
        if (texts.isEmpty()) return 0

        val pageBlob = texts.joinToString(" ")
        if (!PAGE_MARK.containsMatchIn(pageBlob)) return 0

        val groups = mutableListOf<List<String>>()
        collectGroups(root, groups, intArrayOf(MAX_NODES), 0)

        val rows = extract(texts, groups)
        if (!looksLikeList(rows)) return 0

        // 同一页（同一批名字）就没必要反复解析
        val sig = payer.key + "|" + rows.joinToString(",") { it.name }
        if (sig == lastPageSig) return 0
        lastPageSig = sig

        return record(ctx, payer, rows)
    }

    /** 写进线索池，按名字 3 天内去重。返回新写入的条数。 */
    private fun record(ctx: Context, payer: Payer, rows: List<Row>): Int {
        var added = 0
        try {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arr = org.json.JSONArray(prefs.getString(KEY, "[]"))
            val ts = System.currentTimeMillis()
            rows.forEach { r ->
                var dup = false
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("name") == r.name && ts - o.optLong("ts") < 3L * 86400000L) dup = true
                }
                if (dup) return@forEach
                arr.put(
                    org.json.JSONObject().apply {
                        put("name", r.name)
                        put("amount", r.amount ?: -1.0)
                        put("ts", ts)
                        put("detail", r.detail)
                        put("nextDate", r.nextDate)
                        put("payer", payer.title)
                        put("signup", r.amount == null)
                    },
                )
                added++
            }
            if (added > 0) {
                val cut = org.json.JSONArray()
                val from = if (arr.length() > MAX_KEEP) arr.length() - MAX_KEEP else 0
                for (i in from until arr.length()) cut.put(arr.getJSONObject(i))
                prefs.edit().putString(KEY, cut.toString()).apply()
            }
        } catch (e: Exception) {
        }
        if (added > 0) {
            try {
                val store = ButlerStore.get(ctx)
                val names = rows.joinToString("、") { it.name }
                store.addChat(
                    false,
                    "刚从${payer.title}的「${payer.pageName}」页读到 ${rows.size} 条签约：$names。\n" +
                        "这些是你自己打开那一页时我看到的，只记在本机。回到「守护」页核对一下 —— " +
                        "认了才进清单，不是你的点「不是我的」。",
                )
            } catch (e: Exception) {
            }
        }
        return added
    }

    /** 读回线索池 */
    fun findings(ctx: Context): List<SubScanner.Candidate> {
        val out = mutableListOf<SubScanner.Candidate>()
        try {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arr = org.json.JSONArray(prefs.getString(KEY, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val amt = o.optDouble("amount", -1.0)
                val payer = o.optString("payer", "")
                out.add(
                    SubScanner.Candidate(
                        name = o.optString("name"),
                        amount = if (amt < 0) null else amt,
                        dateMs = o.optLong("ts"),
                        snippet = o.optString("detail"),
                        source = if (payer.isEmpty()) "代扣页" else "代扣页·$payer",
                        nextDate = o.optString("nextDate", ""),
                        signup = o.optBoolean("signup", amt < 0),
                    ),
                )
            }
        } catch (e: Exception) {
        }
        return out.sortedByDescending { it.dateMs }
    }

    fun clear(ctx: Context) {
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
            lastPageSig = ""
        } catch (e: Exception) {
        }
    }

    /** 打开设置里的无障碍页（新版本系统直接跳到本服务那一项） */
    fun openSettings(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
        }
    }
}

/**
 * 无障碍服务本体。纪律：**只处理白名单收单方的窗口**，其他包的事件根本收不到
 * （`packageNames` 写在 `lb_a11y_config.xml` 里，由系统过滤）。
 */
class A11yScannerService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        A11yScanner.connected = true
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 400
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in A11yScanner.WATCHED_PKGS) return
        // 滚动/刷新会密集触发，节流到 ~0.6s 一次；真正去重靠 handleWindow 里的页面指纹
        val root = rootInActiveWindow ?: return
        try {
            A11yScanner.handleWindow(this, pkg, root)
        } catch (e: Exception) {
            // 解析失败不影响系统；绝不让异常冒出去
        }
    }

    override fun onInterrupt() {
        A11yScanner.connected = false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        A11yScanner.connected = false
        return super.onUnbind(intent)
    }
}
