package com.lifebutler.app.data

import android.content.Context
import android.net.Uri
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 备份文本的「文件进出」通道。
 *
 * 为什么必须有这一条：备份内容里内嵌着头像 / 家人照片 / 相册 / 对话图 / 档案文件的 base64
 * （见 [ButlerStore.exportState]，上限 [ButlerStore] 里的 BLOB_BUDGET），实际能到好几 MB。
 * 而剪贴板走 Binder 跨进程，实用上限只有 1MB 量级 —— 把这么大的东西塞进剪贴板，
 * 轻则失败、重则抛 TransactionTooLargeException 把 App 打崩。
 *
 * 所以：**大备份一律走文件**（存到网盘 / 发给自己都行），剪贴板只留作小数据的快捷方式。
 *
 * 这里两个函数都**不抛异常**，失败时把原因当字符串返回，让界面能如实告诉用户发生了什么 ——
 * 备份/恢复这种「丢了就真没了」的操作，最忌讳静默失败。
 */
object BackupIO {

    /** 备份文件的 MIME。写成 json 是为了让文件管理器给个像样的图标与打开方式。 */
    const val MIME = "application/json"

    // 文件选择器的过滤器：json 优先，但也放开通配，免得各家文件管理器把我们的文件藏起来。
    // （注意别把通配写成注释里的通配符 —— 那两个字符会把上面的块注释提前闭合，整行就变成语法错误）
    val PICK_MIMES = arrayOf("application/json", "*/*")

    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    /** 给系统「新建文件」对话框的默认文件名 */
    fun suggestedName(): String = "生活管家-备份-" + LocalDateTime.now().format(STAMP) + ".json"

    /**
     * 把备份文本写进用户选中的文件。
     * @return null 表示成功；否则是一句可以直接显示给用户的失败原因。
     */
    fun write(ctx: Context, uri: Uri, text: String): String? {
        return try {
            val out = ctx.contentResolver.openOutputStream(uri, "wt")
                ?: return "系统没有给出可写的位置"
            out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            null
        } catch (e: Exception) {
            "写入失败：" + e.javaClass.simpleName
        }
    }

    /**
     * 读出用户选中的备份文件。
     * @return 备份文本；读不到时返回 null（调用方负责提示）。
     */
    fun read(ctx: Context, uri: Uri): String? {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        } catch (e: Exception) {
            null
        }
    }
}
