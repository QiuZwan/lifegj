package com.lifebutler.app.data

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一键扫描:在本机寻找「自动续费」线索。
 * 1) 扣费短信分析(需 READ_SMS 权限,本机处理、不上传)
 * 2) 通知使用权捕获的扣费 / 签约通知(本机线索池)
 * 3) 已安装的常见订阅类应用检查(通过 <queries> 白名单,无需全量应用权限)
 *
 * 只搬运本机真实存在的信息:解析不到扣费日就留空,不做任何推测。
 */
object SubScanner {

    data class Candidate(
        val name: String,
        val amount: Double?,
        val dateMs: Long,
        val snippet: String,
        val source: String = "短信",
        /** 从原文里解析出的真实下次扣费日(M-d);解析不到为空串 */
        val nextDate: String = "",
        /**
         * 「签约 / 开通」类线索 —— 用户刚和商户签了自动扣款协议，**当下并没有扣钱**。
         *
         * 这类通知常常一个金额都没有（签约不等于扣款），所以 [amount] 允许为空。
         * 界面据此显示「金额未知」而不是 ¥0.00；认领时也不会凭空写一笔扣费流水。
         */
        val signup: Boolean = false,
    )

    /** 常见订阅/支付类应用(包名 -> 展示名) */
    private val KNOWN_APPS = listOf(
        "com.eg.android.AlipayGphone" to "支付宝",
        "com.tencent.mm" to "微信",
        "com.qiyi.video" to "爱奇艺",
        "com.tencent.qqlive" to "腾讯视频",
        "com.netease.cloudmusic" to "网易云音乐",
        "com.gotokeep.keep" to "Keep",
        "com.jingdong.app.mall" to "京东",
        "com.sankuai.meituan" to "美团",
        "tv.danmaku.bili" to "哔哩哔哩",
        "com.youku.phone" to "优酷",
        "com.tencent.qqmusic" to "QQ音乐",
        "com.taobao.taobao" to "淘宝",
        "com.ximalaya.ting.android" to "喜马拉雅",
        "com.sdu.didi.psnger" to "滴滴出行",
    )

    private val SUBSCRIPTION_APPS = KNOWN_APPS.filter { it.second != "支付宝" && it.second != "微信" }

    /**
     * 包名 → 展示名；认不出来返回**空串**。
     *
     * 给线索行用（B9：线索有 `pkg` 字段却一直写死「来自通知」）。
     * ⚠️ 认不出来就返回空，让调用方回退成「来自通知」—— 绝不把 `com.xxx.yyy` 这种
     * 包名直接糊到用户脸上，那跟没说一样。
     */
    fun appNameOf(pkg: String): String =
        KNOWN_APPS.firstOrNull { it.first == pkg }?.second.orEmpty()

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** 已安装的订阅类应用名(不含支付平台) */
    fun installedSubApps(context: Context): List<String> =
        SUBSCRIPTION_APPS.filter { isInstalled(context, it.first) }.map { it.second }

    /**
     * 展示名 → 包名。用于扫描结果里那个「打开」按钮。
     *
     * 为什么不复用 [merchantPackage]：那个是按**订阅名**猜的、只覆盖一部分品牌，
     * 而这里的输入本来就来自 [KNOWN_APPS]，一对一查表更准（淘宝 / QQ音乐 这些它认不出）。
     */
    fun packageOf(displayName: String): String? =
        KNOWN_APPS.firstOrNull { it.second == displayName }?.first

    /** 支付平台安装情况 */
    fun installedPlatforms(context: Context): List<Pair<String, Boolean>> = listOf(
        "支付宝" to isInstalled(context, "com.eg.android.AlipayGphone"),
        "微信" to isInstalled(context, "com.tencent.mm"),
    )

    /** 按订阅名猜测对应商家 App 包名 */
    fun merchantPackage(name: String): String? = when {
        name.contains("爱奇艺") -> "com.qiyi.video"
        name.contains("腾讯视频") -> "com.tencent.qqlive"
        name.contains("网易云") -> "com.netease.cloudmusic"
        name.contains("keep", true) -> "com.gotokeep.keep"
        name.contains("京东") -> "com.jingdong.app.mall"
        name.contains("美团") -> "com.sankuai.meituan"
        name.contains("哔哩") -> "tv.danmaku.bili"
        name.contains("优酷") -> "com.youku.phone"
        name.contains("喜马拉雅") -> "com.ximalaya.ting.android"
        name.contains("滴滴") -> "com.sdu.didi.psnger"
        else -> null
    }

    /** 打开指定包名的应用;返回是否成功 */
    fun launchPackage(context: Context, pkg: String): Boolean = try {
        val i = context.packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            true
        } else false
    } catch (e: Exception) {
        false
    }

    fun fmtDate(ms: Long): String = SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(ms))

    fun fmtIso(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(ms))

    /* ── 关键词 ── */

    private val STRONG = Regex("(自动续费|自动扣款|连续包月|代扣|免密支付|签约成功|扣款成功)")
    private val WEAK = Regex("(扣款|扣费|续费成功)")
    private val EXCLUDE = Regex("(退款|退货|入账|转入|转账|工资|红包|验证码|登录)")

    /**
     * 通知里的**强**扣费词：本身就足以判定"这是一笔自动扣款"。
     * 「支出」「付款」原来被放在这里，但它们的覆盖面太宽 —— 微信里一句
     * "向某某付款 500 元"（不带"转账"二字）就能命中，于是守护清单凭空多出一个订阅。
     */
    private val STRONG_N = Regex("(自动续费|自动扣款|连续包月|免密支付|代扣|已扣款|扣款成功|续费成功)")

    /** 通知里的**弱**信号：单独出现不算数，必须同时有商户标记（商户/收款方/商家/【】）才认 */
    private val WEAK_N = Regex("(扣款|扣费|支出|付款|续费)")

    /** 文本里有没有明确的"商户"标记 —— 弱信号要不要采信，看它 */
    private val MERCHANT_MARK = Regex("(商户|收款方|商家|【)")

    /**
     * 通知里的「签约 / 开通」信号：用户刚和商户签了自动扣款协议。
     *
     * 为什么必须单独一类：**这类通知通常没有金额** —— 签约当下并不扣钱。
     * 而原来「必须有金额才留档」是硬门槛，于是「支付宝 · 签约成功通知」这种最该抓的一条
     * 被直接丢掉（实测：用户刚开通网易云音乐自动续费，扫描里一个字都没有）。
     *
     * 签约其实是"这是自动续费订阅"最强的证据：一次扣款可能只是付款，签约一定是长期授权。
     * 抓到之后仍然只进「待确认」，由用户点一下才落库。
     */
    private val SIGNUP_N =
        Regex("(签约成功|签约|签订.{0,8}协议|自动扣款协议|自动续费协议|免密支付协议|开通.{0,10}(自动续费|连续包月|自动扣款|免密支付))")

    /**
     * 已知品牌名。**排在通用的「商户:」规则前面** ——
     * 「发生消费时商户可自动从你账户扣款」这种句子会把通用规则带偏，抓出「可自动从你账户扣款」
     * 当商户名（实测就是这个）。先认品牌名，认不出再退回通用规则。
     */
    private val KNOWN_BRANDS = Regex(
        "(爱奇艺|腾讯视频|网易云音乐|网易云|QQ音乐|哔哩哔哩|哔哩|优酷|喜马拉雅|Keep|京东|美团|淘宝" +
            "|饿了么|滴滴|WPS|百度网盘|芒果TV|夸克|微博|知乎|得到|盒马|叮咚买菜|山姆|网易严选)",
    )

    /** 签约句式里的商户名：「在网易云音乐开通…」「开通了 Keep 连续包月」 */
    private val SIGNUP_NAME = listOf(
        Regex("在\\s*([^\\s，。,；;！!【】]{2,18}?)\\s*(?:开通|订购|续订|签约|签订|购买)"),
        Regex("(?:开通|订购|续订|签订|签约)了?\\s*([^\\s，。,；;！!【】]{2,18}?)\\s*(?:的)?(?:连续包月|自动续费|自动扣款|免密支付|会员|vip|VIP)"),
    )

    /** 抓到的"名字"其实是一句动作描述（「可自动从你账户扣款」）—— 不能用 */
    private val NOT_A_NAME = Regex("(可|将|会|无需|需|你|我|自动|扣款|扣费|免密|消费|账户|支付|成功|开通|签约|协议)")

    /** 手动补包名时的合法性：至少一个点、不含空格 */
    fun looksLikePackage(pkg: String): Boolean {
        val p = pkg.trim()
        return p.isNotEmpty() && p.contains('.') && !p.contains(' ')
    }

    /* ── 真实的下次扣费日解析 ── */

    private val MD = Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日")
    private val YMD = Regex("(20\\d{2})\\s*[-/年]\\s*(\\d{1,2})\\s*[-/月]\\s*(\\d{1,2})")
    private val DUE_KEY = Regex("扣|续费|代扣|收取|结算|到期|生效")

    /**
     * 只从原文里「读」扣费日:必须是 M月D日 / YYYY-MM-DD 这种明确写法,
     * 且前后 12 个字内出现扣费相关关键词,才认为它是下次扣费日。
     * 读不到就返回空串 —— 宁可留空,也不猜。
     */
    fun parseDueDate(body: String): String {
        for (m in MD.findAll(body)) {
            val s = (m.range.first - 12).coerceAtLeast(0)
            val e = (m.range.last + 12).coerceAtMost(body.length)
            if (DUE_KEY.containsMatchIn(body.substring(s, e))) {
                val mo = m.groupValues[1].toIntOrNull() ?: continue
                val d = m.groupValues[2].toIntOrNull() ?: continue
                if (mo in 1..12 && d in 1..31) return "$mo-$d"
            }
        }
        for (m in YMD.findAll(body)) {
            val s = (m.range.first - 12).coerceAtLeast(0)
            val e = (m.range.last + 12).coerceAtMost(body.length)
            if (DUE_KEY.containsMatchIn(body.substring(s, e))) {
                val mo = m.groupValues[2].toIntOrNull() ?: continue
                val d = m.groupValues[3].toIntOrNull() ?: continue
                if (mo in 1..12 && d in 1..31) return "$mo-$d"
            }
        }
        return ""
    }

    /* ── 短信分析 ── */

    /**
     * 从原文里挑出商户名。**顺序就是可信度**：越像"平台自己写明的商户"越靠前。
     *
     * 实测被这条坑过的一条真实通知（支付宝「签约成功通知」）：
     *   「账户130*****39在网易云音乐开通网易云音乐vip会员，发生消费时商户可自动从你账户扣款…」
     * 原来的顺序是「商户:」规则在前，于是抓出「可自动从你账户扣款」当商户名 —— 一句话被当成了店名。
     * 所以：先看签约句式（在 X 开通…），再看已知品牌，最后才用通用规则，并且过滤掉像动作的描述。
     */
    private fun extractMerchant(body: String): String? {
        // ① 签约/开通句式
        for (r in SIGNUP_NAME) {
            val n = r.find(body)?.groupValues?.get(1)?.trim() ?: continue
            if (n.length in 2..18 && !NOT_A_NAME.containsMatchIn(n)) return n
        }
        // ② 已知品牌名
        KNOWN_BRANDS.find(body)?.let { return it.groupValues[1] }
        // ③ 【商户名】这种显式标记
        Regex("[【\\[]([^】\\]]{2,18})[】\\]]").find(body)?.let { return it.groupValues[1].trim() }
        // ④ 通用规则（「商户：xxx」/「收款方 xxx」）—— 抓到动作描述就丢掉
        Regex("(?:商户|收款方|商家|平台|服务商)[:：]?\\s*([^，。,；;！!\\s]{2,18})").find(body)?.let {
            val n = it.groupValues[1].trim()
            if (n.length in 2..18 && !NOT_A_NAME.containsMatchIn(n)) return n
        }
        return null
    }

    /**
     * 短信解析。判据与 [parseNotification] **故意保持同一套**。
     *
     * ⚠️ 原来短信侧也要求「必须读出 X 元」（`amt ?: return null`），于是
     * 「您已与 XX 签订自动扣款协议」这类**没有金额的签约短信**被整条丢掉。
     * 这和通知侧那个 bug 是同一个病 —— 而短信恰恰是所有来源里**唯一能回头看历史**的一条
     * （通知只在监听服务连上之后才有，装 App 之前的历史根本收不到）。
     * 现在签约类同样允许没有金额，用 [Candidate.signup] 标出来。
     */
    private fun parse(body: String, dateMs: Long): Candidate? {
        if (EXCLUDE.containsMatchIn(body)) return null
        val signup = SIGNUP_N.containsMatchIn(body)
        val strong = STRONG.containsMatchIn(body)
        val weak = WEAK.containsMatchIn(body)
        if (!strong && !signup && !weak) return null
        val amt = Regex("(\\d+(?:\\.\\d{1,2})?)\\s*元").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
        // 金额是硬门槛，唯一的例外是「签约 / 开通」：签约当下不扣钱，原文里本来就没有金额。
        // 给它编一个 0 才是撒谎 —— 所以留空，界面如实写「金额未知」。
        if (!signup && amt == null) return null
        if (amt != null && (amt <= 0 || amt > 3000)) return null
        val name = extractMerchant(body) ?: return null
        if (name.length < 2 || name.length > 18) return null
        // 弱信号（扣费 / 续费成功）单独出现不采信：必须同时有明确的商户标记 —— 与通知侧一致。
        if (!strong && !signup && !MERCHANT_MARK.containsMatchIn(body)) return null
        return Candidate(
            name,
            amt,
            dateMs,
            body.replace("\n", " ").take(70),
            "短信",
            parseDueDate(body),
            signup && amt == null,
        )
    }

    /**
     * 扫描收件箱里的扣费 / 签约短信；按商户去重，取最新一条。
     *
     * 范围给到**近 365 天、最多 2000 条**：这是全 App 唯一能「回头看」的来源
     * （通知只在监听服务连上之后才有，装 App 之前的历史一条都收不到）。
     * 原来只回看 180 天，用户「上个月开的自动续费怎么没扫到」会被误会成功能坏了。
     */
    fun scanSms(context: Context): List<Candidate> {
        val out = HashMap<String, Candidate>()
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf("address", "body", "date"), null, null, "date desc",
            )
            var n = 0
            cursor?.use { c ->
                val idxBody = c.getColumnIndexOrThrow("body")
                val idxDate = c.getColumnIndexOrThrow("date")
                val cutoff = System.currentTimeMillis() - 365L * 86400000L
                while (c.moveToNext() && n < 2000) {
                    n++
                    val date = c.getLong(idxDate)
                    if (date < cutoff) continue
                    val body = c.getString(idxBody) ?: continue
                    val cand = parse(body, date) ?: continue
                    val old = out[cand.name]
                    if (old == null || cand.dateMs > old.dateMs) out[cand.name] = cand
                }
            }
        } catch (e: Exception) {
            // 权限不足或读取失败:静默返回已有结果
        }
        return out.values.sortedByDescending { it.dateMs }
    }

    /* ── 通知线索池(由 NotifListenerService 写入,本机保存) ── */

    private const val NOTIF_PREFS = "lifebutler_scan"
    private const val NOTIF_KEY = "notif_findings"

    /**
     * 系统设置里那条「通知使用权」授权还在不在。
     * ⚠️ 它**只代表授权还在**，不代表服务真的在收 —— 见 [notificationsEnabled]。
     */
    fun notificationAccessGranted(context: Context): Boolean {
        return try {
            val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            flat.contains(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    /** 监听服务当前的连接状态：null = 未知（进程刚起），true = 连着，false = 被系统断开 */
    fun notificationListenerConnected(): Boolean? = NotifListenerService.connected

    /**
     * 界面判断"到底在不在收扣费通知"：**授权在** 且 **服务没被断开**，两个条件都要满足。
     *
     * 为什么不能只看授权：系统有时会把通知监听服务断开（省电策略、异常重启），
     * 而设置里那条授权**仍然在**。原来只读授权 → App 内显示"已开启"，用户以为在收，
     * 实际一条都没进来；他会以为是"扫描不准"，而不是"根本没在工作"。
     */
    fun notificationsEnabled(context: Context): Boolean =
        notificationAccessGranted(context) && notificationListenerConnected() != false

    private fun parseNotification(body: String, ts: Long): Candidate? {
        if (EXCLUDE.containsMatchIn(body)) return null
        val signup = SIGNUP_N.containsMatchIn(body)
        val strong = STRONG_N.containsMatchIn(body)
        val weak = WEAK_N.containsMatchIn(body)
        if (!strong && !signup && !weak) return null
        // 弱信号（支出 / 付款 / 续费 …）单独出现不采信：必须同时有明确的商户标记，
        // 否则"向某某付款 500 元"这类无关通知会被当成一笔订阅。
        if (!strong && !signup && !MERCHANT_MARK.containsMatchIn(body)) return null
        val amt = Regex("(\\d+(?:\\.\\d{1,2})?)\\s*元").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: Regex("[¥￥]\\s*(\\d+(?:\\.\\d{1,2})?)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
        // 金额是硬门槛，只有一种情况可以没有：**签约 / 开通**。
        // 签约当下不扣钱，原文里本来就没有金额 —— 这时候编一个 0 才是撒谎。
        // 其余（扣款类）读不出金额说明这条通知我们没读懂，宁可不要（保持原来的行为）。
        if (!signup && amt == null) return null
        if (amt != null && (amt <= 0 || amt > 3000)) return null
        val name = extractMerchant(body) ?: return null
        if (name.length < 2 || name.length > 18) return null
        // signup 只在**这一条确实没读出金额**时才算数：带金额的签约（首月已扣 25 元）是一笔真扣款
        return Candidate(name, amt, ts, body.take(70), "通知", parseDueDate(body), signup && amt == null)
    }

    /**
     * 服务回调:命中关键词才留档(同名 3 天内去重,只保留最近 200 条)。
     * 「签约 / 开通」也算命中 —— 且它**允许没有金额**（签约当下不扣钱）。见 [SIGNUP_N]。
     */
    fun recordNotification(context: Context, pkg: String, title: String, text: String) {
        val body = (title + " " + text).replace("\n", " ").trim()
        if (body.isEmpty()) return
        val cand = parseNotification(body, System.currentTimeMillis()) ?: return
        try {
            val prefs = context.getSharedPreferences(NOTIF_PREFS, Context.MODE_PRIVATE)
            val arr = org.json.JSONArray(prefs.getString(NOTIF_KEY, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("name") == cand.name && cand.dateMs - o.optLong("ts") < 3L * 86400000L) return
            }
            val o = org.json.JSONObject()
            o.put("name", cand.name)
            o.put("amount", cand.amount ?: -1.0)
            o.put("ts", cand.dateMs)
            o.put("snippet", cand.snippet)
            o.put("pkg", pkg)
            o.put("nextDate", cand.nextDate)
            o.put("signup", cand.signup)
            arr.put(o)
            val cut = org.json.JSONArray()
            val from = if (arr.length() > 200) arr.length() - 200 else 0
            for (i in from until arr.length()) cut.put(arr.getJSONObject(i))
            prefs.edit().putString(NOTIF_KEY, cut.toString()).apply()
        } catch (e: Exception) {
        }
        // 不再直接落库：先记成一条「待认领线索」。
        // 命中的通知只说明"可能发生了扣费"，判不出"这是不是一笔订阅" —— 关键词里
        // 「付款」「支出」这类词太宽，让它们替用户认定，账目和守护清单很快就会被污染。
        // 用户在守护页点「认得」之后才写真实扣费 / 加进守护清单（见 ButlerStore.confirmClaim）。
        try {
            val store = ButlerStore.get(context)
            val amt = cand.amount ?: 0.0
            val added = store.addPendingClaim(cand.name, amt, cand.dateMs, cand.nextDate, pkg, cand.snippet)
            if (added) {
                // 签约类不能照着"扣费"说：钱还没扣。金额也不报 ¥0.00，就说"没写金额"。
                val msg = if (cand.signup) {
                    "刚收到一条「${cand.name}」的签约通知 —— 你开通了自动扣款，但这一笔还没扣钱，" +
                        "金额原文里没写，我不猜。先放进「待确认」了：你在守护页认一下是不是你的订阅，" +
                        "认了我才加进守护清单；不是就点「不是我的」。"
                } else {
                    val amtText = if (amt > 0) "¥" + store.fmtMoney(amt) + " " else ""
                    "刚收到一条「${cand.name}」的扣费通知（${amtText}）。我先放进「待确认」了 —— " +
                        "你在守护页认一下是不是你的订阅，认了我才记账；不是就点「不是我的」。"
                }
                store.addChat(false, msg)
            }
        } catch (e: Exception) {
        }
    }

    fun notificationFindings(context: Context): List<Candidate> {
        val out = mutableListOf<Candidate>()
        try {
            val prefs = context.getSharedPreferences(NOTIF_PREFS, Context.MODE_PRIVATE)
            val arr = org.json.JSONArray(prefs.getString(NOTIF_KEY, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val amt = o.optDouble("amount", -1.0)
                out.add(
                    Candidate(
                        o.optString("name"),
                        if (amt < 0) null else amt,
                        o.optLong("ts"),
                        o.optString("snippet"),
                        "通知",
                        o.optString("nextDate", ""),
                        o.optBoolean("signup", false),
                    ),
                )
            }
        } catch (e: Exception) {
        }
        return out.sortedByDescending { it.dateMs }
    }

    fun clearNotificationFindings(context: Context) {
        try {
            context.getSharedPreferences(NOTIF_PREFS, Context.MODE_PRIVATE).edit().remove(NOTIF_KEY).apply()
        } catch (e: Exception) {
        }
    }

    /**
     * 合并多条来源的线索：按商户去重，保留较新一条；来源合并标注；扣费日与金额都取"读得到的那个"。
     *
     * 现在有三条来源（短信 / 通知 / 代扣页），要能两两合并不丢信息 ——
     * 所以来源用 `+` 累加（`短信 + 代扣页·支付宝`），而不是只认固定的两个。
     */
    fun mergeCandidates(a: List<Candidate>, b: List<Candidate>): List<Candidate> {
        val map = LinkedHashMap<String, Candidate>()
        (a + b).forEach { c ->
            val old = map[c.name]
            if (old == null) {
                map[c.name] = c
            } else {
                val newer = if (c.dateMs > old.dateMs) c else old
                val src = if (old.source == c.source) old.source else old.source + " + " + c.source
                val due = if (newer.nextDate.isNotEmpty()) newer.nextDate
                else if (c.nextDate.isNotEmpty()) c.nextDate
                else old.nextDate
                // 金额取**读得到的那个**：短信说「25 元」、代扣页只说「已签约」，合并后不该丢掉那 25。
                // 于是「签约（没金额）」只有在两边都没金额时才成立 —— 有一条读到过真金额，它就不是纯签约。
                val amt = newer.amount ?: old.amount
                map[c.name] = newer.copy(
                    source = src,
                    nextDate = due,
                    amount = amt,
                    signup = newer.signup && amt == null,
                )
            }
        }
        return map.values.sortedByDescending { it.dateMs }
    }

    /**
     * **只给调试版用的**一条探针：把任意一条通知正文过一遍解析器，回一句人话。
     *
     * 为什么要它：用户报「某某通知没扫出来」时，光看代码猜不出是哪一关没过
     * （关键词？金额？商户名？银行 App 根本不在监听名单里？）。有了这条，
     * 拿他截图里的原话喂进来，一眼就能看到是"没命中"还是"命中了但名字抓错"。
     *
     * 纯函数，不写任何数据；release 包里没有任何入口会调它（见 MainActivity 的 notif64 深链）。
     */
    fun debugPreview(title: String, text: String): String {
        val body = (title + " " + text).replace("\n", " ").trim()
        if (body.isEmpty()) return "正文为空"
        val c = parseNotification(body, System.currentTimeMillis())
        if (c == null) {
            // 按**真实的判定顺序**报第一个没过的那一关。
            // 报一堆"可能的原因"等于什么都没报 —— 实测"向张三付款 500 元"会被报成"认不出商户名"，
            // 而它其实是卡在"只有弱信号、又没有商户标记"。这两件事的修法完全不同。
            val strong = STRONG_N.containsMatchIn(body)
            val signup = SIGNUP_N.containsMatchIn(body)
            val weak = WEAK_N.containsMatchIn(body)
            val amt = Regex("(\\d+(?:\\.\\d{1,2})?)\\s*元").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: Regex("[¥￥]\\s*(\\d+(?:\\.\\d{1,2})?)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            val why = when {
                EXCLUDE.containsMatchIn(body) -> "命中排除词（退款/转账/验证码/登录…）"
                !strong && !signup && !weak -> "没有扣费 / 签约关键词"
                !strong && !signup && !MERCHANT_MARK.containsMatchIn(body) ->
                    "只有弱信号（付款/支出/续费），又没有「商户·收款方·【】」标记"
                !signup && amt == null -> "既不是签约、又读不出金额"
                amt != null && (amt <= 0 || amt > 3000) -> "金额越界（<=0 或 >3000）"
                extractMerchant(body) == null -> "认不出商户名（也不在已知品牌里）"
                else -> "其他（商户名长度不合格）"
            }
            return "未命中：$why"
        }
        return "命中 商户=${c.name} 金额=${c.amount?.let { "¥$it" } ?: "留空"} " +
            "签约=${c.signup} 下次扣费=${c.nextDate.ifEmpty { "原文没写" }}"
    }
}
