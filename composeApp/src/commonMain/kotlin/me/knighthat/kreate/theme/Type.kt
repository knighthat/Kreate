package me.knighthat.kreate.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.comfortaa_bold
import kreate.composeapp.generated.resources.comfortaa_light
import kreate.composeapp.generated.resources.comfortaa_medium
import kreate.composeapp.generated.resources.comfortaa_regular
import kreate.composeapp.generated.resources.comfortaa_semiBold
import kreate.composeapp.generated.resources.lato_black
import kreate.composeapp.generated.resources.lato_black_italic
import kreate.composeapp.generated.resources.lato_bold
import kreate.composeapp.generated.resources.lato_bold_italic
import kreate.composeapp.generated.resources.lato_italic
import kreate.composeapp.generated.resources.lato_light
import kreate.composeapp.generated.resources.lato_light_italic
import kreate.composeapp.generated.resources.lato_regular
import kreate.composeapp.generated.resources.lato_thin
import kreate.composeapp.generated.resources.lato_thin_italic
import org.jetbrains.compose.resources.Font


// Default Material 3 typography values
private val baseline = Typography()

@Composable
fun AppTopography(): Typography {
    val bodyFontFamily = FontFamily(
        Font(
            resource = Res.font.comfortaa_light,
            weight = FontWeight.Light
        ),
        Font(
            resource = Res.font.comfortaa_regular,
            weight = FontWeight.Normal
        ),
        Font(
            resource = Res.font.comfortaa_semiBold,
            weight = FontWeight.SemiBold
        ),
        Font(
            resource = Res.font.comfortaa_medium,
            weight = FontWeight.Medium
        ),
        Font(
            resource = Res.font.comfortaa_bold,
            weight = FontWeight.Bold
        )
    )
    val displayFontFamily = FontFamily(
        Font(
            resource = Res.font.lato_thin,
            weight = FontWeight.Thin
        ),
        Font(
            resource = Res.font.lato_thin_italic,
            weight = FontWeight.Thin,
            style = FontStyle.Italic
        ),
        Font(
            resource = Res.font.lato_light,
            weight = FontWeight.Light
        ),
        Font(
            resource = Res.font.lato_light_italic,
            weight = FontWeight.Light,
            style = FontStyle.Italic
        ),
        Font(
            resource = Res.font.lato_regular,
            weight = FontWeight.Normal
        ),
        Font(
            resource = Res.font.lato_italic,
            weight = FontWeight.Normal,
            style = FontStyle.Italic
        ),
        Font(
            resource = Res.font.lato_bold,
            weight = FontWeight.Bold
        ),
        Font(
            resource = Res.font.lato_bold_italic,
            weight = FontWeight.Bold,
            style = FontStyle.Italic
        ),
        Font(
            resource = Res.font.lato_black,
            weight = FontWeight.Black
        ),
        Font(
            resource = Res.font.lato_black_italic,
            weight = FontWeight.Black,
            style = FontStyle.Italic
        )
    )

    return Typography(
        displayLarge = baseline.displayLarge.copy(fontFamily = displayFontFamily),
        displayMedium = baseline.displayMedium.copy(fontFamily = displayFontFamily),
        displaySmall = baseline.displaySmall.copy(fontFamily = displayFontFamily),
        headlineLarge = baseline.headlineLarge.copy(fontFamily = displayFontFamily),
        headlineMedium = baseline.headlineMedium.copy(fontFamily = displayFontFamily),
        headlineSmall = baseline.headlineSmall.copy(fontFamily = displayFontFamily),
        titleLarge = baseline.titleLarge.copy(fontFamily = displayFontFamily),
        titleMedium = baseline.titleMedium.copy(fontFamily = displayFontFamily),
        titleSmall = baseline.titleSmall.copy(fontFamily = displayFontFamily),
        bodyLarge = baseline.bodyLarge.copy(fontFamily = bodyFontFamily),
        bodyMedium = baseline.bodyMedium.copy(fontFamily = bodyFontFamily),
        bodySmall = baseline.bodySmall.copy(fontFamily = bodyFontFamily),
        labelLarge = baseline.labelLarge.copy(fontFamily = bodyFontFamily),
        labelMedium = baseline.labelMedium.copy(fontFamily = bodyFontFamily),
        labelSmall = baseline.labelSmall.copy(fontFamily = bodyFontFamily),
    )
}
