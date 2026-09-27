// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * fork: settings UI follows the shadcn/ui design system (zinc base color, "new-york" style):
 * neutral colors, 1px borders, 8dp radius, grouped cards, small muted labels.
 * Token values are the ones of shadcn/ui's zinc theme, except that cards are a step lighter than the page
 * (and the page a step darker in light mode), so the grouped boxes stand out.
 */
class ShadcnColors(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val muted: Color,
    val mutedForeground: Color,
    val border: Color,
    val input: Color,
    val primary: Color,
    val primaryForeground: Color,
    val accent: Color,
    val destructive: Color,
    val warning: Color,
    val warningBackground: Color,
    val ring: Color,
)

val ShadcnLight = ShadcnColors(
    background = Color(0xFFF4F4F5),
    foreground = Color(0xFF09090B),
    card = Color(0xFFFFFFFF),
    muted = Color(0xFFF4F4F5),
    mutedForeground = Color(0xFF71717A),
    border = Color(0xFFE4E4E7),
    input = Color(0xFFE4E4E7),
    primary = Color(0xFF18181B),
    primaryForeground = Color(0xFFFAFAFA),
    accent = Color(0xFFF4F4F5),
    destructive = Color(0xFFEF4444),
    warning = Color(0xFFB45309),
    warningBackground = Color(0xFFFFFBEB),
    ring = Color(0xFFA1A1AA),
)

val ShadcnDark = ShadcnColors(
    background = Color(0xFF09090B),
    foreground = Color(0xFFFAFAFA),
    card = Color(0xFF1C1C20),
    muted = Color(0xFF2E2E33),
    mutedForeground = Color(0xFFA1A1AA),
    border = Color(0xFF34343A),
    input = Color(0xFF27272A),
    primary = Color(0xFFFAFAFA),
    primaryForeground = Color(0xFF18181B),
    accent = Color(0xFF27272A),
    destructive = Color(0xFFDC2626),
    warning = Color(0xFFFBBF24),
    warningBackground = Color(0xFF1C1407),
    ring = Color(0xFF52525B),
)

val LocalShadcn = staticCompositionLocalOf { ShadcnLight }

/** shadcn radius: --radius 0.5rem (8dp), sm 6dp, md 8dp, lg 10dp, xl 12dp */
val ShadcnShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val s = if (dark) ShadcnDark else ShadcnLight
    val scheme = if (dark) darkColorScheme(
        primary = s.primary, onPrimary = s.primaryForeground,
        primaryContainer = s.muted, onPrimaryContainer = s.foreground,
        secondary = s.mutedForeground, onSecondary = s.background,
        secondaryContainer = s.accent, onSecondaryContainer = s.foreground,
        tertiary = s.mutedForeground, onTertiary = s.background,
        background = s.background, onBackground = s.foreground,
        surface = s.background, onSurface = s.foreground,
        surfaceVariant = s.muted, onSurfaceVariant = s.mutedForeground,
        surfaceContainerLowest = s.background, surfaceContainerLow = s.background,
        surfaceContainer = s.background, surfaceContainerHigh = s.card, surfaceContainerHighest = s.input,
        surfaceTint = Color.Transparent,
        outline = s.ring, outlineVariant = s.border,
        error = s.destructive, onError = s.primaryForeground,
    ) else lightColorScheme(
        primary = s.primary, onPrimary = s.primaryForeground,
        primaryContainer = s.muted, onPrimaryContainer = s.foreground,
        secondary = s.mutedForeground, onSecondary = s.background,
        secondaryContainer = s.accent, onSecondaryContainer = s.foreground,
        tertiary = s.mutedForeground, onTertiary = s.background,
        background = s.background, onBackground = s.foreground,
        surface = s.background, onSurface = s.foreground,
        surfaceVariant = s.muted, onSurfaceVariant = s.mutedForeground,
        surfaceContainerLowest = s.background, surfaceContainerLow = s.background,
        surfaceContainer = s.background, surfaceContainerHigh = s.card, surfaceContainerHighest = s.input,
        surfaceTint = Color.Transparent,
        outline = s.ring, outlineVariant = s.border,
        error = s.destructive, onError = s.primaryForeground,
    )
    val base = Typography()
    fun TextStyle.tight(size: Int, weight: FontWeight, line: Int) =
        copy(fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = (-0.1).sp)
    val typography = Typography(
        headlineSmall = base.headlineSmall.tight(22, FontWeight.SemiBold, 28),
        titleLarge = base.titleLarge.tight(18, FontWeight.SemiBold, 24),
        titleMedium = base.titleMedium.tight(15, FontWeight.SemiBold, 20),
        titleSmall = base.titleSmall.tight(13, FontWeight.SemiBold, 18),
        bodyLarge = base.bodyLarge.tight(15, FontWeight.Medium, 20),
        bodyMedium = base.bodyMedium.tight(13, FontWeight.Normal, 18),
        bodySmall = base.bodySmall.tight(12, FontWeight.Normal, 16),
        labelLarge = base.labelLarge.tight(14, FontWeight.Medium, 20),
        labelMedium = base.labelMedium.tight(12, FontWeight.Medium, 16),
        labelSmall = base.labelSmall.tight(11, FontWeight.Medium, 14),
    )
    CompositionLocalProvider(LocalShadcn provides s) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = ShadcnShapes,
            content = content
        )
    }
}

const val previewDark = true
