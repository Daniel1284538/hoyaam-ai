package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeSetting(val title: String) {
    SYSTEM("تلقائي (حسب النظام)"),
    LIGHT("الوضع النهاري"),
    DARK("الوضع الليلي (للقراءة المركزة)")
}

data class CustomHoyaamColors(
    val bg: Color,
    val card: Color,
    val cardMuted: Color,
    val inset: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val text2: Color,
    val textDim: Color,
    val accent: Color,
    val accentHover: Color,
    val heroBg: Color,
    val heroText: Color,
    val heroSub: Color,
    val heroDecor: Color,
    val good: Color,
    val warn: Color,
    val danger: Color,
    val gold: Color
)

val LocalHoyaamColors = staticCompositionLocalOf {
    CustomHoyaamColors(
        bg = LightBg,
        card = LightCard,
        cardMuted = LightCardMuted,
        inset = LightInset,
        border = LightBorder,
        borderStrong = LightBorderStrong,
        text = LightText,
        text2 = LightText2,
        textDim = LightTextDim,
        accent = LightAccent,
        accentHover = LightAccentHover,
        heroBg = LightHeroBg,
        heroText = LightHeroText,
        heroSub = LightHeroSub,
        heroDecor = LightHeroDecor,
        good = LightGood,
        warn = LightWarn,
        danger = LightDanger,
        gold = LightGold
    )
}

private val LightCustomColors = CustomHoyaamColors(
    bg = LightBg,
    card = LightCard,
    cardMuted = LightCardMuted,
    inset = LightInset,
    border = LightBorder,
    borderStrong = LightBorderStrong,
    text = LightText,
    text2 = LightText2,
    textDim = LightTextDim,
    accent = LightAccent,
    accentHover = LightAccentHover,
    heroBg = LightHeroBg,
    heroText = LightHeroText,
    heroSub = LightHeroSub,
    heroDecor = LightHeroDecor,
    good = LightGood,
    warn = LightWarn,
    danger = LightDanger,
    gold = LightGold
)

private val DarkCustomColors = CustomHoyaamColors(
    bg = DarkBg,
    card = DarkCard,
    cardMuted = DarkCardMuted,
    inset = DarkInset,
    border = DarkBorder,
    borderStrong = DarkBorderStrong,
    text = DarkText,
    text2 = DarkText2,
    textDim = DarkTextDim,
    accent = DarkAccent,
    accentHover = DarkAccentHover,
    heroBg = DarkHeroBg,
    heroText = DarkHeroText,
    heroSub = DarkHeroSub,
    heroDecor = DarkHeroDecor,
    good = DarkGood,
    warn = DarkWarn,
    danger = DarkDanger,
    gold = DarkGold
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    primaryContainer = LightHeroBg,
    onPrimaryContainer = LightHeroText,
    secondary = LightGold,
    onSecondary = Color.White,
    tertiary = LightAccent,
    onTertiary = Color.White,
    background = LightBg,
    onBackground = LightText,
    surface = LightCard,
    onSurface = LightText,
    surfaceVariant = LightInset,
    onSurfaceVariant = LightTextDim,
    outline = LightBorder,
    error = LightDanger,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkBg,
    primaryContainer = DarkHeroBg,
    onPrimaryContainer = DarkHeroText,
    secondary = DarkGold,
    onSecondary = DarkBg,
    tertiary = DarkAccent,
    onTertiary = Color.Black,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkCard,
    onSurface = DarkText,
    surfaceVariant = DarkInset,
    onSurfaceVariant = DarkTextDim,
    outline = DarkBorder,
    error = DarkDanger,
    onError = Color.White
)

@Composable
fun HoyaamTheme(
    themeSetting: AppThemeSetting = AppThemeSetting.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeSetting) {
        AppThemeSetting.SYSTEM -> systemDark
        AppThemeSetting.LIGHT -> false
        AppThemeSetting.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val customColors = if (isDark) DarkCustomColors else LightCustomColors

    CompositionLocalProvider(LocalHoyaamColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

