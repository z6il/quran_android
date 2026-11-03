package com.quran.labs.androidquran.feature.library

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.quran.labs.androidquran.R

/** The page's colours: a book reads best on paper, or dark at night. */
enum class PageTheme(val title: Int, val background: Long, val text: Long, val accent: Long, val dark: Boolean) {
  WHITE(R.string.library_theme_white, 0xFFFFFFFF, 0xFF1E1E1E, 0xFF1F6B52, false),
  PAPER(R.string.library_theme_paper, 0xFFF7F1E3, 0xFF3D3328, 0xFF7A4B12, false),
  SEPIA(R.string.library_theme_sepia, 0xFFEBDDC3, 0xFF3B2C1D, 0xFF8A4B1C, false),
  MINT(R.string.library_theme_mint, 0xFFE6EFE7, 0xFF1F3327, 0xFF1F6B52, false),
  GRAY(R.string.library_theme_gray, 0xFF34363A, 0xFFDCDAD5, 0xFF9CCFB8, true),
  NIGHT(R.string.library_theme_night, 0xFF16171A, 0xFFCBC7BE, 0xFFD9B26E, true),
  BLACK(R.string.library_theme_black, 0xFF000000, 0xFFB9B5AC, 0xFFD9B26E, true);

  val backgroundColor get() = Color(background)
  val textColor get() = Color(text)
  val accentColor get() = Color(accent)
  /** the bars over the page: a shade of the page itself */
  val chromeColor get() = if (dark) Color(background).copy(alpha = 0.96f).compositeOverWhiteish(0.06f) else Color(background).darken(0.04f)
}

private fun Color.darken(by: Float) = Color(red * (1 - by), green * (1 - by), blue * (1 - by), alpha)
private fun Color.compositeOverWhiteish(by: Float) = Color(red + (1 - red) * by, green + (1 - green) * by, blue + (1 - blue) * by, 1f)

/** The Arabic face a book is set in. */
enum class ReaderFont(val title: Int) {
  KITAB(R.string.library_font_kitab),
  NASKH(R.string.library_font_naskh)
}

/** How the reader wants pages to look, kept across books. [theme] null follows the app (paper by day, night after dark). */
data class ReaderStyle(
  val theme: PageTheme? = null,
  val scale: Float = 1f,
  val lineSpacing: Float = 1.85f,
  val margin: Int = 20,
  val font: ReaderFont = ReaderFont.KITAB,
  val justify: Boolean = true,
  /** a PDF's pages drawn light on dark when the page theme is dark */
  val pdfNight: Boolean = true,
  val keepScreenOn: Boolean = true,
  /** an EPUB laid out in pages turned like a book's, rather than one long scroll */
  val bookPages: Boolean = true
) {
  fun pageTheme(appDark: Boolean): PageTheme = theme ?: if (appDark) PageTheme.NIGHT else PageTheme.PAPER

  fun save(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
    putString("theme", theme?.name ?: "")
    putFloat("text_scale", scale)
    putFloat("line_spacing", lineSpacing)
    putInt("margin", margin)
    putString("font", font.name)
    putBoolean("justify", justify)
    putBoolean("pdf_night", pdfNight)
    putBoolean("keep_on", keepScreenOn)
    putBoolean("book_pages", bookPages)
  }

  companion object {
    private const val PREFS = "library"
    val SCALES = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.75f, 2f)
    val SPACINGS = listOf(1.5f, 1.85f, 2.2f)
    val MARGINS = listOf(10, 20, 34)

    fun load(context: Context): ReaderStyle {
      val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      return ReaderStyle(
        theme = p.getString("theme", "")?.let { name -> PageTheme.entries.firstOrNull { it.name == name } },
        scale = p.getFloat("text_scale", 1f),
        lineSpacing = p.getFloat("line_spacing", 1.85f),
        margin = p.getInt("margin", 20),
        font = p.getString("font", null)?.let { name -> ReaderFont.entries.firstOrNull { it.name == name } } ?: ReaderFont.KITAB,
        justify = p.getBoolean("justify", true),
        pdfNight = p.getBoolean("pdf_night", true),
        keepScreenOn = p.getBoolean("keep_on", true),
        bookPages = p.getBoolean("book_pages", true)
      )
    }
  }
}

/** the Compose font for [font]; the system's Naskh is the default sans for Arabic */
@Composable
internal fun readerFontFamily(font: ReaderFont): FontFamily {
  val assets = LocalContext.current.assets
  return remember(font, assets) {
    when (font) {
      ReaderFont.KITAB -> FontFamily(Font("kitab.ttf", assets))
      ReaderFont.NASKH -> FontFamily.Default
    }
  }
}

/** The Aa sheet: the page's theme, the text's size, spacing, margins and face. */
@Composable
internal fun AppearanceSheet(style: ReaderStyle, kind: BookKind, appDark: Boolean, onChange: (ReaderStyle) -> Unit) {
  Column(
    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp, 0.dp, 20.dp, 32.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Label(R.string.library_theme)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      item {
        ThemeSwatch(
          background = style.pageTheme(appDark).backgroundColor, text = style.pageTheme(appDark).textColor,
          title = stringResource(R.string.library_theme_auto), selected = style.theme == null
        ) { onChange(style.copy(theme = null)) }
      }
      items(PageTheme.entries) { t ->
        ThemeSwatch(t.backgroundColor, t.textColor, stringResource(t.title), style.theme == t) { onChange(style.copy(theme = t)) }
      }
    }
    if (kind != BookKind.PDF) {
      Label(R.string.library_text_size)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("A", fontSize = 14.sp)
        Slider(
          value = ReaderStyle.SCALES.indexOf(style.scale).coerceAtLeast(0).toFloat(),
          onValueChange = { onChange(style.copy(scale = ReaderStyle.SCALES[it.toInt()])) },
          valueRange = 0f..(ReaderStyle.SCALES.size - 1).toFloat(),
          steps = ReaderStyle.SCALES.size - 2,
          modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        )
        Text("A", fontSize = 24.sp)
      }
      Label(R.string.library_line_spacing)
      Choices(listOf(R.string.library_compact, R.string.library_normal, R.string.library_relaxed), ReaderStyle.SPACINGS.indexOf(style.lineSpacing)) {
        onChange(style.copy(lineSpacing = ReaderStyle.SPACINGS[it]))
      }
      Label(R.string.library_margins)
      Choices(listOf(R.string.library_narrow, R.string.library_normal, R.string.library_wide), ReaderStyle.MARGINS.indexOf(style.margin)) {
        onChange(style.copy(margin = ReaderStyle.MARGINS[it]))
      }
      Label(R.string.library_font)
      Choices(ReaderFont.entries.map { it.title }, style.font.ordinal) { onChange(style.copy(font = ReaderFont.entries[it])) }
      Toggle(R.string.library_justify, style.justify) { onChange(style.copy(justify = it)) }
      if (kind == BookKind.EPUB) Toggle(R.string.library_book_pages, style.bookPages) { onChange(style.copy(bookPages = it)) }
    } else {
      Toggle(R.string.library_pdf_night, style.pdfNight) { onChange(style.copy(pdfNight = it)) }
    }
    Toggle(R.string.library_keep_on, style.keepScreenOn) { onChange(style.copy(keepScreenOn = it)) }
  }
}

@Composable
private fun Label(text: Int) {
  Text(stringResource(text), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ThemeSwatch(background: Color, text: Color, title: String, selected: Boolean, onClick: () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
    Box(
      Modifier
        .size(52.dp)
        .clip(CircleShape)
        .background(background)
        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f), CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Text("ب", style = TextStyle(color = text, fontSize = 22.sp, fontWeight = FontWeight.Bold))
    }
    Spacer(Modifier.size(4.dp))
    Text(title, style = MaterialTheme.typography.labelSmall)
  }
}

@Composable
private fun Choices(titles: List<Int>, selected: Int, onPick: (Int) -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    titles.forEachIndexed { i, t ->
      FilterChip(selected = i == selected, onClick = { onPick(i) }, label = { Text(stringResource(t)) }, shape = RoundedCornerShape(50))
    }
  }
}

@Composable
private fun Toggle(text: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onChange)
  }
}
