package com.lifebutler.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/* ───────────────────── AI 管家配置 ───────────────────── */

/** 一个可一键填好的接口预设(都走 OpenAI 兼容的 /chat/completions) */
data class AiPreset(val label: String, val base: String, val model: String)

val AI_PRESETS: List<AiPreset> = listOf(
    AiPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    AiPreset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
    AiPreset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
    AiPreset("月之暗面", "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
    AiPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
)

/**
 * AI 管家配置:接口地址、API Key、模型名,只写在本机 SharedPreferences 里。
 *
 * 诚信约定:
 * - Key 只存在本机,不进备份文本、不进任何第三方;请求只发往用户自己填的那个地址。
 * - 没配置就是没配置,界面会明说「当前是离线规则模式」,不会假装 AI 已经接上。
 */
object AiConfig {

    private const val PREF = "lifebutler_ai"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun base(ctx: Context): String = sp(ctx).getString("base", "").orEmpty()
    fun key(ctx: Context): String = sp(ctx).getString("key", "").orEmpty()
    fun model(ctx: Context): String = sp(ctx).getString("model", "").orEmpty()

    /** 三项齐全才算接上;缺一项都退回离线规则模式 */
    fun isReady(ctx: Context): Boolean =
        base(ctx).isNotBlank() && key(ctx).isNotBlank() && model(ctx).isNotBlank()

    fun save(ctx: Context, base: String, key: String, model: String) {
        sp(ctx).edit()
            .putString("base", base.trim().trimEnd('/'))
            .putString("key", key.trim())
            .putString("model", model.trim())
            .apply()
    }

    fun clear(ctx: Context) {
        sp(ctx).edit().clear().apply()
    }

    /** 界面上只显示掩码,不把 Key 原样铺在屏幕上 */
    fun mask(key: String): String = when {
        key.isBlank() -> ""
        key.length <= 10 -> "••••••"
        else -> key.take(5) + "••••••" + key.takeLast(4)
    }
}

/* ───────────────────── AI 管家 ───────────────────── */

/**
 * 智能管家:把用户的一句话交给模型,模型回一个 JSON(要说的话 + 要执行的动作),
 * 动作由本机真正写进 ButlerStore,查询则由本机数据快照回答。
 *
 * 为什么用「JSON 动作协议」而不是各家不同的 function calling:
 * 只要对方兼容 /chat/completions 就能用,不挑模型;一次请求就能既回答又落库。
 */
object AiButler {

    data class Reply(
        val text: String,
        val actions: List<String> = emptyList(),
        val failed: Boolean = false,
    )

    private const val TIMEOUT_CONNECT = 20_000
    private const val TIMEOUT_READ = 60_000

    /** 问一句:返回要说的话 + 真正执行了哪些写入 */
    suspend fun ask(
        ctx: Context,
        store: ButlerStore,
        userText: String,
        history: List<Pair<Boolean, String>> = emptyList(),
    ): Reply {
        val base = AiConfig.base(ctx)
        val key = AiConfig.key(ctx)
        val model = AiConfig.model(ctx)
        if (base.isBlank() || key.isBlank() || model.isBlank()) {
            return Reply(offlineText(store, userText), failed = true)
        }
        return try {
            val body = buildBody(model, systemPrompt(store), history, userText)
            val raw = postJson("$base/chat/completions", key, body)
            val content = JSONObject(raw)
                .optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content").orEmpty()

            val obj = extractJson(content)
            if (obj == null) {
                // 没有按约定给 JSON:把这句原样当回答用,但不擅自执行任何写入
                Reply(content.trim().ifBlank { "模型这次没返回内容，再说一次试试。" })
            } else {
                val reply = obj.optString("reply").trim().ifBlank { "记下了。" }
                val done = ArrayList<String>()
                obj.optJSONArray("actions")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { a -> applyAction(store, a)?.let { done.add(it) } }
                    }
                }
                Reply(reply, done)
            }
        } catch (e: Exception) {
            Reply(offlineText(store, userText, e.message), failed = true)
        }
    }

    /** 测试连接:发一句最短的问候,能拿到回复就算通 */
    suspend fun ping(base: String, key: String, model: String): String? {
        val b = base.trim().trimEnd('/')
        if (b.isBlank() || key.isBlank() || model.isBlank()) return "三项都要填"
        return try {
            val msgs = JSONArray().put(JSONObject().put("role", "user").put("content", "回复两个字：正常"))
            val body = JSONObject()
                .put("model", model.trim())
                .put("messages", msgs)
                .put("max_tokens", 16)
                .put("stream", false)
                .toString()
            val raw = postJson("$b/chat/completions", key.trim(), body)
            val content = JSONObject(raw)
                .optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content").orEmpty()
            if (content.isBlank()) "通了，但模型没返回内容" else null
        } catch (e: Exception) {
            e.message?.take(140) ?: "连接失败"
        }
    }

    /* ── 离线兜底:没配置 / 连不上时,规则引擎照样能记事记账 ── */

    private fun offlineText(store: ButlerStore, userText: String, reason: String? = null): String {
        val local = store.reply(userText)
        val head = if (reason == null) {
            "还没接入 AI，现在是离线规则模式（去「我的 → AI 智能管家」填一个接口就能用自然语言加东西）。"
        } else {
            "这次没连上模型（${reason.take(60)}）。"
        }
        return if (local.contains("这句我还没听懂")) {
            "$head\n\n这句离线规则也没认出来，换个说法，比如「记一下：明天交房租」或「记账：午饭 25」。"
        } else {
            "$head\n\n先用离线规则处理了：$local"
        }
    }

    /* ── 真正落库 ── */

    /**
     * 执行模型给出的一个动作。
     * 返回一句「做了什么」的人类可读描述;返回 null 表示这个动作不合法或无事可做。
     */
    private fun applyAction(store: ButlerStore, a: JSONObject): String? = when (a.optString("type")) {
        "add_task" -> {
            val text = a.optString("text").trim()
            if (text.isEmpty()) null
            else {
                store.addTask(text, a.optString("meta").trim().ifBlank { "来自智能管家" })
                "待办「$text」"
            }
        }

        "add_expense" -> {
            val amt = a.optDouble("amount", 0.0)
            if (amt <= 0.0 || amt.isNaN()) null
            else {
                val note = a.optString("note").trim()
                val cat = a.optString("category").trim().ifBlank { store.guessExpenseCategory(note) }
                store.addExpense(amt, cat, note)
                "记账 $cat ¥${store.fmtMoney(amt)}"
            }
        }

        "add_subscription" -> {
            val name = a.optString("name").trim()
            val amt = a.optDouble("amount", 0.0)
            if (name.isEmpty() || amt <= 0.0 || amt.isNaN()) null
            else {
                store.addSub(name, amt, a.optString("next_date").trim(), "智能管家")
                "订阅「$name」¥${store.fmtMoney(amt)}/月"
            }
        }

        "add_charge" -> {
            val name = a.optString("name").trim()
            val amt = a.optDouble("amount", 0.0)
            if (name.isEmpty() || amt <= 0.0 || amt.isNaN()) null
            else {
                val d = a.optString("date").trim().ifBlank { LocalDate.now().toString() }
                store.addCharge(name, amt, d, "智能管家")
                "扣费流水「$name」¥${store.fmtMoney(amt)}"
            }
        }

        "close_subscription" -> {
            val name = a.optString("name").trim()
            val hit = store.subs.filter { !it.closing }
                .firstOrNull { name.isNotEmpty() && (it.name.contains(name) || name.contains(it.name)) }
            if (hit == null) {
                if (name.isNotEmpty()) "没找到「$name」，未改动" else null
            } else {
                store.markSubClosing(hit.id, withReceipt = false)
                "把「${hit.name}」标记为关闭中"
            }
        }

        "add_obligation" -> {
            val title = a.optString("title").trim()
            if (title.isEmpty()) null
            else {
                store.addObligation(
                    title,
                    a.optString("date").trim(),
                    a.optString("note").trim(),
                    a.optString("tag").trim().ifBlank { "证件" },
                )
                "义务「$title」"
            }
        }

        "add_member" -> {
            val name = a.optString("name").trim()
            if (name.isEmpty()) null
            else {
                store.addMember(name, a.optString("label").trim().ifBlank { "提醒" }, a.optString("date").trim())
                "家人「$name」"
            }
        }

        "add_key_date" -> {
            val title = a.optString("title").trim()
            if (title.isEmpty()) null
            else {
                store.addKeyDate(title, a.optString("date").trim(), a.optString("note").trim())
                "日子「$title」"
            }
        }

        "add_archive" -> {
            val title = a.optString("title").trim()
            if (title.isEmpty()) null
            else {
                store.addArchive(title, a.optString("note").trim())
                "档案组「$title」"
            }
        }

        else -> null
    }

    /* ── 提示词 ── */

    private fun systemPrompt(store: ButlerStore): String = """
你是「生活管家」手机应用里的智能管家。你能读写用户存在本机的数据。

【今天】${LocalDate.now()}

【本机数据快照】
${snapshot(store)}

【怎么做事】
1. 用户说一件事，你就把它真正记进对应的地方（待办 / 记账 / 订阅 / 义务 / 家人 / 关键日期 / 档案组）。
2. 用户问你问题时，只依据上面的快照回答。快照里没有的，直说本机没有这条记录，绝不猜测、绝不编造数字和日期。
3. 你不能替用户打电话、发短信、联系客服，也不能真的取消第三方订阅。遇到这类请求要如实说做不到，并帮他把这件事记成待办。
4. 金额一律用 ¥。

【输出格式】只输出一个 JSON 对象，不要写解释，不要用代码块包裹：
{"reply":"对用户说的话","actions":[]}

reply：中文、口语化、最多 3 句，不要用星号井号或列表符号。
actions：没有要执行的就给空数组；有就按下面的格式，一次可以给多个。

{"type":"add_task","text":"要做的事","meta":"补充说明，可空"}
{"type":"add_expense","amount":25,"category":"餐饮","note":"午饭"}
{"type":"add_subscription","name":"网易云音乐","amount":15,"next_date":"2026-10-05"}
{"type":"add_charge","name":"网易云音乐","amount":15,"date":"2026-09-05"}
{"type":"close_subscription","name":"爱奇艺"}
{"type":"add_obligation","title":"车险续保","date":"2026-11-01","note":"可先比价","tag":"车辆"}
{"type":"add_member","name":"妈妈","label":"复诊","date":"2026-09-24"}
{"type":"add_key_date","title":"爸妈结婚纪念日","date":"10-22","note":""}
{"type":"add_archive","title":"保单","note":"续保可提前比价"}

日期规则：能确定年份就写 yyyy-MM-dd；只说了月日（生日、纪念日这类）就写 MM-DD，系统会按最近的将来算。
记账 category 只能是：餐饮 / 交通 / 购物 / 居家 / 娱乐 / 医疗 / 人情 / 其他。
""".trim()

    /** 本机数据快照:只放模型回答问题时真正需要的东西,并做长度上限 */
    private fun snapshot(store: ButlerStore): String {
        val sb = StringBuilder()
        val today = LocalDate.now()

        val pending = store.tasks.filter { !it.done }
        sb.append("待办（未完成 ").append(pending.size).append(" 条）")
        if (pending.isEmpty()) sb.append("：无\n")
        else sb.append("：").append(pending.take(20).joinToString("；") { it.text }).append('\n')

        val active = store.subs.filter { !it.closing }
        sb.append("订阅（进行中 ").append(active.size).append(" 笔，每月合计 ¥")
            .append(store.fmtMoney(active.sumOf { it.amount })).append("）")
        if (active.isEmpty()) sb.append("：无")
        else sb.append("：").append(
            active.take(20).joinToString("；") { s ->
                "${s.name} ¥${store.fmtMoney(s.amount)}" +
                    (if (s.nextDate.isBlank()) "（扣费日未填）" else "（下次 ${s.nextDate}）")
            },
        )
        sb.append('\n')
        sb.append("已标记关闭 ").append(store.closedCount).append(" 笔，每月少支出 ¥")
            .append(store.fmtMoney(store.monthlySaved)).append('\n')

        val open = store.obligations.filter { !it.done }
        sb.append("到期事务（未完成 ").append(open.size).append(" 条）")
        if (open.isEmpty()) sb.append("：无\n")
        else sb.append("：").append(
            open.take(15).joinToString("；") { "${it.title}(${it.date.ifBlank { "无日期" }}·${it.tag})" },
        ).append('\n')

        sb.append("家人")
        if (store.members.isEmpty()) sb.append("：无\n")
        else sb.append("：").append(
            store.members.take(15).joinToString("；") { "${it.name}·${it.label}·${it.date.ifBlank { "无日期" }}" },
        ).append('\n')

        sb.append("关键日期")
        if (store.keyDates.isEmpty()) sb.append("：无\n")
        else sb.append("：").append(
            store.keyDates.take(15).joinToString("；") { "${it.title}·${it.date}" },
        ).append('\n')

        sb.append("档案组")
        if (store.archive.isEmpty()) sb.append("：无\n")
        else sb.append("：").append(
            store.archive.take(15).joinToString("；") { "${it.title}(${it.files.size} 个文件)" },
        ).append('\n')

        sb.append("家庭相册：").append(store.album.size).append(" 张照片（照片本身不参与问答）\n")

        val todayList = store.expenses.filter { it.date == today.toString() }
        sb.append("今天记账：").append(todayList.size).append(" 笔，合计 ¥")
            .append(store.fmtMoney(todayList.sumOf { it.amount })).append('\n')

        val monthList = store.expensesInMonth(today.year, today.monthValue)
        sb.append("本月记账：").append(monthList.size).append(" 笔，合计 ¥")
            .append(store.fmtMoney(monthList.sumOf { it.amount }))
        if (monthList.isNotEmpty()) {
            sb.append("（").append(
                store.expenseCategoryTotals(monthList).take(6)
                    .joinToString("、") { "${it.first} ¥${store.fmtMoney(it.second)}" },
            ).append("）")
        }
        sb.append('\n')

        val since = today.minusDays(30).toString()
        val recent = store.expenses.filter { it.date >= since }
        sb.append("近 30 天记账：").append(recent.size).append(" 笔，合计 ¥")
            .append(store.fmtMoney(recent.sumOf { it.amount }))
        if (recent.isNotEmpty()) {
            sb.append("（明细：").append(
                recent.sortedByDescending { it.date }.take(25).joinToString("；") {
                    "${it.date} ${it.category}¥${store.fmtMoney(it.amount)}${if (it.note.isBlank()) "" else "(${it.note})"}"
                },
            ).append("）")
        }
        sb.append('\n')

        val charges = store.charges.sortedByDescending { it.at }.take(15)
        sb.append("真实扣费流水")
        if (charges.isEmpty()) sb.append("：无\n")
        else sb.append("：").append(
            charges.joinToString("；") { "${it.date} ${it.subName} ¥${store.fmtMoney(it.amount)}" },
        ).append('\n')

        return sb.toString()
    }

    /* ── HTTP ── */

    private fun buildBody(
        model: String,
        system: String,
        history: List<Pair<Boolean, String>>,
        userText: String,
    ): String {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        history.filter { it.second.isNotBlank() }.takeLast(8).forEach { (fromUser, text) ->
            msgs.put(JSONObject().put("role", if (fromUser) "user" else "assistant").put("content", text))
        }
        msgs.put(JSONObject().put("role", "user").put("content", userText))
        return JSONObject()
            .put("model", model)
            .put("messages", msgs)
            .put("temperature", 0.3)
            .put("max_tokens", 900)
            .put("stream", false)
            .toString()
    }

    private suspend fun postJson(url: String, key: String, body: String): String =
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_CONNECT
                readTimeout = TIMEOUT_READ
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Accept", "application/json")
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val hint = try {
                        JSONObject(text).optJSONObject("error")?.optString("message").orEmpty()
                    } catch (e: Exception) {
                        ""
                    }
                    throw RuntimeException("HTTP $code ${hint.ifBlank { text }.take(160)}".trim())
                }
                text
            } finally {
                conn.disconnect()
            }
        }

    /** 从模型回复里抠出 JSON(容忍 ```json 包裹和前后废话) */
    private fun extractJson(s: String): JSONObject? {
        val t = s.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(t.substring(start, end + 1))
        } catch (e: Exception) {
            null
        }
    }
}
