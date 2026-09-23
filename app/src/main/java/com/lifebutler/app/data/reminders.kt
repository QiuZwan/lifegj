package com.lifebutler.app.data

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lifebutler.app.MainActivity
import com.lifebutler.app.R
import java.time.LocalDate
import java.util.Calendar

/** 每日简报与提醒:本地闹钟 + 系统通知;内容全部由本机数据生成。 */
object ReminderScheduler {
    private const val ALARM_ACTION = "com.lifebutler.app.DAILY_REMINDER"

    fun ensureScheduled(context: Context) {
        try {
            // 备忘提醒与「每日简报」互相独立:关掉简报不影响你自己设的备忘提醒
            MemoReminders.rescheduleAll(context)
            val store = ButlerStore.get(context)
            if (!store.reminderEnabled.value) {
                cancel(context)
                return
            }
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(store.reminderHour.value), pending(context))
        } catch (e: Exception) {
        }
    }

    fun cancel(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pending(context))
        } catch (e: Exception) {
        }
    }

    private fun pending(context: Context): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).setAction(ALARM_ACTION)
        return PendingIntent.getBroadcast(
            context, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun nextTrigger(hour: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: ""
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ReminderScheduler.ensureScheduled(context)
            return
        }
        try {
            Notifier.postDailyDigest(context)
        } catch (e: Exception) {
        }
        ReminderScheduler.ensureScheduled(context)
    }
}

object Notifier {
    private const val CHANNEL_ID = "lifebutler_daily"
    private const val MEMO_CHANNEL_ID = "lifebutler_memo"

    /** 每日简报的通知 id 基数（与备忘提醒的 2000+ 错开，互不覆盖） */
    private const val DIGEST_ID_BASE = 1001

    private fun channel(context: Context): NotificationManager {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "每日简报与提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    // 重要度**故意不写死成"紧急"**：每天一条都横幅+响铃会很烦，而 Android 的渠道
                    // 重要度一旦创建，App 之后就改不动了（只能用户自己在系统里调）。所以这里把
                    // 「去哪调、怎么调」直接写进描述，把选择权交给用户（渠道创建过一次就不再生效，
                    // 改描述对老装机无效，但新装/清数据后能看到）。
                    description = "临近扣费、到期事务与家人的重要日期。觉得不够醒目的话，" +
                        "可在系统「设置 → 通知 → 生活管家」里把本渠道调成「紧急」（会横幅并响铃）。"
                },
            )
        }
        return nm
    }

    /**
     * 通知在锁屏上的可见性。
     *
     * 为什么需要它：用户去「我的 → 应用锁」把锁打开，本来就是因为不想让别人看到相册、
     * 证件档案与血型/用药。但应用锁**只管 App 内部** —— 锁屏上依然明文躺着
     * 「「XX会员」明天扣费 ¥25」「明天下午三点去物业交费」。借手机给同事看一眼、
     * 或者手机放在桌上，全被人看见了。锁只锁了一半。
     *
     * 所以应用锁开启时，通知一律 VISIBILITY_SECRET（锁屏上不显示任何内容）；
     * 没开锁时用 PRIVATE（锁屏隐藏正文，解锁后正常）。
     */
    private fun notifVisibility(context: Context): Int =
        if (ButlerStore.get(context).appLockEnabled.value) Notification.VISIBILITY_SECRET
        else Notification.VISIBILITY_PRIVATE

    /**
     * 通知的「公开版」—— 出现在**锁屏**上的那一份。
     *
     * 只写「有 1 条提醒，解锁后查看」，不带金额、不带正文、不带商户名。
     * 这样即使用户只是把手机放桌上，锁屏那一眼也漏不出"这个月视频网站要扣 25 块"这类信息；
     * 同时又保留"有东西在等我"这个提示，不至于把提醒功能弄哑。
     */
    private fun publicVersion(context: Context, channelId: String): Notification =
        Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle("生活管家有 1 条提醒")
            .setContentText("解锁后查看")
            .build()

    /**
     * 一条「今天要留意」的条目。
     *
     * [text] 是一行文案（每日简报 / 桌面小组件用）；[title] / [sub] 是首页卡片用的两行。
     * 两种呈现共用同一份来源，才不会出现"桌面说 5 件、首页只列 2 条"。
     */
    data class Watch(
        val days: Long,
        val text: String,
        val title: String,
        val sub: String,
        val route: String,
        val urgent: Boolean,
        /** 订阅那几条带上自己的 id，首页点开可以直接弹「怎么关」的详情 */
        val id: String = "",
    )

    /**
     * 「今天要留意」的全部条目 —— **首页 / 每日简报 / 桌面小组件共用这一份**。
     *
     * 为什么要抽出来：原来这份汇总逻辑只活在 [buildDailyDigest] 里，于是桌面与通知
     * 说「今天有 5 件要留意」，点开首页的「替你盯着的」却只列得出 2 条（订阅 + 义务），
     * 剩下那 3 条里可能正好有他真正想看的（妈妈的复诊、结婚纪念日、试用到期）。
     * 两处口径不同源，用户就会同时不信这两个 —— 这正是小组件与通知共用一份数据的初衷。
     *
     * 提前量不写死：每条可以有自己的 `remindAhead`（在详情里选「提前 1/3/7/30 天」），
     * 没选的吃全局设置里的默认值。
     */
    fun watchList(context: Context): List<Watch> {
        val store = ButlerStore.get(context)
        val out = mutableListOf<Watch>()
        val subAhead = store.reminderSubDays.value
        val dueAhead = store.reminderDueDays.value

        store.subs.filter { !it.closing }.forEach { s ->
            // 试用截止单独说：这一天不是「开始收钱」，而是「免费到此为止」，措辞要分得清
            val trial = store.trialDaysLeft(s)
            // 试用到期也吃用户设的提前量（原来是写死 0～1 天）。
            // 「提前一天才说开始收费」太晚了：那天用户正忙，转头就忘了，第二天钱就扣掉了。
            // 下限夹到 1 天，免得全局提前量设成 0 时连「明天要收费」都不说。
            val trialAhead = store.aheadDaysFor(s.remindAhead, subAhead).coerceAtLeast(1)
            if (trial != null && trial in 0L..trialAhead.toLong()) {
                val whenText = if (trial == 0L) "今天" else "$trial 天后"
                out += Watch(
                    trial,
                    "「${s.name}」试用${whenText}到期，之后会自动续费 ¥${store.fmtMoney(s.amount)}",
                    "${s.name} · 试用${whenText}到期",
                    "之后会自动续费 ¥${store.fmtMoney(s.amount)}，不想续就提前取消",
                    "guard",
                    trial <= 1,
                    s.id,
                )
            }
            val ahead = store.aheadDaysFor(s.remindAhead, subAhead)
            val d = store.daysUntil(s.nextDate)
            if (d != null && d in 0L..ahead.toLong()) {
                // 涨价：只在有**两笔真实扣费**可比时才说，不推算、不预测
                val jump = store.priceJumpOf(s.name)
                val extra = if (jump != null) "，比上次贵了 ¥${store.fmtMoney(jump)}" else ""
                val whenText = if (d <= 1) "明天" else "$d 天后"
                out += Watch(
                    d,
                    "「${s.name}」${whenText}扣费 ¥${store.fmtMoney(s.amount)}$extra",
                    "${s.name} · ¥${store.fmtMoney(s.amount)}/月",
                    "${whenText}自动扣费$extra",
                    "guard",
                    d <= 3,
                    s.id,
                )
            }
        }
        store.obligations.filter { !it.done }.forEach { o ->
            val ahead = store.aheadDaysFor(o.remindAhead, dueAhead)
            val d = store.daysUntil(o.date)
            if (d != null && d in 0L..ahead.toLong()) {
                val whenText = if (d == 0L) "今天" else "$d 天后"
                out += Watch(
                    d,
                    "「${o.title}」${whenText}到期",
                    "${o.title} · ${if (d == 0L) "今天" else "还有 $d 天"}",
                    o.note.ifEmpty { "到期前会提前提醒" },
                    "duties",
                    d <= 14,
                )
            }
        }
        store.keyDates.forEach { k ->
            val ahead = store.aheadDaysFor(k.remindAhead, dueAhead)
            val d = store.daysUntil(k.date)
            if (d != null && d in 0L..ahead.toLong()) {
                val whenText = if (d == 0L) "就是今天" else "$d 天后"
                out += Watch(
                    d,
                    "「${k.title}」$whenText",
                    "${k.title} · ${if (d == 0L) "就是今天" else "还有 $d 天"}",
                    k.note.ifEmpty { store.fmtCn(k.date) },
                    "family",
                    d <= 14,
                )
            }
        }
        // 家人的日期（复诊 / 生日 / 疫苗）原来**完全没进简报，也没进首页** ——
        // 家里的事漏掉是最不该的。提前量跟着这一条自己的设置走，与订阅 / 义务 / 纪念日一致。
        store.members.forEach { m ->
            val ahead = store.aheadDaysFor(m.remindAhead, dueAhead)
            val d = store.daysUntil(m.date)
            if (d != null && d in 0L..ahead.toLong()) {
                val label = m.label.ifBlank { "重要日期" }
                val whenText = if (d == 0L) "就是今天" else "$d 天后"
                out += Watch(
                    d,
                    "「${m.name}」的$label$whenText",
                    "${m.name}的$label · ${if (d == 0L) "就是今天" else "还有 $d 天"}",
                    store.fmtCn(m.date),
                    "family",
                    d <= 3,
                )
            }
        }
        // 预算超支：只有用户**自己设过**预算才说。没设就一个字不提 ——
        // 凭空替他定一个数、再告诉他「你超了」，是编造出来的焦虑（口径同 budgetStatus）。
        store.budgetStatus()?.let { (spent, cap, over) ->
            if (over) {
                val overText = "本月已花 ¥${store.fmtMoney(spent)}，超出预算 ¥${store.fmtMoney(cap)}"
                out += Watch(0L, overText, "本月预算已超", overText, "ledger", true)
            }
        }
        return out.sortedBy { it.days }
    }

    /** 要留意的条数（首页 / 小组件 / 简报表头共用同一份口径） */
    fun watchCount(context: Context): Int = watchList(context).size

    /**
     * 生成今日简报文案;没有可提醒内容时返回 null。第三项指明点击后直达页面。
     * 条目全部来自 [watchList]，与首页、小组件同源。
     */
    fun buildDailyDigest(context: Context): Triple<String, String, String>? {
        val store = ButlerStore.get(context)
        val watches = watchList(context)
        val pending = store.tasks.count { !it.done }
        if (watches.isEmpty() && pending == 0) return null
        val count = watches.size + (if (pending > 0) 1 else 0)
        val title = if (watches.isEmpty()) "今天有 $pending 件待办" else "今天有 $count 件要留意"
        val text = buildString {
            if (pending > 0) append("待办 $pending 件")
            if (watches.isNotEmpty()) {
                if (isNotEmpty()) append("；")
                append(watches.take(3).joinToString("；") { it.text })
            }
            if (watches.size > 3) append("；等 ${watches.size} 项")
        }
        return Triple(title, text, if (watches.isNotEmpty()) "守护" else "今日")
    }

    fun postDailyDigest(context: Context) {
        val digest = buildDailyDigest(context) ?: return
        post(context, digest.first, digest.second, digest.third)
    }

    fun postTest(context: Context) {
        post(context, "测试提醒 · 一切正常", "看到这条说明提醒通道正常工作；以后每天会按设定时间发简报。", "我的")
    }

    private fun memoChannel(context: Context): NotificationManager {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(MEMO_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(MEMO_CHANNEL_ID, "备忘提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "备忘录里你自己设定的提醒时间"
                },
            )
        }
        return nm
    }

    /** 备忘提醒:到点发一条;点击直达「备忘录」页。同一备忘固定通知 id,重复响不会堆一堆 */
    fun postMemoReminder(context: Context, id: String, title: String, content: String) {
        try {
            val nm = memoChannel(context)
            val body = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(90).orEmpty()
            val text = body.ifBlank { "你设的提醒时间到了。" }
            val pi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).putExtra("open_tab", "memo"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = Notification.Builder(context, MEMO_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_check)
                .setContentTitle(title.ifBlank { "备忘提醒" })
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setVisibility(notifVisibility(context))
                .setPublicVersion(publicVersion(context, MEMO_CHANNEL_ID))
                .setAutoCancel(true)
                .build()
            nm.notify(2000 + (id.hashCode() and 0xFFFF), n)
        } catch (e: Exception) {
        }
    }

    private fun post(context: Context, title: String, text: String, tab: String) {
        val nm = channel(context)
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra("open_tab", tab),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setVisibility(notifVisibility(context))
            .setPublicVersion(publicVersion(context, CHANNEL_ID))
            .setAutoCancel(true)
            .build()
        // id 按「日期」派生：同一天重发还是覆盖自己，但**不同的天各自独立** ——
        // 原来固定用 1001，今天那条会把昨天没看的那条覆盖掉，连几天没点开就只剩最新一条。
        // 取模 7 = 滚动保留最近 7 天，免得攒成一堆。
        val id = DIGEST_ID_BASE + (LocalDate.now().toEpochDay() % 7).toInt()
        nm.notify(id, n)
    }
}

/**
 * 单条备忘的提醒:每条备忘各自一个闹钟(requestCode 由 id 派生),
 * 到点由 [MemoReceiver] 发通知;改期即覆盖同一个闹钟,删除/清除提醒即取消。
 * 只排「将来」的时间——过去的提醒不会重排,所以重启后不会补响一堆旧提醒。
 */
object MemoReminders {
    const val ACTION = "com.lifebutler.app.MEMO_REMINDER"
    const val EXTRA_ID = "memo_id"
    const val EXTRA_TITLE = "memo_title"

    private fun reqCode(id: String): Int = id.hashCode() and 0x7fffffff
    fun schedule(context: Context, id: String, title: String, at: Long) {
        if (id.isEmpty() || at <= System.currentTimeMillis()) return
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, id, title))
        } catch (e: Exception) {
        }
    }

    fun cancel(context: Context, id: String) {
        if (id.isEmpty()) return
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pending(context, id, ""))
        } catch (e: Exception) {
        }
    }

    /** 重新排定全部未到点的备忘提醒(开机 / 启动 / 恢复备份后调用) */
    fun rescheduleAll(context: Context) {
        try {
            val store = ButlerStore.get(context)
            val now = System.currentTimeMillis()
            store.memos.forEach { m ->
                if (m.remindAt > now) schedule(context, m.id, m.title, m.remindAt)
            }
        } catch (e: Exception) {
        }
    }

    private fun pending(context: Context, id: String, title: String): PendingIntent {
        val i = Intent(context, MemoReceiver::class.java)
            .setAction(ACTION)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_TITLE, title)
        return PendingIntent.getBroadcast(
            context, reqCode(id), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class MemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MemoReminders.ACTION) return
        val id = intent.getStringExtra(MemoReminders.EXTRA_ID) ?: return
        try {
            val store = ButlerStore.get(context)
            val m = store.memos.firstOrNull { it.id == id }
            // 备忘已删除、或提醒已被取消 → 不打扰
            if (m == null) return
            if (m.remindAt <= 0L) return
            val fallbackTitle = intent.getStringExtra(MemoReminders.EXTRA_TITLE).orEmpty()
            Notifier.postMemoReminder(context, m.id, m.title.ifBlank { fallbackTitle }, m.content)
        } catch (e: Exception) {
        }
    }
}
