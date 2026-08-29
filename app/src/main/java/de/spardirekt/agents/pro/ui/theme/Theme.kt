package de.spardirekt.agents.pro.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object VppColors {
    val backgroundLight = Color(0xFFF7F5FB)
    val backgroundGlow = Color(0xFFEAE4F8)
    val cardNavy = Color(0xFF141B3A)
    val cardNavySecondary = Color(0xFF1B2448)
    val cardInset = Color(0xFF0E1430)
    val accentPurple = Color(0xFF7C5CFF)
    val accentBlue = Color(0xFF3D8BFF)
    val textDark = Color(0xFF1A1F36)
    val textLight = Color(0xFFF4F6FF)
    val textMuted = Color(0xFF9AA3C7)
    val textMutedDark = Color(0xFF6B728E)
    val success = Color(0xFF3DDC97)
    val error = Color(0xFFC45B6A)
    val errorCard = Color(0xFF2A1520)
    val chipSelected = Color(0xFFE8E0FF)
    val outlineSoft = Color(0xFFD7D2E8)
    val bottomBar = Color(0xFF0F1738)
    val navPill = Color(0xFFE6DEFF)
}

object VppShapes {
    val cardShape = RoundedCornerShape(26.dp)
    val buttonShape = RoundedCornerShape(20.dp)
    val chipShape = RoundedCornerShape(50)
    val thumbShape = RoundedCornerShape(16.dp)
    val insetShape = RoundedCornerShape(18.dp)
    val navPill = RoundedCornerShape(18.dp)
}

object VppDimens {
    val screenPadding: Dp = 22.dp
    val cardPadding: Dp = 22.dp
    val sectionGap: Dp = 16.dp
}

val GradientAccent = Brush.horizontalGradient(
    listOf(VppColors.accentPurple, VppColors.accentBlue)
)

val GradientTitle = Brush.horizontalGradient(
    listOf(Color(0xFF6B4EFF), Color(0xFF3D8BFF), Color(0xFF5B8CFF))
)

data class VppTypographyTokens(
    val screenTitle: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val body: TextStyle,
    val secondary: TextStyle,
    val buttonLabel: TextStyle,
    val badge: TextStyle
)

val VppType = VppTypographyTokens(
    screenTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp
    ),
    sectionTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    cardTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    secondary = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    buttonLabel = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 0.2.sp
    ),
    badge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 0.3.sp
    )
)

val LocalVppType = staticCompositionLocalOf { VppType }

val MaterialTypography = Typography(
    displayLarge = VppType.screenTitle,
    titleLarge = VppType.sectionTitle,
    titleMedium = VppType.cardTitle,
    bodyMedium = VppType.body,
    bodySmall = VppType.secondary,
    labelLarge = VppType.buttonLabel,
    labelSmall = VppType.badge
)

@Composable
fun VeoPromptProTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalVppType provides VppType) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = androidx.compose.material3.lightColorScheme(
                primary = VppColors.accentPurple,
                secondary = VppColors.accentBlue,
                background = VppColors.backgroundLight,
                surface = VppColors.backgroundLight,
                onPrimary = Color.White,
                onBackground = VppColors.textDark,
                onSurface = VppColors.textDark,
                error = VppColors.error
            ),
            typography = MaterialTypography,
            content = content
        )
    }
}
