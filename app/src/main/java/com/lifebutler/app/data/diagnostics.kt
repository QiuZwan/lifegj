package com.lifebutler.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 「上传日志」用：把定位问题真正需要的东西攒成一份**纯文本诊断日志**。
 *
 * 三条硬规矩(别拆)：
 *  1. **默认不带用户内容**。只有版本/机型/系统/权限与开关状态/各类记录的**条数**；
 *     聊天正文、照片、档案正文一律不进 —— 要带聊天必须用户自己勾(见 [buildText] 的 withChat)。
 *  2. **不自动上传**。这里只「生成文件 + 唤起系统分享」，发不发、发给谁由用户在分享面板里决定。
 *     本 App 没有后端，任何"已上传"的文案都是骗人。
 *  3. **不许把 API Key 写进去**。AI 配置只报 [AiConfig.Source] 这一层(自己填的/内置的/没接)。
 */
object Diagnostics {

    /** 诊断文件落在 cacheDir(系统随时可回收；也不影响「清空全部数据」的语义)。 */
    fun write(ctx: Context, text: String): File {
        val dir = File(ctx.cacheDir, "diag").apply { mkdirs() }
        // 上一轮的删掉，别越攒越多
        dir.listFiles()?.forEach { if (it.isFile) it.delete() }
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        return File(dir, "lifebutler_diag_$ts.txt").apply { writeText(text) }
    }

    /** 只读授权给接收方，且只授权这一个文件。 */
    fun shareIntent(ctx: Context, file: File, subject: String): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildText(ctx: Context, store: ButlerStore, question: String, withChat: Boolean): String = buildString {
        appendLine("=== 生活管家 · 诊断日志 ===")
        appendLine("生成时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
        appendLine("版本：v" + UpdateCheck.versionName(ctx) + "（versionCode " + UpdateCheck.versionCode(ctx) + "）")
        appendLine("机型：" + Build.MANUFACTURER + " " + Build.MODEL + "（" + Build.DEVICE + "）")
        appendLine("系统：Android " + Build.VERSION.RELEASE + "（API " + Build.VERSION.SDK_INT + "）")
        appendLine("架构：" + Build.SUPPORTED_ABIS.joinToString(","))
        val dm = ctx.resources.displayMetrics
        appendLine("屏幕：" + dm.widthPixels + "x" + dm.heightPixels + " @" + dm.density + "x")
        appendLine()

        appendLine("--- 开关与权限 ---")
        appendLine("深色模式：" + onOff(store.darkMode.value))
        appendLine("通知权限：" + perm(ctx, "android.permission.POST_NOTIFICATIONS"))
        appendLine("位置权限：" + perm(ctx, "android.permission.ACCESS_COARSE_LOCATION"))
        appendLine("读取短信权限：" + perm(ctx, "android.permission.READ_SMS"))
        appendLine("桌面天气：" + onOff(Weather.enabled(ctx)))
        appendLine("扣费通知读取（通知使用权）：" + onOff(notifAccess(ctx)))
        appendLine(
            "AI 管家：" + when (AiConfig.source(ctx)) {
                AiConfig.Source.OWN -> "用户自己的接口"
                AiConfig.Source.BUILTIN -> "内置共享额度"
                AiConfig.Source.NONE -> "未接入（离线规则模式）"
            } + "（只报来源，不记录任何 Key）",
        )
        appendLine("悬浮管家位置：fx=" + num(store.butlerFx) + " fy=" + num(store.butlerFy) + "（-1 = 默认贴右侧）")
        appendLine("使用天数：" + store.dayCount())
        appendLine()

        appendLine("--- 本机记录条数（只报条数，不含内容）---")
        appendLine("任务 " + store.tasks.size + " 条（未完成 " + store.tasks.count { !it.done } + "）")
        appendLine("守护 " + store.obligations.size + " 条（未完成 " + store.obligations.count { !it.done } + "）")
        appendLine("订阅 " + store.subs.size + " 条（在用 " + store.subs.count { !it.closing } + "）")
        appendLine("备忘 " + store.memos.size + " 条")
        appendLine("家庭成员 " + store.members.size + " 人 · 纪念日 " + store.keyDates.size + " 个")
        appendLine("档案 " + store.archive.size + " 组 · 相册 " + store.album.size + " 张")
        appendLine("对话 " + store.chat.size + " 条")
        appendLine("账目 " + store.expenses.size + " 笔 · 扣费流水 " + store.charges.size + " 条")
        appendLine("有记录：" + onOff(store.hasAnyRecord()))
        appendLine()

        if (question.isNotBlank()) {
            appendLine("--- 用户描述 ---")
            appendLine(question.trim())
            appendLine()
        }
        if (withChat) {
            appendLine("--- 最近对话（用户主动勾选附带）---")
            store.chat.takeLast(40).forEach {
                appendLine((if (it.fromUser) "我：" else "管家：") + it.text.replace("\n", " "))
            }
            appendLine()
        }

        appendLine("--- 系统日志（仅本 App 进程，最多 1500 行，尽力而为）---")
        appendLine(logcat())
        appendLine("=== 日志结束 ===")
    }

    /* ────────────────── 意见反馈：图片附件 + 发送 ────────────────── */

    /** 反馈附的图放这儿。在 cache 下，每次进页面清一次，**不参与 clearAll**（它不是用户记录）。 */
    private const val FEEDBACK_DIR = "feedback"

    fun clearFeedbackImages(ctx: Context) {
        File(ctx.cacheDir, FEEDBACK_DIR).listFiles()?.forEach { if (it.isFile) it.delete() }
    }

    /**
     * 把相册里挑的图压一份落到本地，返回可用的绝对路径。
     *
     * 为什么不直接把 `content://` 递出去：
     *  ① 缩略图只有一条渲染入口(`LocalImage`，吃的是文件路径)，`content://` 走不了那条路；
     *  ② 分享时用 FileProvider 给临时只读授权，比赌各家接收方对 `content://` 授权的脾气稳。
     * 压缩走的是 [ButlerStore.saveShrunk] —— 全项目只有那一处做降采样，别在这儿再抄一份。
     */
    fun addFeedbackImages(ctx: Context, store: ButlerStore, uris: List<Uri>, room: Int): List<String> {
        if (room <= 0) return emptyList()
        val dir = File(ctx.cacheDir, FEEDBACK_DIR).apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val out = ArrayList<String>()
        uris.take(room).forEachIndexed { i, u ->
            val f = File(dir, "fb_${stamp}_$i.jpg")
            if (store.saveShrunk(u, f, maxDim = 1600)) out.add(f.absolutePath) else f.delete()
        }
        return out
    }

    fun deleteImage(path: String) {
        runCatching { File(path).delete() }
    }

    /**
     * 「意见反馈」的发送入口：描述 +（可选）图片 +（可选）诊断日志 → 交给系统分享面板。
     *
     * 三条路各不相同，别合并成一条：
     *  · 只有文字、没勾日志 → `ACTION_SEND` text/plain，**不带附件**（短信 / 微信都收得下）
     *  · 没图片但勾了日志   → `ACTION_SEND`，附件是那只 .txt
     *  · 有图片             → `ACTION_SEND_MULTIPLE`；勾了日志就把 .txt 一起挂上
     *
     * ⚠️ 依旧**不自动上传**：这里只是把东西交给系统分享面板，发给谁由用户在面板里选。
     */
    fun shareFeedback(ctx: Context, store: ButlerStore, desc: String, pics: List<String>, withDiag: Boolean) {
        val subject = "生活管家 · 反馈"
        val images = pics.map { File(it) }.filter { it.exists() }

        if (images.isEmpty() && !withDiag) {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, desc + envLine(ctx))
            }
            ctx.startActivity(Intent.createChooser(i, subject))
            return
        }

        val diag = if (withDiag) write(ctx, buildText(ctx, store, desc, false)) else null

        if (images.isEmpty()) {
            ctx.startActivity(Intent.createChooser(shareIntent(ctx, diag!!, subject), subject))
            return
        }

        val streams = ArrayList<Uri>()
        images.forEach { streams.add(uriOf(ctx, it)) }
        diag?.let { streams.add(uriOf(ctx, it)) }
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            // 混了 .txt 就得是 */*；光图片时给 image/*，接收方能更快认出这是图片
            type = if (diag != null) "*/*" else "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, desc + envLine(ctx))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, subject))
    }

    /**
     * 直接用邮件发：正文是描述 + 一行环境信息。
     *
     * 附件走不了 `mailto:`（协议本身不支持），所以这条只用来"说事"；
     * 要带图和日志，走 [shareFeedback] 那条。
     */
    fun mailFeedback(ctx: Context, desc: String) {
        val uri = Uri.parse(
            "mailto:" + UpdateCheck.SUPPORT_EMAIL +
                "?subject=" + Uri.encode("生活管家 · 反馈") +
                "&body=" + Uri.encode(desc + envLine(ctx)),
        )
        ctx.startActivity(Intent(Intent.ACTION_SENDTO, uri))
    }

    private fun uriOf(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)

    /** 纯文字反馈末尾带的一行环境信息 —— 不用用户自己报机型版本。 */
    private fun envLine(ctx: Context): String =
        "\n\n—— 生活管家 v" + UpdateCheck.versionName(ctx) + "（" + Build.MANUFACTURER + " " +
            Build.MODEL + " / Android " + Build.VERSION.RELEASE + "）"

    private fun onOff(b: Boolean) = if (b) "开" else "关"

    private fun num(f: Float) = if (f < 0f) "-1" else String.format("%.3f", f)

    private fun perm(ctx: Context, name: String): String = try {
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, name) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) "已授权" else "未授权"
    } catch (e: Exception) {
        "未知"
    }

    /** 有没有拿到「通知使用权」(扣费通知就靠它)。 */
    private fun notifAccess(ctx: Context): Boolean = try {
        val flat = android.provider.Settings.Secure.getString(
            ctx.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        flat.contains(ctx.packageName)
    } catch (e: Exception) {
        false
    }

    /**
     * 读**本进程**的 logcat。
     *
     * 为什么不读整机日志：① 非 debuggable 的 App 本来也只能看到自己 UID 的日志；
     * ② 明确加 `--pid`，免得把别人的日志(甚至别的 App 的聊天通知)拌进用户要发出去的文件里。
     * 拿不到就如实说"系统没给日志"，不要让这一节空着让人以为是程序挂了。
     */
    private fun logcat(): String = try {
        val p = ProcessBuilder(
            "logcat", "-d", "-t", "1500", "--pid=" + android.os.Process.myPid(), "-v", "time",
        ).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor(4, TimeUnit.SECONDS)
        p.destroy()
        text.trim().take(300_000).ifBlank { "（系统没有给出本 App 的日志，只有上面的状态概览）" }
    } catch (e: Exception) {
        "（读日志失败：" + e.javaClass.simpleName + "，只有上面的状态概览）"
    }
}
