package com.lifebutler.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
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

    /**
     * 单个备份文件的读取上限（12MB，与 [ButlerStore.isValidBackup] 的事后检查同口径）。
     *
     * 为什么要有：原来 `read()` 是 `bufferedReader().readText()` —— **整个文件一次性读进内存，
     * 没有任何长度保护**。粘贴那条路径有 2MB 的拦截，文件路径却没有。
     * 从一个共享目录 / 聊天记录里误选到一个几百 MB 的 json，就会直接 OOM 闪退；
     * 用户看到的只是"点了下恢复，App 没了"，根本联系不到"选错了文件"。
     */
    const val MAX_BYTES = 12_000_000L

    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    /** 给系统「新建文件」对话框的默认文件名 */
    fun suggestedName(): String = "生活管家-备份-" + LocalDateTime.now().format(STAMP) + ".json"

    /**
     * 读取结果。用密封类而不是 `String?`，是为了把「读不出来」和「太大不读」分开说清楚 ——
     * 这两件事给用户看的话完全不同，混成一个 null 只会让人一头雾水。
     */
    sealed class ReadResult {
        class Ok(val text: String) : ReadResult()

        /** [message] 是一句可以直接显示给用户的失败原因 */
        class Fail(val message: String) : ReadResult()
    }

    /**
     * 问一下文件有多大。拿不到（有些 provider 不给 SIZE 列）时返回 null，
     * 调用方按"未知"处理 —— 别把"问不出来"当成"很小"。
     */
    fun sizeOf(ctx: Context, uri: Uri): Long? = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.SIZE)
                if (i >= 0 && !c.isNull(i)) c.getLong(i) else null
            } else null
        }
    } catch (e: Exception) {
        null
    }

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
     *
     * 两道保护，缺一不可：
     * ① 读之前先看 `OpenableColumns.SIZE`，超限就**根本不读**（省下那几百 MB 的内存）；
     * ② SIZE 问不出来、或 provider 报了假值时，靠 [maxBytes] 边读边卡 —— 读到上限就停。
     *
     * 分块读进 [ByteArrayOutputStream] 再整体按 UTF-8 解码，而不是逐块 `String(...)`：
     * 一个中文字符占 3 字节，逐块解码正好把它劈成两半时会出现乱码，JSON 就废了。
     */
    fun read(ctx: Context, uri: Uri, maxBytes: Long = MAX_BYTES): ReadResult {
        return try {
            val declared = sizeOf(ctx, uri)
            if (declared != null && declared > maxBytes) {
                return ReadResult.Fail(
                    "这个文件有 ${declared / 1024 / 1024}MB，不像是备份" +
                        "（备份一般几十到几百 KB，大图会内嵌进去但也不过几 MB）"
                )
            }
            val text = ctx.contentResolver.openInputStream(uri)?.use { ins ->
                val buf = ByteArray(64 * 1024)
                val out = ByteArrayOutputStream()
                var total = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    total += n
                    // SIZE 拿不到或撒谎时的兜底
                    if (total > maxBytes) {
                        return ReadResult.Fail("这个文件超过 ${maxBytes / 1024 / 1024}MB，不像是备份")
                    }
                    out.write(buf, 0, n)
                }
                out.toString("UTF-8")
            }
            if (text.isNullOrBlank()) ReadResult.Fail("这个文件读不出来，换个文件试试")
            else ReadResult.Ok(text)
        } catch (e: Exception) {
            ReadResult.Fail("读取失败：" + e.javaClass.simpleName)
        }
    }
}
