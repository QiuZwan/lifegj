package com.lifebutler.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lifebutler.app.R
import com.lifebutler.app.data.ButlerStore
import com.lifebutler.app.data.Diagnostics
import com.lifebutler.app.data.UpdateCheck
import com.lifebutler.app.ui.components.IconBadge
import com.lifebutler.app.ui.components.LbCard
import com.lifebutler.app.ui.components.LbGhostButton
import com.lifebutler.app.ui.components.LbListRow
import com.lifebutler.app.ui.components.LbPrimaryButton
import com.lifebutler.app.ui.components.SectionHeader
import com.lifebutler.app.ui.components.lbDialogBody
import com.lifebutler.app.ui.components.lbPressable
import com.lifebutler.app.ui.icons.LbIcons
import com.lifebutler.app.ui.theme.LbAccent
import com.lifebutler.app.ui.theme.LbAccentSoft
import com.lifebutler.app.ui.theme.LbInk
import com.lifebutler.app.ui.theme.LbInk2
import com.lifebutler.app.ui.theme.LbInk3
import com.lifebutler.app.ui.theme.LbLine
import com.lifebutler.app.ui.theme.LbOnDark
import com.lifebutler.app.ui.theme.LbSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「关于管家」及其子页：版本信息 / 管家帮助 / 服务协议 / 隐私协议 / 意见反馈 / 上传日志。
 *
 * 参照微信「关于微信」的**功能划分**（不是 UI）：一屏放齐版本与各项条款入口，点进去各是一页。
 *
 * ⚠️ 三处必须保持诚实，改之前先想清楚：
 *  1. 「检查更新」查不到就得说查不到。[UpdateCheck] 把「没查成」和「已是最新」分开报，
 *     这里不许把 Failed 显示成"已是最新"。
 *  2. 「上传日志」**不自动上传**，只唤起系统分享；文案里不许出现"已上传"。
 *  3. 隐私协议里那几条(联网只在这几处、内置额度是明文共享、不采集设备标识)是**照着代码写的**，
 *     改了行为就要同步改文字。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenFeedback: () -> Unit,
) {
    val ctx = LocalContext.current
    val version = remember { UpdateCheck.versionName(ctx) }
    val versionCode = remember { UpdateCheck.versionCode(ctx) }
    val scope = rememberCoroutineScope()

    var checking by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("") }
    var newer by remember { mutableStateOf<UpdateCheck.CheckResult.Newer?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var askLog by remember { mutableStateOf(false) }

    fun doCheck() {
        if (checking) return
        checking = true
        hint = "正在检查…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { UpdateCheck.check(version) }
            checking = false
            when (r) {
                is UpdateCheck.CheckResult.Latest -> hint = "已是最新版本（v${r.version}）"
                is UpdateCheck.CheckResult.Newer -> {
                    hint = "发现新版本 v${r.version}"
                    newer = r
                }
                is UpdateCheck.CheckResult.Failed -> {
                    hint = "没能检查成功"
                    failed = r.reason
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        BackHeader("关于管家", onBack)

        /* ① 图标 + 名字 + 版本 */
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = LbSurface,
                border = BorderStroke(1.dp, LbLine),
            ) {
                /*
                 * ⚠️ **别用 `painterResource(R.mipmap.ic_launcher)`** —— 那是个 `<adaptive-icon>`
                 * XML，不是 VectorDrawable，`painterResource` 只认 VectorDrawable 和位图，
                 * 会在运行期抛 `IllegalArgumentException: Only VectorDrawables and rasterized
                 * asset types are supported`，一进这一页就闪退(踩过一次)。
                 *
                 * 所以按自适应图标的真实构成自己叠两层：背景 + 前景都是 108dp 画布的矢量，
                 * 放进 72dp 的圆角方块里，视觉上正好等于系统把图标裁成方形时的样子
                 * (可见区就是 108 里的中央 72)。
                 */
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_background),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                "生活管家",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = LbInk,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                "版本 v$version（$versionCode）",
                fontSize = 11.5.sp,
                color = LbInk3,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        /* ② 版本信息 + 检查更新 */
        LbCard(Modifier.padding(top = 16.dp)) {
            LbListRow(
                leading = { IconBadge(LbIcons.refresh, LbAccentSoft, LbAccent, size = 34.dp) },
                title = "版本信息",
                sub = "当前 v$version" + if (hint.isNotBlank()) " · $hint" else "",
                trailing = { CheckUpdatePill(checking, onCheck = { doCheck() }) },
                onClick = { doCheck() },
            )
        }
        Text(
            "检查更新会访问 GitHub 的公开接口查最新版本号，只发这一个请求，不带你的任何数据。",
            fontSize = 11.sp,
            color = LbInk3,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )

        /* ③ 帮助与反馈 */
        SectionHeader("支持")
        LbCard {
            LbListRow(
                leading = { IconBadge(LbIcons.messageCircle, LbAccentSoft, LbAccent, size = 34.dp) },
                title = "管家帮助",
                sub = "常用功能怎么用 · 常见问题",
                trailing = { Chevron() },
                onClick = onOpenHelp,
            )
            RowDivider()
            LbListRow(
                leading = { IconBadge(LbIcons.inbox, LbAccentSoft, LbAccent, size = 34.dp) },
                title = "意见反馈",
                sub = "说一句，可以附上诊断日志",
                trailing = { Chevron() },
                onClick = onOpenFeedback,
            )
            RowDivider()
            LbListRow(
                leading = { IconBadge(LbIcons.send, LbAccentSoft, LbAccent, size = 34.dp) },
                title = "上传日志",
                sub = "导出诊断日志，帮我定位问题",
                trailing = { Chevron() },
                onClick = { askLog = true },
            )
        }

        /* ④ 条款 */
        SectionHeader("协议")
        LbCard {
            LbListRow(
                leading = { IconBadge(LbIcons.fileText, LbAccentSoft, LbAccent, size = 34.dp) },
                title = "服务协议",
                trailing = { Chevron() },
                onClick = onOpenTerms,
            )
            RowDivider()
            LbListRow(
                leading = { IconBadge(LbIcons.shieldLock, LbAccentSoft, LbAccent, size = 34.dp) },
                title = "隐私协议",
                sub = "数据只在本机 · 联网只有三处",
                trailing = { Chevron() },
                onClick = onOpenPrivacy,
            )
        }

        Text(
            "生活管家 v$version\n数据全部保存在这台手机上 · 不注册、不登录、不采集设备标识",
            fontSize = 11.sp,
            color = LbInk3,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 10.dp),
        )
    }

    /* 发现新版本 */
    newer?.let { n ->
        LbDialog(
            title = "发现新版本 v${n.version}",
            text = buildString {
                val notes = notesForDialog(n.notes)
                if (notes.isNotBlank()) append(notes).append("\n\n")
                append("当前版本 v$version。更新包会从发布页下载，装之前系统会再确认一次。")
            },
            primary = "去下载",
            onPrimary = {
                openUrl(ctx, n.pageUrl)
                newer = null
            },
            secondary = "稍后",
            onSecondary = { newer = null },
            onDismiss = { newer = null },
        )
    }

    /* 没查成 */
    failed?.let { reason ->
        LbDialog(
            title = "没能检查成功",
            text = reason + "\n\n你可以自己打开发布页看一眼有没有新版本。",
            primary = "打开发布页",
            onPrimary = {
                openUrl(ctx, UpdateCheck.RELEASES_PAGE)
                failed = null
            },
            secondary = "知道了",
            onSecondary = { failed = null },
            onDismiss = { failed = null },
        )
    }

    /* 上传日志前的告知 */
    if (askLog) {
        LbDialog(
            title = "上传日志",
            text = "会生成一份纯文本诊断日志，里面有：版本、机型、系统、权限与开关状态、" +
                "各类记录的**条数**、以及本 App 自己的系统日志。\n\n" +
                "**不含**你的聊天内容、照片和档案正文。日志只在你确认后走系统分享发出去 —— " +
                "这个应用没有服务器，不会自动上传任何东西。\n\n" +
                "要连最近对话一起发，请走「意见反馈」并在那里勾选。",
            primary = "生成并分享",
            onPrimary = {
                askLog = false
                shareDiagnostics(ctx, question = "", withChat = false)
            },
            secondary = "取消",
            onSecondary = { askLog = false },
            onDismiss = { askLog = false },
        )
    }
}

/* ────────────────────────── 管家帮助 ────────────────────────── */

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        BackHeader("管家帮助", onBack)
        Text(
            "从哪开始、每个页面干什么、机器人怎么用 —— 都在这里。",
            fontSize = 12.sp,
            color = LbInk3,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 8.dp),
        )

        HelpSection(
            "先做这三件事",
            listOf(
                "在「我的」里把称呼改了 —— 管家跟你说话会用它。",
                "去「守护」页点一次「一键扫描本机自动续费」，把在扣钱的订阅先捞出来。",
                "想让管家听懂整句话，去「我的 → AI 智能管家」接一个接口（不接就是离线规则模式，也能用）。",
            ),
        )

        HelpSection(
            "五个页面分别在干什么",
            listOf(
                "今日：今天要做的事、在守护的订阅什么时候扣、管家每天的那句简报。",
                "守护：自动续费清单。扫描来源有三个 —— 扣费短信、微信/支付宝等扣费通知、已装的常见订阅类应用。",
                "智能管家：跟它说话。它不只是聊天，能直接帮你写进本机 —— 加订阅、记一笔、加个备忘、跳去某个页面。",
                "家庭：家庭成员、纪念日、家庭档案库（证件照片一类，导出成一整份文本）。",
                "我的：称呼头像、提醒与免打扰、深色模式、桌面天气、AI 设置、备份恢复、清空数据，还有这个「关于管家」。",
            ),
        )

        HelpSection(
            "悬浮小管家",
            listOf(
                "贴右侧的小机器人，可以拖到屏幕任何地方，松手就存住。",
                "点一下就地弹出面板，直接打字发给管家，不用先跳到「智能管家」页。",
                "它默认是静止的；点开面板的时候它才开始转，收起来就停回静止。",
                "它跟「智能管家」页共用同一份对话 —— 在这边说过的，那边也看得到。",
            ),
        )

        HelpSection(
            "备忘录与提醒",
            listOf(
                "随手记，可以分类、可以设提醒时间，到点由本机闹钟叫你。",
                "提醒要「通知权限」；不给权限的话它记着，但不会响。",
                "「我的 → 提醒与免打扰」里可以开关每日简报、改提醒时间。",
            ),
        )

        HelpSection(
            "AI 管家怎么工作（说实话的那种）",
            listOf(
                "没接任何接口时是「离线规则模式」：它只认得写死的句式（比如「记一下：…」「加个订阅：…」），" +
                    "不会自己发挥，也不会编。",
                "想让它听懂整句话，在「我的 → AI 智能管家」里填自己的接口地址、Key 和模型名。",
                "安装包里还带了一份内置的共享额度（智谱 GLM-4-Flash 的免费模型）。" +
                    "注意：那份 Key 就在安装包里、是**倒序存放的，不是加密**；所有使用者共用同一份，" +
                    "会排队、会限流、也可能被服务方封掉。不想用可以关掉，或换成自己的 Key。",
                "发出去的只有你点发送的那一句，加上本机的**摘要**（比如「现有 5 条守护、3 个任务」），" +
                    "不会把你的照片、档案正文发出去。",
                "它说「已经办好了」的时候，是真的写进本机了才会这么说；没写成它会明说「没能写进本机」，" +
                    "以那句话为准。",
            ),
        )

        HelpSection(
            "数据在哪",
            listOf(
                "全部在这台手机里。没有账号、没有服务器，我们也看不到。",
                "换手机或者重装之前，用「我的 → 备份与恢复」导出一份文本，新机器上粘回去。",
                "「清空全部数据」会连本机的照片文件一起删掉，删了就找不回来。",
            ),
        )

        HelpSection(
            "常见问题",
            listOf(
                "提醒不响？→ 我的 → 提醒与免打扰：先看通知权限有没有给，再看提醒时间。",
                "订阅没扫到？→ 守护 → 一键扫描。扣费短信要授权「读取短信」，扣费通知要开「通知使用权」，" +
                    "两个都是只在本机解析、不上传。",
                "管家说做了但其实没做？→ 看它最后有没有「没能写进本机」。有那句就是没做成，重说一次或换个说法。",
                "天气不动？→ 要位置权限（只用来查天气）。关掉桌面天气后，App 完全不联网。",
                "小机器人不见了？→ 多半是被拖到边上了，试着在屏幕右侧和底部找找。" +
                    "「清空全部数据」会把它复位回右侧。",
                "换了手机数据没了？→ 本机存储，没有云端备份。这就是为什么建议定期导出。",
            ),
        )

        Box(Modifier.height(14.dp))
    }
}

@Composable
private fun HelpSection(title: String, items: List<String>) {
    SectionHeader(title)
    LbCard {
        items.forEachIndexed { i, it ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(vertical = 7.dp))
            Row(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(LbAccent),
                )
                RichText(
                    text = it,
                    fontSize = 12.5.sp,
                    color = LbInk2,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(start = 9.dp),
                )
            }
        }
    }
}

/* ────────────────────────── 协议正文 ────────────────────────── */

@Composable
fun TermsScreen(onBack: () -> Unit) = LegalScreen("服务协议", TERMS, onBack)

@Composable
fun PrivacyScreen(onBack: () -> Unit) = LegalScreen("隐私协议", PRIVACY, onBack)

@Composable
private fun LegalScreen(title: String, blocks: List<Pair<String, String>>, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        BackHeader(title, onBack)
        Text(
            "最近更新：随当前版本发布",
            fontSize = 11.sp,
            color = LbInk3,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        )
        blocks.forEach { (head, body) ->
            Text(
                head,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = LbInk,
                modifier = Modifier.padding(top = 16.dp),
            )
            RichText(
                text = body,
                fontSize = 12.5.sp,
                color = LbInk2,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            "本页文字是照着这个版本的实际行为写的。将来行为变了，这里会跟着改。",
            fontSize = 11.sp,
            color = LbInk3,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 20.dp, bottom = 14.dp),
        )
    }
}

/* ────────────────────────── 意见反馈 ────────────────────────── */

/** 意见反馈最多能附几张图（跟微信一样留 9 格）。 */
private const val MAX_PICS = 9

@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ButlerStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var withLog by remember { mutableStateOf(true) }
    var pics by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // 进来就把光标放进输入框、键盘自己弹起来，少点一下（跟悬浮小管家的面板一个做法）；
    // 同时清掉上一轮挑的图 —— 它们只是那一条反馈的附件，不是用户记录，不该留在盘上。
    LaunchedEffect(Unit) {
        Diagnostics.clearFeedbackImages(ctx)
        delay(160)
        runCatching { focusRequester.requestFocus() }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICS),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val added = withContext(Dispatchers.IO) {
                Diagnostics.addFeedbackImages(ctx, store, uris, MAX_PICS - pics.size)
            }
            pics = pics + added
            busy = false
            if (added.isEmpty()) Toast.makeText(ctx, "图片没能读进来", Toast.LENGTH_SHORT).show()
        }
    }

    fun send() {
        try {
            Diagnostics.shareFeedback(ctx, store, text.trim(), pics, withLog)
            Toast.makeText(ctx, "已交给系统分享", Toast.LENGTH_SHORT).show()
            text = ""
            pics = emptyList()
            // ⚠️ 这里**不能**删图：分享面板是另一个界面，用户要过几秒才点收件方，
            // 那时收件方才来读这些 content:// —— 面板一弹出就把文件删掉 = 附件时有时无的幽灵 bug。
            // 清理放在**下一次**进这个页面时（上面的 LaunchedEffect），跟诊断日志那只 .txt 一个路子。
        } catch (e: Exception) {
            Toast.makeText(ctx, "分享失败：" + e.javaClass.simpleName, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        BackHeader("意见反馈", onBack)
        Text(
            "碰上哪儿不对、哪句话没听懂、哪里点着别扭，都可以写。",
            fontSize = 12.sp,
            color = LbInk3,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 8.dp),
        )

        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(fontSize = 13.sp, color = LbInk, lineHeight = 20.sp),
            cursorBrush = SolidColor(LbAccent),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(top = 12.dp)
                .heightIn(min = 140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LbSurface)
                .padding(14.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text("比如：在「守护」里扫出来的订阅重复了两条…", fontSize = 13.sp, color = LbInk3)
                    }
                    inner()
                }
            },
        )

        /* 图片附件。缩略图走 LocalImage（项目里只认这一条渲染入口）；移除时连盘上的文件一起删。 */
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("图片（可选）", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = LbInk)
            Text("最多 $MAX_PICS 张", fontSize = 11.sp, color = LbInk3, modifier = Modifier.padding(start = 6.dp))
            if (pics.isNotEmpty()) {
                Text(
                    "${pics.size}/$MAX_PICS",
                    fontSize = 11.sp,
                    color = LbInk3,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pics.forEach { p ->
                Box(Modifier.size(72.dp)) {
                    LocalImage(
                        path = p,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        maxDim = 220,
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(19.dp)
                            .clip(CircleShape)
                            .background(LbInk3)
                            .lbPressable(onClick = {
                                Diagnostics.deleteImage(p)
                                pics = pics - p
                            }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            LbIcons.x,
                            contentDescription = "移除这张",
                            tint = LbOnDark,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }
            if (pics.size < MAX_PICS) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LbSurface)
                        .border(BorderStroke(1.dp, LbLine), RoundedCornerShape(12.dp))
                        .lbPressable(onClick = {
                            if (!busy) {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            }
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        LbIcons.plus,
                        contentDescription = "添加图片",
                        tint = LbInk3,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LbSurface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("附上诊断日志", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = LbInk)
                Text(
                    "版本、机型、权限状态、各类记录的条数、本 App 的日志；不含聊天和照片",
                    fontSize = 11.sp,
                    color = LbInk3,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Switch(
                checked = withLog,
                onCheckedChange = { withLog = it },
                colors = SwitchDefaults.colors(checkedTrackColor = LbAccent),
            )
        }

        Text(
            "点「发送」会弹出系统的分享面板，发给谁由你决定 —— 微信、QQ、邮箱都行；" +
                "图和日志会作为附件一起带上。这个应用没有服务器，不会自动上传，也收不到你没发出去的东西。",
            fontSize = 11.sp,
            color = LbInk3,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 10.dp),
        )

        LbPrimaryButton(
            text = "发送",
            enabled = text.isNotBlank() || pics.isNotEmpty() || withLog,
            onClick = { send() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
        )
        LbGhostButton(
            text = "用邮件发到 " + UpdateCheck.SUPPORT_EMAIL,
            onClick = {
                try {
                    Diagnostics.mailFeedback(ctx, text.trim())
                } catch (e: Exception) {
                    Toast.makeText(ctx, "没有能发邮件的应用", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        Text(
            "邮件那条只带文字 —— mailto 协议本身不支持附件。要连图和日志一起发，用上面的「发送」。",
            fontSize = 11.sp,
            color = LbInk3,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "顺带一句：管家听懂整句话这件事，是接在你自己的接口上的。" +
                "如果它答非所问，多半是接口或模型的问题，不一定是应用的问题。",
            fontSize = 11.sp,
            color = LbInk3,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 14.dp),
        )
    }
}

/* ────────────────────────── 小零件 ────────────────────────── */

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onBack)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(LbIcons.chevronLeft, contentDescription = "返回", tint = LbInk2, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LbLine),
    )
}

@Composable
private fun Chevron() {
    Icon(LbIcons.chevronRight, contentDescription = null, tint = LbInk3, modifier = Modifier.size(15.dp))
}

/** 「检查更新」那颗小胶囊。检查中就地转成文字，别让它看起来还能再点。 */
@Composable
private fun CheckUpdatePill(checking: Boolean, onCheck: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(LbAccentSoft)
            .then(
                if (checking) Modifier
                else Modifier.lbPressable(onClick = onCheck),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            if (checking) "检查中…" else "检查更新",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = LbAccent,
        )
    }
}

/**
 * 把 `**重点**` 渲染成加粗。
 *
 * ⚠️ Compose 的 `Text` **不认 Markdown**：协议里那句「**不是加密**」如果不处理，
 * 用户看到的就是字面上的两个星号（踩过一次，截图里清清楚楚）。
 * 协议里那几处强调是必须被读到的，所以这里给一个最小的加粗渲染，只认 `**` 这一种记号。
 */
@Composable
private fun RichText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
) {
    val ann = remember(text) {
        buildAnnotatedString {
            var i = 0
            while (true) {
                val s = text.indexOf("**", i)
                if (s < 0) {
                    append(text.substring(i))
                    break
                }
                val e = text.indexOf("**", s + 2)
                if (e < 0) {
                    append(text.substring(i))
                    break
                }
                append(text.substring(i, s))
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(text.substring(s + 2, e))
                }
                i = e + 2
            }
        }
    }
    Text(ann, fontSize = fontSize, color = color, lineHeight = lineHeight, modifier = modifier)
}

/** 更新说明在弹窗里最多显示这么多字。正文本身已可滚动,这个上限只是防超长。 */
private const val NOTES_IN_DIALOG_MAX = 1800

/**
 * 更新说明的展示口径:超长时**按行截断**,并在末尾如实说"后面还有、去看发布页"。
 *
 * ⚠️ 两个坑都踩过:① 之前写 `take(800)` 硬切,会把「…不显示「共 N 条」这类假」这种半句留在屏幕上,
 * 看着像乱码;② 800 字实测约 1064dp 高,超过整屏可用高度,把底下的「去下载」按钮顶出了屏幕
 * —— 而弹窗不会滚,用户的表现就是「滑不动、找不到按钮」。
 * 前者靠 `substringBeforeLast('\n')`(切在行边界,不会切进 `**加粗**` 中间),后者靠正文挂 `lbDialogBody()`。
 */
private fun notesForDialog(raw: String): String {
    val notes = prettyNotes(raw)
    if (notes.length <= NOTES_IN_DIALOG_MAX) return notes
    val head = notes.take(NOTES_IN_DIALOG_MAX).substringBeforeLast('\n')
    return head + "\n\n（更新说明较长,这里只显示到上面这一段;完整说明在发布页里。）"
}

/**
 * 把 GitHub Release 的说明（Markdown）收拾成能塞进小弹窗的纯文本。
 *
 * 说明正文是我们自己写的，带 `##` 小标题、`- ` 列表、表格和反引号；
 * 直接塞进 `Text` 会连井号和竖线一起显示出来。这里只做「不渲染 Markdown 时最不难看」的处理。
 */
private fun prettyNotes(raw: String): String = raw.lines()
    .map { line ->
        val t = line.trimStart()
        when {
            t.startsWith("#") -> t.trimStart('#').trim()
            t.startsWith("- ") || t.startsWith("* ") -> "· " + t.drop(2)
            t.startsWith("|") -> ""          // 表格留着只会是一堆竖线，丢掉
            else -> line
        }
    }
    .joinToString("\n")
    .replace("`", "")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

/** 自己拼一个双按钮弹窗：通用的 [com.lifebutler.app.ui.components.LbTwoActionDialog] 右侧是**红**的，
 * 用它来放「稍后 / 取消」会比较像危险操作，这里要的是「主按钮 + 一个安静的次要按钮」。 */
@Composable
private fun LbDialog(
    title: String,
    text: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = LbSurface) {
            Column(Modifier.padding(20.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk)
                RichText(
                    text = text,
                    fontSize = 12.5.sp,
                    color = LbInk2,
                    lineHeight = 19.sp,
                    // 正文必须能滚:更新说明的长度由发布页决定,不挂这个底下两个按钮会被顶出屏幕
                    modifier = Modifier.padding(top = 8.dp).lbDialogBody(),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LbGhostButton(secondary, onSecondary, Modifier.weight(1f))
                    LbPrimaryButton(primary, onPrimary, Modifier.weight(1f))
                }
            }
        }
    }
}

/* ────────────────────────── 分享诊断日志 ────────────────────────── */

private fun shareDiagnostics(ctx: Context, question: String, withChat: Boolean) {
    try {
        val store = ButlerStore.get(ctx)
        val text = Diagnostics.buildText(ctx, store, question, withChat)
        val f = Diagnostics.write(ctx, text)
        ctx.startActivity(Intent.createChooser(Diagnostics.shareIntent(ctx, f, "生活管家 · 诊断日志"), "发送诊断日志"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "导出日志失败：" + e.javaClass.simpleName, Toast.LENGTH_SHORT).show()
    }
}

private fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(ctx, "没有能打开网页的应用", Toast.LENGTH_SHORT).show()
    }
}

/* ────────────────────────── 协议正文内容 ────────────────────────── */

private val TERMS = listOf(
    "这是什么" to
        "「生活管家」是一个装在你自己手机上的记录工具：帮你看住自动续费、记下要做的事和家庭事项，" +
        "并可以（你自己决定）接一个 AI 模型，用说话的方式帮你写进本机。",
    "你的数据归你" to
        "全部记录都存在这台手机里。我们不提供账号、不收集你的数据，因此也看不到、更没法替你恢复 —— " +
        "换机或重装前请自己用「备份与恢复」导出一份。",
    "AI 说的话不等于事实" to
        "AI 可能说错。订阅是否真的会扣、扣多少，以商家和银行的扣款为准；本应用不代收、不代扣，" +
        "也无法替你取消第三方的订阅（「去关闭」给的是真实步骤和跳转，最后一步要你自己点）。",
    "关于内置的共享额度" to
        "安装包里带了一份免费的共享 Key，所有使用者共用同一份额度，可能出现排队、限流，或被服务方封禁。" +
        "这份 Key 是明文倒序存放的，**不是加密**，任何人都可能从安装包里取出来。" +
        "你可以随时在「我的 → AI 智能管家」里关掉它，或换成自己的 Key。请勿用它做违法或滥用的事。",
    "只在本机解析的内容" to
        "扣费短信、扣费通知只在这台手机上解析，用来生成守护清单，不上传任何内容。",
    "免责" to
        "因设备故障、系统清理、误删、卸载应用等造成的数据丢失，我们无法找回。请在换机前自行导出备份。",
    "使用规范" to
        "不得把本应用用于违法用途；不得反向破解、批量刷取或转售内置额度。",
    "协议变更与终止" to
        "本协议可能随新版本调整，随包发布，继续使用即视为接受。你随时可以卸载本应用，" +
        "卸载即删除本机全部数据。",
)

private val PRIVACY = listOf(
    "一句话" to
        "不注册、不登录、不采集设备标识、不做统计埋点。你的记录只在这台手机里。",
    "我们在这台手机上存了什么" to
        "任务、守护清单、订阅、备忘、账目、家庭成员与纪念日、家庭档案、相册与头像、跟管家的对话，" +
        "以及界面偏好（深色模式、悬浮管家位置、提醒时间）。都存在应用的私有目录，卸载即删除。",
    "联网只发生在这三处" to
        "① 桌面天气（可关）：开启后把你的**大致位置**（经纬度）发给 open-meteo.com，换回气温和天气；" +
        "关掉之后完全不联网。\n" +
        "② AI 智能管家：你点发送时，把你这一句 + 本机摘要（例如「现有 5 条守护、3 个任务」）发给" +
        "**你自己配置的**模型服务（若用内置额度，则发给对应的服务方）换回回答。不发照片，不发档案正文。\n" +
        "③ 检查更新（关于管家页）：访问 GitHub 的公开接口查最新版本号，只发这一个请求，不带你的任何数据。",
    "权限都用来干什么" to
        "通知：每日简报和备忘提醒。\n" +
        "读取短信：只在「一键扫描」里解析扣费短信，仅本机。\n" +
        "位置（粗略）：只用于查天气。\n" +
        "通知使用权：读取扣费通知，仅本机解析。\n" +
        "查询已安装应用白名单：判断你装了哪些常见订阅类 App，避免申请「读取全部应用」的权限。\n" +
        "这些都可以不授权，应用会相应降级，而不是罢工。",
    "关于内置的 AI 额度" to
        "安装包里带了一份共享 Key（智谱 GLM-4-Flash），是明文倒序存放的，**不是加密** —— " +
        "谁都可能从安装包里取出来。因此：额度是所有使用者共用的，可能排队、限流、被封；" +
        "你发出去的对话内容会经过该服务方。介意就在设置里关掉它，或使用自己的 Key。",
    "我们不收集什么" to
        "不采集 IMEI、MAC、广告标识等设备标识；不读通讯录；不上传相册；不接入任何第三方统计或广告 SDK" +
        "（本应用的全部依赖只有 AndroidX / Jetpack Compose 与 3D 渲染库）。",
    "你的控制权" to
        "「我的 → 清空全部数据」会删掉本机记录和本机照片文件；卸载应用会删掉全部数据。" +
        "诊断日志只在**你确认后**、由**你通过系统分享**发出，接收方由你选择，应用不会自动上传。",
    "儿童" to
        "本应用不面向上网能力有限的低龄儿童单独提供服务，也不会针对儿童做任何采集。",
    "变更" to
        "本协议随版本更新。如果行为有变化，这里的文字和更新说明会一起改。",
)
