package com.lifebutler.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ── 「静护」Design Tokens · 支持深色模式 ──
 * 浅色:骨白纸面 + 深玉绿;深色:墨绿夜色 + 浅玉绿。
 * 颜色以 getter 暴露,读取 LbTheme.dark 状态,切换即时生效。 */

object LbTheme {
    var dark by mutableStateOf(false)
}

val LbBg: Color get() = if (LbTheme.dark) Color(0xFF121612) else Color(0xFFF4F3EF)
val LbSurface: Color get() = if (LbTheme.dark) Color(0xFF1B211C) else Color(0xFFFCFBF8)
val LbSurface2: Color get() = if (LbTheme.dark) Color(0xFF242B25) else Color(0xFFF0EFE8)
val LbInk: Color get() = if (LbTheme.dark) Color(0xFFEFEDE6) else Color(0xFF191D1A)
val LbInk2: Color get() = if (LbTheme.dark) Color(0xFFAEB5AC) else Color(0xFF525851)
val LbInk3: Color get() = if (LbTheme.dark) Color(0xFF7C837A) else Color(0xFF878D85)
val LbLine: Color get() = if (LbTheme.dark) Color(0xFF2B322C) else Color(0xFFE6E4DC)
val LbLineStrong: Color get() = if (LbTheme.dark) Color(0xFF39413A) else Color(0xFFD8D6CC)
val LbAccent: Color get() = if (LbTheme.dark) Color(0xFF6FB79A) else Color(0xFF2E6B58)
val LbAccentDeep: Color get() = if (LbTheme.dark) Color(0xFF8FC7AE) else Color(0xFF235043)
val LbAccentSoft: Color get() = if (LbTheme.dark) Color(0xFF1F322A) else Color(0xFFE8F0EB)
val LbOnAccent: Color get() = if (LbTheme.dark) Color(0xFF0E2019) else Color(0xFFF3F7F4)
val LbDark: Color get() = if (LbTheme.dark) Color(0xFF26332C) else Color(0xFF1E2A24)
val LbOnDark: Color = Color(0xFFF6F5F0)
val LbOnDark2: Color = Color(0x9EF6F5F0)
val LbAmber: Color get() = if (LbTheme.dark) Color(0xFFD8A95C) else Color(0xFF96661F)
val LbAmberSoft: Color get() = if (LbTheme.dark) Color(0xFF33291A) else Color(0xFFF6EEDC)
val LbRust: Color get() = if (LbTheme.dark) Color(0xFFE08A7E) else Color(0xFFA0453B)
val LbRustSoft: Color get() = if (LbTheme.dark) Color(0xFF3A2320) else Color(0xFFF7E9E6)

private fun lbColorScheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = Color(0xFF6FB79A),
        onPrimary = Color(0xFF0E2019),
        primaryContainer = Color(0xFF1F322A),
        onPrimaryContainer = Color(0xFF8FC7AE),
        background = Color(0xFF121612),
        onBackground = Color(0xFFEFEDE6),
        surface = Color(0xFF1B211C),
        onSurface = Color(0xFFEFEDE6),
        surfaceVariant = Color(0xFF242B25),
        onSurfaceVariant = Color(0xFFAEB5AC),
        outline = Color(0xFF39413A),
        outlineVariant = Color(0xFF2B322C),
        error = Color(0xFFE08A7E),
        onError = Color(0xFF2A100C),
        errorContainer = Color(0xFF3A2320),
    )
} else {
    lightColorScheme(
        primary = Color(0xFF2E6B58),
        onPrimary = Color(0xFFF3F7F4),
        primaryContainer = Color(0xFFE8F0EB),
        onPrimaryContainer = Color(0xFF235043),
        background = Color(0xFFF4F3EF),
        onBackground = Color(0xFF191D1A),
        surface = Color(0xFFFCFBF8),
        onSurface = Color(0xFF191D1A),
        surfaceVariant = Color(0xFFF0EFE8),
        onSurfaceVariant = Color(0xFF525851),
        outline = Color(0xFFD8D6CC),
        outlineVariant = Color(0xFFE6E4DC),
        error = Color(0xFFA0453B),
        onError = Color.White,
        errorContainer = Color(0xFFF7E9E6),
    )
}

@Composable
private fun lbTypography(): Typography = remember(LbTheme.dark) {
    Typography(
        headlineLarge = TextStyle(
            fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 33.sp, color = LbInk,
        ),
        headlineMedium = TextStyle(
            fontSize = 23.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp, color = LbInk,
        ),
        titleMedium = TextStyle(
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LbInk,
        ),
        titleSmall = TextStyle(
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LbInk,
        ),
        bodyMedium = TextStyle(
            fontSize = 14.sp, lineHeight = 22.sp, color = LbInk,
        ),
        bodySmall = TextStyle(
            fontSize = 12.5.sp, lineHeight = 18.sp, color = LbInk2,
        ),
        labelSmall = TextStyle(
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LbInk3,
        ),
    )
}

private val LbShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LifeButlerTheme(dark: Boolean = LbTheme.dark, content: @Composable () -> Unit) {
    SideEffect { LbTheme.dark = dark }
    MaterialTheme(
        colorScheme = lbColorScheme(dark),
        typography = lbTypography(),
        shapes = LbShapes,
        content = content,
    )
}
