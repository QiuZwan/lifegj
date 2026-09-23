package com.lifebutler.app.data

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 扣费通知读取:在用户手动开启「通知使用权」后,于本机捕获支付/银行类应用的扣费通知,
 * 仅保留与扣费相关的字段(商户/金额/时间/下一步日期/片段),存入本机线索池供「一键扫描」使用。
 *
 * 注意:命中关键词**不再直接写扣费流水 / 守护清单** —— 那是"替用户认定这是他花的钱"。
 * 现在只落一条「待认领线索」,由用户在守护页点一下才落库(见 [ButlerStore.confirmClaim])。
 *
 * 其余通知不解析、不保存;全部数据不出本机。
 */
class NotifListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        setConnected(true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // 系统省电策略 / 异常重启会把监听断开，而"设置里那条授权"仍然在 ——
        // 这正是"界面说开着、实际一条都收不到"的来源。记下来，让界面能如实说。
        setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val ctx = applicationContext ?: return
        if (!isWatched(ctx, n.packageName)) return
        val extras = n.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val detail = listOf(
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT),
        ).filterNotNull().joinToString(" ")
        try {
            SubScanner.recordNotification(ctx, n.packageName, title, detail)
        } catch (e: Exception) {
            // 任何解析异常都不影响系统通知展示
        }
    }

    companion object {
        /**
         * 监听服务的连接状态。
         *
         * `null` = 进程刚起、还没收到回调（**未知**）；`true` = 已连上；`false` = 被系统断开了。
         * 用"未知"而不是直接当作"断开了"，是为了避免冷启动那一刻误报「功能没在工作」——
         * 那种假阴性比问题本身更让人不信任。
         */
        @Volatile
        var connected: Boolean? = null
            private set

        /** 界面读它做判断；只在服务回调里改 */
        internal fun setConnected(v: Boolean) {
            connected = v
        }

        /**
         * 内置监听来源：支付平台 + 常见银行的动账/扣费推送（包名 → 展示名）。
         * 界面直接渲染这份名单，**加账号只改这里一处**。
         */
        val WATCHED_BUILTIN: List<Pair<String, String>> = listOf(
            // 支付平台
            "com.tencent.mm" to "微信",
            "com.eg.android.AlipayGphone" to "支付宝",
            "com.unionpay" to "云闪付",
            // 银行 App(信用卡/储蓄卡动账提醒常从这里推送)
            "com.icbc" to "工商银行",
            "com.chinamworld.main" to "建设银行",
            "com.android.bankabc" to "农业银行",
            "com.bankcomm.Bankcomm" to "交通银行",
            "cmb.pb" to "招商银行",
            "com.cmbchina.ccd.pluto.cmbActivity" to "招商银行掌上生活",
            "com.chinamobile.android.manager" to "中国移动",
            "com.spdbccc.app" to "浦发信用卡",
            "com.cgbchina.xpt" to "广发银行",
            "com.pingan.paces.ccms" to "平安口袋银行",
            "com.cebbank.mobile.cemb" to "光大银行",
            "com.citicbank.card" to "中信银行",
        )

        private val BUILTIN_SET = WATCHED_BUILTIN.map { it.first }.toSet()

        /** 这个包名要不要解析:内置名单 + 用户自己补的 */
        fun isWatched(context: Context, pkg: String): Boolean =
            pkg in BUILTIN_SET || pkg in ButlerStore.get(context).watchedExtraPackages.value
    }
}
