package com.lifebutler.app.data

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * 一键扫描:在本机寻找「自动续费」线索。
 * 1) 扣费短信分析(需 READ_SMS 权限,本机处理、不上传)
 * 2) 通知使用权捕获的扣费通知(本机线索池)
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

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** 已安装的订阅类应用名(不含支付平台) */
    fun installedSubApps(context: Context): List<String> =
        SUBSCRIPTION_APPS.filter { isInstalled(context, it.first) }.map { it.second }

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

    private val STRONG_N = Regex("(自动续费|自动扣款|免密支付|代扣|已扣款|扣款|支出|付款|续费)")

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

    private fun extractMerchant(body: String): String? {
        Regex("(?:商户|收款方|商家|平台)[:：]?\\s*([^，。,；;！!\\s]{2,18})").find(body)?.let { return it.groupValues[1].trim() }
        Regex("[【\\[]([^】\\]]{2,18})[】\\]]").find(body)?.let { return it.groupValues[1].trim() }
        Regex("(爱奇艺|腾讯视频|网易云音乐|Keep|京东|美团|哔哩哔哩|优酷|QQ音乐|淘宝|饿了么|滴滴|WPS|百度网盘|喜马拉雅)").find(body)?.let { return it.groupValues[1] }
        return null
    }

    private fun parse(body: String, dateMs: Long): Candidate? {
        if (EXCLUDE.containsMatchIn(body)) return null
        val strong = STRONG.containsMatchIn(body)
        val weak = WEAK.containsMatchIn(body)
        if (!strong && !weak) return null
        val amt = Regex("(\\d+(?:\\.\\d{1,2})?)\\s*元").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        if (amt <= 0 || amt > 3000) return null
        val name = extractMerchant(body) ?: return null
        if (name.length < 2 || name.length > 18) return null
        if (!strong && !Regex("(商户|收款方|【)").containsMatchIn(body)) return null
        return Candidate(name, amt, dateMs, body.replace("\n", " ").take(70), "短信", parseDueDate(body))
    }

    /** 分析近 180 天收件箱;返回按商户去重的候选项(取最新一条) */
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
                val cutoff = System.currentTimeMillis() - 180L * 86400000L
                while (c.moveToNext() && n < 400) {
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

    /** 是否已开启「通知使用权」(设置 → 通知 → 通知使用权) */
    fun notificationsEnabled(context: Context): Boolean {
        return try {
            val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            flat.contains(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    private fun parseNotification(body: String, ts: Long): Candidate? {
        if (EXCLUDE.containsMatchIn(body)) return null
        if (!STRONG_N.containsMatchIn(body)) return null
        val amt = Regex("(\\d+(?:\\.\\d{1,2})?)\\s*元").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: Regex("[¥￥]\\s*(\\d+(?:\\.\\d{1,2})?)").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: return null
        if (amt <= 0 || amt > 3000) return null
        val name = extractMerchant(body) ?: return null
        if (name.length < 2 || name.length > 18) return null
        return Candidate(name, amt, ts, body.take(70), "通知", parseDueDate(body))
    }

    /** 服务回调:命中扣费关键词才留档(同名 3 天内去重,只保留最近 200 条) */
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
            arr.put(o)
            val cut = org.json.JSONArray()
            val from = if (arr.length() > 200) arr.length() - 200 else 0
            for (i in from until arr.length()) cut.put(arr.getJSONObject(i))
            prefs.edit().putString(NOTIF_KEY, cut.toString()).apply()
        } catch (e: Exception) {
        }
        // 实时同步:写真实扣费流水,并自动加入守护清单(去重;移除过的不再加回)
        try {
            val store = ButlerStore.get(context)
            val amt = cand.amount ?: 0.0
            if (amt > 0) store.addCharge(cand.name, amt, LocalDate.now().toString(), "通知")
            if (store.subs.none { it.name == cand.name } && !store.isDismissed(cand.name)) {
                store.addScannedSub(cand.name, amt, "通知", cand.nextDate)
                val amtText = if (amt > 0) "¥" + store.fmtMoney(amt) + " " else ""
                store.addChat(false, "刚收到一条「${cand.name}」的扣费通知（${amtText}），已自动放进守护清单；不是你的订阅就长按删掉。")
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

    /** 合并短信与通知线索(按商户去重,保留较新一条;来源合并标注;扣费日取非空的那个) */
    fun mergeCandidates(a: List<Candidate>, b: List<Candidate>): List<Candidate> {
        val map = LinkedHashMap<String, Candidate>()
        (a + b).forEach { c ->
            val old = map[c.name]
            if (old == null) {
                map[c.name] = c
            } else {
                val newer = if (c.dateMs > old.dateMs) c else old
                val src = if (old.source != c.source) "短信·通知" else old.source
                val due = if (newer.nextDate.isNotEmpty()) newer.nextDate
                else if (c.nextDate.isNotEmpty()) c.nextDate
                else old.nextDate
                map[c.name] = newer.copy(source = src, nextDate = due)
            }
        }
        return map.values.sortedByDescending { it.dateMs }
    }
}
