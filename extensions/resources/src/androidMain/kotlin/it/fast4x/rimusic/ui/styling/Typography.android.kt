package it.fast4x.rimusic.ui.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.kreate.resources.R
import it.fast4x.rimusic.enums.FontType

@Immutable
actual data class Typography actual constructor(
    val xxxs: TextStyle,
    val xxs: TextStyle,
    val xs: TextStyle,
    val s: TextStyle,
    val m: TextStyle,
    val l: TextStyle,
    val xl: TextStyle,
    val xxl: TextStyle,
    val xxxl: TextStyle,
    val xlxl: TextStyle,
) {
    fun copy(color: Color) = Typography(
        xxxs = xxs.copy(color = color),
        xxs = xxs.copy(color = color),
        xs = xs.copy(color = color),
        s = s.copy(color = color),
        m = m.copy(color = color),
        l = l.copy(color = color),
        xl = xl.copy(color = color),
        xxl = xxl.copy(color = color),
        xxxl = xxxl.copy(color = color),
        xlxl = xlxl.copy(color = color),
    )

    companion object : Saver<Typography, List<Any>> {
        override fun restore(value: List<Any>) = typographyOf(
            Color((value[0] as Long).toULong()),
            value[1] as Boolean,
            value[2] as Boolean,
            value[3] as FontType
        )

        override fun SaverScope.save(value: Typography) =
            listOf(
                value.xxs.color.value.toLong(),
                value.xxs.fontFamily == FontFamily.Default,
                value.xxs.platformStyle?.paragraphStyle?.includeFontPadding ?: false,
                FontType.Rubik
            )
    }
}

fun typographyOf(color: Color, useSystemFont: Boolean, applyFontPadding: Boolean, fontType: FontType): Typography {
    val textStyle = TextStyle(
        fontFamily = if (useSystemFont) {
            FontFamily.Default
        } else {
                FontFamily(
                    Font(
                        resId = when (fontType) {
                            FontType.Rubik -> R.font.rubik_w300
                            FontType.Poppins -> R.font.poppins_w300
                        },
                        weight = FontWeight.Light
                    ),
                    Font(
                        resId = when (fontType) {
                            FontType.Rubik -> R.font.rubik_w400
                            FontType.Poppins -> R.font.poppins_w400
                        },
                        weight = FontWeight.Normal
                    ),
                    Font(
                        resId = when (fontType) {
                            FontType.Rubik -> R.font.rubik_w500
                            FontType.Poppins -> R.font.poppins_w500
                        },
                        weight = FontWeight.Medium
                    ),
                    Font(
                        resId = when (fontType) {
                            FontType.Rubik -> R.font.rubik_w600
                            FontType.Poppins -> R.font.poppins_w600
                        },
                        weight = FontWeight.SemiBold
                    ),
                    Font(
                        resId = when (fontType) {
                            FontType.Rubik -> R.font.rubik_w700
                            FontType.Poppins -> R.font.poppins_w700
                        },
                        weight = FontWeight.Bold
                    ),
                )

        },
        fontWeight = FontWeight.Normal,
        color = color,
        platformStyle = PlatformTextStyle(includeFontPadding = applyFontPadding)
    )

    return Typography(
        xxxs = textStyle.copy(fontSize = 10.sp),
        xxs = textStyle.copy(fontSize = 12.sp),
        xs = textStyle.copy(fontSize = 14.sp),
        s = textStyle.copy(fontSize = 16.sp),
        m = textStyle.copy(fontSize = 18.sp),
        l = textStyle.copy(fontSize = 20.sp),
        xl = textStyle.copy(fontSize = 24.sp),
        xxl = textStyle.copy(fontSize = 28.sp),
        xxxl = textStyle.copy(fontSize = 36.sp),
        xlxl = textStyle.copy(fontSize = 34.sp)
    )
}
