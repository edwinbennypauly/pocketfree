package app.pocketfree.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.pocketfree.domain.AccentTheme

// Meaning colours stay the same in every theme: green = income, red = expense, amber = warning.
val IncomeGreen = Color(0xFF16A34A)
val ExpenseRed = Color(0xFFDC2626)
val WarningAmber = Color(0xFFF59E0B)

/** One accent: strong primary + tinted container + tinted backgrounds, for light and dark. */
class AccentPalette(
    val primaryL: Color, val containerL: Color, val onContainerL: Color, val bgL: Color, val variantL: Color,
    val primaryD: Color, val containerD: Color, val onContainerD: Color, val bgD: Color, val surfaceD: Color, val variantD: Color
)

object Palettes {
    private fun c(argb: Long) = Color(argb)

    private val teal = AccentPalette(
        c(0xFF0F766E), c(0xFFCCFBF1), c(0xFF042F2E), c(0xFFF2FAF9), c(0xFFDCEBE9),
        c(0xFF5EEAD4), c(0xFF115E59), c(0xFFCCFBF1), c(0xFF08151A), c(0xFF0F1F24), c(0xFF1A2F34)
    )
    private val blue = AccentPalette(
        c(0xFF1D4ED8), c(0xFFDBEAFE), c(0xFF172554), c(0xFFF3F7FF), c(0xFFDFE7F7),
        c(0xFF93C5FD), c(0xFF1E3A8A), c(0xFFDBEAFE), c(0xFF0A1226), c(0xFF111B33), c(0xFF1C2744)
    )
    private val purple = AccentPalette(
        c(0xFF7C3AED), c(0xFFEDE9FE), c(0xFF2E1065), c(0xFFF8F5FF), c(0xFFE8E2F6),
        c(0xFFC4B5FD), c(0xFF5B21B6), c(0xFFEDE9FE), c(0xFF120B22), c(0xFF1A1230), c(0xFF2A2145)
    )
    private val green = AccentPalette(
        c(0xFF15803D), c(0xFFDCFCE7), c(0xFF052E16), c(0xFFF3FBF6), c(0xFFDFEDE4),
        c(0xFF86EFAC), c(0xFF166534), c(0xFFDCFCE7), c(0xFF08140D), c(0xFF0F1D14), c(0xFF1B2D21)
    )
    private val orange = AccentPalette(
        c(0xFFC2410C), c(0xFFFFEDD5), c(0xFF431407), c(0xFFFFF8F2), c(0xFFF3E4D8),
        c(0xFFFDBA74), c(0xFF9A3412), c(0xFFFFEDD5), c(0xFF1A0F08), c(0xFF241810), c(0xFF38261A)
    )
    private val rose = AccentPalette(
        c(0xFFBE123C), c(0xFFFFE4E6), c(0xFF4C0519), c(0xFFFFF6F7), c(0xFFF4E1E4),
        c(0xFFFDA4AF), c(0xFF9F1239), c(0xFFFFE4E6), c(0xFF1A0A0E), c(0xFF251217), c(0xFF3A1D25)
    )

    fun of(accent: AccentTheme): AccentPalette = when (accent) {
        AccentTheme.TEAL, AccentTheme.DYNAMIC -> teal
        AccentTheme.BLUE -> blue
        AccentTheme.PURPLE -> purple
        AccentTheme.GREEN -> green
        AccentTheme.ORANGE -> orange
        AccentTheme.ROSE -> rose
    }

    fun light(p: AccentPalette): ColorScheme = lightColorScheme(
        primary = p.primaryL,
        onPrimary = Color.White,
        primaryContainer = p.containerL,
        onPrimaryContainer = p.onContainerL,
        secondary = p.primaryL,
        onSecondary = Color.White,
        secondaryContainer = p.containerL,
        onSecondaryContainer = p.onContainerL,
        tertiary = p.primaryL,
        onTertiary = Color.White,
        background = p.bgL,
        onBackground = Color(0xFF0F172A),
        surface = Color.White,
        onSurface = Color(0xFF0F172A),
        surfaceVariant = p.variantL,
        onSurfaceVariant = Color(0xFF475569),
        surfaceTint = p.primaryL,
        outline = Color(0xFFC7D0DC),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = p.bgL,
        surfaceContainer = lerp(p.bgL, p.containerL, 0.5f),
        surfaceContainerHigh = lerp(p.bgL, p.containerL, 0.8f),
        surfaceContainerHighest = p.variantL,
        error = Color(0xFFDC2626)
    )

    fun dark(p: AccentPalette): ColorScheme = darkColorScheme(
        primary = p.primaryD,
        onPrimary = p.bgD,
        primaryContainer = p.containerD,
        onPrimaryContainer = p.onContainerD,
        secondary = p.primaryD,
        onSecondary = p.bgD,
        secondaryContainer = p.containerD,
        onSecondaryContainer = p.onContainerD,
        tertiary = p.primaryD,
        onTertiary = p.bgD,
        background = p.bgD,
        onBackground = Color(0xFFE5E7EB),
        surface = p.surfaceD,
        onSurface = Color(0xFFE5E7EB),
        surfaceVariant = p.variantD,
        onSurfaceVariant = Color(0xFFA1A9B6),
        surfaceTint = p.primaryD,
        outline = Color(0xFF3B4756),
        surfaceContainerLowest = p.bgD,
        surfaceContainerLow = p.surfaceD,
        surfaceContainer = lerp(p.surfaceD, p.variantD, 0.5f),
        surfaceContainerHigh = lerp(p.surfaceD, p.variantD, 0.8f),
        surfaceContainerHighest = p.variantD,
        error = Color(0xFFF87171)
    )
}
