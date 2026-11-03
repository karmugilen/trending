package app.omalauncher.trending.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.omalauncher.trending.data.Settings
import app.omalauncher.trending.data.ThemeMode
import kotlin.math.abs

data class PaletteOption(val name: String, val seed: Color)

val Palettes: List<PaletteOption> = listOf(
    PaletteOption("amber", Color(0xFFE68E0D)),
    PaletteOption("blue", Color(0xFF1E88E5)),
    PaletteOption("teal", Color(0xFF00897B)),
    PaletteOption("green", Color(0xFF43A047)),
    PaletteOption("purple", Color(0xFF8E24AA)),
    PaletteOption("pink", Color(0xFFD81B60)),
    PaletteOption("red", Color(0xFFE53935)),
    PaletteOption("indigo", Color(0xFF3949AB)),
)

val TrendingTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 48.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    ),
)

fun colorSchemeFor(seed: Color, dark: Boolean, black: Boolean): ColorScheme {
    val (h, s, _) = colorToHsl(seed)
    val isAmber = seed == Palettes[0].seed || (h in 25f..48f)

    return if (dark) {
        val tertiaryColor = if (isAmber) Color(0xFFD35F5F) else hslToColor((h + 60f) % 360f, 0.60f, 0.60f)
        val tertiaryContainerColor = if (isAmber) Color(0xFF5C1418) else hslToColor((h + 60f) % 360f, 0.65f, 0.22f)
        val onTertiaryColor = if (isAmber) Color(0xFF3F0008) else hslToColor((h + 60f) % 360f, 0.80f, 0.12f)
        val onTertiaryContainerColor = if (isAmber) Color(0xFFFFDAD6) else hslToColor((h + 60f) % 360f, 0.70f, 0.85f)

        val primaryColor = if (isAmber) seed else hslToColor(h, (s * 0.9f).coerceAtLeast(0.6f), 0.65f)
        val primaryContainerColor = if (isAmber) Color(0xFF422300) else hslToColor(h, 0.7f, 0.22f)
        val onPrimaryColor = if (isAmber) Color(0xFF1E1000) else hslToColor(h, 0.8f, 0.12f)
        val onPrimaryContainerColor = if (isAmber) Color(0xFFFFDCB8) else hslToColor(h, 0.8f, 0.85f)

        val secHue = (h + 15f) % 360f
        val secondaryColor = if (isAmber) Color(0xFFFFB870) else hslToColor(secHue, (s * 0.5f).coerceAtLeast(0.4f), 0.65f)
        val secondaryContainerColor = if (isAmber) Color(0xFF3B2810) else hslToColor(secHue, 0.5f, 0.22f)
        val onSecondaryColor = if (isAmber) Color(0xFF281800) else hslToColor(secHue, 0.7f, 0.12f)
        val onSecondaryContainerColor = if (isAmber) Color(0xFFFFDCB8) else hslToColor(secHue, 0.7f, 0.85f)

        val bgColor = if (black) Color(0xFF000000) else Color(0xFF121214)
        val surfaceColor = if (black) Color(0xFF000000) else Color(0xFF1A1A1E)
        val surfaceVariantColor = if (black) Color(0xFF1F1F1F) else Color(0xFF27272C)
        val onSurfaceColor = Color(0xFFE6E1E5)
        val onSurfaceVariantColor = Color(0xFFA5A5A9)
        val outlineColor = Color(0xFF79747E)
        val outlineVariantColor = Color(0xFF3B3B40)

        val lowest = if (black) Color(0xFF000000) else Color(0xFF0E0E10)
        val low = if (black) Color(0xFF0D0D0D) else Color(0xFF161619)
        val cont = if (black) Color(0xFF141414) else Color(0xFF1F1F24)
        val high = if (black) Color(0xFF1C1C1C) else Color(0xFF28282E)
        val highest = if (black) Color(0xFF242424) else Color(0xFF33333A)

        darkColorScheme(
            primary = primaryColor,
            onPrimary = onPrimaryColor,
            primaryContainer = primaryContainerColor,
            onPrimaryContainer = onPrimaryContainerColor,
            secondary = secondaryColor,
            onSecondary = onSecondaryColor,
            secondaryContainer = secondaryContainerColor,
            onSecondaryContainer = onSecondaryContainerColor,
            tertiary = tertiaryColor,
            onTertiary = onTertiaryColor,
            tertiaryContainer = tertiaryContainerColor,
            onTertiaryContainer = onTertiaryContainerColor,
            background = bgColor,
            onBackground = Color(0xFFE0E0E0),
            surface = surfaceColor,
            onSurface = onSurfaceColor,
            surfaceVariant = surfaceVariantColor,
            onSurfaceVariant = onSurfaceVariantColor,
            surfaceContainerLowest = lowest,
            surfaceContainerLow = low,
            surfaceContainer = cont,
            surfaceContainerHigh = high,
            surfaceContainerHighest = highest,
            outline = outlineColor,
            outlineVariant = outlineVariantColor,
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        val tertiaryHue = if (isAmber) 0f else (h + 60f) % 360f
        val tertiaryColor = if (isAmber) Color(0xFFB3261E) else hslToColor(tertiaryHue, 0.60f, 0.40f)
        val tertiaryContainerColor = if (isAmber) Color(0xFFFFDAD6) else hslToColor(tertiaryHue, 0.50f, 0.90f)
        val onTertiaryColor = Color.White
        val onTertiaryContainerColor = if (isAmber) Color(0xFF410002) else hslToColor(tertiaryHue, 0.70f, 0.15f)

        val primaryColor = if (isAmber) hslToColor(h, 0.95f, 0.38f) else hslToColor(h, (s * 0.9f).coerceAtLeast(0.6f), 0.38f)
        val primaryContainerColor = if (isAmber) Color(0xFFFFE0B2) else hslToColor(h, 0.6f, 0.90f)
        val onPrimaryColor = Color.White
        val onPrimaryContainerColor = if (isAmber) Color(0xFF2E1500) else hslToColor(h, 0.8f, 0.15f)

        val secHue = (h + 15f) % 360f
        val secondaryColor = if (isAmber) Color(0xFF795548) else hslToColor(secHue, 0.4f, 0.40f)
        val secondaryContainerColor = if (isAmber) Color(0xFFD7CCC8) else hslToColor(secHue, 0.4f, 0.90f)
        val onSecondaryColor = Color.White
        val onSecondaryContainerColor = if (isAmber) Color(0xFF271B16) else hslToColor(secHue, 0.6f, 0.15f)

        val bgColor = if (isAmber) Color(0xFFFAF8F5) else hslToColor(h, 0.08f, 0.98f)
        val surfaceColor = if (isAmber) Color(0xFFFAF8F5) else hslToColor(h, 0.08f, 0.98f)
        val surfaceVariantColor = if (isAmber) Color(0xFFEBE6DD) else hslToColor(h, 0.10f, 0.90f)
        val onSurfaceColor = Color(0xFF1D1B16)
        val onSurfaceVariantColor = Color(0xFF4E4639)
        val outlineColor = Color(0xFF7F7667)
        val outlineVariantColor = Color(0xFFD1C5B4)

        val lowest = Color(0xFFFFFFFF)
        val low = if (isAmber) Color(0xFFF4F0E8) else hslToColor(h, 0.08f, 0.96f)
        val cont = if (isAmber) Color(0xFFEEEAE1) else hslToColor(h, 0.08f, 0.93f)
        val high = if (isAmber) Color(0xFFE8E4DA) else hslToColor(h, 0.08f, 0.90f)
        val highest = if (isAmber) Color(0xFFE2DDD3) else hslToColor(h, 0.08f, 0.87f)

        lightColorScheme(
            primary = primaryColor,
            onPrimary = onPrimaryColor,
            primaryContainer = primaryContainerColor,
            onPrimaryContainer = onPrimaryContainerColor,
            secondary = secondaryColor,
            onSecondary = onSecondaryColor,
            secondaryContainer = secondaryContainerColor,
            onSecondaryContainer = onSecondaryContainerColor,
            tertiary = tertiaryColor,
            onTertiary = onTertiaryColor,
            tertiaryContainer = tertiaryContainerColor,
            onTertiaryContainer = onTertiaryContainerColor,
            background = bgColor,
            onBackground = Color(0xFF1D1B16),
            surface = surfaceColor,
            onSurface = onSurfaceColor,
            surfaceVariant = surfaceVariantColor,
            onSurfaceVariant = onSurfaceVariantColor,
            surfaceContainerLowest = lowest,
            surfaceContainerLow = low,
            surfaceContainer = cont,
            surfaceContainerHigh = high,
            surfaceContainerHighest = highest,
            outline = outlineColor,
            outlineVariant = outlineVariantColor,
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        )
    }
}

@Composable
fun isDarkTheme(settings: Settings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.BLACK -> true
}

@Composable
fun TrendingTheme(
    settings: Settings,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(settings)
    val black = settings.themeMode == ThemeMode.BLACK
    val context = LocalContext.current

    val rawScheme = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamicScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (black) {
            dynamicScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF0D0D0D),
                surfaceContainer = Color(0xFF141414),
                surfaceContainerHigh = Color(0xFF1C1C1C),
                surfaceContainerHighest = Color(0xFF242424),
            )
        } else {
            dynamicScheme
        }
    } else {
        val palette = Palettes[settings.palette.coerceIn(Palettes.indices)]
        colorSchemeFor(seed = palette.seed, dark = dark, black = black)
    }

    val animatedScheme = animateColorScheme(rawScheme)

    MaterialTheme(
        colorScheme = animatedScheme,
        typography = TrendingTypography,
        content = content,
    )
}

@Composable
private fun animateColorScheme(scheme: ColorScheme): ColorScheme {
    val animSpec = tween<Color>(durationMillis = 250)
    val primary by animateColorAsState(scheme.primary, animSpec, label = "primary")
    val onPrimary by animateColorAsState(scheme.onPrimary, animSpec, label = "onPrimary")
    val primaryContainer by animateColorAsState(scheme.primaryContainer, animSpec, label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(scheme.onPrimaryContainer, animSpec, label = "onPrimaryContainer")
    val secondary by animateColorAsState(scheme.secondary, animSpec, label = "secondary")
    val onSecondary by animateColorAsState(scheme.onSecondary, animSpec, label = "onSecondary")
    val secondaryContainer by animateColorAsState(scheme.secondaryContainer, animSpec, label = "secondaryContainer")
    val onSecondaryContainer by animateColorAsState(scheme.onSecondaryContainer, animSpec, label = "onSecondaryContainer")
    val tertiary by animateColorAsState(scheme.tertiary, animSpec, label = "tertiary")
    val onTertiary by animateColorAsState(scheme.onTertiary, animSpec, label = "onTertiary")
    val tertiaryContainer by animateColorAsState(scheme.tertiaryContainer, animSpec, label = "tertiaryContainer")
    val onTertiaryContainer by animateColorAsState(scheme.onTertiaryContainer, animSpec, label = "onTertiaryContainer")
    val background by animateColorAsState(scheme.background, animSpec, label = "background")
    val onBackground by animateColorAsState(scheme.onBackground, animSpec, label = "onBackground")
    val surface by animateColorAsState(scheme.surface, animSpec, label = "surface")
    val onSurface by animateColorAsState(scheme.onSurface, animSpec, label = "onSurface")
    val surfaceVariant by animateColorAsState(scheme.surfaceVariant, animSpec, label = "surfaceVariant")
    val onSurfaceVariant by animateColorAsState(scheme.onSurfaceVariant, animSpec, label = "onSurfaceVariant")
    val surfaceContainerLowest by animateColorAsState(scheme.surfaceContainerLowest, animSpec, label = "surfaceContainerLowest")
    val surfaceContainerLow by animateColorAsState(scheme.surfaceContainerLow, animSpec, label = "surfaceContainerLow")
    val surfaceContainer by animateColorAsState(scheme.surfaceContainer, animSpec, label = "surfaceContainer")
    val surfaceContainerHigh by animateColorAsState(scheme.surfaceContainerHigh, animSpec, label = "surfaceContainerHigh")
    val surfaceContainerHighest by animateColorAsState(scheme.surfaceContainerHighest, animSpec, label = "surfaceContainerHighest")
    val outline by animateColorAsState(scheme.outline, animSpec, label = "outline")
    val outlineVariant by animateColorAsState(scheme.outlineVariant, animSpec, label = "outlineVariant")

    return scheme.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        outline = outline,
        outlineVariant = outlineVariant,
    )
}

private fun colorToHsl(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) {
        return floatArrayOf(0f, 0f, l)
    }
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> (g - b) / d + (if (g < b) 6f else 0f)
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    return floatArrayOf(h, s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float, a: Float = 1f): Color {
    val normH = ((h % 360f) + 360f) % 360f
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((normH / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        normH < 60f -> Triple(c, x, 0f)
        normH < 120f -> Triple(x, c, 0f)
        normH < 180f -> Triple(0f, c, x)
        normH < 240f -> Triple(0f, x, c)
        normH < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = a,
    )
}
