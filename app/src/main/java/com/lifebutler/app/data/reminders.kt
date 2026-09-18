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

    /** 生成今日简报文案;没有可提醒内容时返回 null。第三项指明点击后直达页面 */
    fun buildDailyDigest(context: Context): Triple<String, String, String>? {
        val store = ButlerStore.get(context)
        val items = mutableListOf<Pair<Long, String>>()
        store.subs.filter { !it.closing }.forEach { s ->
            val d = store.daysUntil(s.nextDate)
            if (d != null && d in 0L..3L) {
                items += d to "「${s.name}」${if (d <= 1) "明天" else "$d 天后"}扣费 ¥${store.fmtMoney(s.amount)}"
            }
        }
        store.obligations.filter { !it.done }.forEach { o ->
            val d = store.daysUntil(o.date)
            if (d != null && d in 0L..7L) {
                items += d to "「${o.title}」${if (d == 0L) "今天" else "$d 天后"}到期"
            }
        }
        store.keyDates.forEach { k ->
            val d = store.daysUntil(k.date)
            if (d != null && d in 0L..7L) {
                items += d to "「${k.title}」${if (d == 0L) "就是今天" else "$d 天后"}"
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
