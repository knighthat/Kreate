/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.ui.screen.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.font.FontSlot
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.navigation.RouteResults
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import me.knighthat.kreate3.ui.theme.fontFamilyOf
import org.koin.compose.koinInject
import java.io.File

/** Height of the tab bar, as in the full player. */
private val TAB_BAR_HEIGHT = 56.dp

/**
 * The font the samples are drawn in. `null` is the app's own, the theme's text styles carry it.
 */
private val LocalPreviewFont = compositionLocalOf<FontFamily?> { null }

/**
 * A group of scripts, named by one glyph of it. The texts are examples, so they are never
 * translated.
 */
private enum class FontTab(val glyph: String) {
    LATIN("Aa"),
    CJK("漢"),
    RTL("ع"),
    THAI("ก"),
    INDIC("अ"),
}

/**
 * A font the user picked for [slot] and is looking at before keeping it.
 *
 * @param file where it waits, see [me.knighthat.kreate3.font.FontFiles.stage]
 * @param name how it is shown, the name of the file it came from
 */
data class StagedFont(val slot: FontSlot, val file: File, val name: String)

/**
 * Shows how a font looks in each group of scripts, a page each, to judge it before keeping it. It
 * is opened for an answer (see [me.knighthat.kreate3.ui.navigation.rememberRouteResult]): it starts
 * from the [StagedFont] the opener passed under [key] and answers it on Save. Going back answers
 * nothing, the font is dropped.
 */
@Composable
fun FontPreviewScreen(key: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val results = koinInject<RouteResults>()
    val staged = remember(key) { results.input<StagedFont>(key) }
    if (staged == null) {
        // Only in memory, the process was gone since the page was opened
        LaunchedEffect(Unit) { onBack() }
        return
    }

    FontPreviewContent(
        fontFamily = remember(staged) { fontFamilyOf(staged.file) },
        onSave = {
            results.answer(key, staged)
            onBack()
        },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * @param fontFamily the font to preview, `null` for the app's own
 */
@Composable
private fun FontPreviewContent(
    fontFamily: FontFamily?,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { FontTab.entries.size }
    val scope = rememberCoroutineScope()

    DetailScaffold(
        title = stringResource(R.string.screen_title_font_preview),
        onBack = onBack,
        modifier = modifier,
        actions = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) }
        },
        bottomBar = {
            TabBar(
                selected = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            )
        },
    ) { padding ->
        CompositionLocalProvider(LocalPreviewFont provides fontFamily) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val tab = FontTab.entries[page]
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(padding)
                        .padding(16.dp),
                ) {
                    when (tab) {
                        FontTab.LATIN -> LatinPage()
                        FontTab.CJK -> CjkPage()
                        FontTab.RTL -> RtlPage()
                        FontTab.THAI -> ThaiPage()
                        FontTab.INDIC -> IndicPage()
                    }
                }
            }
        }
    }
}

/**
 * The tabs of the full player, along the bottom: a track across the top with the selected tab's
 * part of it highlighted. Each tab is named by a glyph of its scripts.
 */
@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) =
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        SecondaryTabRow(
            selectedTabIndex = selected,
            containerColor = Color.Transparent,
            indicator = {
                // The row places them at its bottom, they fill the height to draw at the top
                val atTop = Modifier
                    .fillMaxHeight()
                    .wrapContentHeight(Alignment.Top)
                // A track across every tab, given one tab's width it overflows to the end
                TabRowDefaults.SecondaryIndicator(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier
                        .tabIndicatorLayout { measurable, constraints, tabPositions ->
                            val width = tabPositions.lastOrNull()?.right?.roundToPx() ?: 0
                            val placeable = measurable.measure(
                                constraints.copy(minWidth = width, maxWidth = width)
                            )
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.place(0, 0)
                            }
                        }
                        .then(atTop),
                )
                // The selected tab's part of it
                TabRowDefaults.SecondaryIndicator(
                    Modifier
                        .tabIndicatorOffset(selected, matchContentSize = false)
                        .then(atTop)
                )
            },
            divider = {},
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(TAB_BAR_HEIGHT),
        ) {
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                FontTab.entries.forEach { tab ->
                    Tab(
                        selected = selected == tab.ordinal,
                        onClick = { onSelect(tab.ordinal) },
                        text = { Text(tab.glyph, style = MaterialTheme.typography.titleLarge) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

//<editor-fold defaultstate="collapsed" desc="Pages">

@Composable
private fun LatinPage() {
    SampleText(PANGRAM)
    SampleText(PANGRAM, italic = true)
    SampleText(PANGRAM, weight = FontWeight.Bold)
    SampleText(PANGRAM, weight = FontWeight.Bold, italic = true)
    SampleText(DIGITS, style = MaterialTheme.typography.titleLarge)
    // Letters with marks, a group per language family
    LATIN_ACCENTS.forEach { SampleText(it, style = MaterialTheme.typography.titleMedium) }
    SampleText(LOREM_IPSUM)
}

@Composable
private fun CjkPage() {
    // Han characters are shared, each language is tagged so its own forms are picked
    ScriptRow("简", "敏捷的棕色狐狸跳过了懒狗。", locale = "zh-Hans")
    ScriptRow("繁", "敏捷的棕色狐狸跳過了懶狗。", locale = "zh-Hant")
    ScriptRow("日", "素早い茶色の狐はのろまな犬を飛び越える。", locale = "ja")
    ScriptRow("あ", "あいうえお かきくけこ アイウエオ カキクケコ", locale = "ja")
    ScriptRow("한", "빠른 갈색 여우가 게으른 개를 뛰어넘는다.", locale = "ko")
    ScriptRow("가", "가 나 다 라 마 바 사 아 자 차 카 타 파 하", locale = "ko")
}

@Composable
private fun RtlPage() {
    ScriptRow("ع", "الثعلب البني السريع يقفز فوق الكلب الكسول", locale = "ar")
    // One letter in its four forms: alone, at the start, in the middle and at the end
    ScriptRow("ب", "ب بـ ـبـ ـب", locale = "ar")
    ScriptRow("٣", "٠ ١ ٢ ٣ ٤ ٥ ٦ ٧ ٨ ٩", locale = "ar")
    ScriptRow("א", "השועל החום המהיר קופץ מעל הכלב העצלן", locale = "he")
}

@Composable
private fun ThaiPage() {
    ScriptRow("ก", "เป็นมนุษย์สุดประเสริฐเลิศคุณค่า กว่าบรรดาฝูงสัตว์เดรัจฉาน", locale = "th")
    // The 44 consonants, then vowels and tone marks stacked on a consonant
    ScriptRow("ข", "กขฃคฅฆงจฉชซฌญฎฏฐฑฒณดตถทธนบปผฝพฟภมยรลวศษสหฬอฮ", locale = "th")
    ScriptRow("ก่", "ก่ ก้ ก๊ ก๋ กิ กี กึ กื กุ กู เก แก โก ไก", locale = "th")
    ScriptRow("๓", "๐ ๑ ๒ ๓ ๔ ๕ ๖ ๗ ๘ ๙", locale = "th")
}

@Composable
private fun IndicPage() {
    ScriptRow("अ", "हिन्दी भाषा में संगीत सुनें", locale = "hi")
    // Letters joined into one shape
    ScriptRow("क्ष", "क्ष त्र ज्ञ श्र द्य", locale = "hi")
    ScriptRow("অ", "বাংলা ভাষায় গান শুনুন", locale = "bn")
    ScriptRow("அ", "தமிழ் மொழியில் இசை கேளுங்கள்", locale = "ta")
    ScriptRow("అ", "తెలుగు భాషలో సంగీతం వినండి", locale = "te")
    ScriptRow("ಅ", "ಕನ್ನಡ ಭಾಷೆಯಲ್ಲಿ ಸಂಗೀತ ಆಲಿಸಿ", locale = "kn")
    ScriptRow("അ", "മലയാളം ഭാഷയിൽ സംഗീതം കേൾക്കുക", locale = "ml")
    ScriptRow("અ", "ગુજરાતી ભાષામાં સંગીત સાંભળો", locale = "gu")
}

//</editor-fold>

/**
 * A sentence, in regular and then in bold, behind a glyph of its script.
 */
@Composable
private fun ScriptRow(tag: String, text: String, locale: String) =
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SampleText(
            text = tag,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            locale = locale,
            modifier = Modifier.width(40.dp),
            align = TextAlign.Center,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            // The full width, or right-to-left text has no room to start from the right
            SampleText(text, Modifier.fillMaxWidth(), locale = locale)
            SampleText(text, Modifier.fillMaxWidth(), weight = FontWeight.Bold, locale = locale)
        }
    }

/**
 * A [Text] in the font being previewed.
 *
 * @param locale the language tag the text is shaped for, picks the forms of shared characters
 */
@Composable
private fun SampleText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    weight: FontWeight? = null,
    italic: Boolean = false,
    locale: String? = null,
    align: TextAlign = TextAlign.Unspecified,
) {
    val family = LocalPreviewFont.current
    Text(
        text = text,
        color = color,
        textAlign = align,
        modifier = modifier,
        style = style.copy(
            fontFamily = family ?: style.fontFamily,
            fontWeight = weight ?: style.fontWeight,
            fontStyle = if (italic) FontStyle.Italic else style.fontStyle,
            localeList = locale?.let { LocaleList(it) } ?: style.localeList,
            // Right-to-left text starts at the right, whatever the app's own direction is. Digits
            // alone have no direction of their own, the language gives it
            textDirection = if (locale in RTL_LANGUAGES) TextDirection.Rtl else TextDirection.Content,
        ),
    )
}

private val RTL_LANGUAGES = setOf("ar", "he")

private const val PANGRAM = "The quick brown fox jumps over the lazy dog."

private const val DIGITS = "0123456789"

private const val LOREM_IPSUM =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt " +
            "ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation " +
            "ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in " +
            "reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur."

/** Latin letters with marks: Western Europe, Central Europe, Turkish and Romanian, Vietnamese. */
private val LATIN_ACCENTS = listOf(
    "À Á Â Ã Ä Å Æ Ç È É Ê Ë Ì Í Î Ï Ð Ñ Ò Ó Ô Õ Ö Ø Ù Ú Û Ü Ý Þ ß " +
            "à á â ã ä å æ ç è é ê ë ì í î ï ð ñ ò ó ô õ ö ø ù ú û ü ý þ ÿ",
    "Ą Ć Č Ď Ę Ě Ł Ń Ň Ő Ř Ś Š Ť Ů Ű Ź Ż Ž ą ć č ď ę ě ł ń ň ő ř ś š ť ů ű ź ż ž",
    "Ğ İ Ş Ă Â Î Ș Ț ğ ı ş ă â î ș ț",
    "Ă Â Đ Ê Ô Ơ Ư ă â đ ê ô ơ ư à á ả ã ạ ằ ắ ẳ ẵ ặ ầ ấ ẩ ẫ ậ ề ế ể ễ ệ ồ ố ổ ỗ ộ " +
            "ờ ớ ở ỡ ợ ừ ứ ử ữ ự ỳ ý ỷ ỹ ỵ",
)

//<editor-fold defaultstate="collapsed" desc="Previews">

@ScreenPreviews
@Composable
private fun FontPreviewPreview() = PreviewTheme {
    FontPreviewContent(fontFamily = null, onSave = {}, onBack = {})
}

//</editor-fold>
