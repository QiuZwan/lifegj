package com.lifebutler.app.data

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 版本信息 + 「检查更新」。
 *
 * ⚠️ 诚实约定(想改之前先看这段)：
 * 本仓库 `QiuZwan/lifegj` 现在是 **private**。不带令牌去查它的 Releases API，
 * GitHub 一律回 404(私有仓库对外不区分"不存在"和"没权限"，都当不存在)。
 * 所以 [check] 必须把「没查成」和「已是最新」分成两种结果报出去 ——
 * 一旦把 404 当成"已是最新"，就是在替"我们根本没查到"这件事撒谎。
 *
 * 仓库(或只把 Releases)公开之后，这里一行都不用改，检测立刻可用。
 */
object UpdateCheck {

    const val REPO = "QiuZwan/lifegj"
    const val RELEASES_PAGE = "https://github.com/QiuZwan/lifegj/releases"

    /**
     * 「意见反馈」里「用邮件发」的默认收件人。
     *
     * 取的是这个仓库的提交邮箱（作者本人的），所以它一定是能收到的地址。
     * **要换收件人就改这一处** —— 别在界面里再写死第二个邮箱。
     */
    const val SUPPORT_EMAIL = "19175187219@163.com"

    sealed interface CheckResult {
        /** 确实查到比本机新的版本。[apkUrl] 可能为空(那次发布没挂 apk)。 */
        data class Newer(
            val version: String,
            val notes: String,
            val apkUrl: String?,
            val pageUrl: String,
        ) : CheckResult

        /** 查到了，本机不比线上旧。 */
        data class Latest(val version: String) : CheckResult

        /** 没查成。[reason] 要能分清"发布页没公开"和"网络不通"。 */
        data class Failed(val reason: String) : CheckResult
    }

    @Suppress("DEPRECATION")
    fun versionName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName?.trim().orEmpty().ifBlank { "?" }
    } catch (e: Exception) {
        "?"
    }

    fun versionCode(ctx: Context): Long = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    } catch (e: Exception) {
        -1L
    }

    /**
     * 把 `v2.10.1` / `2.10` 拆成数字逐段比。
     *
     * 为什么不直接比字符串：字符串比会把 "2.9" 判成比 "2.10" 新(逐字符 '9' > '1')，
     * 而这个项目的版本号正好是 2.9 → 2.10 → 2.10.1 这么走的，一升就错。
     */
    fun parse(v: String): List<Int> =
        v.trim().trimStart('v', 'V')
            .split('.', '-', '+')
            .map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    /** >0 表示 [a] 比 [b] 新，0 表示同版本。 */
    fun compare(a: String, b: String): Int {
        val x = parse(a)
        val y = parse(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrNull(i) ?: 0) - (y.getOrNull(i) ?: 0)
            if (d != 0) return if (d > 0) 1 else -1
        }
        return 0
    }

    /** 阻塞式，调用方放 IO 线程，别在主线程调。 */
    fun check(localVersion: String): CheckResult {
        val (code, body) = try {
            httpGet("https://api.github.com/repos/$REPO/releases/latest")
        } catch (e: Exception) {
            return CheckResult.Failed("网络不通，连不上 GitHub。（${e.javaClass.simpleName}）")
        }
        when (code) {
            200 -> Unit
            404 -> return CheckResult.Failed(
                "查不到发布页：这个仓库还没公开（私有仓库对外一律返回 404），所以自动检测暂时用不了。",
            )
            403, 429 -> return CheckResult.Failed("GitHub 限制了本次查询（未登录的公共配额用完了），过一会儿再试。")
            else -> return CheckResult.Failed("发布页返回 HTTP $code，暂时查不了。")
        }
        return try {
            val o = JSONObject(body)
            val tag = o.optString("tag_name").ifBlank { o.optString("name") }.trim()
            if (tag.isBlank()) return CheckResult.Failed("发布页里没有版本号，读不出来。")
            val version = tag.trimStart('v', 'V')
            val page = o.optString("html_url").ifBlank { RELEASES_PAGE }
            if (compare(tag, localVersion) <= 0) return CheckResult.Latest(version)
            var apk: String? = null
            o.optJSONArray("assets")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val a = arr.optJSONObject(i) ?: continue
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apk = a.optString("browser_download_url").ifBlank { null }
                        break
                    }
                }
            }
            CheckResult.Newer(version, o.optString("body").trim(), apk, page)
        } catch (e: Exception) {
            CheckResult.Failed("发布页的内容读不出来（${e.javaClass.simpleName}）。")
        }
    }

    private fun httpGet(url: String): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.requestMethod = "GET"
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "LifeButler-Android")
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            c.disconnect()
        }
    }
}
