package com.lifebutler.app.data

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 扣费通知读取:在用户手动开启「通知使用权」后,于本机捕获支付/银行类应用的扣费通知,
 * 仅保留与扣费相关的字段(商户/金额/时间/下一步日期/片段),存入本机线索池供「一键扫描」使用,
 * 同时写入真实扣费流水。其余通知不解析、不保存;全部数据不出本机。
 */
class NotifListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName !in WATCHED) return
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
            SubScanner.recordNotification(applicationContext, n.packageName, title, detail)
        } catch (e: Exception) {
            // 任何解析异常都不影响系统通知展示
        }
    }

    companion object {
        /** 监听来源:支付平台 + 常见银行的动账/扣费推送 */
        private val WATCHED = setOf(
            // 支付平台
            "com.tencent.mm",                        // 微信
            "com.eg.android.AlipayGphone",           // 支付宝
            "com.unionpay",                          // 云闪付
            // 银行 App(信用卡/储蓄卡动账提醒常从这里推送)
            "com.icbc",                              // 工商银行
            "com.chinamworld.main",                  // 建设银行
            "com.android.bankabc",                   // 农业银行
            "com.bankcomm.Bankcomm",                 // 交通银行
            "cmb.pb",                                // 招商银行
            "com.cmbchina.ccd.pluto.cmbActivity",    // 招商银行掌上生活
            "com.chinamobile.android.manager",       // 中国移动
            "com.spdbccc.app",                       // 浦发信用卡
            "com.cgbchina.xpt",                      // 广发银行
            "com.pingan.paces.ccms",                 // 平安口袋银行
            "com.cebbank.mobile.cemb",               // 光大银行
            "com.citicbank.card",                    // 中信银行
        )
    }
}
