package com.lifebutler.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 「静护」线性图标集(与设计稿同源)。
 * 路径数据来自 Tabler Icons v3(MIT License,https://tabler.io/icons), 1.7px 描边, 24dp 网格。
 * 在 Compose 中通过 Icon(imageVector, tint = ...) 使用,颜色随 tint 变化。
 */
private fun lb(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathData = addPathNodes(pathData),
        )
    }.build()

object LbIcons {
    val home2: ImageVector by lazy { lb("home2", "M5 12l-2 0l9 -9l9 9l-2 0 M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-7 M10 12h4v4h-4l0 -4") }
    val shieldCheck: ImageVector by lazy { lb("shieldCheck", "M11.46 20.846a12 12 0 0 1 -7.96 -14.846a12 12 0 0 0 8.5 -3a12 12 0 0 0 8.5 3a12 12 0 0 1 -.09 7.06 M15 19l2 2l4 -4") }
    val messageCircle: ImageVector by lazy { lb("messageCircle", "M3 20l1.3 -3.9c-2.324 -3.437 -1.426 -7.872 2.1 -10.374c3.526 -2.501 8.59 -2.296 11.845 .48c3.255 2.777 3.695 7.266 1.029 10.501c-2.666 3.235 -7.615 4.215 -11.574 2.293l-4.7 1") }
    val users: ImageVector by lazy { lb("users", "M5 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0 M3 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2 M16 3.13a4 4 0 0 1 0 7.75 M21 21v-2a4 4 0 0 0 -3 -3.85") }
    val folders: ImageVector by lazy { lb("folders", "M9 3h3l2 2h5a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-9a2 2 0 0 1 2 -2 M17 16v2a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-9a2 2 0 0 1 2 -2h2") }
    val creditCard: ImageVector by lazy { lb("creditCard", "M3 8a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3l0 -8 M3 10l18 0 M7 15l.01 0 M11 15l2 0") }
    val calendarEvent: ImageVector by lazy { lb("calendarEvent", "M4 7a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2l0 -12 M16 3l0 4 M8 3l0 4 M4 11l16 0 M8 15h2v2h-2l0 -2") }
    val check: ImageVector by lazy { lb("check", "M5 12l5 5l10 -10") }
    val chevronRight: ImageVector by lazy { lb("chevronRight", "M9 6l6 6l-6 6") }
    val chevronLeft: ImageVector by lazy { lb("chevronLeft", "M15 6l-6 6l6 6") }
    val arrowUpRight: ImageVector by lazy { lb("arrowUpRight", "M17 7l-10 10 M8 7l9 0l0 9") }
    val refresh: ImageVector by lazy { lb("refresh", "M20 11a8.1 8.1 0 0 0 -15.5 -2m-.5 -4v4h4 M4 13a8.1 8.1 0 0 0 15.5 2m.5 4v-4h-4") }
    val circleCheck: ImageVector by lazy { lb("circleCheck", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0 M9 12l2 2l4 -4") }
    val alertTriangle: ImageVector by lazy { lb("alertTriangle", "M12 9v4 M10.363 3.591l-8.106 13.534a1.914 1.914 0 0 0 1.636 2.871h16.214a1.914 1.914 0 0 0 1.636 -2.87l-8.106 -13.536a1.914 1.914 0 0 0 -3.274 0 M12 16h.01") }
    val fileText: ImageVector by lazy { lb("fileText", "M14 3v4a1 1 0 0 0 1 1h4 M17 21h-10a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2h7l5 5v11a2 2 0 0 1 -2 2 M9 9l1 0 M9 13l6 0 M9 17l6 0") }
    val mapPin: ImageVector by lazy { lb("mapPin", "M9 11a3 3 0 1 0 6 0a3 3 0 0 0 -6 0 M17.657 16.657l-4.243 4.243a2 2 0 0 1 -2.827 0l-4.244 -4.243a8 8 0 1 1 11.314 0") }
    val cake: ImageVector by lazy { lb("cake", "M3 20h18v-8a3 3 0 0 0 -3 -3h-12a3 3 0 0 0 -3 3v8 M3 14.803c.312 .135 .654 .204 1 .197a2.4 2.4 0 0 0 2 -1a2.4 2.4 0 0 1 2 -1a2.4 2.4 0 0 1 2 1a2.4 2.4 0 0 0 2 1a2.4 2.4 0 0 0 2 -1a2.4 2.4 0 0 1 2 -1a2.4 2.4 0 0 1 2 1a2.4 2.4 0 0 0 2 1c.35 .007 .692 -.062 1 -.197 M12 4l1.465 1.638a2 2 0 1 1 -3.015 .099l1.55 -1.737") }
    val heart: ImageVector by lazy { lb("heart", "M19.5 12.572l-7.5 7.428l-7.5 -7.428a5 5 0 1 1 7.5 -6.566a5 5 0 1 1 7.5 6.572") }
    val shieldLock: ImageVector by lazy { lb("shieldLock", "M12 3a12 12 0 0 0 8.5 3a12 12 0 0 1 -8.5 15a12 12 0 0 1 -8.5 -15a12 12 0 0 0 8.5 -3 M11 11a1 1 0 1 0 2 0a1 1 0 1 0 -2 0 M12 12l0 2.5") }
    val cat: ImageVector by lazy { lb("cat", "M20 3v10a8 8 0 1 1 -16 0v-10l3.432 3.432a7.963 7.963 0 0 1 4.568 -1.432c1.769 0 3.403 .574 4.728 1.546l3.272 -3.546 M2 16h5l-4 4 M22 16h-5l4 4 M11 16a1 1 0 1 0 2 0a1 1 0 1 0 -2 0 M9 11v.01 M15 11v.01") }
    val search: ImageVector by lazy { lb("search", "M3 10a7 7 0 1 0 14 0a7 7 0 1 0 -14 0 M21 21l-6 -6") }
    val plus: ImageVector by lazy { lb("plus", "M12 5l0 14 M5 12l14 0") }
    val microphone: ImageVector by lazy { lb("microphone", "M9 5a3 3 0 0 1 3 -3a3 3 0 0 1 3 3v5a3 3 0 0 1 -3 3a3 3 0 0 1 -3 -3l0 -5 M5 10a7 7 0 0 0 14 0 M8 21l8 0 M12 17l0 4") }
    val inbox: ImageVector by lazy { lb("inbox", "M4 6a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2l0 -12 M4 13h3l3 3h4l3 -3h3") }
    val car: ImageVector by lazy { lb("car", "M5 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0 M15 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0 M5 17h-2v-6l2 -5h9l4 5h1a2 2 0 0 1 2 2v4h-2m-4 0h-6m-6 -6h15m-6 0v-5") }
    val phoneCall: ImageVector by lazy { lb("phoneCall", "M5 4h4l2 5l-2.5 1.5a11 11 0 0 0 5 5l1.5 -2.5l5 2v4a2 2 0 0 1 -2 2a16 16 0 0 1 -15 -15a2 2 0 0 1 2 -2 M15 7a2 2 0 0 1 2 2 M15 3a6 6 0 0 1 6 6") }
    val wallet: ImageVector by lazy { lb("wallet", "M17 8v-3a1 1 0 0 0 -1 -1h-10a2 2 0 0 0 0 4h12a1 1 0 0 1 1 1v3m0 4v3a1 1 0 0 1 -1 1h-12a2 2 0 0 1 -2 -2v-12 M20 12v4h-4a2 2 0 0 1 0 -4h4") }
    val receipt: ImageVector by lazy { lb("receipt", "M5 21v-16a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v16l-3 -2l-2 2l-2 -2l-2 2l-2 -2l-3 2m4 -14h6m-6 4h6m-2 4h2") }
    val clock: ImageVector by lazy { lb("clock", "M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0 M12 7v5l3 3") }
    val bell: ImageVector by lazy { lb("bell", "M10 5a2 2 0 1 1 4 0a7 7 0 0 1 4 6v3a4 4 0 0 0 2 3h-16a4 4 0 0 0 2 -3v-3a7 7 0 0 1 4 -6 M9 17v1a3 3 0 0 0 6 0v-1") }
    val x: ImageVector by lazy { lb("x", "M18 6l-12 12 M6 6l12 12") }
    val settings: ImageVector by lazy { lb("settings", "M10.325 4.317c.426 -1.756 2.924 -1.756 3.35 0a1.724 1.724 0 0 0 2.573 1.066c1.543 -.94 3.31 .826 2.37 2.37a1.724 1.724 0 0 0 1.065 2.572c1.756 .426 1.756 2.924 0 3.35a1.724 1.724 0 0 0 -1.066 2.573c.94 1.543 -.826 3.31 -2.37 2.37a1.724 1.724 0 0 0 -2.572 1.065c-.426 1.756 -2.924 1.756 -3.35 0a1.724 1.724 0 0 0 -2.573 -1.066c-1.543 .94 -3.31 -.826 -2.37 -2.37a1.724 1.724 0 0 0 -1.065 -2.572c-1.756 -.426 -1.756 -2.924 0 -3.35a1.724 1.724 0 0 0 1.066 -2.573c-.94 -1.543 .826 -3.31 2.37 -2.37c1 .608 2.296 .07 2.572 -1.065 M9 12a3 3 0 1 0 6 0a3 3 0 0 0 -6 0") }
    val eye: ImageVector by lazy { lb("eye", "M10 12a2 2 0 1 0 4 0a2 2 0 0 0 -4 0 M21 12c-2.4 4 -5.4 6 -9 6c-3.6 0 -6.6 -2 -9 -6c2.4 -4 5.4 -6 9 -6c3.6 0 6.6 2 9 6") }
    val pencil: ImageVector by lazy { lb("pencil", "M4 20h4l10.5 -10.5a2.828 2.828 0 1 0 -4 -4l-10.5 10.5v4 M13.5 6.5l4 4") }
    val scan: ImageVector by lazy { lb("scan", "M5 12h14 M3 7v-2a2 2 0 0 1 2 -2h2 M3 17v2a2 2 0 0 0 2 2h2 M17 3h2a2 2 0 0 1 2 2v2 M17 21h2a2 2 0 0 0 2 -2v-2") }
    val camera: ImageVector by lazy { lb("camera", "M5 7h1a2 2 0 0 0 2 -2a1 1 0 0 1 1 -1h6a1 1 0 0 1 1 1a2 2 0 0 0 2 2h1a2 2 0 0 1 2 2v9a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-9a2 2 0 0 1 2 -2 M9 13a3 3 0 1 0 6 0a3 3 0 0 0 -6 0") }
    val listCheck: ImageVector by lazy { lb("listCheck", "M3.5 5.5l1.5 1.5l2.5 -2.5 M3.5 11.5l1.5 1.5l2.5 -2.5 M3.5 17.5l1.5 1.5l2.5 -2.5 M11 6l9 0 M11 12l9 0 M11 18l9 0") }
    val buildingStore: ImageVector by lazy { lb("buildingStore", "M3 21l18 0 M3 7v1a3 3 0 0 0 6 0v-1m0 1a3 3 0 0 0 6 0v-1m0 1a3 3 0 0 0 6 0v-1h-18l2 -4h14l2 4 M5 21l0 -10.15 M19 21l0 -10.15 M9 21v-4a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2v4") }
    val user: ImageVector by lazy { lb("user", "M8 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0 M6 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2") }
    val download: ImageVector by lazy { lb("download", "M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2 -2v-2 M7 11l5 5l5 -5 M12 4l0 12") }
    val send: ImageVector by lazy { lb("send", "M10 14l11 -11 M21 3l-6.5 18a.55 .55 0 0 1 -1 0l-3.5 -7l-7 -3.5a.55 .55 0 0 1 0 -1l18 -6.5") }
    val trash: ImageVector by lazy { lb("trash", "M4 7l16 0 M10 11l0 6 M14 11l0 6 M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2 -2l1 -12 M9 7v-3a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v3") }
    val deviceFloppy: ImageVector by lazy { lb("deviceFloppy", "M6 4h10l4 4v10a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2 M12 14m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0 M14 4l0 4l-4 0l0 -4") }
    val moon: ImageVector by lazy { lb("moon", "M12 3a6 6 0 0 0 9 9a9 9 0 1 1 -9 -9Z") }
    val sun: ImageVector by lazy { lb("sun", "M12 12m-4 0a4 4 0 1 0 8 0a4 4 0 1 0 -8 0 M3 12h1m8 -9v1m8 8h1m-9 8v1m-6.4 -15.4l.7 .7m12.1 -.7l-.7 .7m0 11.4l.7 .7m-12.1 -.7l-.7 .7") }
    val cloud: ImageVector by lazy { lb("cloud", "M6.657 18c-2.572 0 -4.657 -2.007 -4.657 -4.483c0 -2.475 2.085 -4.482 4.657 -4.482c.393 -1.762 1.794 -3.2 3.675 -3.773c1.88 -.572 3.956 -.193 5.444 1c1.488 1.19 2.162 3.007 1.77 4.769h.99c1.913 0 3.464 1.56 3.464 3.486c0 1.927 -1.551 3.487 -3.465 3.487h-11.878") }
    val cloudRain: ImageVector by lazy { lb("cloudRain", "M7 18a4.6 4.4 0 0 1 0 -9a5 4.5 0 0 1 11 2h1a3.5 3.5 0 0 1 0 7 M11 13v2m0 3v2m4 -5v2m0 3v2") }
    val notebook: ImageVector by lazy { lb("notebook", "M6 4h11a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-11a1 1 0 0 1 -1 -1v-14a1 1 0 0 1 1 -1 M9 4v18 M13 8l2 0 M13 12l2 0") }
    val pin: ImageVector by lazy { lb("pin", "M15 4.5l-4 4l-4 1.5l-1.5 1.5l7 7l1.5 -1.5l1.5 -4l4 -4 M9 15l-4.5 4.5 M14.5 4l5.5 5.5") }
}