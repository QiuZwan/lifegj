package com.lifebutler.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/* ───────────────────────── 数据模型 ───────────────────────── */

data class ButlerTask(val id: String, val text: String, val meta: String, val done: Boolean)
data class ButlerSub(val id: String, val name: String, val amount: Double, val nextDate: String, val closing: Boolean, val source: String, val closingAt: Long)
data class ButlerObligation(val id: String, val title: String, val note: String, val date: String, val tag: String, val done: Boolean)
data class ButlerMember(val id: String, val name: String, val label: String, val date: String, val photo: String)
data class ButlerKeyDate(val id: String, val title: String, val date: String, val note: String)
data class ButlerArchive(val id: String, val title: String, val count: Int, val note: String, val files: List<String>)
data class ButlerChat(val id: String, val fromUser: Boolean, val text: String, val photoPath: String)

/**
 * 家庭相册里的一张照片。
 * 与「家人头像」是两件事:头像 = 每位家人的那张脸(挂在成员卡上);
 * 相册 = 全家人的照片墙(独立成册,可以放很多张)。两者互不覆盖。
 */
data class ButlerPhoto(val id: String, val path: String, val note: String, val at: Long)
data class ButlerExpense(val id: String, val amount: Double, val category: String, val note: String, val date: String, val at: Long)

/** 真实扣费流水:只由用户手动记录,或扫描/通知真的命中时写入;不由程序推算。 */
data class ButlerCharge(val id: String, val subName: String, val amount: Double, val date: String, val at: Long, val source: String)

/** 关闭历史:每次标记关闭就记一条,用于统计「已关闭 N 笔」;删除订阅不会抹掉这条历史。 */
data class ButlerClosedSub(val id: String, val name: String, val amount: Double, val closedAt: Long)

/**
 * 一条备忘:标题 + 正文 + 分类 + 可选提醒时间。
 * createdAt / updatedAt 为写入时的本机时间戳;remindAt 为 0 表示不提醒。
 * remindAt 到了就交给系统通知响一次,过去的提醒不会重排、不会重复打扰。
 */
data class ButlerMemo(
    val id: String,
    val title: String,
    val content: String,
    val category: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val remindAt: Long,
)

/** 备忘录排序键:最近更新 / 创建时间 / 提醒时间(三者都保持「置顶在前」) */
enum class MemoSort { Updated, Created, Remind }

/**
 * 本机数据仓库:全部用户数据保存在本机 SharedPreferences(JSON),
 * 不联网、不上传;所有页面读写同一份数据,改动即时持久化。
 *
 * 诚信约定:
 * - 不做任何推算或编造。没有记录的地方显示空态,不填看起来像真的数字。
 * - 统计口径全部可由列表数据复算(已关闭笔数、每月省下、本月花销等)。
 * - 首次安装是干净的,不自动塞演示内容;演示内容需用户主动「载入」。
 */
class ButlerStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("lifebutler", Context.MODE_PRIVATE)
    private val appCtx = context.applicationContext

    val profileName: MutableState<String> = mutableStateOf("小满")
    val bloodType: MutableState<String> = mutableStateOf("")
    val meds: MutableState<String> = mutableStateOf("")
    val emergencyContact: MutableState<String> = mutableStateOf("")
    val avatarPath: MutableState<String> = mutableStateOf("")
    val reminderEnabled: MutableState<Boolean> = mutableStateOf(true)
    val reminderHour: MutableState<Int> = mutableStateOf(9)
    val darkMode: MutableState<Boolean> = mutableStateOf(false)
    private var startDate: String = LocalDate.now().toString()

    val tasks: SnapshotStateList<ButlerTask> = mutableStateListOf()
    val subs: SnapshotStateList<ButlerSub> = mutableStateListOf()
    val obligations: SnapshotStateList<ButlerObligation> = mutableStateListOf()
    val members: SnapshotStateList<ButlerMember> = mutableStateListOf()
    val album: SnapshotStateList<ButlerPhoto> = mutableStateListOf()
    val keyDates: SnapshotStateList<ButlerKeyDate> = mutableStateListOf()
    val archive: SnapshotStateList<ButlerArchive> = mutableStateListOf()
    val chat: SnapshotStateList<ButlerChat> = mutableStateListOf()
    val expenses: SnapshotStateList<ButlerExpense> = mutableStateListOf()
    val charges: SnapshotStateList<ButlerCharge> = mutableStateListOf()
    val closedHistory: SnapshotStateList<ButlerClosedSub> = mutableStateListOf()
    val memos: SnapshotStateList<ButlerMemo> = mutableStateListOf()

    /** 备忘分类:在「未分类」之外可由用户自增自删 */
    val memoCategories: MutableState<List<String>> = mutableStateOf(DEFAULT_MEMO_CATEGORIES)

    init {
        load()
    }

    /* ── 派生统计(全部可从上面的列表复算,不存冗余数字) ── */

    /** 处于「关闭中」的订阅月费合计 = 此后每月不再支出的钱 */
    val monthlySaved: Double
        get() = subs.filter { it.closing }.sumOf { it.amount }

    /** 历史上标记过关闭的订阅笔数 */
    val closedCount: Int
        get() = closedHistory.size

    /** 本月因为已关闭订阅而不用再付的月费(关闭时间在本月或更早) */
    val savedThisMonth: Double
        get() {
            val now = LocalDate.now()
            val monthStart = now.withDayOfMonth(1)
            return closedHistory
                .filter { e ->
                    val d = dateOfMillis(e.closedAt)
                    !d.isAfter(now) && !d.isBefore(monthStart)
                }
                .sumOf { it.amount }
        }

    /* ── 日期工具 ── */

    fun parseDate(raw: String): LocalDate? {
        var t = raw.trim()
        if (t.isEmpty()) return null
        t = t.replace("年", "-").replace("月", "-").replace("日", "").replace("/", "-").replace(".", "-")
        val parts = t.split("-").filter { it.isNotEmpty() }
        return try {
            when (parts.size) {
                3 -> LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                2 -> {
                    var d = LocalDate.of(LocalDate.now().year, parts[0].toInt(), parts[1].toInt())
                    if (d.isBefore(LocalDate.now())) d = d.plusYears(1)
                    d
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun dateOfMillis(ms: Long): LocalDate =
        if (ms <= 0L) LocalDate.now()
        else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    fun daysUntil(date: String): Long? {
        val d = parseDate(date) ?: return null
        return ChronoUnit.DAYS.between(LocalDate.now(), d)
    }

    fun daysText(date: String): String {
        if (date.isBlank()) return "未设日期"
        val n = daysUntil(date) ?: return "未设日期"
        return when {
            n < 0 -> "已过期 ${-n} 天"
            n == 0L -> "就是今天"
            n == 1L -> "明天"
            else -> "$n 天后"
        }
    }

    /** 列表右侧短日期:临近用「明天 / N 天后」,否则用「M-d」;没日期就说待补全 */
    fun dateLabel(date: String): String {
        if (date.isBlank()) return "待补全"
        val d = parseDate(date) ?: return date
        val n = daysUntil(date)
        return if (n != null && n <= 1) daysText(date) else "%d-%02d".format(d.monthValue, d.dayOfMonth)
    }

    fun fmtCn(date: String): String {
        if (date.isBlank()) return "日期待补全"
        val d = parseDate(date) ?: return date
        return "%d 月 %d 日".format(d.monthValue, d.dayOfMonth)
    }

    fun dayCount(): Int {
        val s = parseDate(startDate) ?: LocalDate.now()
        return (ChronoUnit.DAYS.between(s, LocalDate.now()).toInt() + 1).coerceAtLeast(1)
    }

    /** 金额显示:四舍五入到 1 位小数,整数不带小数点 */
    fun fmtMoney(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        val rounded = Math.round(v * 10.0) / 10.0
        return if (Math.abs(rounded - Math.floor(rounded)) < 1e-9) rounded.toLong().toString()
        else "%.1f".format(rounded)
    }

    /* ── 变更操作(全部即时持久化) ── */

    fun addTask(text: String, meta: String = "来自对话") {
        tasks.add(ButlerTask(id(), text.trim(), meta, false)); save()
    }

    fun toggleTask(id: String) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i >= 0) { tasks[i] = tasks[i].copy(done = !tasks[i].done); save() }
    }

    fun removeTask(id: String) { tasks.removeAll { it.id == id }; save() }

    fun updateTask(id: String, text: String, meta: String) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i >= 0 && text.isNotBlank()) {
            tasks[i] = tasks[i].copy(text = text.trim(), meta = meta.trim())
            save()
        }
    }

    fun updateSub(id: String, name: String, amount: Double, date: String) {
        val i = subs.indexOfFirst { it.id == id }
        if (i >= 0) {
            subs[i] = subs[i].copy(name = name.trim(), amount = amount, nextDate = date)
            save()
        }
    }

    fun updateObligation(id: String, title: String, date: String, note: String, tag: String) {
        val i = obligations.indexOfFirst { it.id == id }
        if (i >= 0) {
            obligations[i] = obligations[i].copy(title = title.trim(), date = date, note = note.trim(), tag = tag.ifEmpty { obligations[i].tag })
            save()
        }
    }

    fun setReminder(enabled: Boolean, hour: Int) {
        reminderEnabled.value = enabled
        reminderHour.value = hour
        save()
    }

    fun setDarkMode(on: Boolean) {
        darkMode.value = on
        save()
    }

    /* ── 本机文件(头像 / 家人照片 / 对话图片 / 档案文件)统一放在 filesDir ── */

    fun fileOf(name: String): File = File(appCtx.filesDir, name)

    /** 是否为自选照片的本地文件路径(而非内置插图代号) */
    fun isLocalPhoto(p: String): Boolean = p.startsWith("/") || p.startsWith("file://")

    private fun decodeShrunk(uri: android.net.Uri, maxDim: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appCtx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDim && bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = appCtx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val side = maxOf(bmp.width, bmp.height)
            if (side <= maxDim) bmp else {
                val ratio = maxDim.toFloat() / side
                Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * ratio).toInt().coerceAtLeast(1),
                    (bmp.height * ratio).toInt().coerceAtLeast(1),
                    true,
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveShrunk(uri: android.net.Uri, file: File, maxDim: Int = 512): Boolean = try {
        val bmp = decodeShrunk(uri, maxDim) ?: return false
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        true
    } catch (e: Exception) {
        false
    }

    /** 把相册选择的照片压缩后存为头像 */
    fun saveAvatar(context: Context, uri: android.net.Uri): Boolean {
        return try {
            val f = File(context.filesDir, "avatar_v1.jpg")
            val ok = saveShrunk(uri, f)
            if (ok) {
                avatarPath.value = f.absolutePath
                save()
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    /** 为某位家人保存自选照片 */
    fun setMemberPhoto(id: String, uri: android.net.Uri): Boolean {
        return try {
            val f = fileOf("member_$id.jpg")
            val ok = saveShrunk(uri, f)
            if (ok) {
                val i = members.indexOfFirst { it.id == id }
                if (i >= 0) {
                    members[i] = members[i].copy(photo = f.absolutePath)
                    save()
                }
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    /* ── 家庭相册(与家人头像分开存,互不覆盖) ── */

    /** 一次可多选:每张单独压缩后落盘,返回真正存进去的张数 */
    fun addAlbumPhotos(uris: List<android.net.Uri>, note: String = ""): Int {
        var ok = 0
        uris.forEach { u ->
            try {
                val f = fileOf("album_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
                if (saveShrunk(u, f, 1600)) {
                    album.add(ButlerPhoto(id(), f.absolutePath, note.trim(), System.currentTimeMillis()))
                    ok++
                }
            } catch (e: Exception) {
            }
        }
        if (ok > 0) save()
        return ok
    }

    fun updateAlbumNote(id: String, note: String) {
        val i = album.indexOfFirst { it.id == id }
        if (i >= 0) {
            album[i] = album[i].copy(note = note.trim())
            save()
        }
    }

    fun removeAlbumPhoto(id: String) {
        val i = album.indexOfFirst { it.id == id }
        if (i >= 0) {
            try {
                File(album[i].path).delete()
            } catch (e: Exception) {
            }
            album.removeAt(i)
            save()
        }
    }

    /** 对话图片:压缩后存本机,返回可直接渲染的绝对路径 */
    fun saveChatPhoto(uri: android.net.Uri): String? {
        return try {
            val f = fileOf("chat_${UUID.randomUUID()}.jpg")
            if (saveShrunk(uri, f)) f.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 把外部文件复制进应用私有目录,返回落盘后的文件名(basename,便于备份还原)。
     * 图片会压缩;其它类型(如 PDF)原样复制,单文件上限 4MB。
     */
    fun importArchiveFile(uri: android.net.Uri, archiveId: String, name: String): String? {
        return try {
            val clean = name.replace(Regex("[^\\w.\\-\\u4e00-\\u9fa5]"), "_").takeLast(40).ifEmpty { "file" }
            val isImage = (appCtx.contentResolver.getType(uri) ?: "").startsWith("image/")
            if (isImage) {
                val f = fileOf("arch_${archiveId}_${System.currentTimeMillis()}_$clean.jpg")
                if (saveShrunk(uri, f, 1024)) f.name else null
            } else {
                val f = fileOf("arch_${archiveId}_${System.currentTimeMillis()}_$clean")
                appCtx.contentResolver.openInputStream(uri)?.use { input ->
                    f.outputStream().use { out -> input.copyTo(out) }
                } ?: return null
                if (f.length() > 4L * 1024 * 1024) {
                    f.delete()
                    null
                } else f.name
            }
        } catch (e: Exception) {
            null
        }
    }

    fun addArchiveFiles(archiveId: String, names: List<String>) {
        val i = archive.indexOfFirst { it.id == archiveId }
        if (i >= 0 && names.isNotEmpty()) {
            val merged = (archive[i].files + names).distinct()
            archive[i] = archive[i].copy(files = merged, count = merged.size)
            save()
        }
    }

    fun removeArchiveFile(archiveId: String, name: String) {
        val i = archive.indexOfFirst { it.id == archiveId }
        if (i >= 0) {
            try {
                fileOf(name).delete()
            } catch (e: Exception) {
            }
            val rest = archive[i].files.filter { it != name }
            archive[i] = archive[i].copy(files = rest, count = rest.size)
            save()
        }
    }

    /** 全机已归档的真实文件总数 */
    fun archiveFileCount(): Int = archive.sumOf { it.files.size }

    fun updateMember(id: String, name: String, label: String, date: String) {
        val i = members.indexOfFirst { it.id == id }
        if (i >= 0 && name.isNotBlank()) {
            members[i] = members[i].copy(name = name.trim(), label = label.trim(), date = date)
            save()
        }
    }

    fun removeSub(id: String) { subs.removeAll { it.id == id }; save() }

    /* ── 扫描/通知自动加入与「不再加回」记忆 ── */

    private val dismissed = linkedSetOf<String>()

    fun isDismissed(name: String): Boolean = dismissed.contains(name)

    fun dismissName(name: String) {
        dismissed.add(name)
        save()
    }

    /**
     * 扫描/通知自动加入订阅。
     * nextDate 只在扫描真的从短信里解析到扣费日时才传;解析不到留空,
     * 界面显示「扣费日待补全」,绝不凭空编一个日期。
     */
    fun addScannedSub(name: String, amount: Double, source: String, nextDate: String = "", force: Boolean = false) {
        val n = name.trim()
        if (n.isEmpty()) return
        if (subs.any { it.name == n }) return
        if (!force && dismissed.contains(n)) return
        if (force) dismissed.remove(n)
        subs.add(ButlerSub(id(), n, amount, nextDate.trim(), false, source, 0L))
        save()
    }

    fun addSub(name: String, amount: Double, date: String, source: String = "手动") {
        subs.add(ButlerSub(id(), name.trim(), amount, date, false, source, 0L)); save()
    }

    /** 标记为关闭中:写一条真实关闭历史,统计数字由历史复算 */
    fun markSubClosing(id: String, withReceipt: Boolean = true) {
        val i = subs.indexOfFirst { it.id == id }
        if (i >= 0 && !subs[i].closing) {
            val now = System.currentTimeMillis()
            subs[i] = subs[i].copy(closing = true, closingAt = now)
            closedHistory.add(ButlerClosedSub(id(), subs[i].name, subs[i].amount, now))
            if (withReceipt) {
                chat.add(ButlerChat(id(), false, "好，已把「${subs[i].name}」标记为关闭中。取消需要在对应平台完成；之后如果还有扣费，我会提醒你复核。", ""))
            }
            save()
        }
    }

    /* ── 真实扣费流水 ── */

    fun addCharge(subName: String, amount: Double, date: String = LocalDate.now().toString(), source: String = "手动") {
        if (subName.isBlank() || amount <= 0) return
        charges.add(ButlerCharge(id(), subName.trim(), amount, date, System.currentTimeMillis(), source))
        save()
    }

    fun removeCharge(id: String) { charges.removeAll { it.id == id }; save() }

    /** 某笔订阅的真实扣费记录,按时间倒序 */
    fun chargesOf(subName: String): List<ButlerCharge> =
        charges.filter { it.subName == subName }.sortedByDescending { it.at }

    /** 关闭后仍在扣费:关闭时间之后还有该商户的真实扣费记录 */
    fun hasChargeAfterClosing(sub: ButlerSub): Boolean =
        sub.closing && sub.closingAt > 0 && charges.any { it.subName == sub.name && it.at > sub.closingAt }

    fun addObligation(title: String, date: String, note: String, tag: String = "证件") {
        obligations.add(ButlerObligation(id(), title.trim(), note.trim(), date, tag, false)); save()
    }

    fun toggleObligation(id: String) {
        val i = obligations.indexOfFirst { it.id == id }
        if (i >= 0) { obligations[i] = obligations[i].copy(done = !obligations[i].done); save() }
    }

    fun removeObligation(id: String) { obligations.removeAll { it.id == id }; save() }

    fun addMember(name: String, label: String, date: String) {
        members.add(ButlerMember(id(), name.trim(), label.trim(), date, "")); save()
    }

    fun removeMember(id: String) { members.removeAll { it.id == id }; save() }

    fun addKeyDate(title: String, date: String, note: String) {
        keyDates.add(ButlerKeyDate(id(), title.trim(), date, note.trim())); save()
    }

    fun removeKeyDate(id: String) { keyDates.removeAll { it.id == id }; save() }

    fun addArchive(title: String, note: String) {
        archive.add(ButlerArchive(id(), title.trim(), 0, note.trim(), emptyList())); save()
    }

    fun removeArchive(id: String) {
        archive.firstOrNull { it.id == id }?.files?.forEach {
            try {
                fileOf(it).delete()
            } catch (e: Exception) {
            }
        }
        archive.removeAll { it.id == id }
        save()
    }

    fun addChat(fromUser: Boolean, text: String, photoPath: String = "") {
        chat.add(ButlerChat(id(), fromUser, text, photoPath)); save()
    }

    /* ── 备忘录(新建 / 编辑 / 删除 / 分类 / 搜索 / 排序 / 提醒) ── */

    /** 新增一条备忘;标题与正文全空则不落库。返回真正写进去的那条(便于界面继续操作) */
    fun addMemo(title: String, content: String, category: String, remindAt: Long): ButlerMemo? {
        val t = title.trim()
        val c = content.trim()
        if (t.isEmpty() && c.isEmpty()) return null
        val now = System.currentTimeMillis()
        val m = ButlerMemo(
            id = id(),
            title = t.ifEmpty { "无标题" },
            content = c,
            category = category.trim().ifEmpty { MEMO_UNCATEGORIZED },
            pinned = false,
            createdAt = now,
            updatedAt = now,
            remindAt = remindAt,
        )
        memos.add(0, m)
        save()
        syncMemoReminder(m)
        return m
    }

    /** 编辑:更新时间戳;提醒时间改动会同步重排本机闹钟 */
    fun updateMemo(id: String, title: String, content: String, category: String, remindAt: Long) {
        val i = memos.indexOfFirst { it.id == id }
        if (i < 0) return
        val t = title.trim()
        val c = content.trim()
        if (t.isEmpty() && c.isEmpty()) return
        val updated = memos[i].copy(
            title = t.ifEmpty { "无标题" },
            content = c,
            category = category.trim().ifEmpty { memos[i].category.ifEmpty { MEMO_UNCATEGORIZED } },
            remindAt = remindAt,
            updatedAt = System.currentTimeMillis(),
        )
        memos[i] = updated
        save()
        syncMemoReminder(updated)
    }

    /** 置顶 / 取消置顶 */
    fun toggleMemoPin(id: String) {
        val i = memos.indexOfFirst { it.id == id }
        if (i >= 0) {
            memos[i] = memos[i].copy(pinned = !memos[i].pinned)
            save()
        }
    }

    fun removeMemo(id: String) {
        if (memos.none { it.id == id }) return
        memos.removeAll { it.id == id }
        save()
        MemoReminders.cancel(appCtx, id)
    }

    /** 关键词搜索:标题与正文均匹配,忽略大小写;空关键词返回全部 */
    fun searchMemos(keyword: String): List<ButlerMemo> {
        val k = keyword.trim()
        if (k.isEmpty()) return memos.toList()
        return memos.filter { it.title.contains(k, true) || it.content.contains(k, true) }
    }

    /** 排序:置顶恒在前;其余按所选时间键。按提醒时间时,未设提醒的排在最后 */
    fun sortedMemos(list: List<ButlerMemo>, order: MemoSort): List<ButlerMemo> = when (order) {
        MemoSort.Updated -> list.sortedWith(
            compareByDescending<ButlerMemo> { it.pinned }.thenByDescending { it.updatedAt },
        )
        MemoSort.Created -> list.sortedWith(
            compareByDescending<ButlerMemo> { it.pinned }.thenByDescending { it.createdAt },
        )
        MemoSort.Remind -> list.sortedWith(
            compareByDescending<ButlerMemo> { it.pinned }
                .thenBy { if (it.remindAt <= 0L) Long.MAX_VALUE else it.remindAt },
        )
    }

    fun memoCountOf(category: String): Int = memos.count { it.category == category }

    /** 新增分类:重名(忽略大小写)或空名不生效 */
    fun addMemoCategory(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || memoCategories.value.any { it.equals(n, true) }) return false
        memoCategories.value = memoCategories.value + n
        save()
        return true
    }

    /** 删除分类:其下的备忘改挂「未分类」,不会连带删除任何内容 */
    fun removeMemoCategory(name: String) {
        if (name == MEMO_UNCATEGORIZED) return
        if (!memoCategories.value.contains(name)) return
        memoCategories.value = memoCategories.value.filter { it != name }
        for (i in memos.indices) {
            if (memos[i].category == name) memos[i] = memos[i].copy(category = MEMO_UNCATEGORIZED)
        }
        save()
    }

    /** 把「已到点」的备忘提醒说明白:设了未来提醒才排闹钟,否则确保取消 */
    private fun syncMemoReminder(m: ButlerMemo) {
        if (m.remindAt > System.currentTimeMillis()) {
            MemoReminders.schedule(appCtx, m.id, m.title, m.remindAt)
        } else {
            MemoReminders.cancel(appCtx, m.id)
        }
    }

    /* ── 记账 ── */

    fun addExpense(amount: Double, category: String, note: String) {
        if (amount <= 0) return
        expenses.add(ButlerExpense(id(), amount, category.ifEmpty { "其他" }, note.trim(), LocalDate.now().toString(), System.currentTimeMillis()))
        save()
    }

    fun updateExpense(id: String, amount: Double, category: String, note: String) {
        val i = expenses.indexOfFirst { it.id == id }
        if (i >= 0 && amount > 0) {
            expenses[i] = expenses[i].copy(amount = amount, category = category.ifEmpty { "其他" }, note = note.trim())
            save()
        }
    }

    fun removeExpense(id: String) { expenses.removeAll { it.id == id }; save() }

    fun expenseTotalToday(): Double {
        val t = LocalDate.now().toString()
        return expenses.filter { it.date == t }.sumOf { it.amount }
    }

    fun expenseCountToday(): Int {
        val t = LocalDate.now().toString()
        return expenses.count { it.date == t }
    }

    fun expenseCategoryTotals(list: List<ButlerExpense>): List<Pair<String, Double>> =
        list.groupBy { it.category }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }

    fun expensesInMonth(year: Int, month: Int): List<ButlerExpense> =
        expenses.filter {
            val d = parseDate(it.date) ?: return@filter false
            d.year == year && d.monthValue == month
        }

    fun closedInMonth(year: Int, month: Int): List<ButlerClosedSub> =
        closedHistory.filter {
            val d = dateOfMillis(it.closedAt)
            d.year == year && d.monthValue == month
        }

    /** 从一句话里猜记账分类(离线规则模式和 AI 管家共用) */
    fun guessExpenseCategory(text: String): String = when {
        Regex("吃|饭|餐|外卖|奶茶|咖啡|火锅|烧烤|水果|零食|早餐|午餐|晚餐|宵夜|食堂").containsMatchIn(text) -> "餐饮"
        Regex("打车|地铁|公交|加油|停车|高铁|机票|单车|滴滴|出租车").containsMatchIn(text) -> "交通"
        Regex("买|购|淘宝|京东|拼多多|快递|衣服|鞋|数码|超市|商场").containsMatchIn(text) -> "购物"
        Regex("房租|水电|燃气|物业|日用品|家居|清洁|宽带|话费").containsMatchIn(text) -> "居家"
        Regex("电影|游戏|演出|展览|KTV|唱歌|门票|娱乐").containsMatchIn(text) -> "娱乐"
        Regex("药|医院|挂号|体检|牙|诊所|医保|看病").containsMatchIn(text) -> "医疗"
        Regex("红包|礼物|随礼|人情|份子|请客").containsMatchIn(text) -> "人情"
        else -> "其他"
    }

    /* ── 迷你管家大脑:对话里可真正完成记录与查询 ── */

    fun reply(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return "我在。把事情说给我，或问「今天有什么安排」。"

        // ① 记账优先:必须排在「记一下 / 记个…」之前,否则「记个账」会被当成待办
        val isExpenseIntent = Regex("^(记账|记一笔|记个账|记下账|花销|支出|花了)[：:，,、\\s]?").containsMatchIn(t) ||
            (t.contains("花了") && Regex("\\d").containsMatchIn(t))
        if (isExpenseIntent) {
            val amount = Regex("(\\d+(?:\\.\\d{1,2})?)").find(t)?.groupValues?.get(1)?.toDoubleOrNull()
            if (amount != null && amount > 0) {
                val cat = guessExpenseCategory(t)
                val note = t.replaceFirst(Regex("^(记账|记一笔|记个账|记下账|花销|支出|花了)[：:，,]?\\s*"), "")
                    .replace(Regex("\\d+(?:\\.\\d{1,2})?"), "")
                    .replace(Regex("元|块钱|块"), "")
                    .trim(' ', '，', ',', '。', '、', '的', '-')
                addExpense(amount, cat, note)
                return "好，记下了：$cat ¥${fmtMoney(amount)}" + (if (note.isNotEmpty()) "（$note）" else "") +
                    "。今天累计 ¥${fmtMoney(expenseTotalToday())}，共 ${expenseCountToday()} 笔。"
            }
            return "想记多少钱？像这样：「记账：午饭 25」。"
        }

        // ② 记事(记一下 / 提醒我 / 别忘了 …)
        val m = Regex("(?:帮我)?(?:记一下|记下|记个|备忘|提醒我|别忘了|加个?待办|添加待办)[：:，,、\\s]?").find(t)
        if (m != null) {
            val cleaned = (t.substring(0, m.range.first) + t.substring(m.range.last + 1))
                .trim(' ', '。', '!', '！', '~', '、', '，', ',')
            if (cleaned.isNotEmpty()) {
                addTask(cleaned)
                return "好，已经记进「今天要做的事」：$cleaned。完成后在今日页勾掉就行。"
            }
        }

        // ③ 取消 / 退订 → 直接进关闭流程
        if (t.contains("取消") || t.contains("退订")) {
            val frag = t
                .replace(Regex("自动续费|帮我|我想|我要|取消|退订|关闭|关掉"), "")
                .trim(' ', '：', ':', '，', ',', '。')
            val hit = subs.filter { !it.closing }.firstOrNull { frag.isNotEmpty() && (it.name.contains(frag) || frag.contains(it.name)) }
            return if (hit != null) {
                markSubClosing(hit.id, withReceipt = false)
                "好，已在守护清单把「${hit.name}」标记为关闭中。真正的取消要在对应平台完成，步骤在详情页；之后如果仍有扣费，我会提醒你复核。"
            } else {
                "想取消哪一项？说「取消 爱奇艺」这样的名字就行；找不到的话，先去「守护」页看一眼名字。"
            }
        }

        // ④ 查账:今天花了多少
        if ((t.contains("花了") || t.contains("花销") || t.contains("花费") || t.contains("支出")) && (t.contains("多少") || t.contains("查") || t.contains("看"))) {
            val t0 = LocalDate.now().toString()
            val todayList = expenses.filter { it.date == t0 }
            if (todayList.isEmpty()) return "今天还没记账。说「记账：午饭 25」，或在「今日」页点「今天花的钱」记一笔。"
            val total = todayList.sumOf { it.amount }
            val cats = expenseCategoryTotals(todayList).take(3)
            val seven = (0..6).sumOf { i -> expenses.filter { it.date == LocalDate.now().minusDays(i.toLong()).toString() }.sumOf { it.amount } }
            return "今天花了 ¥${fmtMoney(total)} · ${todayList.size} 笔：" + cats.joinToString("、") { "${it.first} ¥${fmtMoney(it.second)}" } + "。最近 7 天合计 ¥${fmtMoney(seven)}。"
        }

        // ⑤ 今天的安排 / 待办清单
        if (t.contains("今天") || t.contains("安排") || t.contains("要做什么") || t.contains("有什么") || t.contains("待办")) {
            val pending = tasks.count { !it.done }
            val list = tasks.filter { !it.done }.take(3).joinToString("、") { it.text }
            val nextOb = obligations.filter { !it.done }
                .mapNotNull { o -> daysUntil(o.date)?.let { o to it } }
                .minByOrNull { it.second }
            val nextSub = subs.filter { !it.closing }
                .mapNotNull { s -> daysUntil(s.nextDate)?.let { s to it } }
                .minByOrNull { it.second }
            val sb = StringBuilder("今天还有 $pending 件待办")
            if (list.isNotEmpty()) sb.append("：").append(list)
            nextOb?.let { sb.append(";最近到期「${it.first.title}」${daysText(it.first.date)}") }
            nextSub?.let { sb.append(";下一笔扣费「${it.first.name}」${daysText(it.first.nextDate)}") }
            return sb.append("。").toString()
        }

        // ⑥ 订阅 / 开销
        if (t.contains("订阅") || t.contains("扣费")) {
            val active = subs.filter { !it.closing }
            if (active.isEmpty()) return "现在还没有订阅记录，在「守护」页右上角可以添加。"
            val total = active.sumOf { it.amount }
            val noDate = active.count { it.nextDate.isBlank() }
            return "现在有 ${active.size} 笔订阅，每月合计 ¥${fmtMoney(total)}" +
                (if (noDate > 0) "（其中 $noDate 笔还不知道扣费日，可以在守护页补全）" else "") +
                "；已经标记关闭 ${closedCount} 笔，每月少支出 ¥${fmtMoney(monthlySaved)}。"
        }

        // ⑦ 省下
        if (t.contains("省下") || t.contains("省了")) {
            return if (closedCount == 0) {
                "还没有关闭过订阅，所以还没有省下的记录。"
            } else {
                "已经标记关闭 $closedCount 笔订阅，每月少支出 ¥${fmtMoney(monthlySaved)}。"
            }
        }

        // ⑧ 重要的日子
        if (t.contains("生日") || t.contains("纪念日") || t.contains("日子") || t.contains("复诊") || t.contains("疫苗")) {
            val kd = keyDates.sortedBy { daysUntil(it.date) ?: Long.MAX_VALUE }.take(2)
            val mem = members.sortedBy { daysUntil(it.date) ?: Long.MAX_VALUE }.firstOrNull { daysUntil(it.date) != null }
            if (kd.isEmpty() && mem == null) {
                return "还没有记日子。去「家庭」页把生日、复诊记下来，我来替你数。"
            }
            val parts = mutableListOf<String>()
            kd.forEach { parts.add("「${it.title}」${daysText(it.date)}") }
            mem?.let { parts.add("「${it.name}的${it.label}」${daysText(it.date)}") }
            return "最近的日子：" + parts.joinToString("；") + "。都在「家庭」页。"
        }

        // ⑨ 帮助
        if (t.contains("能") || t.contains("会什么") || t.contains("帮助") || t.contains("怎么用")) {
            return "我能做这些：说「记一下：……」记事；说「记账：午饭 25」记花销；说「取消 XX」进关闭流程；问「今天有什么安排」「今天花了多少」「最近有什么日子」。"
        }

        return "这句我还没听懂。可以试试：「记一下：明天交房租」／「记账：午饭 25」／「取消 爱奇艺」。"
    }

    fun renameProfile(name: String) {
        val n = name.trim()
        if (n.isNotEmpty()) {
            profileName.value = n
            save()
        }
    }

    fun setEmergency(blood: String, medsText: String, contact: String) {
        bloodType.value = blood.trim()
        this.meds.value = medsText.trim()
        emergencyContact.value = contact.trim()
        save()
    }

    /** 载入演示数据:仅当用户主动点击时执行,所有演示订阅都标来源「演示」 */
    fun loadDemo() {
        clearLists()
        profileName.value = "小满"
        bloodType.value = ""; meds.value = ""; emergencyContact.value = ""
        startDate = LocalDate.now().toString()
        seedDemoData()
        save()
    }

    fun hasAnyRecord(): Boolean = tasks.isNotEmpty() || subs.isNotEmpty() || obligations.isNotEmpty() ||
        members.isNotEmpty() || keyDates.isNotEmpty() || archive.isNotEmpty() ||
        expenses.isNotEmpty() || charges.isNotEmpty() || closedHistory.isNotEmpty() ||
        album.isNotEmpty() || memos.isNotEmpty()

    /** 清空全部数据(从空开始,仅保留一句欢迎语) */
    fun clearAll() {
        // 顺带清掉本机私人文件,避免「已清空」后照片还留在磁盘上
        try {
            appCtx.filesDir.listFiles()?.forEach { f ->
                if (f.isFile && (f.name.startsWith("member_") || f.name.startsWith("chat_") ||
                        f.name.startsWith("arch_") || f.name.startsWith("album_"))
                ) f.delete()
            }
        } catch (e: Exception) {
        }
        // 先把备忘提醒的闹钟逐个取消,再清空数据(清空后就找不到这些 id 了)
        memos.forEach { MemoReminders.cancel(appCtx, it.id) }
        clearLists()
        profileName.value = "小满"
        avatarPath.value = ""
        bloodType.value = ""; meds.value = ""; emergencyContact.value = ""
        reminderEnabled.value = true
        reminderHour.value = 9
        darkMode.value = false
        startDate = LocalDate.now().toString()
        chat.add(ButlerChat(id(), false, "数据已清空，从今天开始记录吧。说「记一下：…」试试，或去「守护」页扫描本机自动续费。", ""))
        save()
    }

    private fun clearLists() {
        tasks.clear(); subs.clear(); obligations.clear(); members.clear()
        keyDates.clear(); archive.clear(); chat.clear(); expenses.clear()
        charges.clear(); closedHistory.clear(); dismissed.clear(); album.clear()
        memos.clear(); memoCategories.value = DEFAULT_MEMO_CATEGORIES
    }

    /* ── 备份与恢复 ── */

    private val BLOB_BUDGET = 8_000_000
    private val BLOB_PER_FILE = 600_000

    /** 备份:状态 JSON + 头像/家人照片/对话图片/档案文件(base64),换机可还原 */
    fun exportState(): String {
        val base = try {
            JSONObject(prefs.getString("state_v1", "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        try {
            val blobs = JSONObject()
            var budget = BLOB_BUDGET
            val seen = mutableSetOf<String>()
            fun putFile(file: File?) {
                if (file == null || !file.exists() || file.length() > BLOB_PER_FILE) return
                if (!seen.add(file.name)) return
                val enc = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                if (enc.length > budget) return
                budget -= enc.length
                blobs.put(file.name, enc)
            }
            putFile(File(appCtx.filesDir, "avatar_v1.jpg"))
            members.forEach { m -> if (m.photo.startsWith("/")) putFile(File(m.photo)) }
            album.forEach { p -> if (p.path.startsWith("/")) putFile(File(p.path)) }
            chat.forEach { c -> if (c.photoPath.startsWith("/")) putFile(File(c.photoPath)) }
            archive.forEach { a -> a.files.forEach { putFile(fileOf(it)) } }
            if (blobs.length() > 0) base.put("fileBlobs", blobs)
        } catch (e: Exception) {
        }
        return base.toString()
    }

    /** 校验备份内容是否像一个合法备份(含体积上限保护) */
    fun isValidBackup(raw: String): Boolean {
        if (raw.length > 12_000_000) return false
        return try {
            val o = JSONObject(raw)
            o.has("tasks") || o.has("subs") || o.has("name")
        } catch (e: Exception) {
            false
        }
    }

    /** 恢复:写入备份并重新载入;内嵌文件一并还原 */
    fun importState(raw: String): Boolean {
        return try {
            val o = JSONObject(raw)
            val blobs = o.optJSONObject("fileBlobs")
            o.remove("fileBlobs")
            blobs?.let { b ->
                val keys = b.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    try {
                        fileOf(name).writeBytes(Base64.decode(b.getString(name), Base64.DEFAULT))
                    } catch (e: Exception) {
                    }
                }
            }
            prefs.edit().putString("state_v1", o.toString()).apply()
            clearLists()
            load()
            applyRestoredFiles(blobs)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun applyRestoredFiles(blobs: JSONObject?) {
        val names = blobs?.keys()?.asSequence()?.toSet() ?: emptySet()
        if (names.isEmpty()) return
        var changed = false
        for (i in members.indices) {
            val p = members[i].photo
            val n = if (p.startsWith("/")) File(p).name else ""
            if (n.isNotEmpty() && n in names) {
                members[i] = members[i].copy(photo = fileOf(n).absolutePath)
                changed = true
            }
        }
        for (i in album.indices) {
            val p = album[i].path
            val n = if (p.startsWith("/")) File(p).name else ""
            if (n.isNotEmpty() && n in names) {
                album[i] = album[i].copy(path = fileOf(n).absolutePath)
                changed = true
            }
        }
        for (i in chat.indices) {
            val p = chat[i].photoPath
            val n = if (p.startsWith("/")) File(p).name else ""
            if (n.isNotEmpty() && n in names) {
                chat[i] = chat[i].copy(photoPath = fileOf(n).absolutePath)
                changed = true
            }
        }
        for (i in archive.indices) {
            val kept = archive[i].files.filter { it in names }
            if (kept.size != archive[i].files.size) {
                archive[i] = archive[i].copy(files = kept, count = kept.size)
                changed = true
            }
        }
        val av = avatarPath.value
        if (av.startsWith("/") && File(av).name in names) {
            avatarPath.value = fileOf(File(av).name).absolutePath
        } else if (av.isEmpty() && "avatar_v1.jpg" in names) {
            avatarPath.value = fileOf("avatar_v1.jpg").absolutePath
        }
        if (changed) save()
    }

    /* ── 初始化 ── */

    private fun id(): String = UUID.randomUUID().toString()

    private fun <T> arr(list: List<T>, f: (T) -> JSONObject): JSONArray {
        val a = JSONArray()
        list.forEach { a.put(f(it)) }
        return a
    }

    /**
     * 演示数据(只由用户主动「载入演示数据」触发)。
     * 所有演示订阅来源都标成「演示」,界面显示角标,避免与真实记录混淆。
     */
    private fun seedDemoData() {
        val today = LocalDate.now()
        fun d(plus: Long) = today.plusDays(plus).toString()

        tasks.add(ButlerTask(id(), "19:00 前给妈妈回电话", "她昨晚发过一条语音，还没回", true))
        tasks.add(ButlerTask(id(), "预约周六上午的洗牙", "常去的诊所周六还剩 2 个号", false))
        tasks.add(ButlerTask(id(), "提交上个月的报销单", "3 张发票已在收件箱备好", false))

        subs.add(ButlerSub(id(), "爱奇艺会员", 25.0, d(1), false, "演示", 0L))
        subs.add(ButlerSub(id(), "网易云音乐", 15.0, d(5), false, "演示", 0L))
        subs.add(ButlerSub(id(), "iCloud+", 6.0, d(16), false, "演示", 0L))
        subs.add(ButlerSub(id(), "Keep 会员", 19.0, d(23), false, "演示", 0L))
        subs.add(ButlerSub(id(), "京东 PLUS", 9.9, d(47), false, "演示", 0L))

        obligations.add(ButlerObligation(id(), "宠物疫苗", "团子 · 猫三联加强针", d(9), "宠物", false))
        obligations.add(ButlerObligation(id(), "车险续保", "去年在平安 · 可先比价 3 家", d(42), "车辆", false))
        obligations.add(ButlerObligation(id(), "驾照换证", "体检任意网点可做", d(87), "证件", false))
        obligations.add(ButlerObligation(id(), "身份证有效期", "换证高峰在明年 3 月", d(240), "证件", false))
        obligations.add(ButlerObligation(id(), "年度体检", "报告已归档", d(-14), "健康", true))

        members.add(ButlerMember(id(), "妈妈", "复诊", d(6), "mom"))
        members.add(ButlerMember(id(), "爸爸", "生日", d(12), "dad"))
        members.add(ButlerMember(id(), "团子", "疫苗", d(9), "cat"))

        keyDates.add(ButlerKeyDate(id(), "爸妈结婚纪念日", d(34), "去年你订了蛋糕，今年要不要换一家"))
        keyDates.add(ButlerKeyDate(id(), "爸爸体检预约", d(21), "还没约 · 提前一周提醒"))

        archive.add(ButlerArchive(id(), "证件夹", 0, "驾照与身份证", emptyList()))
        archive.add(ButlerArchive(id(), "保单", 0, "续保可提前比价", emptyList()))
        archive.add(ButlerArchive(id(), "车辆", 0, "年检在 11 月", emptyList()))
        archive.add(ButlerArchive(id(), "健康报告", 0, "体检报告可以放这里", emptyList()))
        archive.add(ButlerArchive(id(), "租约与押金", 0, "证据包 · 随时可导出", emptyList()))

        chat.add(ButlerChat(id(), true, "下周三可能要加班到九点", ""))
        chat.add(ButlerChat(id(), false, "收到。周四早上的健身课如果需要改期，记得跟我说一声；有变化我第一时间提醒你。", ""))
        chat.add(ButlerChat(id(), true, "厨房水龙头开始滴水了，一直没顾上修", ""))
        chat.add(ButlerChat(id(), false, "看到了。修水龙头这事我没法替你联系师傅，不过可以帮你记进待办，定个时间提醒你处理。", ""))
        chat.add(ButlerChat(id(), false, "另外，妈妈的复诊在 ${fmtCn(d(6))}，那天晚上的安排要帮你空出来吗？", ""))

        expenses.add(ButlerExpense(id(), 12.0, "餐饮", "楼下豆浆油条", today.toString(), System.currentTimeMillis() - 6L * 3600 * 1000))
        expenses.add(ButlerExpense(id(), 28.0, "餐饮", "和同事下午茶", today.toString(), System.currentTimeMillis() - 3L * 3600 * 1000))
        expenses.add(ButlerExpense(id(), 18.0, "交通", "加班打车回家", today.minusDays(1).toString(), System.currentTimeMillis() - 20L * 3600 * 1000))
        expenses.add(ButlerExpense(id(), 86.5, "购物", "猫粮 + 猫砂", today.minusDays(1).toString(), System.currentTimeMillis() - 26L * 3600 * 1000))
        expenses.add(ButlerExpense(id(), 45.0, "餐饮", "同事聚餐 AA", today.minusDays(2).toString(), System.currentTimeMillis() - 30L * 3600 * 1000))
        expenses.add(ButlerExpense(id(), 3.0, "交通", "地铁通勤", today.minusDays(3).toString(), System.currentTimeMillis() - 50L * 3600 * 1000))

        val t0 = System.currentTimeMillis()
        memos.add(ButlerMemo(id(), "周末采购清单", "猫粮、猫砂、抽纸\n顺便取一下快递（3 件）", "生活", true, t0 - 3600_000, t0 - 1800_000, 0L))
        memos.add(ButlerMemo(id(), "报销要留的票据", "打车发票 3 张、餐费小票 1 张\n周五前交到财务", "工作", false, t0 - 7200_000, t0 - 7200_000, 0L))
        memos.add(ButlerMemo(id(), "想读的两本书", "《深度工作》\n《被讨厌的勇气》", "灵感", false, t0 - 10800_000, t0 - 10800_000, 0L))
        memos.add(
            ButlerMemo(
                id(), "明天上午给妈妈打个电话", "她昨晚发过一条语音，还没回", "待办", false,
                t0 - 14400_000, t0 - 14400_000,
                today.plusDays(1).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            ),
        )
    }

    private fun save() {
        try {
            val o = JSONObject()
            o.put("name", profileName.value)
            o.put("start", startDate)
            o.put("blood", bloodType.value)
            o.put("meds", meds.value)
            o.put("contact", emergencyContact.value)
            o.put("avatar", avatarPath.value)
            o.put("remind", reminderEnabled.value)
            o.put("remindHour", reminderHour.value)
            o.put("dark", darkMode.value)
            val dis = JSONArray()
            dismissed.forEach { dis.put(it) }
            o.put("dismissed", dis)
            o.put("tasks", arr(tasks) { JSONObject().put("id", it.id).put("text", it.text).put("meta", it.meta).put("done", it.done) })
            o.put("subs", arr(subs) { JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount).put("date", it.nextDate).put("closing", it.closing).put("source", it.source).put("closingAt", it.closingAt) })
            o.put("obligations", arr(obligations) { JSONObject().put("id", it.id).put("title", it.title).put("note", it.note).put("date", it.date).put("tag", it.tag).put("done", it.done) })
            o.put("members", arr(members) { JSONObject().put("id", it.id).put("name", it.name).put("label", it.label).put("date", it.date).put("photo", it.photo) })
            o.put("album", arr(album) { JSONObject().put("id", it.id).put("path", it.path).put("note", it.note).put("at", it.at) })
            o.put("keyDates", arr(keyDates) { JSONObject().put("id", it.id).put("title", it.title).put("date", it.date).put("note", it.note) })
            o.put("archive", arr(archive) {
                JSONObject()
                    .put("id", it.id).put("title", it.title).put("count", it.files.size).put("note", it.note)
                    .put("files", JSONArray(it.files))
            })
            o.put("chat", arr(chat) { JSONObject().put("id", it.id).put("user", it.fromUser).put("text", it.text).put("photoPath", it.photoPath) })
            o.put("expenses", arr(expenses) { JSONObject().put("id", it.id).put("amount", it.amount).put("category", it.category).put("note", it.note).put("date", it.date).put("at", it.at) })
            o.put("charges", arr(charges) { JSONObject().put("id", it.id).put("name", it.subName).put("amount", it.amount).put("date", it.date).put("at", it.at).put("source", it.source) })
            o.put("closedSubs", arr(closedHistory) { JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount).put("closedAt", it.closedAt) })
            o.put("memos", arr(memos) {
                JSONObject()
                    .put("id", it.id).put("title", it.title).put("content", it.content)
                    .put("category", it.category).put("pinned", it.pinned)
                    .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt).put("remindAt", it.remindAt)
            })
            o.put("memoCats", JSONArray(memoCategories.value))
            prefs.edit().putString("state_v1", o.toString()).apply()
        } catch (e: Exception) {
        }
    }

    private fun load() {
        val raw = prefs.getString("state_v1", null)
        if (raw == null) {
            // 首次安装:干净开始,不塞任何演示内容
            chat.add(
                ButlerChat(
                    id(), false,
                    "你好，我是你的生活管家。\n\n从一件小事开始就好：说「记一下：明天交房租」，或者去「守护」页扫一扫本机的自动续费。所有记录都只存在这台手机上。",
                    "",
                ),
            )
            save()
            return
        }
        try {
            val o = JSONObject(raw)
            profileName.value = o.optString("name", "小满")
            startDate = o.optString("start", LocalDate.now().toString())
            bloodType.value = o.optString("blood", "")
            meds.value = o.optString("meds", "")
            emergencyContact.value = o.optString("contact", "")
            avatarPath.value = o.optString("avatar", "")
            reminderEnabled.value = o.optBoolean("remind", true)
            reminderHour.value = o.optInt("remindHour", 9)
            darkMode.value = o.optBoolean("dark", false)
            o.optJSONArray("dismissed")?.let { a ->
                for (i in 0 until a.length()) dismissed.add(a.getString(i))
            }

            o.optJSONArray("tasks")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    tasks.add(ButlerTask(j.getString("id"), j.getString("text"), j.optString("meta"), j.optBoolean("done")))
                }
            }
            o.optJSONArray("subs")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    subs.add(ButlerSub(j.getString("id"), j.getString("name"), j.optDouble("amount", 0.0), j.optString("date"), j.optBoolean("closing"), j.optString("source", "手动"), j.optLong("closingAt", 0L)))
                }
            }
            o.optJSONArray("obligations")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    obligations.add(ButlerObligation(j.getString("id"), j.getString("title"), j.optString("note"), j.getString("date"), j.optString("tag", "证件"), j.optBoolean("done")))
                }
            }
            o.optJSONArray("members")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    members.add(ButlerMember(j.getString("id"), j.getString("name"), j.optString("label"), j.optString("date"), j.optString("photo")))
                }
            }
            o.optJSONArray("album")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    album.add(ButlerPhoto(j.getString("id"), j.optString("path"), j.optString("note"), j.optLong("at", 0L)))
                }
            }
            o.optJSONArray("keyDates")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    keyDates.add(ButlerKeyDate(j.getString("id"), j.getString("title"), j.optString("date"), j.optString("note")))
                }
            }
            o.optJSONArray("archive")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    val fs = mutableListOf<String>()
                    j.optJSONArray("files")?.let { fa ->
                        for (k in 0 until fa.length()) fs.add(fa.getString(k))
                    }
                    archive.add(ButlerArchive(j.getString("id"), j.getString("title"), fs.size, j.optString("note"), fs))
                }
            }
            o.optJSONArray("chat")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    chat.add(ButlerChat(j.getString("id"), j.optBoolean("user"), j.getString("text"), j.optString("photoPath", "")))
                }
            }
            o.optJSONArray("expenses")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    expenses.add(ButlerExpense(j.getString("id"), j.optDouble("amount", 0.0), j.optString("category", "其他"), j.optString("note"), j.optString("date"), j.optLong("at", 0L)))
                }
            }
            o.optJSONArray("charges")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    charges.add(ButlerCharge(j.getString("id"), j.optString("name"), j.optDouble("amount", 0.0), j.optString("date"), j.optLong("at", 0L), j.optString("source", "手动")))
                }
            }
            o.optJSONArray("closedSubs")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    closedHistory.add(ButlerClosedSub(j.getString("id"), j.optString("name"), j.optDouble("amount", 0.0), j.optLong("closedAt", 0L)))
                }
            }

            o.optJSONArray("memoCats")?.let { a ->
                val l = mutableListOf<String>()
                for (i in 0 until a.length()) l.add(a.getString(i))
                if (l.isNotEmpty()) {
                    memoCategories.value = if (l.contains(MEMO_UNCATEGORIZED)) l else l + MEMO_UNCATEGORIZED
                }
            }
            o.optJSONArray("memos")?.let { a ->
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    memos.add(
                        ButlerMemo(
                            j.getString("id"),
                            j.optString("title", "无标题"),
                            j.optString("content", ""),
                            j.optString("category", MEMO_UNCATEGORIZED),
                            j.optBoolean("pinned", false),
                            j.optLong("createdAt", 0L),
                            j.optLong("updatedAt", 0L),
                            j.optLong("remindAt", 0L),
                        ),
                    )
                }
            }

            // 旧数据补全:曾经有过「关闭中」的订阅但没留历史 → 按现有数据补一条真实记录
            if (closedHistory.isEmpty()) {
                subs.filter { it.closing }.forEach { s ->
                    closedHistory.add(ButlerClosedSub(id(), s.name, s.amount, s.closingAt))
                }
            }

            // 旧文案迁移:去掉历史消息里无法兑现的承诺,换成与实际能力一致的表述
            val migrateMap = listOf(
                "垫付" to "照片收到了。报修这事我没法替你联系师傅，不过可以帮你记进待办，定个时间提醒你处理。",
                "约好物业师傅" to "照片收到了。报修这事我没法替你联系师傅，不过可以帮你记进待办，定个时间提醒你处理。",
                "报修流程整理好了" to "照片收到了。报修这事我没法替你联系师傅，不过可以帮你记进待办，定个时间提醒你处理。",
                "点一下就能发给教练" to "收到。改期这件事你直接跟教练说就行；需要我提醒你什么时候发消息，说一声就记下。",
            )
            var migrated = false
            for (i in chat.indices) {
                val t = chat[i].text
                val hit = migrateMap.firstOrNull { t.contains(it.first) }
                if (hit != null) {
                    chat[i] = chat[i].copy(text = hit.second)
                    migrated = true
                }
            }
            if (migrated) save()
        } catch (e: Exception) {
            clearLists()
            chat.add(ButlerChat(id(), false, "本地数据读取失败，已从空白开始。之前的内容可以在「我的 → 备份与恢复」里用备份找回。", ""))
            save()
        }
    }

    companion object {
        /** 「未分类」是兜底分类,不参与删除;删除分类时其下备忘会改挂到这里 */
        const val MEMO_UNCATEGORIZED = "未分类"

        /** 首次安装 / 清空数据后的默认分类 */
        val DEFAULT_MEMO_CATEGORIES = listOf("工作", "生活", "灵感", "待办", MEMO_UNCATEGORIZED)

        @Volatile
        private var instance: ButlerStore? = null

        fun get(context: Context): ButlerStore =
            instance ?: synchronized(this) {
                instance ?: ButlerStore(context.applicationContext).also { instance = it }
            }
    }
}
