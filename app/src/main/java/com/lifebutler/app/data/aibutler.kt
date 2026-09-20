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
data class AiPreset(
    val label: String,
    val base: String,
    val model: String,
    /** 「永久免费」/「送额度」/「按量付费」,界面上做角标用 */
    val tag: String,
    /** 一句话说明:额度、特点、注意事项 */
    val note: String,
    /** 申请 Key 的地址,界面上「去拿 Key」直接打开 */
    val applyUrl: String,
)

/**
 * 内置预设:覆盖国内外的知名服务,分成「有免费额度」和「按量付费」两组。
 *
 * 说明:
 * - 免费额度与模型名都会变,以各家控制台为准;填错了在「模型名」那一栏自己改一下即可。
 * - 这些是**备选**:想换别家、或者想用自己的 Key,才需要在这里挑一家去注册。
 *   开箱默认用的是下面的「内置共享额度」(智谱 glm-4-flash),不用填任何东西。
 * - 火山方舟需要先在控制台**开通对应模型**,否则有 Key 也调不通。
 */
val AI_PRESETS: List<AiPreset> = listOf(

    /* ── 有免费额度(按省心程度排) ── */

    AiPreset(
        label = "智谱 GLM · Flash",
        base = "https://open.bigmodel.cn/api/paas/v4",
        model = "glm-4-flash",
        tag = "永久免费",
        note = "永久免费、中文最稳,长期常驻选它。注册还送 2000 万 token",
        applyUrl = "https://open.bigmodel.cn",
    ),
    AiPreset(
        label = "硅基流动 · Qwen2.5-7B",
        base = "https://api.siliconflow.cn/v1",
        model = "Qwen/Qwen2.5-7B-Instruct",
        tag = "永久免费",
        note = "9B 以下模型永久免费,国内延迟低。注册送 2000 万 token",
        applyUrl = "https://cloud.siliconflow.cn",
    ),
    AiPreset(
        label = "腾讯混元 · Lite",
        base = "https://api.hunyuan.cloud.tencent.com/v1",
        model = "hunyuan-lite",
        tag = "永久免费",
        note = "永久免费,并发不高但个人用足够",
        applyUrl = "https://console.cloud.tencent.com/hunyuan",
    ),
    AiPreset(
        label = "讯飞星火 · Lite",
        base = "https://spark-api-open.xf-yun.com/v1",
        model = "lite",
        tag = "永久免费",
        note = "永久免费且不限 token,只是 QPS 偏低",
        applyUrl = "https://console.xfyun.cn",
    ),
    AiPreset(
        label = "百度千帆 · ERNIE Speed",
        base = "https://qianfan.baidubce.com/v2",
        model = "ernie-speed-128k",
        tag = "永久免费",
        note = "永久免费,中文知识问答不错;需先实名认证才能领",
        applyUrl = "https://console.bce.baidu.com/qianfan",
    ),
    AiPreset(
        label = "火山方舟 · 豆包",
        base = "https://ark.cn-beijing.volces.com/api/v3",
        model = "doubao-seed-1-6-flash-250715",
        tag = "送额度",
        note = "每天 200 万 token 自动刷新,单日额度最大。要先在控制台开通这个模型",
        applyUrl = "https://console.volcengine.com/ark",
    ),
    AiPreset(
        label = "阿里云百炼 · 通义",
        base = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        model = "qwen-plus",
        tag = "送额度",
        note = "新用户送 7000 万 token(90 天),模型最全,适合横向比效果",
        applyUrl = "https://bailian.console.aliyun.com",
    ),
    AiPreset(
        label = "月之暗面 · Kimi",
        base = "https://api.moonshot.cn/v1",
        model = "moonshot-v1-8k",
        tag = "送额度",
        note = "新用户送 15 元代金券,长文本阅读强",
        applyUrl = "https://platform.moonshot.cn",
    ),
    AiPreset(
        label = "Groq",
        base = "https://api.groq.com/openai/v1",
        model = "llama-3.3-70b-versatile",
        tag = "免费额度",
        note = "免费额度大、速度极快。国内直连不稳,可能要挂代理",
        applyUrl = "https://console.groq.com",
    ),
    AiPreset(
        label = "Cerebras",
        base = "https://api.cerebras.ai/v1",
        model = "llama-3.3-70b",
        tag = "免费额度",
        note = "每天约 100 万 token,速度极快。国内直连不稳",
        applyUrl = "https://cloud.cerebras.ai",
    ),
    AiPreset(
        label = "Google Gemini",
        base = "https://generativelanguage.googleapis.com/v1beta/openai/",
        model = "gemini-2.5-flash",
        tag = "免费额度",
        note = "免费层每天重置,上下文超长。国内直连不稳",
        applyUrl = "https://aistudio.google.com",
    ),
    AiPreset(
        label = "OpenRouter",
        base = "https://openrouter.ai/api/v1",
        model = "meta-llama/llama-3.3-70b-instruct:free",
        tag = "免费额度",
        note = "一个 Key 打通几十家。认准模型名以 :free 结尾的才是免费",
        applyUrl = "https://openrouter.ai",
    ),
    AiPreset(
        label = "Mistral",
        base = "https://api.mistral.ai/v1",
        model = "mistral-small-latest",
        tag = "免费额度",
        note = "欧洲服务,免费实验层,需手机号验证",
        applyUrl = "https://console.mistral.ai",
    ),

    /* ── 按量付费(更稳更强) ── */

    AiPreset(
        label = "DeepSeek",
        base = "https://api.deepseek.com/v1",
        model = "deepseek-chat",
        tag = "按量付费",
        note = "便宜且强,性价比最高。新用户也送一小笔额度",
        applyUrl = "https://platform.deepseek.com",
    ),
    AiPreset(
        label = "OpenAI",
        base = "https://api.openai.com/v1",
        model = "gpt-4o-mini",
        tag = "按量付费",
        note = "最通用,但贵、且国内需代理",
        applyUrl = "https://platform.openai.com",
    ),
)

/* ───────────────────── 内置共享额度 ───────────────────── */

/**
 * 打包在安装包里的共享 Key:智谱 GLM-4-Flash(永久免费模型)。
 * 作用是**开箱即用**——装完不填任何东西,对话页就能听懂整句话。
 *
 * ⚠️ 必须清楚的事实(界面上也照实写了):
 * - 这把 Key 就在安装包里,**一定拿得到**,它不是安全边界,也不是保密手段。
 *   下面把它倒着存、运行时翻回来,只是让 `strings app.apk | grep` 这类扫包脚本捞不到明文而已:
 *   反编译看一眼代码、或者把串反过来,立刻就还原了。别把它当密码。
 * - 所有装了本应用的人共用这一把 Key,额度也是共用的:用的人多了会排队、限流,甚至被平台封掉。
 * - 它只连 智谱 open.bigmodel.cn 这一家,并且只在用户没填自己的接口时才会被使用。
 * - 用户随时可以在「我的 → AI 智能管家」里关掉内置额度,换成自己的 Key。
 *
 * (为什么不能用拼接常量:R8 会把 `A + "." + B` 直接折成一个整串写进 dex,
 *  实测过——折完之后整串照样在包里躺着,拆开完全没用。)
 */
private const val BUILTIN_KEY_REVERSED = "T2kFPV7EJLk0emvi.0ebceaa25a1a2c385ab481a4682e688f"

/** 内置额度用的接口地址 */
const val BUILTIN_BASE = "https://open.bigmodel.cn/api/paas/v4"

/** 内置额度用的模型名(智谱永久免费) */
const val BUILTIN_MODEL = "glm-4-flash"

/** 内置额度的 Key:倒序存放,取的时候翻回来 */
val BUILTIN_KEY: String get() = BUILTIN_KEY_REVERSED.reversed()

/** 界面上给内置额度用的名字 */
const val BUILTIN_LABEL = "内置免费额度 · 智谱 GLM-4-Flash"

/**
 * AI 管家配置:接口地址、API Key、模型名,只写在本机 SharedPreferences 里。
 *
 * 两套配置,优先级明确:
 * 1. **你自己填的**(base/key/model 三项齐全)→ 最高优先,内置额度自动让位;
 * 2. **内置共享额度**(默认开)→ 没填自己的就用它,开箱可用;
 * 3. 两者都没有 → 离线规则模式,界面会明说。
 *
 * 诚信约定:
 * - Key 只存在本机,不进备份文本、不进任何第三方;请求只发往「当前生效的那一个」地址。
 * - 用的一定是界面上显示的那个来源,不会偷偷换;没接上就说没接上,不假装 AI 已经可用。
 */
object AiConfig {

    private const val PREF = "lifebutler_ai"
    private const val K_BASE = "base"
    private const val K_KEY = "key"
    private const val K_MODEL = "model"
    private const val K_USE_BUILTIN = "use_builtin"

    /** 当前生效的是哪一套 */
    enum class Source { OWN, BUILTIN, NONE }

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /* ── 用户自己填的那一套 ── */

    fun base(ctx: Context): String = sp(ctx).getString(K_BASE, "").orEmpty()
    fun key(ctx: Context): String = sp(ctx).getString(K_KEY, "").orEmpty()
    fun model(ctx: Context): String = sp(ctx).getString(K_MODEL, "").orEmpty()

    /** 自己填的三项齐全才算「有」 */
    fun hasOwn(ctx: Context): Boolean =
        base(ctx).isNotBlank() && key(ctx).isNotBlank() && model(ctx).isNotBlank()

    fun save(ctx: Context, base: String, key: String, model: String) {
        sp(ctx).edit()
            .putString(K_BASE, base.trim().trimEnd('/'))
            .putString(K_KEY, key.trim())
            .putString(K_MODEL, model.trim())
            .apply()
    }

    /** 清掉自己填的那一套(内置额度的开关保持原样,不连坐) */
    fun clearOwn(ctx: Context) {
        sp(ctx).edit().remove(K_BASE).remove(K_KEY).remove(K_MODEL).apply()
    }

    /** 旧的调用点:等同于清掉自己填的 */
    fun clear(ctx: Context) = clearOwn(ctx)

    /* ── 内置共享额度 ── */

    /** 是否允许使用内置共享额度,默认开 */
    fun useBuiltin(ctx: Context): Boolean = sp(ctx).getBoolean(K_USE_BUILTIN, true)

    fun setUseBuiltin(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean(K_USE_BUILTIN, on).apply()
    }

    /* ── 实际生效的那一套 ── */

    fun source(ctx: Context): Source = when {
        hasOwn(ctx) -> Source.OWN
        useBuiltin(ctx) -> Source.BUILTIN
        else -> Source.NONE
    }

    fun effBase(ctx: Context): String = when (source(ctx)) {
        Source.OWN -> base(ctx)
        Source.BUILTIN -> BUILTIN_BASE
        Source.NONE -> ""
    }

    fun effKey(ctx: Context): String = when (source(ctx)) {
        Source.OWN -> key(ctx)
        Source.BUILTIN -> BUILTIN_KEY
        Source.NONE -> ""
    }

    fun effModel(ctx: Context): String = when (source(ctx)) {
        Source.OWN -> model(ctx)
        Source.BUILTIN -> BUILTIN_MODEL
        Source.NONE -> ""
    }

    /** 接上了就能用自然语言(不管是自己的接口还是内置额度) */
    fun isReady(ctx: Context): Boolean = source(ctx) != Source.NONE

    /** 界面上的一行状态文字,如实说明现在用的是哪一个 */
    fun statusText(ctx: Context): String = when (source(ctx)) {
        Source.OWN -> "AI 已接入 · 你的接口 · ${model(ctx)}"
        Source.BUILTIN -> "AI 已接入 · $BUILTIN_LABEL"
        Source.NONE -> "离线规则模式"
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
        /** 模型要求打开的页面(内部路由),界面会给一个「带我去」的按钮;null = 不跳 */
        val nav: String? = null,
        val failed: Boolean = false,
    )

    /** 一个动作执行完的结果。wrote=false 表示没写进本机,what 只是一句解释 */
    private data class Applied(val what: String, val wrote: Boolean = true)

    /**
     * 动作格式表。
     * `systemPrompt()` 和「追补」时的追问**共用这一份** —— 以前格式只写在系统提示词里,
     * 追补那一轮模型看不到格式,只能回一句「做不到」,等于白追。
     * 新增动作时只改这里一处,两边的清单不会走散。
     */
    private val ACTION_CHEATSHEET = """
{"type":"add_task","text":"要做的事","meta":"补充说明，可空"}
{"type":"add_expense","amount":25,"category":"餐饮","note":"午饭"}
{"type":"add_subscription","name":"网易云音乐","amount":15,"next_date":"2026-10-05"}
{"type":"add_charge","name":"网易云音乐","amount":15,"date":"2026-09-05"}
{"type":"close_subscription","name":"爱奇艺"}
{"type":"add_obligation","title":"车险续保","date":"2026-11-01","note":"可先比价","tag":"车辆"}
{"type":"add_member","name":"妈妈","label":"复诊","date":"2026-09-24"}
{"type":"add_key_date","title":"爸妈结婚纪念日","date":"10-22","note":""}
{"type":"add_archive","title":"保单","note":"续保可提前比价"}
{"type":"add_memo","title":"体检报告","content":"周五前把报告取回来","category":"生活","remind_at":"2026-09-25 09:00"}
{"type":"complete_task","text":"交物业费"}
{"type":"toggle_dark","on":true}
{"type":"open_screen","screen":"vault"}
""".trim()

    private const val TIMEOUT_CONNECT = 20_000
    private const val TIMEOUT_READ = 60_000

    /** 问一句:返回要说的话 + 真正执行了哪些写入 */
    suspend fun ask(
        ctx: Context,
        store: ButlerStore,
        userText: String,
        history: List<Pair<Boolean, String>> = emptyList(),
    ): Reply {
        val base = AiConfig.effBase(ctx)
        val key = AiConfig.effKey(ctx)
        val model = AiConfig.effModel(ctx)
        if (base.isBlank() || key.isBlank() || model.isBlank()) {
            return Reply(offlineText(store, userText), failed = true)
        }
        return try {
            val sys = systemPrompt(store)
            logDebug(ctx, "来源", "${AiConfig.source(ctx).name} / $model / $base")
            logDebug(ctx, "提示词", sys)
            val body = buildBody(model, sys, history, userText)
            val raw = postJson("$base/chat/completions", key, body)
            logDebug(ctx, "原文", raw)
            val content = JSONObject(raw)
                .optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content").orEmpty()

            var obj = extractJson(content)
            if (obj == null) {
                // 没有按约定给 JSON:把这句原样当回答用,但不擅自执行任何写入
                return Reply(content.trim().ifBlank { "模型这次没返回内容，再说一次试试。" })
            }
            var rawReply = obj.optString("reply").trim().ifBlank { "记下了。" }
            var (done, nav) = runActions(ctx, store, obj)

            // 它嘴上说「已经加好了」,actions 却是空的 —— 本机什么都没写。
            // 提示词里已经专门警告过这一条,但 glm-4-flash / glm-4.5-flash 都还会偶尔犯(实测约 1/16),
            // 光靠提示词收不干净。这里带着它自己刚才那段 JSON 再追一次,只要它把 actions 补上。
            if (done.isEmpty() && looksLikeClaim(rawReply)) {
                logDebug(ctx, "追补", "说了做却没给动作，追问一次：$rawReply")
                val fixed = repairActions(ctx, base, key, model, sys, history, userText, content)
                if (fixed != null) {
                    val (d2, n2) = runActions(ctx, store, fixed)
                    if (d2.isNotEmpty()) {
                        done = d2
                        nav = n2
                        fixed.optString("reply").trim().takeIf { it.isNotBlank() }?.let { rawReply = it }
                    }
                }
            }

            // 补过一次还是没落库,就不能再让用户以为已经写进去了 —— 如实说没做到。
            val reply = if (done.isEmpty() && looksLikeClaim(rawReply)) {
                "$rawReply（这句我没能写进本机，补上金额或时间再说一次）"
            } else rawReply
            Reply(reply, done, nav)
        } catch (e: Exception) {
            Reply(offlineText(store, userText, e.message), failed = true)
        }
    }

    /* ── 动作执行 ── */

    /** 逐个执行模型给的 actions。跳转(action=open_screen)不是写入,单独还给界面去办 */
    private fun runActions(
        ctx: Context,
        store: ButlerStore,
        obj: JSONObject,
    ): Pair<List<String>, String?> {
        val done = ArrayList<String>()
        var nav: String? = null
        obj.optJSONArray("actions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                if (a.optString("type") == "open_screen") {
                    resolveScreen(a.optString("screen"))?.let { nav = it }
                    continue
                }
                val r = applyAction(ctx, store, a) ?: continue
                // 只把真写进去的算「办好了」;「没找到…未改动」不算,否则回复里那句
                // 「已经标好了」就没人纠得动了。
                if (r.wrote) done.add(r.what)
            }
        }
        return done to nav
    }

    /**
     * 这句话像不像「我做好了」的承诺?
     * 只用来判断要不要追补、要不要在回复后面挂一句实话,判错也只是多打一次请求,不会误改数据。
     * 特意避开「还没有/没有添加/没找到」这种否定说法,免得用户每问一句都白追一次。
     */
    private fun looksLikeClaim(reply: String): Boolean =
        CLAIM_WORDS.any { reply.contains(it) } &&
            listOf("没有添加", "没找到", "还没有", "没有记录", "没有添加任何").none { reply.contains(it) }

    private val CLAIM_WORDS = listOf(
        "已经", "已添加", "已记", "已保存", "已创建", "已设置", "已帮你",
        "记下了", "记好了", "记录好了", "创建好了", "添加好了", "加好了", "保存好了", "设置好了", "帮你",
    )

    /**
     * 追补动作:把第一轮的原文以 assistant 轮塞回去,再明确要求只输出 JSON、把 actions 补上。
     * 拿不到合法 JSON 就返回 null(视为补不回来,由调用方如实告知用户)。
     */
    private suspend fun repairActions(
        ctx: Context,
        base: String,
        key: String,
        model: String,
        sys: String,
        history: List<Pair<Boolean, String>>,
        userText: String,
        firstContent: String,
    ): JSONObject? = try {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", sys))
        history.filter { it.second.isNotBlank() }.takeLast(8).forEach { (fromUser, text) ->
            msgs.put(
                JSONObject()
                    .put("role", if (fromUser) "user" else "assistant")
                    .put("content", if (fromUser) text else asProtocolJson(text)),
            )
        }
        msgs.put(JSONObject().put("role", "user").put("content", userText))
        msgs.put(JSONObject().put("role", "assistant").put("content", firstContent))
        msgs.put(
            JSONObject().put("role", "user").put(
                "content",
                "你上面这条 reply 说这件事已经办好了，但 actions 是空的，本机其实什么都没写。" +
                    "用户刚才说的是：「$userText」。现在只输出一个 JSON，" +
                    "把该做的 action 按下面的格式补进 actions 里，**不要留空**：\n" +
                    ACTION_CHEATSHEET +
                    "\n只有「删改已有记录 / 往相册加照片 / 往档案组加文件 / 打电话发短信 / 改系统设置 / 查外部信息」" +
                    "这几种才允许给空数组。上面这类记一笔、加一条的事，你都能做，没有理由不做。" +
                    "不要写解释，不要用代码块。",
            ),
        )
        val body = JSONObject()
            .put("model", model)
            .put("messages", msgs)
            .put("temperature", 0.2)
            .put("max_tokens", 900)
            .put("stream", false)
            .toString()
        val raw = postJson("$base/chat/completions", key, body)
        logDebug(ctx, "追补原文", raw)
        val c = JSONObject(raw)
            .optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content").orEmpty()
        extractJson(c)
    } catch (e: Exception) {
        logDebug(ctx, "追补失败", e.message ?: "未知")
        null
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

    /* ── 调试 ── */

    /**
     * 只有 debug 包才打日志:把发出去的提示词和拿回来的原文留在 logcat 里。
     * 排「模型没按格式回 JSON」「动作没落库」这类问题时,没这个就只能猜。
     * 用 logcat -s LbAI:V 看;release 包里这段直接 return,不会把用户数据写进系统日志。
     */
    private fun logDebug(ctx: Context, label: String, text: String) {
        val debuggable = (ctx.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
        android.util.Log.d("LbAI", "[$label] " + text.replace('\n', '⏎').take(3500))
    }

    /* ── 离线兜底:没配置 / 连不上时,规则引擎照样能记事记账 ── */

    private fun offlineText(store: ButlerStore, userText: String, reason: String? = null): String {
        val local = store.reply(userText)
        val head = if (reason == null) {
            "现在是离线规则模式（是你在「我的 → AI 智能管家」里把内置额度也关掉了）。打开内置免费额度，或者填一个自己的接口，就能用自然语言加东西。"
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
    private fun applyAction(ctx: Context, store: ButlerStore, a: JSONObject): Applied? {
        val what: String? = when (a.optString("type")) {
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

        "add_memo" -> {
            val content = a.optString("content").trim()
            val title = a.optString("title").trim().ifBlank { content.replace('\n', ' ').take(16) }
            if (title.isEmpty()) null
            else {
                val at = parseDateTime(a.optString("remind_at"))
                val memo = store.addMemo(title, content, a.optString("category").trim().ifBlank { "未分类" }, at)
                if (memo != null && at > 0) MemoReminders.schedule(ctx, memo.id, title, at)
                val askedRemind = a.optString("remind_at").isNotBlank()
                "备忘「$title」" + when {
                    at > 0 -> "（提醒 ${stamp(at)}）"
                    askedRemind -> "（提醒时间没看懂，没设提醒）"
                    else -> ""
                }
            }
        }

        "complete_task" -> {
            // 模型常把待办说短:待办是「明天下午三点去物业交费」,它会写「交物业费」。
            // 原来的 contains 双向判断对不上这种,于是它嘴上说「已标记完成」而本机没动。
            // 改成取最长公共片段,重叠 2 个字以上就算同一条,并在候选中挑重叠最多的那个。
            val text = a.optString("text").trim()
            val pending = store.tasks.filter { !it.done }
            val hit = if (text.isEmpty()) null
            else pending.maxByOrNull { overlap(it.text, text) }?.takeIf { overlap(it.text, text) >= 2 }
            when {
                text.isEmpty() -> null
                hit == null -> "没找到「$text」这条待办，未改动"
                else -> {
                    store.toggleTask(hit.id)
                    "已完成待办「${hit.text}」"
                }
            }
        }

        "toggle_dark" -> {
            val on = a.optBoolean("on", !store.darkMode.value)
            store.setDarkMode(on)
            if (on) "深色模式已打开" else "深色模式已关闭"
        }

        else -> null
        }
        if (what == null) return null
        // 「没找到…，未改动」这种不是写入,只是给用户一句解释(约定:凡没写成都以「未改动」结尾)。
        // 必须区分开:真写进去了才算「动作完成」,否则模型嘴上一句「已完成」就把它盖过去了。
        return Applied(what, wrote = !what.endsWith("未改动"))
    }

    /**
     * 两句话之间最长的公共片段有多少个字。
     * 用来把「交物业费」对到「明天下午三点去物业交费」——模型给的待办名往往和原文不完全一样。
     * 字符串都很短(十几个字),O(n·m) 足够。
     */
    private fun overlap(a: String, b: String): Int {
        var best = 0
        for (i in a.indices) {
            for (j in b.indices) {
                var k = 0
                while (i + k < a.length && j + k < b.length && a[i + k] == b[j + k]) k++
                if (k > best) best = k
            }
        }
        return best
    }

    /**
     * 把模型写的页面名对到应用内部路由。
     * 认不出来的返回 null —— 宁可不动,也不要拿着瞎猜的路由跳错页。
     */
    private fun resolveScreen(raw: String): String? {
        val s = raw.trim().lowercase()
        if (s.isEmpty()) return null
        return when {
            listOf("vault", "archive", "archives", "档案", "档案库", "文件").any { s == it || s.contains(it) } -> "vault"
            listOf("ledger", "expense", "expenses", "记账", "记账本", "账单", "花销", "明细").any { s == it || s.contains(it) } -> "ledger"
            listOf("memo", "memos", "note", "notes", "备忘", "备忘录", "笔记").any { s == it || s.contains(it) } -> "memo"
            listOf("report", "monthly", "月报", "报表", "报表页").any { s == it || s.contains(it) } -> "report"
            listOf("duties", "duty", "obligation", "义务", "时间线", "证件").any { s == it || s.contains(it) } -> "duties"
            listOf("scan", "扫描").any { s == it || s.contains(it) } -> "scan"
            listOf("states", "status", "状态", "系统状态").any { s == it || s.contains(it) } -> "states"
            listOf("today", "home", "今日", "首页").any { s == it || s.contains(it) } -> "today"
            listOf("guard", "subs", "subscription", "守护", "订阅", "扣款").any { s == it || s.contains(it) } -> "guard"
            listOf("family", "member", "家庭", "家人").any { s == it || s.contains(it) } -> "family"
            listOf("mine", "settings", "我的", "设置").any { s == it || s.contains(it) } -> "mine"
            listOf("chat", "butler", "管家", "对话").any { s == it || s.contains(it) } -> "chat"
            else -> null
        }
    }

    /** 解析「yyyy-MM-dd HH:mm」这类提醒时间;解析不出来就返回 0(不设提醒),不猜 */
    private fun parseDateTime(s: String): Long {
        if (s.isBlank()) return 0L
        val t = s.trim().replace('T', ' ').replace('：', ':')
        for (f in listOf("yyyy-MM-dd HH:mm", "yyyy-M-d H:mm", "yyyy-MM-dd HH:mm:ss")) {
            try {
                val d = java.time.LocalDateTime.parse(t, java.time.format.DateTimeFormatter.ofPattern(f))
                return d.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) {
                // 换下一个格式继续试
            }
        }
        return 0L
    }

    /* ── 提示词 ── */

    private fun systemPrompt(store: ButlerStore): String = """
你是「生活管家」手机应用里的智能管家。你能读写用户存在本机的数据。

【今天】${LocalDate.now()}

【本机数据快照】
${snapshot(store)}

【怎么做事】
1. 用户说一件事，你就把它真正记进对应的地方（待办 / 记账 / 订阅 / 义务 / 家人 / 关键日期 / 档案组 / 备忘录）。
2. 用户问你问题时，只依据上面的快照回答。快照里没有的，直说本机没有这条记录，绝不猜测、绝不编造数字和日期。
3. 用户问某个模块的清单（比如「我有哪些档案」「备忘里有什么」「这个月记了几笔」）时，**直接把内容列出来**：
   条数少就全列，条数多就列前几条并说清总数，不要只回一句「请去档案页查看」。
4. 如果用户明显是想看那一页（说「打开」「让我看看」），除了回答，再给一个 open_screen 动作把他带过去。
5. 金额一律用 ¥。

【你做不到的事，必须如实说，不要假装能做到】
- 你不能替用户往相册加照片、往档案组加文件（那要他本人去选文件），也**不能修改或删除已有记录**（只能新增，或把待办标成已完成）。想改金额、改日期、改内容，就如实说做不到，并建议他在对应页面长按编辑。
- 你不能替用户打电话、发短信、联系客服，也不能真的取消第三方订阅。
- 你不能改系统设置（通知权限、位置权限、通知使用权）。
- 你查不到外面的信息（天气、汇率、新闻、别人的电话）。只能依据本机快照回答。

【输出格式】只输出一个 JSON 对象，不要写解释，不要用代码块包裹：
{"reply":"对用户说的话","actions":[]}

reply：中文、口语化、最多 3 句，不要用星号井号或列表符号。
actions：没有要执行的就给空数组；有就按下面的格式，一次可以给多个。

⚠️ 最容易犯的错：reply 里说「已经帮你加好了」，actions 却是空的。这就是骗人——本机什么都没写。
只要用户是在让你记 / 加 / 建 / 存（哪怕话说得很随意），actions 就必须非空；
只有「用户只是问问题」或「你做不到这件事」时，才允许给空数组。
先想清楚要写哪一类，再照着下面的格式把它写出来。

$ACTION_CHEATSHEET

open_screen 的 screen 只能填这几个：vault(档案库) / ledger(记账本) / memo(备忘录) / report(月报) /
duties(义务时间线) / scan(一键扫描) / states(系统状态) / today(今日) / guard(守护与订阅) / family(家庭) / mine(我的)。
用户没要求看某一页时就不要给这个动作。

日期规则：能确定年份就写 yyyy-MM-dd；只说了月日（生日、纪念日这类）就写 MM-DD，系统会按最近的将来算。
提醒时间 remind_at 只能写 yyyy-MM-dd HH:mm（24 小时制）；用户没说具体时间就不要给这个字段，不要自己编一个时间。
记账 category 只能是：餐饮 / 交通 / 购物 / 居家 / 娱乐 / 医疗 / 人情 / 其他。

两处最容易放错地方，看清楚再写：
- 有到期日、需要用户本人去办的事（车险续保、年检、证件到期、该去体检了）→ add_obligation（到期事务）。
  只是要记住的日子（生日、纪念日、节日）→ add_key_date（关键日期）。别把「续保」这类放进关键日期。
- 用户问「我有哪些档案」问的是**档案组本身**，哪怕那个组里一个文件都还没放也要把组名报出来，
  不能因为「0 个文件」就答成「没有任何档案」。
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

        sb.append("档案组（共 ").append(store.archive.size).append(" 组）")
        if (store.archive.isEmpty()) sb.append("：无\n")
        else sb.append("：").append(
            store.archive.take(15).joinToString("；") { a ->
                val names = a.files.take(5).joinToString("、") { it.substringAfterLast('/') }
                val more = if (a.files.size > 5) " 等 ${a.files.size} 个" else ""
                // 别只写「0 个文件」:模型会顺势答成「一个档案都没有」,把组本身给漏掉
                val inner = if (a.files.isEmpty()) "里面还没放文件" else "${a.files.size} 个文件：$names$more"
                "${a.title}（$inner）" + (if (a.note.isBlank()) "" else "(备注：${a.note.take(24)})")
            },
        ).append('\n')

        val memos = store.memos
        sb.append("备忘录（共 ").append(memos.size).append(" 条")
        if (memos.isNotEmpty()) {
            sb.append("，分类：").append(
                memos.groupingBy { it.category.ifBlank { "未分类" } }.eachCount()
                    .entries.joinToString("、") { "${it.key}${it.value}" },
            )
        }
        sb.append("）")
        if (memos.isEmpty()) sb.append("：无\n")
        else sb.append("：").append(
            memos.sortedByDescending { it.updatedAt }.take(15).joinToString("；") { m ->
                val body = m.content.replace('\n', ' ').trim().take(30)
                "${m.title}${if (body.isBlank()) "" else "($body)"}·分类:${m.category.ifBlank { "未分类" }}" +
                    (if (m.remindAt > 0) "·提醒 ${stamp(m.remindAt)}" else "") +
                    (if (m.pinned) "·置顶" else "")
            },
        ).append('\n')

        sb.append("家庭相册：共 ").append(store.album.size).append(" 张")
        val noted = store.album.filter { it.note.isNotBlank() }
        if (noted.isNotEmpty()) {
            sb.append("，其中带备注的 ").append(noted.size).append(" 张（").append(
                noted.sortedByDescending { it.at }.take(8).joinToString("、") { it.note.take(14) },
            ).append("）")
        }
        sb.append("。照片本身不参与问答，只能按照片数量与上面的备注回答，不要编造照片内容\n")

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

    /** 时间戳 → 「M-d HH:mm」,快照里说备忘录提醒时间用 */
    private fun stamp(ms: Long): String = java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
        .let { "%d-%d %02d:%02d".format(it.monthValue, it.dayOfMonth, it.hour, it.minute) }

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
            if (fromUser) {
                msgs.put(JSONObject().put("role", "user").put("content", text))
            } else {
                // 关键:助手这一侧必须始终长成「协议 JSON」的样子。
                // 历史里只要混进一条纯文本的助手发言(欢迎语、离线规则回复),模型就会照抄那个风格,
                // 之后一律用大白话回答、连 JSON 都不肯吐 —— 实测过,「说记下了但什么都没写进本机」就是这个原因。
                msgs.put(JSONObject().put("role", "assistant").put("content", asProtocolJson(text)))
            }
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

    /** 把任意一条助手历史发言统一成 {"reply":…,"actions":[]} 的形态 */
    private fun asProtocolJson(text: String): String {
        extractJson(text)?.let { if (it.has("reply")) return it.toString() }
        return JSONObject()
            .put("reply", text.take(200))
            .put("actions", JSONArray())
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
