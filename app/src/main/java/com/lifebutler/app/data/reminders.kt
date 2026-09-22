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

    private fun channel(context: Context): NotificationManager {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "每日简报与提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "临近扣费、到期事务与家人的重要日期"
                },
            )
        }
        return nm
    }

    /**
     * 生成今日简报文案;没有可提醒内容时返回 null。第三项指明点击后直达页面。
     *
     * 提前量不再写死：每条可以有自己的 `remindAhead`（在详情里选「提前 1/3/7/30 天」），
     * 没选的吃全局设置里的默认值。原来订阅固定 3 天、到期固定 7 天，
     * 想提前两周知道车险该续了是做不到的。
     */
    fun buildDailyDigest(context: Context): Triple<String, String, String>? {
        val store = ButlerStore.get(context)
        val items = mutableListOf<Pair<Long, String>>()
        val subAhead = store.reminderSubDays.value
        val dueAhead = store.reminderDueDays.value

        store.subs.filter { !it.closing }.forEach { s ->
            // 试用截止单独说：这一天不是「开始收钱」，而是「免费到此为止」，措辞要分得清
            val trial = store.trialDaysLeft(s)
            if (trial != null && trial in 0L..1L) {
                items += trial to "「${s.name}」试用${if (trial == 0L) "今天" else "明天"}到期，之后会自动续费 ¥${store.fmtMoney(s.amount)}"
            }
            val ahead = store.aheadDaysFor(s.remindAhead, subAhead)
            val d = store.daysUntil(s.nextDate)
            if (d != null && d in 0L..ahead.toLong()) {
                // 涨价：只在有**两笔真实扣费**可比时才说，不推算、不预测
                val jump = store.priceJumpOf(s.name)
                val extra = if (jump != null) "，比上次贵了 ¥${store.fmtMoney(jump)}" else ""
                items += d to "「${s.name}」${if (d <= 1) "明天" else "$d 天后"}扣费 ¥${store.fmtMoney(s.amount)}$extra"
            }
        }
        store.obligations.filter { !it.done }.forEach { o ->
            val ahead = store.aheadDaysFor(o.remindAhead, dueAhead)
            val d = store.daysUntil(o.date)
            if (d != null && d in 0L..ahead.toLong()) {
                items += d to "「${o.title}」${if (d == 0L) "今天" else "$d 天后"}到期"
            }
        }
        store.keyDates.forEach { k ->
            val ahead = store.aheadDaysFor(k.remindAhead, dueAhead)
            val d = store.daysUntil(k.date)
            if (d != null && d in 0L..ahead.toLong()) {
                items += d to "「${k.title}」${if (d == 0L) "就是今天" else "$d 天后"}"
            }
        }
        // 家人的日期（复诊 / 生日 / 疫苗）原来**完全没进简报** —— 家里的事漏掉是最不该的
        store.members.forEach { m ->
            val d = store.daysUntil(m.date)
            if (d != null && d in 0L..dueAhead.toLong()) {
                items += d to "「${m.name}」的${m.label.ifBlank { "重要日期" }}${if (d == 0L) "就是今天" else "$d 天后"}"
            }
        }
        val sorted = items.sortedBy { it.first }.map { it.second }
        val pending = store.tasks.count { !it.done }
        if (sorted.isEmpty() && pending == 0) return null
        val count = sorted.size + (if (pending > 0) 1 else 0)
        val title = if (sorted.isEmpty()) "今天有 $pending 件待办" else "今天有 $count 件要留意"
        val text = buildString {
            if (pending > 0) append("待办 $pending 件")
            if (sorted.isNotEmpty()) {
                if (isNotEmpty()) append("；")
                append(sorted.take(3).joinToString("；"))
            }
            if (sorted.size > 3) append("；等 ${sorted.size} 项")
        }
        return Triple(title, text, if (sorted.isNotEmpty()) "守护" else "今日")
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
            .setAutoCancel(true)
            .build()
        nm.notify(1001, n)
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
