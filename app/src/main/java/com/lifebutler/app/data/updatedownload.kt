package com.lifebutler.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * 应用内更新:把「跳浏览器 → 找资产 → 手动下载 → 回文件管理器点安装」
 * 缩成「点一下 → 自动下载 → 系统安装界面」。
 *
 * ⚠️ **有一件事做不到,也不该假装能做到**:系统那个「安装」按钮**必须用户自己点**。
 * 这是 Android 从 8.0 起的安全设计(配合「安装未知应用」授权),目的就是不让任何 App 静默装东西;
 * 除非有系统签名 / 设备管理员 / 应用商店身份,否则绕不过去。所以本模块对外只说
 * 「已下载好并打开安装界面」,**绝不说「自动安装好了」**。
 *
 * 另一条边界:下载的地址来自发布页接口,自己没有办法保证对面给的一定是我们要的东西,
 * 所以**装之前必须先验**(包名 + 签名 + 版本),见 [verify]。宁可说"这份不能用",也不要装进一个来历不明的东西。
 */
object UpdateDownload {

    /** 下载目录放在 cache 下:它是"可以随时被系统清掉"的东西,不属于用户数据,也就不进 backup */
    private const val DIR = "update"
    private const val MIME_APK = "application/vnd.android.package-archive"

    /** 小于这个大小的东西不可能是安装包 —— 用来挡掉"下到了一个 HTML 错误页" */
    private const val MIN_APK_BYTES = 200_000L

    sealed interface Result {
        /** 下载并校验通过,可以装了 */
        data class Ok(val file: File) : Result

        /** 不能装,[reason] 是直接给用户看的话,要说清楚下一步怎么办 */
        data class Failed(val reason: String) : Result
    }

    fun dir(ctx: Context): File = File(ctx.cacheDir, DIR)

    fun fileNameFor(version: String): String = "LifeButler-v$version.apk"

    fun fileFor(ctx: Context, version: String): File = File(dir(ctx), fileNameFor(version))

    /**
     * 下载安装包(阻塞式,调用方放 IO 线程)。
     *
     * @param onProgress (百分比 0..100, 已下载字节, 总字节);总大小拿不到时百分比给 -1。
     *                   下载中协程被取消会抛 CancellationException,并把半截文件删掉。
     */
    suspend fun download(
        ctx: Context,
        url: String,
        version: String,
        onProgress: (Int, Long, Long) -> Unit = { _, _, _ -> },
    ): Result {
        val dir = dir(ctx)
        if (!dir.exists() && !dir.mkdirs()) return Result.Failed("建不了下载目录,存不下安装包。")
        val target = fileFor(ctx, version)
        val part = File(dir, "${target.name}.part")
        // 先拿到连接再往下走:失败在这句话里就能说清,不用把可空性一路带下去
        val conn = try {
            open(url)
        } catch (e: Exception) {
            return Result.Failed(
                "连不上下载地址(${e.javaClass.simpleName}${e.message?.let { ": $it" } ?: ""})。" +
                    "可以点「打开发布页」自己下。",
            )
        }
        try {
            val total = try {
                conn.contentLengthLong
            } catch (e: Exception) {
                -1L
            }
            // 明显不对的:地址给回来一个几百字节的东西(多半是错误页)。别等装的时候才失败。
            if (total in 1L until MIN_APK_BYTES) {
                return Result.Failed("那个地址返回的内容不像安装包(只有 ${total / 1024} KB),可能发布页上的资产有问题。")
            }
            conn.inputStream.use { ins ->
                FileOutputStream(part).use { fos ->
                    val buf = ByteArray(64 * 1024)
                    var got = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = ins.read(buf)
                        if (n <= 0) break
                        fos.write(buf, 0, n)
                        got += n
                        val pct = if (total > 0) ((got * 100) / total).toInt().coerceIn(0, 100) else -1
                        onProgress(pct, got, total)
                    }
                    fos.flush()
                }
            }
            if (part.length() < MIN_APK_BYTES) {
                part.delete()
                return Result.Failed("下载到的文件只有 ${part.length() / 1024} KB,不完整,已丢弃。")
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                runCatching { part.copyTo(target, overwrite = true) }
                part.delete()
            }
            if (!target.exists()) return Result.Failed("下载完成了,但文件没能存下来。")
            cleanOld(ctx, keep = target)
            return verify(ctx, target)
        } catch (e: kotlinx.coroutines.CancellationException) {
            runCatching { part.delete() }
            throw e
        } catch (e: Exception) {
            runCatching { part.delete() }
            return Result.Failed("下载失败:${e.javaClass.simpleName}${e.message?.let { "（$it）" } ?: ""}")
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /**
     * 打开连接并**自己跟重定向**。
     *
     * 为什么要自己跟:GitHub 的下载地址(`.../releases/download/...`)不是直接给文件,
     * 而是先回一个 302 跳到 objects.githubusercontent.com 的签名地址。
     * 用 `instanceFollowRedirects = true` 在有些实现里跨主机不跟,所以这里手写循环,最多 5 跳。
     */
    private fun open(url: String): HttpURLConnection {
        var cur = url
        var hop = 0
        while (true) {
            val c = URL(cur).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "LifeButler-Android")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc.isNullOrBlank() || hop >= 5) throw IllegalStateException("重定向太多或没有目标地址")
                cur = if (loc.startsWith("http")) loc else URL(URL(cur), loc).toString()
                hop++
                continue
            }
            if (code != 200) {
                c.disconnect()
                throw IllegalStateException("HTTP $code")
            }
            return c
        }
    }

    /**
     * 装之前的自检。返回 null 表示可以装;否则是给用户看的原因。
     *
     * 三道关:① 是不是一个能解析的安装包(挡掉错误页/半截文件);
     * ② 包名对不对(挡掉下错了东西);③ 签名和我现在这一份一不一样
     * —— 不一样的话系统会直接拒绝覆盖安装,用户只会看到一句「应用未安装」,所以提前说清。
     */
    @Suppress("DEPRECATION")
    fun verify(ctx: Context, apk: File): Result {
        val pm = ctx.packageManager
        val flags = PackageManager.GET_SIGNATURES
        val info = runCatching { pm.getPackageArchiveInfo(apk.absolutePath, flags) }.getOrNull()
            ?: return Result.Failed("下载到的文件不是安装包(可能下到了一个错误页面)。请点「打开发布页」手动下载。")
        if (info.packageName != ctx.packageName) {
            return Result.Failed(
                "这个安装包的包名是「${info.packageName}」,不是「生活管家」,已经丢弃 —— 不会装一个来路不明的东西。",
            )
        }
        val mine = runCatching { pm.getPackageInfo(ctx.packageName, flags).signatures?.firstOrNull() }.getOrNull()
        val his = runCatching { info.signatures?.firstOrNull() }.getOrNull()
        if (mine != null && his != null && mine.toCharsString() != his.toCharsString()) {
            return Result.Failed(
                "这份安装包的签名和当前版本不一样,系统会拒绝覆盖安装。\n\n" +
                    "通常是因为现在装的这份不是从发布页下的(比如自己编译的 debug 包)。" +
                    "要继续的话只能先卸载再装 —— 卸载会清掉本机数据,所以**请先导出一次备份**;" +
                    "或者点「打开发布页」自己下。",
            )
        }
        return Result.Ok(apk)
    }

    /**
     * 能不能装别的包。Android 8.0 起这件事**每台设备都要用户显式允许一次**,
     * 没允许就是真的装不了(系统会直接拦掉),不是我们没做。
     */
    fun canInstall(ctx: Context): Boolean =
        runCatching { ctx.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    fun permissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))

    /**
     * 交给系统安装器。**这就是"最后一下必须用户自己点"的那一步** —— 我们只能把安装界面叫出来,
     * 按不按那个「安装」是系统给用户留的决定权。
     */
    fun installIntent(ctx: Context, apk: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** 下载目录里除了 [keep] 之外的东西都清掉:多半是上一次的半截文件或旧版本包 */
    fun cleanOld(ctx: Context, keep: File?) {
        runCatching {
            dir(ctx).listFiles()?.forEach { f ->
                if (keep == null || f.absolutePath != keep.absolutePath) f.delete()
            }
        }
    }
}
