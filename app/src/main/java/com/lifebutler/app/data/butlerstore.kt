package com.lifebutler.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/* ───────────────────────── 数据模型 ───────────────────────── */

data class ButlerTask(val id: String, val text: String, val meta: String, val done: Boolean)

/**
 * 一笔订阅。
 *
 * [trialUntil] 试用截止日（空字符串 = 不是试用 / 没记）。
 * 为什么单独立一个字段而不并进 [nextDate]：试用的「到期」和续费的「扣款日」是两件事，
 * 混在一起用户就分不清「这一天是开始收钱，还是最后一次免费」。
 *
 * [remindAhead] 这一条**单独**提前几天进日报（0 = 跟随全局设置）。
 * 订阅之间金额差得远（6 元的 iCloud 和 200 多的会员），一刀切的阈值总有人不合适。
 */
data class ButlerSub(
    val id: String,
    val name: String,
    val amount: Double,
    val nextDate: String,
    val closing: Boolean,
    val source: String,
    val closingAt: Long,
    val trialUntil: String = "",
    val remindAhead: Int = 0,
)

/** [remindAhead] 含义同 [ButlerSub]：0 = 跟随全局，>0 = 这条单独提前这么多天 */
data class ButlerObligation(
    val id: String,
    val title: String,
    val note: String,
    val date: String,
    val tag: String,
    val done: Boolean,
    val remindAhead: Int = 0,
)

/**
 * 一位家人。
 *
 * [remindAhead] 含义同 [ButlerSub]：0 = 跟随全局「事项到期 / 纪念日」的提前量，
 * >0 = 这位家人的日期单独提前这么多天。
 *
 * 为什么补上它：v2.12 把「订阅 / 义务 / 关键日期可单条设提前量」做完了，唯独漏了家人 ——
 * 于是「备忘能设、义务不能设」这个不一致被修掉之后，换了个对象（家人）还在原地。
 */
data class ButlerMember(
    val id: String,
    val name: String,
    val label: String,
    val date: String,
    val photo: String,
    val remindAhead: Int = 0,
)
data class ButlerKeyDate(val id: String, val title: String, val date: String, val note: String, val remindAhead: Int = 0)
data class ButlerArchive(val id: String, val title: String, val count: Int, val note: String, val files: List<String>)
data class ButlerChat(val id: String, val fromUser: Boolean, val text: String, val photoPath: String)

/**
 * 家庭相册里的一张照片。
 * 与「家人头像」是两件事:头像 = 每位家人的那张脸(挂在成员卡上);
 * 相册 = 全家人的照片墙(独立成册,可以放很多张)。两者互不覆盖。
 */
data class ButlerPhoto(val id: String, val path: String, val note: String, val at: Long)

/**
 * 一笔账。
 *
 * [income] 为 true 表示**收入**（工资 / 报销 / 退款），否则是支出。
 * 为什么加这个：原来金额恒等于支出，用户想记一笔报销只能记成负数或干脆不记，
 * 月报也就永远只能回答「花了多少」，回答不了「这个月到底剩多少」。
 * 存量数据没有这个字段 → 一律当支出（`false`），口径不变。
 */
data class ButlerExpense(
    val id: String,
    val amount: Double,
    val category: String,
    val note: String,
    val date: String,
    val at: Long,
    val income: Boolean = false,
)

/** 真实扣费流水:只由用户手动记录,或扫描/通知真的命中时写入;不由程序推算。 */
data class ButlerCharge(val id: String, val subName: String, val amount: Double, val date: String, val at: Long, val source: String)

/** 关闭历史:每次标记关闭就记一条,用于统计「已关闭 N 笔」;删除订阅不会抹掉这条历史。 */
data class ButlerClosedSub(val id: String, val name: String, val amount: Double, val closedAt: Long)

/**
 * 一条「扣费线索」—— 通知命中了扣费关键词，但**还没被用户认领**。
 *
 * 为什么需要它：原来通知只要命中关键词就无条件写真实扣费流水 + 自动加进守护清单。
 * 而关键词表里有「支出」「付款」这种很宽的词，微信里一句"向某某付款 500 元"就能命中，
 * 于是守护清单里凭空多出一个"订阅"、记账流水里多出一笔 500 元 —— 用户事后只会觉得
 * "这东西在乱记我的账"，而账目一旦被污染，整个记账模块的可信度就没了。
 *
 * 所以改成：命中先落到这里，由用户在守护页点一下「认得」才落库。「不伪造已扣」是底线。
 */
data class ButlerClaim(
    val id: String,
    val name: String,
    val amount: Double,
    val at: Long,
    val nextDate: String,
    val pkg: String,
    val snippet: String,
)

/**
 * 一条备忘:标题 + 正文 + 分类 + 可选提醒时间。
 * createdAt / updatedAt 为写入时的本机时间戳;remindAt 为 0 表示不提醒。
 * remindAt 到了就交给系统通知响一次,过去的提醒不会重排、不会重复打扰。
 */
data class ButlerMemo(
    val id: String,
    val title: String,
    val content: String,
    val category: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val remindAt: Long,
)

/** 备忘录排序键:最近更新 / 创建时间 / 提醒时间(三者都保持「置顶在前」) */
enum class MemoSort { Updated, Created, Remind }

/**
 * 全局检索的一条结果。
 *
 * [kind] 是分组用的类型名（订阅 / 扣费 / 待办 …），[route] 是点进去要翻到哪一页
 * （用 [ButlerStore.kt] 里那套 overlay 名字），[subId] 只在订阅结果里有值 ——
 * 订阅要落到「哪一笔」的详情页，光给个页面名不够。
 */
data class SearchHit(
    val kind: String,
    val id: String,
    val title: String,
    val sub: String,
    val route: String,
    val subId: String? = null,
)

/**
 * 一次导出的结果。
 *
 * 为什么不能只返回一段文本：备份里的图片是 base64 内嵌的，而内嵌总预算（[ButlerStore.BLOB_BUDGET]）
 * 是**全局共享**的 8 MB —— 相册存过几十张照片就能把它用光，后面的对话图与档案文件会被**跳过**。
 * 原来跳过时不声不响，用户看到「备份已存成文件」就以为全备好了，直到换机恢复才发现档案是空的。
 * 所以导出必须把「哪些类别有文件没能进去」一起交出来，由界面如实告诉用户。
 *
 * [skipped] 的 key 是可以直接显示给用户看的类别名（头像 / 家人照片 / 相册 / 对话图片 / 档案）。
 */
data class BackupExport(
    val text: String,
    val fileCount: Int,
    val skipped: Map<String, Int>,
) {
    val hasSkipped: Boolean get() = skipped.isNotEmpty()
    val skippedTotal: Int get() = skipped.values.sum()
    /** 一句可以直接放在提示里的话；没有遗漏时返回 null */
    fun skippedText(): String? =
        if (!hasSkipped) null
        else skipped.entries.joinToString("、") { "${it.key} ${it.value} 个" }
}

/**
 * 一份备份的「体检报告」。
 *
 * 恢复是**整体覆盖**且无法撤销，所以选文件之前必须先让用户看清里面有什么 ——
 * 手机里存着好几份不同日期的备份时，光靠文件名是没法分辨的。
 */
data class BackupPreview(
    val taskCount: Int,
    val subCount: Int,
    val expenseCount: Int,
    val memberCount: Int,
    val albumCount: Int,
    val archiveCount: Int,
    val memoCount: Int,
    val chargeCount: Int,
    val fileCount: Int,
    val totalCount: Int,
)

/**
 * 本机数据仓库:全部用户数据保存在本机 SharedPreferences(JSON),
 * 不联网、不上传;所有页面读写同一份数据,改动即时持久化。
 *
 * 诚信约定:
 * - 不做任何推算或编造。没有记录的地方显示空态,不填看起来像真的数字。
 * - 统计口径全部可由列表数据复算(已关闭笔数、每月省下、本月花销等)。
 * - 首次安装是干净的,不自动塞演示内容;演示内容需用户主动「载入」。
 */
class ButlerStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("lifebutler", Context.MODE_PRIVATE)
    private val appCtx = context.applicationContext

    /** 只有 debug 包才往 logcat 写排查信息,用户的包里一行都不写 */
    private val debuggable =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    val profileName: MutableState<String> = mutableStateOf("小满")
    val bloodType: MutableState<String> = mutableStateOf("")
    val meds: MutableState<String> = mutableStateOf("")
    val emergencyContact: MutableState<String> = mutableStateOf("")
    val avatarPath: MutableState<String> = mutableStateOf("")
    val reminderEnabled: MutableState<Boolean> = mutableStateOf(true)
    val reminderHour: MutableState<Int> = mutableStateOf(9)

    /**
     * 提前几天提醒「要扣费」。默认 3 天。
     *
     * 原来这两个阈值写死在代码里（订阅 3 天、到期/纪念日 7 天），用户想提前两周知道
     * 车险该续了也做不到 —— 而「提前多久算合适」本来就因人因事而异，只能交给用户。
     */
    val reminderSubDays: MutableState<Int> = mutableStateOf(3)

    /** 提前几天提醒「事项到期 / 纪念日」。默认 7 天。 */
    val reminderDueDays: MutableState<Int> = mutableStateOf(7)

    /**
     * 每月支出预算（0 = 没设，不提示超支）。
     *
     * 特意**不**给一个默认值（比如 3000）：凭空替用户定一个预算，然后告诉他「你超支了」，
     * 是编造出来的焦虑。只有用户自己填了数，超支提示才有意义。
     */
    val monthlyBudget: MutableState<Double> = mutableStateOf(0.0)

    /**
     * 是否已经走过首次引导。
     *
     * 界面上还要再叠一个「本机没有任何记录」才会真的弹出来 ——
     * 否则老用户升级上来会莫名其妙被「三步上手」挡一次，那是在教已经会的人怎么用。
     */
    val onboarded: MutableState<Boolean> = mutableStateOf(false)
    val darkMode: MutableState<Boolean> = mutableStateOf(false)

    /**
     * 应用锁：进 App（以及从后台回来）先要过一遍系统锁屏凭据。
     *
     * 为什么用系统凭据而不是自己存密码：我们不想要任何"看起来加密了其实没有"的东西 ——
     * 交给系统 Keyguard 校验，我们既不接触指纹也不存 PIN，本机设没设锁屏也能如实判断。
     * 注意它**不参与 clearAll 的复位**：清空数据不该顺手把别人的门锁打开。
     */
    val appLockEnabled: MutableState<Boolean> = mutableStateOf(false)

    /**
     * 桌面小组件显示多少内容：-1 = 跟随应用锁，0 = 完整，1 = 只显示条数，2 = 隐藏正文。
     *
     * 为什么要这个开关：用户刚在 App 里开了应用锁，回到桌面却发现**扣费明细就明写在桌面上**，
     * 而且任何人拿起手机（还没解锁）都能看到 —— 两个功能互相拆台。
     * 默认「跟随应用锁」：开了锁就默认只显示条数，让"桌面能看"和"不想被别人看"不打架。
     */
    val widgetDetail: MutableState<Int> = mutableStateOf(-1)

    private var startDate: String = LocalDate.now().toString()

    /**
     * 悬浮管家（App 内全局那个小机器人）被拖到的位置，**归一化到 0~1**（相对可拖动区域的宽/高）。
     *
     * 为什么存分数不存像素：屏幕上可拖动的范围会变（换设备、系统字体、深色模式下的状态栏），
     * 存像素下次就可能跑到屏幕外或压在按钮上；存分数永远落在「看起来一样的地方」。
     * -1 表示用户还没拖过它，界面按「默认贴右侧」处理。
     *
     * 故意**不是** Compose state：拖动过程中每帧都会变，做成 state 会把整棵界面树重组一遍；
     * 这里只在松手时读一次、写一次盘，拖动中的实时位置由 ButlerFloat 自己的局部状态管。
     */
    var butlerFx: Float = -1f
    var butlerFy: Float = -1f

    val tasks: SnapshotStateList<ButlerTask> = mutableStateListOf()
    val subs: SnapshotStateList<ButlerSub> = mutableStateListOf()
    val obligations: SnapshotStateList<ButlerObligation> = mutableStateListOf()
    val members: SnapshotStateList<ButlerMember> = mutableStateListOf()
    val album: SnapshotStateList<ButlerPhoto> = mutableStateListOf()
    val keyDates: SnapshotStateList<ButlerKeyDate> = mutableStateListOf()
    val archive: SnapshotStateList<ButlerArchive> = mutableStateListOf()
    val chat: SnapshotStateList<ButlerChat> = mutableStateListOf()
    val expenses: SnapshotStateList<ButlerExpense> = mutableStateListOf()
    val charges: SnapshotStateList<ButlerCharge> = mutableStateListOf()
    val closedHistory: SnapshotStateList<ButlerClosedSub> = mutableStateListOf()
    val memos: SnapshotStateList<ButlerMemo> = mutableStateListOf()

    /** 通知捕获的「扣费线索」：等用户在守护页认领，认得才写进真实扣费 / 守护清单 */
    val pendingClaims: SnapshotStateList<ButlerClaim> = mutableStateListOf()

    /** 备忘分类:在「未分类」之外可由用户自增自删 */
    val memoCategories: MutableState<List<String>> = mutableStateOf(DEFAULT_MEMO_CATEGORIES)

    /**
     * 记账分类:默认给一套常用的,用户可自增自删。
     *
     * 为什么补上:原来固定 8 个,有孩子的想加「教育」、养宠物的想加「宠物」、还贷的想加「房贷」,
     * 都只能塞进「其他」—— 而「其他」越滚越大之后,分类统计就失去意义了。
     * 另外备忘录的分类早就能自己加,记账却不能,同一个 App 里两套规矩也让人莫名其妙。
     */
    val expenseCategories: MutableState<List<String>> = mutableStateOf(DEFAULT_EXPENSE_CATEGORIES)

    /**
     * 用户自己补的「要监听哪些 App」的包名。
     *
     * 为什么需要：能读的通知包名原来是**写死的 15 个**（微信 / 支付宝 / 云闪付 + 12 家银行）。
     * 用户的卡要是不在这 15 家里（地方农商行、微信支付分、美团月付…），开了权限也永远扫不到，
     * 他会直接判定"这个功能是坏的"。给一个输入框，把"扫不到"变成"可以自己加"。
     */
    val watchedExtraPackages: MutableState<List<String>> = mutableStateOf(emptyList())

    // 载入放在类体最后(见文件末尾的 init)。
    // 别挪回这里:Kotlin 按声明顺序初始化属性,而 save() 会用到处处声明的 dismissed(第 43x 行),
    // 放在这里执行时它还是未初始化的 val,load() 里的 save() 会抛 NPE 被静默吞掉 ——
    // 表现就是「首次安装后 prefs 里一直是空的」,界面上却什么都正常,极难查。

    /* ── 派生统计(全部可从上面的列表复算,不存冗余数字) ── */

    /** 处于「关闭中」的订阅月费合计 = 此后每月不再支出的钱 */
    val monthlySaved: Double
        get() = subs.filter { it.closing }.sumOf { it.amount }

    /** 历史上标记过关闭的订阅笔数 */
    val closedCount: Int
        get() = closedHistory.size

    /** 本月因为已关闭订阅而不用再付的月费(关闭时间在本月或更早) */
    val savedThisMonth: Double
        get() {
            val now = LocalDate.now()
            val monthStart = now.withDayOfMonth(1)
            return closedHistory
                .filter { e ->
                    val d = dateOfMillis(e.closedAt)
                    !d.isAfter(now) && !d.isBefore(monthStart)
                }
                .sumOf { it.amount }
        }

    /* ── 日期工具 ── */

    fun parseDate(raw: String): LocalDate? {
        var t = raw.trim()
        if (t.isEmpty()) return null
        t = t.replace("年", "-").replace("月", "-").replace("日", "").replace("/", "-").replace(".", "-")
        val parts = t.split("-").filter { it.isNotEmpty() }
        return try {
            when (parts.size) {
                3 -> LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                2 -> {
                    var d = LocalDate.of(LocalDate.now().year, parts[0].toInt(), parts[1].toInt())
                    if (d.isBefore(LocalDate.now())) d = d.plusYears(1)
                    d
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun dateOfMillis(ms: Long): LocalDate =
        if (ms <= 0L) LocalDate.now()
        else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    fun daysUntil(date: String): Long? {
        val d = parseDate(date) ?: return null
        return ChronoUnit.DAYS.between(LocalDate.now(), d)
    }

    fun daysText(date: String): String {
        if (date.isBlank()) return "未设日期"
        val n = daysUntil(date) ?: return "未设日期"
        return when {
            n < 0 -> "已过期 ${-n} 天"
            n == 0L -> "就是今天"
            n == 1L -> "明天"
            else -> "$n 天后"
        }
    }

    /** 列表右侧短日期:临近用「明天 / N 天后」,否则用「M-d」;没日期就说待补全 */
    fun dateLabel(date: String): String {
        if (date.isBlank()) return "待补全"
        val d = parseDate(date) ?: return date
        val n = daysUntil(date)
        return if (n != null && n <= 1) daysText(date) else "%d-%02d".format(d.monthValue, d.dayOfMonth)
    }

    fun fmtCn(date: String): String {
        if (date.isBlank()) return "日期待补全"
        val d = parseDate(date) ?: return date
        return "%d 月 %d 日".format(d.monthValue, d.dayOfMonth)
    }

    /**
     * 把毫秒时间戳格式化成「M 月 D 日」—— 相册/聊天这类只记 `at` 的地方用。
     *
     * 为什么不直接把 [dateOfMillis] 放开成 public:它是内部解析用的,让 UI 拿到 LocalDate
     * 等于把「怎么显示」的决定权漏到 40 多个调用点上;这里只放一个格式化出口,口径统一。
     */
    fun fmtCnAt(ms: Long): String = fmtCn(dateOfMillis(ms).toString())

    fun dayCount(): Int {
        val s = parseDate(startDate) ?: LocalDate.now()
        return (ChronoUnit.DAYS.between(s, LocalDate.now()).toInt() + 1).coerceAtLeast(1)
    }

    /** 金额显示:四舍五入到 1 位小数,整数不带小数点 */
    fun fmtMoney(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        val rounded = Math.round(v * 10.0) / 10.0
        return if (Math.abs(rounded - Math.floor(rounded)) < 1e-9) rounded.toLong().toString()
        else "%.1f".format(rounded)
    }

    /* ── 变更操作(全部即时持久化) ── */

    fun addTask(text: String, meta: String = "来自对话") {
        tasks.add(ButlerTask(id(), text.trim(), meta, false)); save()
    }

    fun toggleTask(id: String) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i >= 0) { tasks[i] = tasks[i].copy(done = !tasks[i].done); save() }
    }

    fun removeTask(id: String) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i < 0) return
        val item = tasks.removeAt(i)
        rememberUndo("已删除待办「${item.text}」") { tasks.add(i.coerceAtMost(tasks.size), item) }
        save()
    }

    fun updateTask(id: String, text: String, meta: String) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i >= 0 && text.isNotBlank()) {
            tasks[i] = tasks[i].copy(text = text.trim(), meta = meta.trim())
            save()
        }
    }

    /**
     * 改一笔订阅。[trialUntil] / [remindAhead] 传 null 表示「这次不改这一项」——
     * 用可空而不是默认值，是因为调用方大多只改名称/金额/日期，
     * 若给默认值会让每次改金额都**顺手把试用日和提前天数抹成默认**。
     */
    fun updateSub(id: String, name: String, amount: Double, date: String, trialUntil: String? = null, remindAhead: Int? = null) {
        val i = subs.indexOfFirst { it.id == id }
        if (i >= 0) {
            subs[i] = subs[i].copy(
                name = name.trim(),
                amount = amount,
                nextDate = date,
                trialUntil = trialUntil ?: subs[i].trialUntil,
                remindAhead = remindAhead ?: subs[i].remindAhead,
            )
            save()
        }
    }

    fun updateObligation(id: String, title: String, date: String, note: String, tag: String) {
        val i = obligations.indexOfFirst { it.id == id }
        if (i >= 0) {
            obligations[i] = obligations[i].copy(title = title.trim(), date = date, note = note.trim(), tag = tag.ifEmpty { obligations[i].tag })
            save()
        }
    }

    fun setReminder(enabled: Boolean, hour: Int, subDays: Int? = null, dueDays: Int? = null) {
        reminderEnabled.value = enabled
        reminderHour.value = hour
        // 可空 = 「这次不改这项」：老调用点只传开关和时间，不该被顺手抹掉提前量
        subDays?.let { reminderSubDays.value = it.coerceIn(0, 90) }
        dueDays?.let { reminderDueDays.value = it.coerceIn(1, 180) }
        save()
    }

    fun setDarkMode(on: Boolean) {
        darkMode.value = on
        save()
    }

    /**
     * 开关应用锁。只存这个布尔值，**不存任何凭据** —— 校验交给系统锁屏（见 ButlerLockGate）。
     * 立刻生效时间点：下次进入应用 / 从后台回来。
     */
    fun setAppLock(on: Boolean) {
        appLockEnabled.value = on
        save()
    }

    /** 小组件实际生效的显示档位：设了具体值用它，-1 就跟随应用锁（开着 → 只显示条数） */
    fun widgetDetailLevel(): Int =
        if (widgetDetail.value >= 0) widgetDetail.value
        else if (appLockEnabled.value) 1 else 0

    /** 0 = 完整, 1 = 只显示条数, 2 = 隐藏正文, -1 = 跟随应用锁 */
    fun setWidgetDetail(v: Int) {
        widgetDetail.value = v
        save()
    }

    /** 设月度支出预算。0 = 不设（那就不提示超支，见 [budgetStatus]） */
    fun setMonthlyBudget(v: Double) {
        monthlyBudget.value = if (v > 0) v else 0.0
        save()
    }

    fun setOnboarded() {
        onboarded.value = true
        save()
    }

    /* ── 本机文件(头像 / 家人照片 / 对话图片 / 档案文件)统一放在 filesDir ── */

    fun fileOf(name: String): File = File(appCtx.filesDir, name)

    /** 是否为自选照片的本地文件路径(而非内置插图代号) */
    fun isLocalPhoto(p: String): Boolean = p.startsWith("/") || p.startsWith("file://")

    private fun decodeShrunk(uri: android.net.Uri, maxDim: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appCtx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDim && bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = appCtx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val side = maxOf(bmp.width, bmp.height)
            if (side <= maxDim) bmp else {
                val ratio = maxDim.toFloat() / side
                Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * ratio).toInt().coerceAtLeast(1),
                    (bmp.height * ratio).toInt().coerceAtLeast(1),
                    true,
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 把相册里选中的图压到 [maxDim] 以内、按 JPEG(质量 85) 写进 [file]。
     *
     * 公开是给「意见反馈」附图片用的 —— 那边也走这一个压缩入口，
     * 别在别处再抄一份解码/降采样(全项目就这一处做这件事)。
     */
    fun saveShrunk(uri: android.net.Uri, file: File, maxDim: Int = 512): Boolean = try {
        val bmp = decodeShrunk(uri, maxDim) ?: return false
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        true
    } catch (e: Exception) {
        false
    }

    /** 把相册选择的照片压缩后存为头像 */
    fun saveAvatar(context: Context, uri: android.net.Uri): Boolean {
        return try {
            val f = File(context.filesDir, "avatar_v1.jpg")
            val ok = saveShrunk(uri, f)
            if (ok) {
                avatarPath.value = f.absolutePath
                save()
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    /** 为某位家人保存自选照片 */
    fun setMemberPhoto(id: String, uri: android.net.Uri): Boolean {
        return try {
            val f = fileOf("member_$id.jpg")
            val ok = saveShrunk(uri, f)
            if (ok) {
                val i = members.indexOfFirst { it.id == id }
                if (i >= 0) {
                    members[i] = members[i].copy(photo = f.absolutePath)
                    save()
                }
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    /* ── 家庭相册(与家人头像分开存,互不覆盖) ── */

    /** 一次可多选:每张单独压缩后落盘,返回真正存进去的张数 */
    fun addAlbumPhotos(uris: List<android.net.Uri>, note: String = ""): Int {
        var ok = 0
        uris.forEach { u ->
            try {
                val f = fileOf("album_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
                if (saveShrunk(u, f, 1600)) {
                    album.add(ButlerPhoto(id(), f.absolutePath, note.trim(), System.currentTimeMillis()))
                    ok++
                }
            } catch (e: Exception) {
            }
        }
        if (ok > 0) save()
        return ok
    }

    fun updateAlbumNote(id: String, note: String) {
        val i = album.indexOfFirst { it.id == id }
        if (i >= 0) {
            album[i] = album[i].copy(note = note.trim())
            save()
        }
    }

    fun removeAlbumPhoto(id: String) {
        val i = album.indexOfFirst { it.id == id }
        if (i < 0) return
        val item = album.removeAt(i)
        val f = File(item.path)
        // 图片文件不立刻删：万一是误删，撤销回来还要用原图
        rememberUndo("已删除相册照片", onDiscard = { try { f.delete() } catch (e: Exception) {} }) {
            album.add(i.coerceAtMost(album.size), item)
        }
        save()
    }

    /** 对话图片:压缩后存本机,返回可直接渲染的绝对路径 */
    fun saveChatPhoto(uri: android.net.Uri): String? {
        return try {
            val f = fileOf("chat_${UUID.randomUUID()}.jpg")
            if (saveShrunk(uri, f)) f.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 把外部文件复制进应用私有目录,返回落盘后的文件名(basename,便于备份还原)。
     * 图片会压缩;其它类型(如 PDF)原样复制,单文件上限 4MB。
     */
    fun importArchiveFile(uri: android.net.Uri, archiveId: String, name: String): Pair<String?, String?> {
        return try {
            val clean = name.replace(Regex("[^\\w.\\-\\u4e00-\\u9fa5]"), "_").takeLast(40).ifEmpty { "file" }
            val isImage = (appCtx.contentResolver.getType(uri) ?: "").startsWith("image/")
            if (isImage) {
                // 图片一律先压到 1024 长边再入库：不压的话，一张 4000 万像素的手机照
                // 轻松超过 4MB，用户只会看到「没能存入」而不知道该怎么办。
                val f = fileOf("arch_${archiveId}_${System.currentTimeMillis()}_$clean.jpg")
                if (saveShrunk(uri, f, 1024)) f.name to null else null to "这张图读不出来（换一张或先导出成文件再试）"
            } else {
                val f = fileOf("arch_${archiveId}_${System.currentTimeMillis()}_$clean")
                val streamed = appCtx.contentResolver.openInputStream(uri)?.use { input ->
                    f.outputStream().use { out -> input.copyTo(out) }
                    true
                } ?: false
                if (!streamed) {
                    null to "这个文件读不到（可能已被移动或没有读取权限）"
                } else if (f.length() > ARCHIVE_MAX_BYTES) {
                    // 空口拒绝不如给条路：把实际大小说出来，用户才知道该压到多少
                    val mb = "%.1f".format(f.length() / 1024.0 / 1024.0)
                    f.delete()
                    null to "这个文件 $mb MB，超过 4 MB 上限 —— 先压缩，或拆成几份再存"
                } else f.name to null
            }
        } catch (e: Exception) {
            null to "存入失败（${e.javaClass.simpleName}）"
        }
    }

    fun addArchiveFiles(archiveId: String, names: List<String>) {
        val i = archive.indexOfFirst { it.id == archiveId }
        if (i >= 0 && names.isNotEmpty()) {
            val merged = (archive[i].files + names).distinct()
            archive[i] = archive[i].copy(files = merged, count = merged.size)
            save()
        }
    }

    /**
     * 把相册里的某张照片**引用**进一个档案组（不复制文件）。
     *
     * 为什么是引用而不是复制：相册与档案本来就是两个孤岛，用户拍了一张保单照片想归到「保单」组里，
     * 复制一份会让盘上多出一份完全相同的大图，删掉一边另一边还在 —— 越用越糊涂。
     * 引用就一个副作用必须照顾：**盘上的文件是真共用的**，所以删档案时不能直接删文件，
     * 见 [fileAlsoUsedByAlbum]。返回该档案组的标题；组不存在返回 null。
     */
    fun addPhotoToArchive(archiveId: String, photoPath: String): String? {
        val i = archive.indexOfFirst { it.id == archiveId }
        if (i < 0) return null
        val base = File(photoPath).name
        if (base.isBlank()) return null
        if (base !in archive[i].files) {
            val merged = archive[i].files + base
            archive[i] = archive[i].copy(files = merged, count = merged.size)
            save()
        }
        return archive[i].title
    }

    /** 这个文件名是否同时挂在相册里 —— 决定删档案时能不能真把盘上的文件删掉 */
    fun fileAlsoUsedByAlbum(fileName: String): Boolean =
        album.any { File(it.path).name == fileName }

    fun removeArchiveFile(archiveId: String, name: String) {
        val i = archive.indexOfFirst { it.id == archiveId }
        if (i < 0 || name !in archive[i].files) return
        val before = archive[i]
        val rest = before.files.filter { it != name }
        archive[i] = before.copy(files = rest, count = rest.size)
        val f = fileOf(name)
        rememberUndo("已删除档案文件「$name」", onDiscard = {
            // 相册里还引用着同一个文件时不能删 —— 否则相册那张图会变成裂图
            if (!fileAlsoUsedByAlbum(name)) try { f.delete() } catch (e: Exception) {}
        }) {
            archive[i] = before
        }
        save()
    }

    /** 全机已归档的真实文件总数 */
    fun archiveFileCount(): Int = archive.sumOf { it.files.size }

    /** [remindAhead] 传 null = 这次不改这一项（理由同 [updateSub]：别顺手把用户的设置抹掉） */
    fun updateMember(id: String, name: String, label: String, date: String, remindAhead: Int? = null) {
        val i = members.indexOfFirst { it.id == id }
        if (i >= 0 && name.isNotBlank()) {
            members[i] = members[i].copy(
                name = name.trim(),
                label = label.trim(),
                date = date,
                remindAhead = remindAhead ?: members[i].remindAhead,
            )
            save()
        }
    }

    /* ── 删除可撤销 ── */

    /**
     * 最近一次删除的「放回原位」动作。
     *
     * 为什么值得做：本机数据**没有云端可以回捞**，删掉就是真没了 —— 这既是这个 App 的卖点，
     * 也意味着误删的代价比联网应用大得多（联网应用还能去后台翻回收站）。
     * 所以每次删除都把还原动作记下来，界面给 5 秒撤销窗口。
     *
     * 只记最后一次：能同时撤销好几条反而让人搞不清到底撤掉了哪一条。
     */
    /** 一次删除的「还原动作」+「该延迟执行的物理删除」 */
    private class UndoEntry(val label: String, val discard: (() -> Unit)?, val restore: () -> Unit)

    /** 撤销窗口能回溯几步。3 步够覆盖"整理相册时连着删几张"这类高频误操作 */
    private val UNDO_MAX = 3

    /**
     * 可撤销删除的栈。
     *
     * 这里有个容易做错的地方：删相册照片/档案文件时若立刻 `File.delete()`，
     * 撤销回来就只剩一个指向空文件的条目（缩略图裂掉、点开是 0 字节）。
     * 所以图片文件的物理删除一律推迟到「确定不撤销」那一刻 —— 也就是
     * **被挤出栈容量**、或者**整个撤销窗口过期**的时候。
     *
     * 为什么从「只记最后一次」改成栈：原来 `rememberUndo` 一进来就把上一条的 `onDiscard`
     * 执行掉（真删文件），于是**连删两张照片之后第一张立刻不可撤销** ——
     * 而撤销条还挂在屏幕上，看起来像是两张都还能撤。本机数据没有云端可以回捞，
     * 连删多条又再正常不过，所以这里留 3 步。
     */
    private val undoStack = ArrayDeque<UndoEntry>()

    /** 每次「可撤销的删除」+1；界面靠它驱动撤销条弹一次 */
    val undoToken: MutableState<Int> = mutableStateOf(0)

    /** 撤销条上显示的话，例如「已删除「燃气费」」；栈里还有别的时会带上「还有 N 项可撤销」 */
    val undoLabel: MutableState<String> = mutableStateOf("")

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /**
     * 「本机数据动过没有」的一把便宜钥匙，**只给「记住上一次搜索结果」当缓存键**用。
     *
     * 搜索页原来把 `searchAll(q)` 直接写在 composable 体里：每敲一个字、甚至每次无关的
     * 重组，都要把 11 个集合从头扫一遍。按关键字 `remember` 一下就够挡住绝大多数重复计算，
     * 但纯按关键字缓存会在「边搜边改」时给出已经过期的结果。
     * 所以再混一个版本号进来：任何一次落盘（也就是任何一次增删改）都会 +1，缓存自然作废。
     */
    private var dataRev = 0

    /**
     * 给 `remember(...)` 当缓存键。把「各集合长度 + 落盘版本号」揉成一个 Int。
     *
     * 为什么不直接把集合当键逐个传：集合一多就写不全，而本项目的老毛病恰恰是
     * 「新增一个集合、六处要一起改」—— 少写一处不会有编译错误，只是搜索结果悄悄过期。
     */
    fun dataStamp(): Int {
        var h = dataRev
        h = h * 31 + tasks.size
        h = h * 31 + subs.size
        h = h * 31 + charges.size
        h = h * 31 + obligations.size
        h = h * 31 + members.size
        h = h * 31 + keyDates.size
        h = h * 31 + memos.size
        h = h * 31 + expenses.size
        h = h * 31 + archive.size
        h = h * 31 + album.size
        h = h * 31 + chat.size
        return h
    }

    private fun rememberUndo(label: String, onDiscard: (() -> Unit)? = null, restore: () -> Unit) {
        undoStack.addLast(UndoEntry(label, onDiscard, restore))
        // 超出容量的最旧一条：到这一刻才真的把它的文件删掉
        while (undoStack.size > UNDO_MAX) {
            runCatching { undoStack.removeFirst().discard?.invoke() }
        }
        undoLabel.value = undoBarText(label)
        undoToken.value = undoToken.value + 1
    }

    /**
     * 撤销条上的话。**条数放在最前面**：这一行是单行 + 省略号，写在后半截的话会被截掉，
     * 而"还能撤几项"恰恰是用户最需要知道的那半句。
     */
    private fun undoBarText(label: String): String =
        if (undoStack.size > 1) "可撤销 ${undoStack.size} 项 · $label" else label

    /** 撤销最近一次删除。没有可撤销的返回 false */
    fun undoLastDelete(): Boolean {
        val e = undoStack.removeLastOrNull() ?: return false
        runCatching { e.restore() }
        save()
        // 栈里还有别的：把撤销条续上（token 再 +1），用户可以接着撤下一条
        if (undoStack.isNotEmpty()) {
            undoLabel.value = undoBarText(undoStack.last().label)
            undoToken.value = undoToken.value + 1
        }
        return true
    }

    /**
     * 撤销窗口过了：丢掉**栈里全部**还原动作，并让推迟的物理删除落地。
     * 之后 undoLastDelete 不再生效。
     */
    fun discardUndo() {
        while (undoStack.isNotEmpty()) {
            runCatching { undoStack.removeLast().discard?.invoke() }
        }
    }

    fun removeSub(id: String) {
        val i = subs.indexOfFirst { it.id == id }
        if (i < 0) return
        val item = subs.removeAt(i)
        rememberUndo("已删除「${item.name}」") { subs.add(i.coerceAtMost(subs.size), item) }
        save()
    }

    /* ── AI 提出来、等用户点头的「改 / 删」 ── */

    /**
     * 待确认的说明，例如「把订阅「网易云音乐」改成：金额 ¥15 → ¥20」。
     * 非空时对话页会弹一张确认卡；用户点「确认」才真的落库。
     *
     * 为什么改删要单独走这一步：新增记错了最多多一条，自己删掉就行；而改 / 删是**动已有记录**，
     * 一旦认错对象（把「网易云音乐」认成「网易严选」）就是直接毁掉正确数据，且本机没有云端可回捞。
     * 所以模型只负责「提出要改什么」，落库这一步必须由用户点一下。
     */
    val pendingFix: MutableState<String> = mutableStateOf("")

    private var pendingFixApply: (() -> String)? = null

    fun proposeFix(desc: String, apply: () -> String) {
        pendingFix.value = desc
        pendingFixApply = apply
    }

    /** 确认执行，返回「做了什么」；没有待确认的返回 null */
    fun confirmFix(): String? {
        val f = pendingFixApply ?: return null
        pendingFixApply = null
        pendingFix.value = ""
        return f()
    }

    fun cancelFix() {
        pendingFixApply = null
        pendingFix.value = ""
    }

    /* ── 扫描/通知自动加入与「不再加回」记忆 ── */

    private val dismissed = linkedSetOf<String>()

    fun isDismissed(name: String): Boolean = dismissed.contains(name)

    fun dismissName(name: String) {
        dismissed.add(name)
        save()
    }

    /**
     * 扫描/通知自动加入订阅。
     * nextDate 只在扫描真的从短信里解析到扣费日时才传;解析不到留空,
     * 界面显示「扣费日待补全」,绝不凭空编一个日期。
     */
    fun addScannedSub(name: String, amount: Double, source: String, nextDate: String = "", force: Boolean = false) {
        val n = name.trim()
        if (n.isEmpty()) return
        if (subs.any { it.name == n }) return
        if (!force && dismissed.contains(n)) return
        if (force) dismissed.remove(n)
        subs.add(ButlerSub(id(), n, amount, nextDate.trim(), false, source, 0L))
        save()
    }

    fun addSub(name: String, amount: Double, date: String, source: String = "手动", trialUntil: String = "", remindAhead: Int = 0) {
        subs.add(ButlerSub(id(), name.trim(), amount, date, false, source, 0L, trialUntil.trim(), remindAhead)); save()
    }

    /** 单条订阅的「提前几天提醒」。0 = 跟随全局设置 */
    fun setSubRemindAhead(id: String, days: Int) {
        val i = subs.indexOfFirst { it.id == id }
        if (i >= 0) {
            subs[i] = subs[i].copy(remindAhead = days.coerceAtLeast(0))
            save()
        }
    }

    /**
     * 试用截止日（空 = 不是试用）。到期怎么提醒跟这条订阅自己的提前量走
     * （[setSubRemindAhead]，默认吃全局「扣费提前」），不再写死"前一天" ——
     * 只提前一天说"明天开始收费"，人多半已经忙忘了，第二天钱就扣掉了。
     */
    fun setSubTrial(id: String, date: String) {
        val i = subs.indexOfFirst { it.id == id }
        if (i >= 0) {
            subs[i] = subs[i].copy(trialUntil = date.trim())
            save()
        }
    }

    fun setObligationRemindAhead(id: String, days: Int) {
        val i = obligations.indexOfFirst { it.id == id }
        if (i >= 0) {
            obligations[i] = obligations[i].copy(remindAhead = days.coerceAtLeast(0))
            save()
        }
    }

    fun setKeyDateRemindAhead(id: String, days: Int) {
        val i = keyDates.indexOfFirst { it.id == id }
        if (i >= 0) {
            keyDates[i] = keyDates[i].copy(remindAhead = days.coerceAtLeast(0))
            save()
        }
    }

    /** 某位家人的「提前几天提醒」。0 = 跟随全局「事项到期 / 纪念日」 */
    fun setMemberRemindAhead(id: String, days: Int) {
        val i = members.indexOfFirst { it.id == id }
        if (i >= 0) {
            members[i] = members[i].copy(remindAhead = days.coerceAtLeast(0))
            save()
        }
    }

    /**
     * 这一条实际用几天做提前量：自己设了用自己的，没设就吃全局。
     * 界面和日报都走这一个函数，免得两边算法走散。
     */
    fun aheadDaysFor(itemAhead: Int, globalDays: Int): Int =
        if (itemAhead > 0) itemAhead else globalDays

    /** 标记为关闭中:写一条真实关闭历史,统计数字由历史复算 */
    fun markSubClosing(id: String, withReceipt: Boolean = true) {
        val i = subs.indexOfFirst { it.id == id }
        if (i >= 0 && !subs[i].closing) {
            val now = System.currentTimeMillis()
            subs[i] = subs[i].copy(closing = true, closingAt = now)
            closedHistory.add(ButlerClosedSub(id(), subs[i].name, subs[i].amount, now))
            if (withReceipt) {
                chat.add(ButlerChat(id(), false, "好，已把「${subs[i].name}」标记为关闭中。取消需要在对应平台完成；之后如果还有扣费，我会提醒你复核。", ""))
            }
            save()
        }
    }

    /* ── 真实扣费流水 ── */

    fun addCharge(subName: String, amount: Double, date: String = LocalDate.now().toString(), source: String = "手动") {
        if (subName.isBlank() || amount <= 0) return
        charges.add(ButlerCharge(id(), subName.trim(), amount, date, System.currentTimeMillis(), source))
        save()
    }

    fun removeCharge(id: String) {
        val i = charges.indexOfFirst { it.id == id }
        if (i < 0) return
        val item = charges.removeAt(i)
        rememberUndo("已删除「${item.subName}」的扣费记录 ${item.amount} 元") { charges.add(i.coerceAtMost(charges.size), item) }
        save()
    }

    /** 某笔订阅的真实扣费记录,按时间倒序 */
    fun chargesOf(subName: String): List<ButlerCharge> =
        charges.filter { it.subName == subName }.sortedByDescending { it.at }

    /** 关闭后仍在扣费:关闭时间之后还有该商户的真实扣费记录 */
    fun hasChargeAfterClosing(sub: ButlerSub): Boolean =
        sub.closing && sub.closingAt > 0 && charges.any { it.subName == sub.name && it.at > sub.closingAt }

    fun addObligation(title: String, date: String, note: String, tag: String = "证件", remindAhead: Int = 0) {
        obligations.add(ButlerObligation(id(), title.trim(), note.trim(), date, tag, false, remindAhead)); save()
    }

    fun toggleObligation(id: String) {
        val i = obligations.indexOfFirst { it.id == id }
        if (i >= 0) { obligations[i] = obligations[i].copy(done = !obligations[i].done); save() }
    }

    fun removeObligation(id: String) {
        val i = obligations.indexOfFirst { it.id == id }
        if (i < 0) return
        val item = obligations.removeAt(i)
        rememberUndo("已删除「${item.title}」") { obligations.add(i.coerceAtMost(obligations.size), item) }
        save()
    }

    fun addMember(name: String, label: String, date: String) {
        members.add(ButlerMember(id(), name.trim(), label.trim(), date, "")); save()
    }

    fun removeMember(id: String) {
        val i = members.indexOfFirst { it.id == id }
        if (i < 0) return
        val item = members.removeAt(i)
        // 头像文件跟着成员走：撤销窗口关了才真删，否则撤回来看不到人像
        val avatar = File(appCtx.filesDir, "member_$id.jpg")
        rememberUndo("已删除成员「${item.name}」", onDiscard = { if (avatar.isFile) try { avatar.delete() } catch (e: Exception) {} }) {
            members.add(i.coerceAtMost(members.size), item)
        }
        save()
    }

    fun addKeyDate(title: String, date: String, note: String, remindAhead: Int = 0) {
        keyDates.add(ButlerKeyDate(id(), title.trim(), date, note.trim(), remindAhead)); save()
    }

    fun removeKeyDate(id: String) {
        val i = keyDates.indexOfFirst { it.id == id }
        if (i < 0) return
        val item = keyDates.removeAt(i)
        rememberUndo("已删除「${item.title}」") { keyDates.add(i.coerceAtMost(keyDates.size), item) }
        save()
    }

    fun addArchive(title: String, note: String) {
        archive.add(ButlerArchive(id(), title.trim(), 0, note.trim(), emptyList())); save()
    }

    fun removeArchive(id: String) {
        val i = archive.indexOfFirst { it.id == id }
        if (i < 0) return
        val item = archive.removeAt(i)
        // 组里的实体文件同样推迟到撤销窗口关闭才删；相册还在用的那些文件跳过
        val files = item.files.map { fileOf(it) to it }
        rememberUndo("已删除档案「${item.title}」，含 ${item.files.size} 个文件", onDiscard = {
            files.forEach { (f, n) ->
                if (!fileAlsoUsedByAlbum(n)) try { f.delete() } catch (e: Exception) {}
            }
        }) {
            archive.add(i.coerceAtMost(archive.size), item)
        }
        save()
    }

    /**
     * 追加一条对话。
     *
     * 为什么要在这里裁剪：原来是无上限的 `chat.add(...)`，而 `save()` 每次都把**整份**状态
     * 序列化进 SharedPreferences —— 聊几个月之后，每说一句话都要重写一整份越来越大的 JSON，
     * 启动时还要把整份读回内存。表现是「越用越卡」，而用户完全不知道原因。
     *
     * 上限 [CHAT_MAX] 条：AI 提示词只用最近 8 条、诊断日志只用 40 条 —— 更旧的记录**存着也根本不用**。
     * 被裁掉的对话如果带图，顺手把文件删掉，否则盘上会留下没人引用的孤儿图片。
     */
    fun addChat(fromUser: Boolean, text: String, photoPath: String = "") {
        chat.add(ButlerChat(id(), fromUser, text, photoPath))
        if (chat.size > CHAT_MAX) {
            val drop = chat.size - CHAT_MAX
            val removed = chat.take(drop).toList()
            repeat(drop) { chat.removeAt(0) }
            deleteChatPhotos(removed)
        }
        save()
    }

    /** 清空对话记录（连带删掉对话里的图片文件）。界面上给一个明确的入口，别让用户只能靠卸载 */
    fun clearChat() {
        val removed = chat.toList()
        chat.clear()
        deleteChatPhotos(removed)
        save()
    }

    private fun deleteChatPhotos(items: List<ButlerChat>) {
        items.forEach { c ->
            if (c.photoPath.isNotBlank()) {
                try {
                    File(c.photoPath).delete()
                } catch (e: Exception) {
                }
            }
        }
    }

    /* ── 备忘录(新建 / 编辑 / 删除 / 分类 / 搜索 / 排序 / 提醒) ── */

    /** 新增一条备忘;标题与正文全空则不落库。返回真正写进去的那条(便于界面继续操作) */
    fun addMemo(title: String, content: String, category: String, remindAt: Long): ButlerMemo? {
        val t = title.trim()
        val c = content.trim()
        if (t.isEmpty() && c.isEmpty()) return null
        val now = System.currentTimeMillis()
        val m = ButlerMemo(
            id = id(),
            title = t.ifEmpty { "无标题" },
            content = c,
            category = category.trim().ifEmpty { MEMO_UNCATEGORIZED },
            pinned = false,
            createdAt = now,
            updatedAt = now,
            remindAt = remindAt,
        )
        memos.add(0, m)
        save()
        syncMemoReminder(m)
        return m
    }

    /** 编辑:更新时间戳;提醒时间改动会同步重排本机闹钟 */
    fun updateMemo(id: String, title: String, content: String, category: String, remindAt: Long) {
        val i = memos.indexOfFirst { it.id == id }
        if (i < 0) return
        val t = title.trim()
        val c = content.trim()
        if (t.isEmpty() && c.isEmpty()) return
        val updated = memos[i].copy(
            title = t.ifEmpty { "无标题" },
            content = c,
            category = category.trim().ifEmpty { memos[i].category.ifEmpty { MEMO_UNCATEGORIZED } },
            remindAt = remindAt,
            updatedAt = System.currentTimeMillis(),
        )
        memos[i] = updated
        save()
        syncMemoReminder(updated)
    }

    /** 置顶 / 取消置顶 */
    fun toggleMemoPin(id: String) {
        val i = memos.indexOfFirst { it.id == id }
        if (i >= 0) {
            memos[i] = memos[i].copy(pinned = !memos[i].pinned)
            save()
        }
    }

    fun removeMemo(id: String) {
        val i = memos.indexOfFirst { it.id == id }
        if (i < 0) return
        val item = memos.removeAt(i)
        // 撤销回来必须把闹钟重新排上，不然「撤销了但提醒没了」
        rememberUndo("已删除备忘「${item.title}」") {
            memos.add(i.coerceAtMost(memos.size), item)
            syncMemoReminder(item)
        }
        save()
        MemoReminders.cancel(appCtx, id)
    }

    /** 关键词搜索:标题与正文均匹配,忽略大小写;空关键词返回全部 */
    fun searchMemos(keyword: String): List<ButlerMemo> {
        val k = keyword.trim()
        if (k.isEmpty()) return memos.toList()
        return memos.filter { it.title.contains(k, true) || it.content.contains(k, true) }
    }

    /**
     * 跨模块的一次性检索（订阅 / 扣费 / 待办 / 到期 / 家人 / 纪念日 / 备忘 / 记账 / 档案 / 相册 / 对话）。
     *
     * 为什么值得单开一个：原来只有备忘录能搜，用了半年之后「去年那笔 25 是哪一项」
     * 只能一屏屏翻。订阅几十条、记账几百笔时尤其难受。
     *
     * 空查询返回空表（**不**返回全部）—— 搜索页一进去就铺出几百条，比什么都不显示更让人发懵。
     */
    fun searchAll(query: String): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        fun hit(s: String?) = s != null && s.contains(q, true)
        val out = mutableListOf<SearchHit>()

        tasks.filter { hit(it.text) || hit(it.meta) }
            .forEach { out += SearchHit("待办", it.id, it.text, if (it.done) "已完成" else "未完成", "today") }
        subs.filter { hit(it.name) || hit(it.source) }
            .forEach { out += SearchHit("订阅", it.id, it.name, "¥${fmtMoney(it.amount)} · ${it.nextDate}", "guard", it.id) }
        charges.filter { hit(it.subName) }
            .forEach {
                // 一笔扣费只出现在它那笔订阅的详情页里（守护页没有单独的流水列表），
                // 所以「点一条流水」应该是：翻到那笔订阅的详情，并滚到这一行。
                // route = detail + subId = 归属订阅，定位到的是**这一笔扣费**的 id。
                val owner = subs.firstOrNull { s -> s.name == it.subName }
                out += SearchHit(
                    "扣费流水", it.id, it.subName,
                    "¥${fmtMoney(it.amount)} · ${it.date} · ${it.source}",
                    if (owner != null) "detail" else "guard",
                    owner?.id,
                )
            }
        obligations.filter { hit(it.title) || hit(it.note) || hit(it.tag) }
            .forEach { out += SearchHit("到期事务", it.id, it.title, "${it.date} · ${it.tag}", "duties") }
        members.filter { hit(it.name) || hit(it.label) }
            .forEach { out += SearchHit("家人", it.id, it.name, "${it.label} · ${it.date}", "family") }
        keyDates.filter { hit(it.title) || hit(it.note) }
            .forEach { out += SearchHit("纪念日", it.id, it.title, it.date, "family") }
        memos.filter { hit(it.title) || hit(it.content) }
            .forEach { out += SearchHit("备忘录", it.id, it.title, it.content.lineSequence().firstOrNull { l -> l.isNotBlank() }.orEmpty().take(40), "memo") }
        expenses.filter { hit(it.note) || hit(it.category) }
            .forEach { out += SearchHit("记账", it.id, it.note.ifEmpty { it.category }, "${if (it.income) "+" else ""}¥${fmtMoney(it.amount)} · ${it.date}", "ledger") }
        archive.filter { hit(it.title) || hit(it.note) || it.files.any { f -> hit(f) } }
            .forEach { out += SearchHit("档案", it.id, it.title, "${it.files.size} 个文件 · ${it.note}", "vault") }
        album.filter { hit(it.note) }
            .forEach { out += SearchHit("相册", it.id, it.note.ifEmpty { "相册照片" }, fmtCn(dateOfMillis(it.at).toString()), "family") }
        chat.filter { hit(it.text) }
            .forEach { out += SearchHit("对话", it.id, it.text.lineSequence().first().take(40), if (it.fromUser) "你说" else "管家说", "chat") }

        return out
    }

    /** 排序:置顶恒在前;其余按所选时间键。按提醒时间时,未设提醒的排在最后 */
    fun sortedMemos(list: List<ButlerMemo>, order: MemoSort): List<ButlerMemo> = when (order) {
        MemoSort.Updated -> list.sortedWith(
            compareByDescending<ButlerMemo> { it.pinned }.thenByDescending { it.updatedAt },
        )
        MemoSort.Created -> list.sortedWith(
            compareByDescending<ButlerMemo> { it.pinned }.thenByDescending { it.createdAt },
        )
        MemoSort.Remind -> list.sortedWith(
            compareByDescending<ButlerMemo> { it.pinned }
                .thenBy { if (it.remindAt <= 0L) Long.MAX_VALUE else it.remindAt },
        )
    }

    fun memoCountOf(category: String): Int = memos.count { it.category == category }

    /** 新增分类:重名(忽略大小写)或空名不生效 */
    fun addMemoCategory(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || memoCategories.value.any { it.equals(n, true) }) return false
        memoCategories.value = memoCategories.value + n
        save()
        return true
    }

    /** 删除分类:其下的备忘改挂「未分类」,不会连带删除任何内容 */
    fun removeMemoCategory(name: String) {
        if (name == MEMO_UNCATEGORIZED) return
        if (!memoCategories.value.contains(name)) return
        val catsBefore = memoCategories.value
        // 记下被改挂的每一条，撤销时原样还回原分类
        val moved = memos.filter { it.category == name }.map { it.id to it }
        memoCategories.value = catsBefore.filter { it != name }
        for (i in memos.indices) {
            if (memos[i].category == name) memos[i] = memos[i].copy(category = MEMO_UNCATEGORIZED)
        }
        rememberUndo("已删除分类「$name」") {
            memoCategories.value = catsBefore
            moved.forEach { (id, m) ->
                val j = memos.indexOfFirst { it.id == id }
                if (j >= 0) memos[j] = m
            }
        }
        save()
    }

    /* ── 记账分类（与备忘分类同构：可自增自删，删分类不连带删记录） ── */

    /** 新增记账分类。重名（忽略大小写）或空名不生效；@return 是否真的加上了 */
    fun addExpenseCategory(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || expenseCategories.value.any { it.equals(n, true) }) return false
        expenseCategories.value = expenseCategories.value + n
        save()
        return true
    }

    /**
     * 删除记账分类：其下的账目**改挂到「其他」**，一笔都不删。
     *
     * 为什么挂「其他」而不是像备忘那样挂「未分类」：记账的兜底分类一直是「其他」，
     * 多出一个「未分类」会让月报的分类榜凭空多一条谁都不认识的项。
     */
    fun removeExpenseCategory(name: String) {
        if (name == EXPENSE_FALLBACK) return
        val cats = expenseCategories.value
        if (!cats.contains(name)) return
        val moved = expenses.filter { it.category == name }.map { it.id to it }
        expenseCategories.value = cats.filter { it != name }
        for (i in expenses.indices) {
            if (expenses[i].category == name) expenses[i] = expenses[i].copy(category = EXPENSE_FALLBACK)
        }
        rememberUndo("已删除分类「$name」") {
            expenseCategories.value = cats
            moved.forEach { (id, e) ->
                val j = expenses.indexOfFirst { it.id == id }
                if (j >= 0) expenses[j] = e
            }
        }
        save()
    }

    fun expenseCountOf(category: String): Int = expenses.count { it.category == category }

    /* ── 手动补的「监听名单」（通知扣费捕获） ── */

    /** @return 是否真的加上了（空 / 重复 / 明显不是包名的不加） */
    fun addWatchedPackage(pkg: String): Boolean {
        val p = pkg.trim()
        if (p.isEmpty() || !p.contains('.') || p.contains(' ')) return false
        if (watchedExtraPackages.value.any { it.equals(p, true) }) return false
        watchedExtraPackages.value = watchedExtraPackages.value + p
        save()
        return true
    }

    fun removeWatchedPackage(pkg: String) {
        if (watchedExtraPackages.value.none { it == pkg }) return
        watchedExtraPackages.value = watchedExtraPackages.value.filter { it != pkg }
        save()
    }

    /* ── 扣费线索的认领（通知捕获不再直接落库，等用户点一下） ── */

    /**
     * 记一条待认领的扣费线索。同名 3 天内只留一条（同一条通知反复推，不该刷屏）。
     * @return 是否真的新增了
     */
    fun addPendingClaim(name: String, amount: Double, at: Long, nextDate: String, pkg: String, snippet: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return false
        // 用户点过「不是我的」的商户不再回来问。
        // 这条防线以前只有"入守护清单"那一侧看 dismissed，这里没看 —— 而通知栏里那条通知可能还挂着，
        // 监听服务每次重连都会回扫一遍（见 NotifListenerService.onListenerConnected），
        // 结果就是"我说了不是我的，它还一直问"。
        if (isDismissed(n)) return false
        if (pendingClaims.any { it.name == n && at - it.at < 3L * 86400000L }) return false
        pendingClaims.add(ButlerClaim(id(), n, amount, at, nextDate, pkg, snippet.take(70)))
        save()
        return true
    }

    /**
     * 认领一条线索 —— **这时才**写真实扣费流水、才加进守护清单。
     *
     * 用户点「认得」= 他确认这笔确实是自己花的。这一步之前，扣费记录与守护清单都是干净的：
     * 通知里的关键词判据太宽（「支出」「付款」都算），不该由它替用户认定"这是笔订阅"。
     */
    fun confirmClaim(id: String) {
        val i = pendingClaims.indexOfFirst { it.id == id }
        if (i < 0) return
        val c = pendingClaims.removeAt(i)
        if (c.amount > 0) addCharge(c.name, c.amount, LocalDate.now().toString(), "通知")
        if (subs.none { it.name == c.name } && !isDismissed(c.name)) {
            addScannedSub(c.name, c.amount, "通知", c.nextDate)
        }
        save()
    }

    /** 不认这条线索：把这个商户记进「不再加回」，同名的以后也不会再自动提示 */
    fun dismissClaim(id: String) {
        val i = pendingClaims.indexOfFirst { it.id == id }
        if (i < 0) return
        val c = pendingClaims.removeAt(i)
        dismissName(c.name)
        save()
    }

    fun claimCount(): Int = pendingClaims.size

    /** 把「已到点」的备忘提醒说明白:设了未来提醒才排闹钟,否则确保取消 */
    private fun syncMemoReminder(m: ButlerMemo) {
        if (m.remindAt > System.currentTimeMillis()) {
            MemoReminders.schedule(appCtx, m.id, m.title, m.remindAt)
        } else {
            MemoReminders.cancel(appCtx, m.id)
        }
    }

    /* ── 记账（支出 / 收入 + 月度预算） ── */

    /**
     * 记一笔账。[date] 留空 = 记成今天;填了就是**补记**（「昨晚忘了记」是最高频的场景）。
     *
     * 为什么必须能填日期:原来日期写死 `LocalDate.now()`,补记昨天那笔只能记成今天 ——
     * 于是「今天花了 85」里混进昨天的 60、昨天显示 0、近 7 天趋势条跟着整体错位。
     * 用户唯一的办法是骗自己,或者干脆不补。
     */
    fun addExpense(amount: Double, category: String, note: String, income: Boolean = false, date: String = "") {
        if (amount <= 0) return
        val d = normExpenseDate(date)
        expenses.add(
            ButlerExpense(
                id(), amount,
                category.ifEmpty { if (income) "收入" else "其他" },
                note.trim(), d, atForDate(d), income,
            ),
        )
        save()
    }

    fun updateExpense(id: String, amount: Double, category: String, note: String, income: Boolean = false, date: String = "") {
        val i = expenses.indexOfFirst { it.id == id }
        if (i >= 0 && amount > 0) {
            // 编辑时留空 = 保持这笔原来的日期(而不是悄悄改成今天)
            val d = normExpenseDate(date.ifBlank { expenses[i].date })
            expenses[i] = expenses[i].copy(
                amount = amount,
                category = category.ifEmpty { if (income) "收入" else "其他" },
                note = note.trim(),
                income = income,
                date = d,
                at = atForDate(d),
            )
            save()
        }
    }

    /** 记账日期归一化:空或解析不了 → 今天 */
    private fun normExpenseDate(raw: String): String =
        if (raw.isBlank()) LocalDate.now().toString()
        else (parseDate(raw)?.toString() ?: LocalDate.now().toString())

    /**
     * 某一天对应的排序时间戳。
     *
     * 不能一律用"此刻":补记 8 天前的那笔时,`at` 若是现在,
     * 它在「全部记录」里会排到最前面,看起来像是刚花的钱。
     * 过去的日子取当天正午(同一天内多次补记的相对先后无所谓)。
     */
    private fun atForDate(date: String): Long {
        val d = parseDate(date) ?: return System.currentTimeMillis()
        if (d == LocalDate.now()) return System.currentTimeMillis()
        return d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun removeExpense(id: String) {
        val i = expenses.indexOfFirst { it.id == id }
        if (i < 0) return
        val item = expenses.removeAt(i)
        rememberUndo("已删除一笔 ${item.amount} 元${if (item.income) "收入" else "支出"}") { expenses.add(i.coerceAtMost(expenses.size), item) }
        save()
    }

    /** 今日**支出**（不含收入）—— 首页那个数字的口径始终是「花掉多少」，不混进收入免得看不懂 */
    fun expenseTotalToday(): Double {
        val t = LocalDate.now().toString()
        return expenses.filter { it.date == t && !it.income }.sumOf { it.amount }
    }

    fun expenseCountToday(): Int {
        val t = LocalDate.now().toString()
        return expenses.count { it.date == t }
    }

    /** 分类小计只算支出：把「工资」混进「餐饮」那种分类榜里没有意义 */
    fun expenseCategoryTotals(list: List<ButlerExpense>): List<Pair<String, Double>> =
        list.filter { !it.income }.groupBy { it.category }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }

    /** 一段账目里的支出合计 */
    fun spendOf(list: List<ButlerExpense>): Double = list.filter { !it.income }.sumOf { it.amount }

    /** 一段账目里的收入合计 */
    fun incomeOf(list: List<ButlerExpense>): Double = list.filter { it.income }.sumOf { it.amount }

    /**
     * 本月预算状态。
     *
     * @return null 表示**用户没设预算** —— 这种情况下一律不提示超支。
     *         凭空替他定一个数然后说「你超支了」，是编造出来的焦虑，宁可不提。
     *         非 null 时是 (已花, 预算, 是否超支)。
     */
    fun budgetStatus(): Triple<Double, Double, Boolean>? {
        val b = monthlyBudget.value
        if (b <= 0) return null
        val now = LocalDate.now()
        val spent = spendOf(expensesInMonth(now.year, now.monthValue))
        return Triple(spent, b, spent > b)
    }

    /**
     * 「涨价了吗」：同一商户最近两笔真实扣费里，后一笔比前一笔贵多少。
     *
     * @return null = 没有两笔可比（只有一笔、或最新一笔没更贵）。
     *         这个数**只由真实扣费记录算出**，不推算、不预测 —— 与「不伪造已扣」的底线一致。
     */
    fun priceJumpOf(subName: String): Double? {
        val list = charges.filter { it.subName == subName }.sortedByDescending { it.at }
        if (list.size < 2) return null
        val jump = list[0].amount - list[1].amount
        return if (jump > 0.004) jump else null
    }

    /** 试用期还有几天到期（null = 没设试用日 / 已经过了很久）；只往前看，不补报旧的 */
    fun trialDaysLeft(sub: ButlerSub): Long? {
        if (sub.trialUntil.isBlank()) return null
        val d = daysUntil(sub.trialUntil) ?: return null
        return if (d >= 0) d else null
    }

    fun expensesInMonth(year: Int, month: Int): List<ButlerExpense> =
        expenses.filter {
            val d = parseDate(it.date) ?: return@filter false
            d.year == year && d.monthValue == month
        }

    /**
     * 这个月「还会被扣、但还没发生」的订阅 —— 用来回答「这个月还有多少钱要扣」。
     *
     * 这一条与诚信约定里「不伪造已扣」并不冲突,区别在于**口径**:
     * 这里是用户自己填的 `nextDate + amount`,明说成「预计·尚未发生」,
     * 绝不混进 [spendOf] / 记账流水 / 结余里 —— 那些位置只认真实记过的账。
     * 只算未关闭、且下次扣费日落在这个月内的订阅。
     */
    fun subsDueInMonth(year: Int, month: Int): List<ButlerSub> =
        subs.filter {
            if (it.closing) return@filter false
            val d = parseDate(it.nextDate) ?: return@filter false
            d.year == year && d.monthValue == month
        }

    fun closedInMonth(year: Int, month: Int): List<ButlerClosedSub> =
        closedHistory.filter {
            val d = dateOfMillis(it.closedAt)
            d.year == year && d.monthValue == month
        }

    /** 从一句话里猜记账分类(离线规则模式和 AI 管家共用) */
    fun guessExpenseCategory(text: String): String = when {
        Regex("吃|饭|餐|外卖|奶茶|咖啡|火锅|烧烤|水果|零食|早餐|午餐|晚餐|宵夜|食堂").containsMatchIn(text) -> "餐饮"
        Regex("打车|地铁|公交|加油|停车|高铁|机票|单车|滴滴|出租车").containsMatchIn(text) -> "交通"
        Regex("买|购|淘宝|京东|拼多多|快递|衣服|鞋|数码|超市|商场").containsMatchIn(text) -> "购物"
        Regex("房租|水电|燃气|物业|日用品|家居|清洁|宽带|话费").containsMatchIn(text) -> "居家"
        Regex("电影|游戏|演出|展览|KTV|唱歌|门票|娱乐").containsMatchIn(text) -> "娱乐"
        Regex("药|医院|挂号|体检|牙|诊所|医保|看病").containsMatchIn(text) -> "医疗"
        Regex("红包|礼物|随礼|人情|份子|请客").containsMatchIn(text) -> "人情"
        else -> "其他"
    }

    /* ── 迷你管家大脑:对话里可真正完成记录与查询 ── */

    fun reply(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return "我在。把事情说给我，或问「今天有什么安排」。"

        // ① 记账优先:必须排在「记一下 / 记个…」之前,否则「记个账」会被当成待办
        val isExpenseIntent = Regex("^(记账|记一笔|记个账|记下账|花销|支出|花了)[：:，,、\\s]?").containsMatchIn(t) ||
            (t.contains("花了") && Regex("\\d").containsMatchIn(t))
        if (isExpenseIntent) {
            val amount = Regex("(\\d+(?:\\.\\d{1,2})?)").find(t)?.groupValues?.get(1)?.toDoubleOrNull()
            if (amount != null && amount > 0) {
                val cat = guessExpenseCategory(t)
                val note = t.replaceFirst(Regex("^(记账|记一笔|记个账|记下账|花销|支出|花了)[：:，,]?\\s*"), "")
                    .replace(Regex("\\d+(?:\\.\\d{1,2})?"), "")
                    .replace(Regex("元|块钱|块"), "")
                    .trim(' ', '，', ',', '。', '、', '的', '-')
                addExpense(amount, cat, note)
                return "好，记下了：$cat ¥${fmtMoney(amount)}" + (if (note.isNotEmpty()) "（$note）" else "") +
                    "。今天累计 ¥${fmtMoney(expenseTotalToday())}，共 ${expenseCountToday()} 笔。"
            }
            return "想记多少钱？像这样：「记账：午饭 25」。"
        }

        // ② 记事(记一下 / 提醒我 / 别忘了 …)
        val m = Regex("(?:帮我)?(?:记一下|记下|记个|备忘|提醒我|别忘了|加个?待办|添加待办)[：:，,、\\s]?").find(t)
        if (m != null) {
            val cleaned = (t.substring(0, m.range.first) + t.substring(m.range.last + 1))
                .trim(' ', '。', '!', '！', '~', '、', '，', ',')
            if (cleaned.isNotEmpty()) {
                addTask(cleaned)
                return "好，已经记进「今天要做的事」：$cleaned。完成后在今日页勾掉就行。"
            }
        }

        // ③ 取消 / 退订 → 直接进关闭流程
        if (t.contains("取消") || t.contains("退订")) {
            val frag = t
                .replace(Regex("自动续费|帮我|我想|我要|取消|退订|关闭|关掉"), "")
                .trim(' ', '：', ':', '，', ',', '。')
            val hit = subs.filter { !it.closing }.firstOrNull { frag.isNotEmpty() && (it.name.contains(frag) || frag.contains(it.name)) }
            return if (hit != null) {
                markSubClosing(hit.id, withReceipt = false)
                "好，已在守护清单把「${hit.name}」标记为关闭中。真正的取消要在对应平台完成，步骤在详情页；之后如果仍有扣费，我会提醒你复核。"
            } else {
                "想取消哪一项？说「取消 爱奇艺」这样的名字就行；找不到的话，先去「守护」页看一眼名字。"
            }
        }

        // ④ 查账:今天花了多少
        if ((t.contains("花了") || t.contains("花销") || t.contains("花费") || t.contains("支出")) && (t.contains("多少") || t.contains("查") || t.contains("看"))) {
            val t0 = LocalDate.now().toString()
            val todayList = expenses.filter { it.date == t0 }
            if (todayList.isEmpty()) return "今天还没记账。说「记账：午饭 25」，或在「今日」页点「今天花的钱」记一笔。"
            val total = todayList.sumOf { it.amount }
            val cats = expenseCategoryTotals(todayList).take(3)
            val seven = (0..6).sumOf { i -> expenses.filter { it.date == LocalDate.now().minusDays(i.toLong()).toString() }.sumOf { it.amount } }
            return "今天花了 ¥${fmtMoney(total)} · ${todayList.size} 笔：" + cats.joinToString("、") { "${it.first} ¥${fmtMoney(it.second)}" } + "。最近 7 天合计 ¥${fmtMoney(seven)}。"
        }

        // ⑤ 今天的安排 / 待办清单
        if (t.contains("今天") || t.contains("安排") || t.contains("要做什么") || t.contains("有什么") || t.contains("待办")) {
            val pending = tasks.count { !it.done }
            val list = tasks.filter { !it.done }.take(3).joinToString("、") { it.text }
            val nextOb = obligations.filter { !it.done }
                .mapNotNull { o -> daysUntil(o.date)?.let { o to it } }
                .minByOrNull { it.second }
            val nextSub = subs.filter { !it.closing }
                .mapNotNull { s -> daysUntil(s.nextDate)?.let { s to it } }
                .minByOrNull { it.second }
            val sb = StringBuilder("今天还有 $pending 件待办")
            if (list.isNotEmpty()) sb.append("：").append(list)
            nextOb?.let { sb.append(";最近到期「${it.first.title}」${daysText(it.first.date)}") }
            nextSub?.let { sb.append(";下一笔扣费「${it.first.name}」${daysText(it.first.nextDate)}") }
            return sb.append("。").toString()
        }

        // ⑥ 订阅 / 开销
        if (t.contains("订阅") || t.contains("扣费")) {
            val active = subs.filter { !it.closing }
            if (active.isEmpty()) return "现在还没有订阅记录，在「守护」页右上角可以添加。"
            val total = active.sumOf { it.amount }
            val noDate = active.count { it.nextDate.isBlank() }
            return "现在有 ${active.size} 笔订阅，每月合计 ¥${fmtMoney(total)}" +
                (if (noDate > 0) "（其中 $noDate 笔还不知道扣费日，可以在守护页补全）" else "") +
                "；已经标记关闭 ${closedCount} 笔，每月少支出 ¥${fmtMoney(monthlySaved)}。"
        }

        // ⑦ 省下
        if (t.contains("省下") || t.contains("省了")) {
            return if (closedCount == 0) {
                "还没有关闭过订阅，所以还没有省下的记录。"
            } else {
                "已经标记关闭 $closedCount 笔订阅，每月少支出 ¥${fmtMoney(monthlySaved)}。"
            }
        }

        // ⑧ 重要的日子
        if (t.contains("生日") || t.contains("纪念日") || t.contains("日子") || t.contains("复诊") || t.contains("疫苗")) {
            val kd = keyDates.sortedBy { daysUntil(it.date) ?: Long.MAX_VALUE }.take(2)
            val mem = members.sortedBy { daysUntil(it.date) ?: Long.MAX_VALUE }.firstOrNull { daysUntil(it.date) != null }
            if (kd.isEmpty() && mem == null) {
                return "还没有记日子。去「家庭」页把生日、复诊记下来，我来替你数。"
            }
            val parts = mutableListOf<String>()
            kd.forEach { parts.add("「${it.title}」${daysText(it.date)}") }
            mem?.let { parts.add("「${it.name}的${it.label}」${daysText(it.date)}") }
            return "最近的日子：" + parts.joinToString("；") + "。都在「家庭」页。"
        }

        // ⑨ 帮助
        if (t.contains("能") || t.contains("会什么") || t.contains("帮助") || t.contains("怎么用")) {
            return "我能做这些：说「记一下：……」记事；说「记账：午饭 25」记花销；说「取消 XX」进关闭流程；问「今天有什么安排」「今天花了多少」「最近有什么日子」。"
        }

        return "这句我还没听懂。可以试试：「记一下：明天交房租」／「记账：午饭 25」／「取消 爱奇艺」。"
    }

    fun renameProfile(name: String) {
        val n = name.trim()
        if (n.isNotEmpty()) {
            profileName.value = n
            save()
        }
    }

    fun setEmergency(blood: String, medsText: String, contact: String) {
        bloodType.value = blood.trim()
        this.meds.value = medsText.trim()
        emergencyContact.value = contact.trim()
        save()
    }

    /** 载入演示数据:仅当用户主动点击时执行,所有演示订阅都标来源「演示」 */
    fun loadDemo() {
        clearLists()
        profileName.value = "小满"
        bloodType.value = ""; meds.value = ""; emergencyContact.value = ""
        startDate = LocalDate.now().toString()
        seedDemoData()
        save()
    }

    /** 排查用的 debug 通道(只有 debug 包写 logcat)。release 包里这个方法什么都不做。 */
    fun debugFloat(msg: String) {
        if (debuggable) android.util.Log.d("LbState", "[float] $msg")
    }

    /**
     * 记下悬浮管家被拖到的位置（归一化坐标 0~1）。拖动松手时调一次，拖动过程中不要调——
     * 每帧写一次盘纯属浪费，位置也不需要那么高的落盘频率。
     */
    fun setButlerPos(fx: Float, fy: Float) {
        butlerFx = fx.coerceIn(0f, 1f)
        butlerFy = fy.coerceIn(0f, 1f)
        // debug 包留一行:这个值只应该"用户拖完"才变。要是界面上出现了没拖它却变了的情况,
        // 有这行才能分清是「真发生了一次拖动」还是别处在写。release 包里不写。
        if (debuggable) android.util.Log.d("LbState", "[float] setButlerPos $butlerFx $butlerFy")
        save()
    }

    fun hasAnyRecord(): Boolean = tasks.isNotEmpty() || subs.isNotEmpty() || obligations.isNotEmpty() ||
        members.isNotEmpty() || keyDates.isNotEmpty() || archive.isNotEmpty() ||
        expenses.isNotEmpty() || charges.isNotEmpty() || closedHistory.isNotEmpty() ||
        album.isNotEmpty() || memos.isNotEmpty() || pendingClaims.isNotEmpty()

    /**
     * 本机现有记录条数。恢复是**整体覆盖**，所以动手前得先让用户看到"会替换掉多少东西"。
     * 口径与 [hasAnyRecord] 一致（不含已关闭的订阅历史这类附属数据）。
     */
    fun recordCount(): Int = tasks.size + subs.size + obligations.size + members.size +
        keyDates.size + archive.size + expenses.size + charges.size + album.size + memos.size

    /** 清空全部数据(从空开始,仅保留一句欢迎语) */
    fun clearAll() {
        // 顺带清掉本机私人文件,避免「已清空」后照片还留在磁盘上
        // (前缀白名单统一走 isPrivateFile —— 原来这里漏了 avatar_,头像是清不掉的)
        pruneLocalFiles(emptySet())
        // 先把备忘提醒的闹钟逐个取消,再清空数据(清空后就找不到这些 id 了)
        memos.forEach { MemoReminders.cancel(appCtx, it.id) }
        clearLists()
        profileName.value = "小满"
        avatarPath.value = ""
        bloodType.value = ""; meds.value = ""; emergencyContact.value = ""
        reminderEnabled.value = true
        reminderHour.value = 9
        darkMode.value = false
        // 悬浮管家回到默认位置（贴右侧）。它不算「记录」，只是界面摆放，但清空也一并复位更符合直觉。
        butlerFx = -1f; butlerFy = -1f
        startDate = LocalDate.now().toString()
        chat.add(ButlerChat(id(), false, "数据已清空，从今天开始记录吧。说「记一下：…」试试，或去「守护」页扫描本机自动续费。", ""))
        save()
    }

    private fun clearLists() {
        tasks.clear(); subs.clear(); obligations.clear(); members.clear()
        keyDates.clear(); archive.clear(); chat.clear(); expenses.clear()
        charges.clear(); closedHistory.clear(); dismissed.clear(); album.clear()
        memos.clear(); memoCategories.value = DEFAULT_MEMO_CATEGORIES
        expenseCategories.value = DEFAULT_EXPENSE_CATEGORIES
        pendingClaims.clear(); watchedExtraPackages.value = emptyList()
    }

    /* ── 备份与恢复 ── */

    private val BLOB_BUDGET = 8_000_000
    private val BLOB_PER_FILE = 600_000

    /**
     * 对话记录上限。超出就丢最旧的。
     *
     * 为什么是 500 而不是更小：AI 提示词只用最近 8 条，但**用户自己会往回翻**
     * （「上周你说过什么」）。500 条足够覆盖几个月的正常使用，同时把
     * 「每次 save() 重写整份 JSON」的成本压在可控范围内。
     */
    private val CHAT_MAX = 500

    /** 非图片档案的单文件上限。图片不走这里（入库前已压到 1024 长边） */
    private val ARCHIVE_MAX_BYTES = 4L * 1024 * 1024

    /**
     * 备份:状态 JSON + 头像/家人照片/对话图片/档案文件(base64),换机可还原。
     *
     * 顺带把「有文件因为体积上限没能进去」如实交出去（见 [BackupExport]）。
     * 这是唯一一条保命通道,让它静默地不完整是最糟的做法 ——
     * 用户会一路以为"全备好了",直到换机恢复才发现档案是空的。
     */
    fun exportAll(): BackupExport {
        val base = try {
            JSONObject(prefs.getString("state_v1", "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        // 格式指纹:恢复时用它认自己的备份,比"有没有 name 字段"可靠得多
        base.put(BACKUP_APP_KEY, BACKUP_APP_ID)
        base.put(BACKUP_VER_KEY, BACKUP_VER)
        var count = 0
        val skipped = linkedMapOf<String, Int>()
        try {
            val blobs = JSONObject()
            var budget = BLOB_BUDGET
            val seen = mutableSetOf<String>()
            fun putFile(file: File?, category: String) {
                if (file == null || !file.exists()) return
                if (!seen.add(file.name)) return
                if (file.length() > BLOB_PER_FILE) {
                    skipped[category] = (skipped[category] ?: 0) + 1
                    return
                }
                val enc = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                if (enc.length > budget) {
                    // 额度用光:记下来,别让它悄悄消失
                    skipped[category] = (skipped[category] ?: 0) + 1
                    return
                }
                budget -= enc.length
                blobs.put(file.name, enc)
                count++
            }
            putFile(File(appCtx.filesDir, "avatar_v1.jpg"), "头像")
            members.forEach { m -> if (m.photo.startsWith("/")) putFile(File(m.photo), "家人照片") }
            album.forEach { p -> if (p.path.startsWith("/")) putFile(File(p.path), "相册") }
            chat.forEach { c -> if (c.photoPath.startsWith("/")) putFile(File(c.photoPath), "对话图片") }
            archive.forEach { a -> a.files.forEach { putFile(fileOf(it), "档案") } }
            if (blobs.length() > 0) base.put("fileBlobs", blobs)
        } catch (e: Exception) {
        }
        return BackupExport(base.toString(), count, skipped)
    }

    /** 只要文本的调用点(剪贴板那条、诊断日志)走这里 */
    fun exportState(): String = exportAll().text

    /**
     * 给一份备份做「体检」:恢复前先让用户看清里面有什么。
     *
     * 为什么必须有:恢复是**整体覆盖**且无法撤销,而用户手机上往往存着好几份不同日期的备份,
     * 光看文件名根本分不清哪份里有那组保单。@return null 表示这份内容解析不出来。
     */
    fun previewBackup(raw: String): BackupPreview? = try {
        val o = JSONObject(raw)
        fun n(key: String) = o.optJSONArray(key)?.length() ?: 0
        BackupPreview(
            taskCount = n("tasks"),
            subCount = n("subs"),
            expenseCount = n("expenses"),
            memberCount = n("members"),
            albumCount = n("album"),
            archiveCount = n("archive"),
            memoCount = n("memos"),
            chargeCount = n("charges"),
            fileCount = o.optJSONObject("fileBlobs")?.length() ?: 0,
            totalCount = n("tasks") + n("subs") + n("obligations") + n("members") + n("keyDates") +
                n("archive") + n("expenses") + n("charges") + n("memos") + n("album") + n("claims"),
        )
    } catch (e: Exception) {
        null
    }

    /**
     * 校验备份内容是否像一个合法备份(含体积上限保护)。
     *
     * 两条路,一条都不能少:
     * ① **新备份**带格式指纹([BACKUP_APP_ID]) → 直接认;
     * ② **老备份**(v2.13 及更早没有指纹) → 要求 `tasks` 与 `subs` **同时**存在。
     *
     * 原来这里是 `has("tasks") || has("subs") || has("name")` —— 那个 `name` 松到
     * **任何一个带 name 字段的 JSON 都能通过**;而恢复会把 `state_v1` 整份替换掉,
     * 于是从"最近文件"里随手选错一个无关 json 就能清空全部记录。
     */
    fun isValidBackup(raw: String): Boolean {
        if (raw.length > 12_000_000) return false
        return try {
            val o = JSONObject(raw)
            if (o.optString(BACKUP_APP_KEY, "") == BACKUP_APP_ID) return true
            o.has("tasks") && o.has("subs")
        } catch (e: Exception) {
            false
        }
    }

    /** 恢复:写入备份并重新载入;内嵌文件一并还原,盘上不再被引用的旧私人文件清掉 */
    fun importState(raw: String): Boolean {
        return try {
            val o = JSONObject(raw)
            val blobs = o.optJSONObject("fileBlobs")
            o.remove("fileBlobs")
            blobs?.let { b ->
                val keys = b.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    // 只接受"纯文件名":备份是自己导出的,但内容一旦被改过,就不能让它写到别处去
                    if (name.contains('/') || name.contains('\\') || name.contains("..")) continue
                    try {
                        fileOf(name).writeBytes(Base64.decode(b.getString(name), Base64.DEFAULT))
                    } catch (e: Exception) {
                    }
                }
            }
            prefs.edit().putString("state_v1", o.toString()).apply()
            clearLists()
            load()
            applyRestoredFiles(blobs)
            // 清掉「这份备份里没有、但盘上还留着」的旧私人文件。
            // 不清的话,恢复之后旧数据(可能正是不想被别人看到的那些)仍然躺在磁盘上,而且白占空间。
            pruneLocalFiles(blobs?.keys()?.asSequence()?.toSet() ?: emptySet())
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 本机私人文件的命名前缀 ——「清空数据」与「恢复备份」两处都按它认。
     * ⚠️ 新增一类本机文件时必须同时更新这里,否则「清空」会清不干净。
     */
    private val PRIVATE_FILE_PREFIXES = listOf("avatar_", "member_", "chat_", "arch_", "album_")

    private fun isPrivateFile(name: String): Boolean = PRIVATE_FILE_PREFIXES.any { name.startsWith(it) }

    /** 删掉盘上不再被引用的私人文件(白名单前缀内)。[keep] 是必须保留的文件名 */
    private fun pruneLocalFiles(keep: Set<String>) {
        try {
            appCtx.filesDir.listFiles()?.forEach { f ->
                if (f.isFile && isPrivateFile(f.name) && f.name !in keep) f.delete()
            }
        } catch (e: Exception) {
        }
    }

    private fun applyRestoredFiles(blobs: JSONObject?) {
        val names = blobs?.keys()?.asSequence()?.toSet() ?: emptySet()
        if (names.isEmpty()) return
        var changed = false
        for (i in members.indices) {
            val p = members[i].photo
            val n = if (p.startsWith("/")) File(p).name else ""
            if (n.isNotEmpty() && n in names) {
                members[i] = members[i].copy(photo = fileOf(n).absolutePath)
                changed = true
            }
        }
        for (i in album.indices) {
            val p = album[i].path
            val n = if (p.startsWith("/")) File(p).name else ""
            if (n.isNotEmpty() && n in names) {
                album[i] = album[i].copy(path = fileOf(n).absolutePath)
                changed = true
            }
        }
        for (i in chat.indices) {
            val p = chat[i].photoPath
            val n = if (p.startsWith("/")) File(p).name else ""
            if (n.isNotEmpty() && n in names) {
                chat[i] = chat[i].copy(photoPath = fileOf(n).absolutePath)
                changed = true
            }
        }
        for (i in archive.indices) {
            val kept = archive[i].files.filter { it in names }
            if (kept.size != archive[i].files.size) {
                archive[i] = archive[i].copy(files = kept, count = kept.size)
                changed = true
            }
        }
        val av = avatarPath.value
        if (av.startsWith("/") && File(av).name in names) {
            avatarPath.value = fileOf(File(av).name).absolutePath
        } else if (av.isEmpty() && "avatar_v1.jpg" in names) {
            avatarPath.value = fileOf("avatar_v1.jpg").absolutePath
        }
        if (changed) save()
    }

    /* ── 初始化 ── */

    private fun id(): String = UUID.randomUUID().toString()

    private fun <T> arr(list: List<T>, f: (T) -> JSONObject): JSONArray {
        val a = JSONArray()
        list.forEach { a.put(f(it)) }
        return a
    }

    /**
     * 演示数据(只由用户主动「载入演示数据」触发)。
     * 所有演示订阅来源都标成「演示」,界面显示角标,避免与真实记录混淆。
     */
    private fun seedDemoData() {
        val today = LocalDate.now()
        fun d(plus: Long) = today.plusDays(plus).toString()

        tasks.add(ButlerTask(id(), "19:00 前给妈妈回电话", "她昨晚发过一条语音，还没回", true))
        tasks.add(ButlerTask(id(), "预约周六上午的洗牙", "常去的诊所周六还剩 2 个号", false))
        tasks.add(ButlerTask(id(), "提交上个月的报销单", "3 张发票已在收件箱备好", false))

        subs.add(ButlerSub(id(), "爱奇艺会员", 25.0, d(1), false, "演示", 0L))
        subs.add(ButlerSub(id(), "网易云音乐", 15.0, d(5), false, "演示", 0L))
        subs.add(ButlerSub(id(), "iCloud+", 6.0, d(16), false, "演示", 0L))
        subs.add(ButlerSub(id(), "Keep 会员", 19.0, d(23), false, "演示", 0L))
        subs.add(ButlerSub(id(), "京东 PLUS", 9.9, d(47), false, "演示", 0L))

        obligations.add(ButlerObligation(id(), "宠物疫苗", "团子 · 猫三联加强针", d(9), "宠物", false))
        obligations.add(ButlerObligation(id(), "车险续保", "去年在平安 · 可先比价 3 家", d(42), "车辆", false))
        obligations.add(ButlerObligation(id(), "驾照换证", "体检任意网点可做", d(87), "证件", false))
        obligations.add(ButlerObligation(id(), "身份证有效期", "换证高峰在明年 3 月", d(240), "证件", false))
        obligations.add(ButlerObligation(id(), "年度体检", "报告已归档", d(-14), "健康", true))

        members.add(ButlerMember(id(), "妈妈", "复诊", d(6), "mom"))
        members.add(ButlerMember(id(), "爸爸", "生日", d(12), "dad"))
        members.add(ButlerMember(id(), "团子", "疫苗", d(9), "cat"))

        keyDates.add(ButlerKeyDate(id(), "爸妈结婚纪念日", d(34), "去年你订了蛋糕，今年要不要换一家"))
        keyDates.add(ButlerKeyDate(id(), "爸爸体检预约", d(21), "还没约 · 提前一周提醒"))

        archive.add(ButlerArchive(id(), "证件夹", 0, "驾照与身份证", emptyList()))
        archive.add(ButlerArchive(id(), "保单", 0, "续保可提前比价", emptyList()))
        archive.add(ButlerArchive(id(), "车辆", 0, "年检在 11 月", emptyList()))
        archive.add(ButlerArchive(id(), "健康报告", 0, "体检报告可以放这里", emptyList()))
        archive.add(ButlerArchive(id(), "租约与押金", 0, "证据包 · 随时可导出", emptyList()))

        chat.add(ButlerChat(id(), true, "下周三可能要加班到九点", ""))
        chat.add(ButlerChat(id(), false, "收到。周四早上的健身课如果需要改期，记得跟我说一声；有变化我第一时间提醒你。", ""))
        chat.add(ButlerChat(id(), true, "厨房水龙头开始滴水了，一直没顾上修", ""))
        chat.add(ButlerChat(id(), false, "看到了。修水龙头这事我没法替你联系师傅，不过可以帮你记进待办，定个时间提醒你处理。", ""))
        chat.add(ButlerChat(id(), false, "另外，妈妈的复诊在 ${fmtCn(d(6))}，那天晚上的安排要帮你空出来吗？", ""))

        expenses.add(ButlerExpense(id(), 12.0, "餐饮", "楼下豆浆油条", today.toString(), System.currentTimeMillis() - 6L * 3600 * 1000))
        expenses.add(ButlerExpense(id(), 28.0, "餐饮", "和同事下午茶", today.toString(), System.currentTimeMillis() - 3L * 3600 * 1000))
        expenses.add(ButlerExpense(id(), 18.0, "交通", "加班打车回家", today.minusDays(1).toString(), System.currentTimeMillis() - 20L * 3600 * 1000))
        expenses.add(ButlerExpense(id(), 86.5, "购物", "猫粮 + 猫砂", today.minusDays(1).toString(), System.currentTimeMillis() - 26L * 3600 * 1000))
        expenses.add(ButlerExpense(id(), 45.0, "餐饮", "同事聚餐 AA", today.minusDays(2).toString(), System.currentTimeMillis() - 30L * 3600 * 1000))
        expenses.add(ButlerExpense(id(), 3.0, "交通", "地铁通勤", today.minusDays(3).toString(), System.currentTimeMillis() - 50L * 3600 * 1000))

        val t0 = System.currentTimeMillis()
        memos.add(ButlerMemo(id(), "周末采购清单", "猫粮、猫砂、抽纸\n顺便取一下快递（3 件）", "生活", true, t0 - 3600_000, t0 - 1800_000, 0L))
        memos.add(ButlerMemo(id(), "报销要留的票据", "打车发票 3 张、餐费小票 1 张\n周五前交到财务", "工作", false, t0 - 7200_000, t0 - 7200_000, 0L))
        memos.add(ButlerMemo(id(), "想读的两本书", "《深度工作》\n《被讨厌的勇气》", "灵感", false, t0 - 10800_000, t0 - 10800_000, 0L))
        memos.add(
            ButlerMemo(
                id(), "明天上午给妈妈打个电话", "她昨晚发过一条语音，还没回", "待办", false,
                t0 - 14400_000, t0 - 14400_000,
                today.plusDays(1).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            ),
        )
    }

    /**
     * 仅供 debug 包自检(端到端脚本用):prefs 里到底落过盘没有、内存里现在各有多少条。
     *
     * 为什么要有这一行:端到端脚本曾长期读不到基线 —— 界面上明明已经有了那条欢迎语,
     * 但 exportState() 读的 prefs 里是空的,于是脚本把欢迎语当成「第一条新回复」,整张表错位一格。
     * 有了它就能一眼分清是「prefs 没落盘」还是「内存里压根没数据」,不用再猜。release 包里没人调用。
     */
    fun debugProbe(): String {
        val persisted = prefs.getString("state_v1", null)
        return "persistedLen=${persisted?.length ?: -1} chat=${chat.size} tasks=${tasks.size}" +
            " memos=${memos.size} expenses=${expenses.size} archive=${archive.size} dark=${darkMode.value}"
    }

    private fun save() {
        // 放在最前面、且**在 try 之外**：就算这次落盘抛了，也不能让搜索页继续用旧结果。
        dataRev++
        try {
            val o = JSONObject()
            o.put("name", profileName.value)
            o.put("start", startDate)
            o.put("blood", bloodType.value)
            o.put("meds", meds.value)
            o.put("contact", emergencyContact.value)
            o.put("avatar", avatarPath.value)
            o.put("remind", reminderEnabled.value)
            o.put("remindHour", reminderHour.value)
            o.put("remindSubDays", reminderSubDays.value)
            o.put("remindDueDays", reminderDueDays.value)
            o.put("budget", monthlyBudget.value)
            o.put("onboarded", onboarded.value)
            o.put("dark", darkMode.value)
            o.put("lock", appLockEnabled.value)
            o.put("wgt", widgetDetail.value)
            o.put("bfx", butlerFx.toDouble())
            o.put("bfy", butlerFy.toDouble())
            val dis = JSONArray()
            dismissed.forEach { dis.put(it) }
            o.put("dismissed", dis)
            o.put("tasks", arr(tasks) { JSONObject().put("id", it.id).put("text", it.text).put("meta", it.meta).put("done", it.done) })
            o.put("subs", arr(subs) { JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount).put("date", it.nextDate).put("closing", it.closing).put("source", it.source).put("closingAt", it.closingAt).put("trial", it.trialUntil).put("ahead", it.remindAhead) })
            o.put("obligations", arr(obligations) { JSONObject().put("id", it.id).put("title", it.title).put("note", it.note).put("date", it.date).put("tag", it.tag).put("done", it.done).put("ahead", it.remindAhead) })
            o.put("members", arr(members) { JSONObject().put("id", it.id).put("name", it.name).put("label", it.label).put("date", it.date).put("photo", it.photo).put("ahead", it.remindAhead) })
            o.put("album", arr(album) { JSONObject().put("id", it.id).put("path", it.path).put("note", it.note).put("at", it.at) })
            o.put("keyDates", arr(keyDates) { JSONObject().put("id", it.id).put("title", it.title).put("date", it.date).put("note", it.note).put("ahead", it.remindAhead) })
            o.put("archive", arr(archive) {
                JSONObject()
                    .put("id", it.id).put("title", it.title).put("count", it.files.size).put("note", it.note)
                    .put("files", JSONArray(it.files))
            })
            o.put("chat", arr(chat) { JSONObject().put("id", it.id).put("user", it.fromUser).put("text", it.text).put("photoPath", it.photoPath) })
            o.put("expenses", arr(expenses) { JSONObject().put("id", it.id).put("amount", it.amount).put("category", it.category).put("note", it.note).put("date", it.date).put("at", it.at).put("income", it.income) })
            o.put("charges", arr(charges) { JSONObject().put("id", it.id).put("name", it.subName).put("amount", it.amount).put("date", it.date).put("at", it.at).put("source", it.source) })
            o.put("closedSubs", arr(closedHistory) { JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount).put("closedAt", it.closedAt) })
            o.put("memos", arr(memos) {
                JSONObject()
                    .put("id", it.id).put("title", it.title).put("content", it.content)
                    .put("category", it.category).put("pinned", it.pinned)
                    .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt).put("remindAt", it.remindAt)
            })
            o.put("memoCats", JSONArray(memoCategories.value))
            o.put("expenseCats", JSONArray(expenseCategories.value))
            o.put("watchPkgs", JSONArray(watchedExtraPackages.value))
            // 待认领的扣费线索：也是用户数据（虽然还没被认领），必须一起存 / 一起备份
            o.put("claims", arr(pendingClaims) {
                JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount)
                    .put("at", it.at).put("nextDate", it.nextDate).put("pkg", it.pkg).put("snippet", it.snippet)
            })
            prefs.edit().putString("state_v1", o.toString()).apply()
        } catch (e: Exception) {
            // 不要静默吞:首装的第一次 save() 曾经因为属性初始化顺序问题在这里悄悄失败,
            // 表现是「prefs 里一直是空的、界面上却正常」,查了很久。debug 包留一行。
            if (debuggable) android.util.Log.d("LbState", "[save] 落盘失败: ${e::class.java.simpleName} ${e.message}")
        }
    }

    private fun load() {
        val raw = prefs.getString("state_v1", null)
        if (raw == null) {
            // 首次安装:干净开始,不塞任何演示内容
            chat.add(
                ButlerChat(
                    id(), false,
                    "你好，我是你的生活管家。\n\n从一件小事开始就好：说「记一下：明天交房租」，或者去「守护」页扫一扫本机的自动续费。所有记录都只存在这台手机上。",
                    "",
                ),
            )
            save()
            return
        }
        try {
            val o = JSONObject(raw)
            profileName.value = o.optString("name", "小满")
            startDate = o.optString("start", LocalDate.now().toString())
            bloodType.value = o.optString("blood", "")
            meds.value = o.optString("meds", "")
            emergencyContact.value = o.optString("contact", "")
            avatarPath.value = o.optString("avatar", "")
            reminderEnabled.value = o.optBoolean("remind", true)
            reminderHour.value = o.optInt("remindHour", 9)
            reminderSubDays.value = o.optInt("remindSubDays", 3)
            reminderDueDays.value = o.optInt("remindDueDays", 7)
            monthlyBudget.value = o.optDouble("budget", 0.0)
            onboarded.value = o.optBoolean("onboarded", false)
            darkMode.value = o.optBoolean("dark", false)
            appLockEnabled.value = o.optBoolean("lock", false)
            widgetDetail.value = o.optInt("wgt", -1)
            butlerFx = o.optDouble("bfx", -1.0).toFloat()
            butlerFy = o.optDouble("bfy", -1.0).toFloat()
            o.optJSONArray("dismissed")?.let { a ->
                for (i in 0 until a.length()) dismissed.add(a.getString(i))
            }

            o.optJSONArray("tasks")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    tasks.add(ButlerTask(j.getString("id"), j.getString("text"), j.optString("meta"), j.optBoolean("done")))
                }
            }
            o.optJSONArray("subs")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    subs.add(ButlerSub(j.getString("id"), j.getString("name"), j.optDouble("amount", 0.0), j.optString("date"), j.optBoolean("closing"), j.optString("source", "手动"), j.optLong("closingAt", 0L), j.optString("trial", ""), j.optInt("ahead", 0)))
                }
            }
            o.optJSONArray("obligations")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    obligations.add(ButlerObligation(j.getString("id"), j.getString("title"), j.optString("note"), j.getString("date"), j.optString("tag", "证件"), j.optBoolean("done"), j.optInt("ahead", 0)))
                }
            }
            o.optJSONArray("members")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    members.add(ButlerMember(j.getString("id"), j.getString("name"), j.optString("label"), j.optString("date"), j.optString("photo"), j.optInt("ahead", 0)))
                }
            }
            o.optJSONArray("album")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    album.add(ButlerPhoto(j.getString("id"), j.optString("path"), j.optString("note"), j.optLong("at", 0L)))
                }
            }
            o.optJSONArray("keyDates")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    keyDates.add(ButlerKeyDate(j.getString("id"), j.getString("title"), j.optString("date"), j.optString("note"), j.optInt("ahead", 0)))
                }
            }
            o.optJSONArray("archive")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    val fs = mutableListOf<String>()
                    j.optJSONArray("files")?.let { fa ->
                        for (k in 0 until fa.length()) fs.add(fa.getString(k))
                    }
                    archive.add(ButlerArchive(j.getString("id"), j.getString("title"), fs.size, j.optString("note"), fs))
                }
            }
            o.optJSONArray("chat")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    chat.add(ButlerChat(j.getString("id"), j.optBoolean("user"), j.getString("text"), j.optString("photoPath", "")))
                }
            }
            o.optJSONArray("expenses")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    expenses.add(ButlerExpense(j.getString("id"), j.optDouble("amount", 0.0), j.optString("category", "其他"), j.optString("note"), j.optString("date"), j.optLong("at", 0L), j.optBoolean("income", false)))
                }
            }
            o.optJSONArray("charges")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    charges.add(ButlerCharge(j.getString("id"), j.optString("name"), j.optDouble("amount", 0.0), j.optString("date"), j.optLong("at", 0L), j.optString("source", "手动")))
                }
            }
            o.optJSONArray("closedSubs")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    closedHistory.add(ButlerClosedSub(j.getString("id"), j.optString("name"), j.optDouble("amount", 0.0), j.optLong("closedAt", 0L)))
                }
            }

            o.optJSONArray("memoCats")?.let { a ->
                val l = mutableListOf<String>()
                for (i in 0 until a.length()) l.add(a.getString(i))
                if (l.isNotEmpty()) {
                    memoCategories.value = if (l.contains(MEMO_UNCATEGORIZED)) l else l + MEMO_UNCATEGORIZED
                }
            }
            // 记账分类没有「未分类」这一层,「其他」就是兜底;读不到或空表就吃默认那套
            o.optJSONArray("expenseCats")?.let { a ->
                val l = mutableListOf<String>()
                for (i in 0 until a.length()) l.add(a.getString(i))
                if (l.isNotEmpty()) expenseCategories.value = l
            }
            o.optJSONArray("watchPkgs")?.let { a ->
                val l = mutableListOf<String>()
                for (i in 0 until a.length()) l.add(a.getString(i))
                watchedExtraPackages.value = l
            }
            o.optJSONArray("claims")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    pendingClaims.add(
                        ButlerClaim(
                            j.getString("id"), j.getString("name"), j.optDouble("amount", 0.0),
                            j.optLong("at"), j.optString("nextDate"), j.optString("pkg"), j.optString("snippet"),
                        ),
                    )
                }
            }
            o.optJSONArray("memos")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    memos.add(
                        ButlerMemo(
                            j.getString("id"),
                            j.optString("title", "无标题"),
                            j.optString("content", ""),
                            j.optString("category", MEMO_UNCATEGORIZED),
                            j.optBoolean("pinned", false),
                            j.optLong("createdAt", 0L),
                            j.optLong("updatedAt", 0L),
                            j.optLong("remindAt", 0L),
                        ),
                    )
                }
            }

            // 旧数据补全:曾经有过「关闭中」的订阅但没留历史 → 按现有数据补一条真实记录
            if (closedHistory.isEmpty()) {
                subs.filter { it.closing }.forEach { s ->
                    closedHistory.add(ButlerClosedSub(id(), s.name, s.amount, s.closingAt))
                }
            }

            // 旧文案迁移:去掉历史消息里无法兑现的承诺,换成与实际能力一致的表述
            val migrateMap = listOf(
                "垫付" to "照片收到了。报修这事我没法替你联系师傅，不过可以帮你记进待办，定个时间提醒你处理。",
                "约好物业师傅" to "照片收到了。报修这事我没法替你联系师傅，不过可以帮你记进待办，定个时间提醒你处理。",
                "报修流程整理好了" to "照片收到了。报修这事我没法替你联系师傅，不过可以帮你记进待办，定个时间提醒你处理。",
                "点一下就能发给教练" to "收到。改期这件事你直接跟教练说就行；需要我提醒你什么时候发消息，说一声就记下。",
            )
            var migrated = false
            for (i in chat.indices) {
                val t = chat[i].text
                val hit = migrateMap.firstOrNull { t.contains(it.first) }
                if (hit != null) {
                    chat[i] = chat[i].copy(text = hit.second)
                    migrated = true
                }
            }
            if (migrated) save()
        } catch (e: Exception) {
            clearLists()
            chat.add(ButlerChat(id(), false, "本地数据读取失败，已从空白开始。之前的内容可以在「我的 → 备份与恢复」里用备份找回。", ""))
            save()
        }
    }

    /**
     * 载入本机记录。
     *
     * 这个 init **必须待在类体的最后**:Kotlin 按声明顺序初始化属性,而 load() 会调用 save(),
     * save() 里用到了声明在后面的 dismissed / BLOB_* 等。放在前面执行时它们还是未初始化的 val,
     * save() 会抛 NPE 被自己的 catch 静默吞掉 —— 现象是「首次安装后 prefs 里一直是空的」,
     * 但界面上一切正常(数据在内存里),端到端脚本也因此长期读不到基线。
     */
    init {
        load()
    }

    companion object {
        /** 「未分类」是兜底分类,不参与删除;删除分类时其下备忘会改挂到这里 */
        const val MEMO_UNCATEGORIZED = "未分类"

        /** 首次安装 / 清空数据后的默认分类 */
        val DEFAULT_MEMO_CATEGORIES = listOf("工作", "生活", "灵感", "待办", MEMO_UNCATEGORIZED)

        /** 首次安装 / 清空数据后的默认记账分类(用户可自增自删;「其他」是兜底,删了会自动补回) */
        val DEFAULT_EXPENSE_CATEGORIES = listOf("餐饮", "交通", "购物", "居家", "娱乐", "医疗", "人情", "其他")

        /** 记账里兜底的那个分类:删除分类时其下账目改挂到这里 */
        const val EXPENSE_FALLBACK = "其他"

        /** 备份格式指纹:导出时写进 JSON,恢复时用它认自己的备份(见 isValidBackup) */
        const val BACKUP_APP_KEY = "app"
        const val BACKUP_APP_ID = "lifebutler"
        const val BACKUP_VER_KEY = "backupV"
        const val BACKUP_VER = 1

        @Volatile
        private var instance: ButlerStore? = null

        fun get(context: Context): ButlerStore =
            instance ?: synchronized(this) {
                instance ?: ButlerStore(context.applicationContext).also { instance = it }
            }
    }
}
