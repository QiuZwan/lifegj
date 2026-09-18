package com.lifebutler.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 真实天气(可选项):只下载所在城市的天气,不上传任何数据。
 * - 需要:INTERNET(普通权限) + 「大致位置」运行时权限
 * - 关闭后完全不联网;获取失败一律静默回退,不打扰用户
 * - 数据源:Open-Meteo 公开接口(免密钥);结果缓存 1 小时
 */
object Weather {
    data class Info(
        val temp: Int,
        val code: Int,
        val high: Int,
        val low: Int,
        val city: String,
        val at: Long,
    )

    private const val PREFS = "lifebutler_weather"
    private const val FRESH_MS = 60L * 60 * 1000

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)
    fun asked(context: Context): Boolean = prefs(context).getBoolean("asked", false)
    fun setAsked(context: Context) { prefs(context).edit().putBoolean("asked", true).apply() }
    fun setEnabled(context: Context, on: Boolean) { prefs(context).edit().putBoolean("enabled", on).apply() }

    fun hasLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun cached(context: Context): Info? {
        val raw = prefs(context).getString("info", null) ?: return null
        return try {
            val o = JSONObject(raw)
            Info(o.getInt("temp"), o.getInt("code"), o.getInt("high"), o.getInt("low"), o.optString("city"), o.optLong("at"))
        } catch (e: Exception) {
            null
        }
    }

    fun isStale(context: Context): Boolean {
        val c = cached(context) ?: return true
        return System.currentTimeMillis() - c.at > FRESH_MS
    }

    /** 天气代码 → 中文描述(WMO 标准) */
    fun describe(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "少云"
        2 -> "多云"
        3 -> "阴"
        45, 48 -> "有雾"
        51, 53, 55, 56, 57 -> "毛毛雨"
        61, 63, 65, 66, 67 -> "雨"
        71, 73, 75, 77 -> "雪"
        80, 81, 82 -> "阵雨"
        85, 86 -> "阵雪"
        95 -> "雷阵雨"
        96, 99 -> "雷暴"
        else -> "多云"
    }

    /** 图标键:UI 层映射到 LbIcons */
    fun iconKey(code: Int): String = when (code) {
        0, 1 -> "sun"
        2, 3, 45, 48 -> "cloud"
        else -> "cloudRain"
    }

    /** 阻塞式拉取(请在 IO 线程调用);失败返回 null */
    fun refresh(context: Context): Info? {
        if (!hasLocation(context)) return null
        val loc = lastLocation(context) ?: return null
        return try {
            fetch(context, loc.latitude, loc.longitude)
        } catch (e: Exception) {
            null
        }
    }

    private fun lastLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        var best: Location? = null
        for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            try {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.time > best.time) best = l
            } catch (e: Exception) {
            }
        }
        if (best != null && System.currentTimeMillis() - best.time < 30 * 60 * 1000L) return best
        // 位置过旧 → 请求一次单点更新,最多等 8 秒
        val latch = CountDownLatch(1)
        var got: Location? = null
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                got = location
                latch.countDown()
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            }

            override fun onProviderEnabled(provider: String) {
            }

            override fun onProviderDisabled(provider: String) {
            }
        }
        return try {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
            latch.await(8, TimeUnit.SECONDS)
            lm.removeUpdates(listener)
            got ?: best
        } catch (e: Exception) {
            try {
                lm.removeUpdates(listener)
            } catch (ignored: Exception) {
            }
            best
        }
    }

    private fun fetch(context: Context, lat: Double, lon: Double): Info {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"
                .format(lat, lon),
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 7000
        conn.readTimeout = 7000
        conn.setRequestProperty("User-Agent", "LifeButler/1.8 (Android)")
        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val o = JSONObject(body)
        val cur = o.getJSONObject("current")
        val temp = Math.round(cur.getDouble("temperature_2m")).toInt()
        val code = cur.getInt("weather_code")
        val daily = o.getJSONObject("daily")
        val high = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt()
        val low = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt()
        val city = cityOf(context, lat, lon)
        val info = Info(temp, code, high, low, city, System.currentTimeMillis())
        prefs(context).edit().putString(
            "info",
            JSONObject().apply {
                put("temp", info.temp)
                put("code", info.code)
                put("high", info.high)
                put("low", info.low)
                put("city", info.city)
                put("at", info.at)
            }.toString(),
        ).apply()
        return info
    }

    private fun cityOf(context: Context, lat: Double, lon: Double): String {
        return try {
            @Suppress("DEPRECATION")
            val list = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
            val a = list?.firstOrNull() ?: return ""
            (a.locality ?: a.subAdminArea ?: a.adminArea ?: "").toString().removeSuffix("市")
        } catch (e: Exception) {
            ""
        }
    }
}
