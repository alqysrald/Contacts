package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val titleArabic: String) {
    ROYAL_GOLD("الذهبي الملكي (فاخر)"),
    AMOLED_BLACK("الأسود النقي (AMOLED)"),
    MIDNIGHT_NAVY("الأزرق الليلي الفخم"),
    EMERALD_LUXURY("الزمردي الراقي"),
    MODERN_LIGHT("الوضع الفاتح الأنيق")
}

private val RoyalGoldDarkScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = Color(0xFF1E1700),
    primaryContainer = GoldContainer,
    onPrimaryContainer = OnGoldContainer,
    secondary = GoldLight,
    onSecondary = Color(0xFF231B00),
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianBorder,
    error = AlertRuby
)

private val AmoledDarkScheme = darkColorScheme(
    primary = GoldLight,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF221E14),
    onPrimaryContainer = GoldLight,
    secondary = Color(0xFFE2E8F0),
    onSecondary = Color.Black,
    background = AmoledBackground,
    onBackground = Color.White,
    surface = AmoledSurface,
    onSurface = Color.White,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = Color(0xFFA3A3A3),
    outline = AmoledBorder,
    error = AlertRuby
)

private val MidnightNavyDarkScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF003548),
    primaryContainer = Color(0xFF082F49),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF1E1B4B),
    background = Color(0xFF080D1A),
    onBackground = Color(0xFFF0F9FF),
    surface = Color(0xFF0F1A2E),
    onSurface = Color(0xFFF0F9FF),
    surfaceVariant = Color(0xFF172540),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF1E3A5F),
    error = AlertRuby
)

private val EmeraldLuxuryDarkScheme = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF022C22),
    background = Color(0xFF03100B),
    onBackground = Color(0xFFECFDF5),
    surface = Color(0xFF0A2218),
    onSurface = Color(0xFFECFDF5),
    surfaceVariant = Color(0xFF113828),
    onSurfaceVariant = Color(0xFF6EE7B7),
    outline = Color(0xFF1B4E38),
    error = AlertRuby
)

private val ModernLightScheme = lightColorScheme(
    primary = GoldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFF3D6),
    onPrimaryContainer = Color(0xFF2C2000),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFCBD5E1),
    error = AlertRuby
)

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.ROYAL_GOLD,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.ROYAL_GOLD -> RoyalGoldDarkScheme
        AppThemeMode.AMOLED_BLACK -> AmoledDarkScheme
        AppThemeMode.MIDNIGHT_NAVY -> MidnightNavyDarkScheme
        AppThemeMode.EMERALD_LUXURY -> EmeraldLuxuryDarkScheme
        AppThemeMode.MODERN_LIGHT -> ModernLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
