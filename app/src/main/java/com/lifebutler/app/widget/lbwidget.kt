package com.lifebutler.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lifebutler.app.MainActivity
import com.lifebutler.app.R
import com.lifebutler.app.data.ButlerStore
import com.lifebutler.app.data.Notifier

/**
 * 桌面小组件：把「今天有什么要留意」放到桌面上。
 *
 * 为什么值得做：这个 App 的主打信息本来就是「今天要留意什么」，可在此之前必须先打开它才看得到 ——
 * 那条信息本该躺在桌面上。
 *
 * 内容来源与每日简报**共用同一个函数**（[Notifier.buildDailyDigest]），不另写一套算法：
 * 两边各算一份的话，迟早出现「桌面上说 3 件、通知里说 2 件」，用户会同时不信两个。
 */
class LbWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
    }

    companion object {
        /** 桌面上的小组件全部重画一遍。数据一变就调（App 回到前台时调一次最省事） */
        fun refresh(context: Context) {
            try {
                val ctx = context.applicationContext
                val manager = AppWidgetManager.getInstance(ctx) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(ctx, LbWidgetProvider::class.java))
                if (ids.isEmpty()) return
                val views = buildViews(ctx)
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            } catch (e: Exception) {
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_lb)
            val digest = try {
                Notifier.buildDailyDigest(context)
            } catch (e: Exception) {
                null
            }

            // 没有任何要留意的内容时，说清「现在没事」——而不是留一片空白让人以为组件坏了
            val title = digest?.first ?: "现在没有要留意的事"
            val body = digest?.second
                ?: "待办、订阅、到期事务都会在这里出现。所有内容只在本机生成。"

            views.setTextViewText(R.id.lb_widget_title, title)
            views.setTextViewText(R.id.lb_widget_body, body)

            val tab = digest?.third ?: "今日"
            val intent = Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .putExtra("open_tab", tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getActivity(context, WIDGET_CLICK_CODE, intent, flags)
            views.setOnClickPendingIntent(R.id.lb_widget_root, pi)

            // 记一笔的口径与「今日」页那个按钮一致：点进去直接落在记账本
            val ledger = Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .putExtra("open_tab", "ledger")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(
                R.id.lb_widget_body,
                PendingIntent.getActivity(context, WIDGET_BODY_CLICK_CODE, ledger, flags),
            )
            return views
        }

        /** 两个点击目标的 requestCode 必须不同，否则后一个会覆盖前一个的 PendingIntent */
        private const val WIDGET_CLICK_CODE = 7301
        private const val WIDGET_BODY_CLICK_CODE = 7302
    }
}
