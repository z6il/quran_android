package com.quran.labs.androidquran.view

import com.quran.common.search.SearchTextUtil
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.content.res.Configuration
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import com.quran.labs.androidquran.ui.translation.TafsirStyle
import com.quran.labs.androidquran.util.QuranUtils
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.common.TranslationMetadata
import com.quran.labs.androidquran.ui.helpers.TypefaceWrappingSpan
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.translation.model.LocalTranslation

class InlineTranslationView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : ScrollView(context, attrs, defStyle) {
  private var leftRightMargin = 0
  private var topBottomMargin = 0

  @StyleRes
  private var textStyle = 0
  private var ayahFontSize = 0
  private var translationFontSize = 0
  private var footerSpacerHeight = 0
  private var inlineAyahColor: Int = 0
  private var ayahTextColor: Int = 0

  private lateinit var linearLayout: LinearLayout

  private var ayat: List<QuranAyahInfo>? = null
  private var translations: Array<LocalTranslation>? = null

  init {
    init(context)
  }

  private fun init(context: Context) {
    isFillViewport = true
    linearLayout = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
    }
    addView(linearLayout, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    leftRightMargin = resources.getDimensionPixelSize(R.dimen.translation_left_right_margin)
    topBottomMargin = resources.getDimensionPixelSize(R.dimen.translation_top_bottom_margin)
    footerSpacerHeight = resources.getDimensionPixelSize(R.dimen.translation_footer_spacer)
    initResources()
  }

  private fun initResources() {
    val settings = QuranSettings.getInstance(context)
    ayahFontSize = settings.ayahTextSize
    translationFontSize = settings.translationTextSize
    textStyle = R.style.TranslationText
    // a brighter accent on the dark panel
    inlineAyahColor = ContextCompat.getColor(
      context, if (isNight) R.color.translation_inline_ayah_highlight_night_color else R.color.translation_translator_color
    )
    ayahTextColor = ContextCompat.getColor(context, R.color.text_primary)
  }

  fun refresh() {
    val ayat = ayat
    val translations = translations
    if (ayat != null && translations != null) {
      initResources()
      setAyahs(translations, ayat)
    }
  }

  fun setAyahs(translations: Array<LocalTranslation>, ayat: List<QuranAyahInfo>) {
    linearLayout.removeAllViews()
    if (ayat.isNotEmpty() && ayat[0].texts.isNotEmpty()) {
      this.ayat = ayat
      this.translations = translations
      var i = 0
      val ayatSize = ayat.size
      while (i < ayatSize) {
        addTextForAyah(translations, ayat[i])
        i++
      }
      addFooterSpacer()
    }
  }

  fun resetScroll() {
    scrollTo(0, 0)
  }

  private fun addFooterSpacer() {
    val params = LinearLayout.LayoutParams(
      LayoutParams.MATCH_PARENT, footerSpacerHeight
    )
    val view = View(context)
    linearLayout.addView(view, params)
  }

  private val density = resources.displayMetrics.density
  private val isNight
    get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

  private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
    cornerRadius = radiusDp * density
    setColor(color)
  }

  private fun tint(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

  private fun dp(value: Int) = (value * density).toInt()

  /** one card per verse: its number, then each translation or tafsir under a label */
  private fun addTextForAyah(translations: Array<LocalTranslation>, ayah: QuranAyahInfo) {
    val accent = inlineAyahColor
    val card = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      background = rounded(if (isNight) 0x14FFFFFF else tint(accent, 0x0D), 18f)
      setPadding(dp(16), dp(14), dp(16), dp(16))
    }
    val cardParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
      setMargins(leftRightMargin / 2, topBottomMargin, leftRightMargin / 2, topBottomMargin / 2)
    }

    val ayahHeader = TextView(context).apply {
      text = context.resources.getString(R.string.sura_ayah, ayah.sura, ayah.ayah)
      setTextColor(accent)
      textSize = 14f
      setTypeface(null, Typeface.BOLD)
      background = rounded(tint(accent, if (isNight) 0x33 else 0x1F), 50f)
      setPadding(dp(14), dp(5), dp(14), dp(5))
    }
    card.addView(ayahHeader, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

    for (i in translations.indices) {
      val (_, _, translationText) = ayah.texts.getOrNull(i) ?: continue
      if (TextUtils.isEmpty(translationText)) continue
      val arabic = translations[i].languageCode == "ar"

      // which translation or tafsir this is
      val label = TextView(context).apply {
        text = translations[i].resolveTranslatorName()
        setTextColor(accent)
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        letterSpacing = 0.02f
      }
      card.addView(label, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(14)
      })

      val body = TextView(context).apply {
        setTextAppearance(textStyle)
        setTextColor(ayahTextColor)
        textSize = translationFontSize * if (arabic) 1.2f else 1f
        setLineSpacing(0f, if (arabic) 1.12f else 1.2f)
        if (arabic || SearchTextUtil.isRtl(translationText)) {
          layoutDirection = View.LAYOUT_DIRECTION_RTL
          textDirection = View.TEXT_DIRECTION_RTL
        }
        setTextIsSelectable(true)
      }
      val full = stylize(ayah.texts[i], translations[i].languageCode, translationText)
      // a long tafsir opens on its first sentences; the rest is a tap away
      val cut = if (full.length > LONG_TAFSIR) TafsirStyle.cutPoint(full, PREVIEW_LENGTH) else -1
      body.text = if (cut == -1) full else full.subSequence(0, cut + 1)
      card.addView(body, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(6)
      })
      if (cut != -1) {
        val minutes = QuranUtils.getLocalizedNumber(TafsirStyle.minutes(full))
        val readAll = context.getString(if (arabic) R.string.tafsir_read_all_ar else R.string.tafsir_read_all_en, minutes)
        val showLess = context.getString(if (arabic) R.string.tafsir_show_less_ar else R.string.tafsir_show_less_en)
        val toggle = TextView(context).apply {
          text = readAll
          setTextColor(accent)
          textSize = 14f
          setTypeface(null, Typeface.BOLD)
          background = rounded(tint(accent, if (isNight) 0x33 else 0x1A), 50f)
          setPadding(dp(14), dp(7), dp(14), dp(7))
          if (arabic) layoutDirection = View.LAYOUT_DIRECTION_RTL
          setOnClickListener {
            val open = body.text.length < full.length
            body.text = if (open) full else full.subSequence(0, cut + 1)
            text = if (open) showLess else readAll
          }
        }
        card.addView(toggle, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
          topMargin = dp(10)
          gravity = if (arabic) android.view.Gravity.END else android.view.Gravity.START
        })
      }
    }
    linearLayout.addView(card, cardParams)
  }

  private fun collapsedFootnoteSpan(number: Int): SpannableString {
    return SpannableString("")
  }

  private fun expandedFootnote(
    spannableStringBuilder: SpannableStringBuilder,
    start: Int,
    end: Int
  ): SpannableStringBuilder {
    return spannableStringBuilder
  }

  private fun stylize(
    metadata: TranslationMetadata,
    languageCode: String? = null,
    translationText: String
  ): CharSequence {
    val spannableStringBuilder = SpannableStringBuilder(translationText)

    if (languageCode == "ar") {
      val spans = listOf(
        TypefaceWrappingSpan(TypefaceManager.getTafseerTypeface(context)),
        RelativeSizeSpan(1.1f)
      )

      spans.forEach { span ->
        spannableStringBuilder.setSpan(
          span,
          0,
          spannableStringBuilder.length,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
      }
    }

    metadata.ayat.forEach { range ->
      val span = ForegroundColorSpan(inlineAyahColor)
      spannableStringBuilder.setSpan(
        span,
        range.first,
        range.last + 1,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
      )
    }

    val text = metadata.footnoteCognizantText(
      spannableStringBuilder,
      listOf(),
      ::collapsedFootnoteSpan,
      ::expandedFootnote
    )
    // Arabic tafsir: chains of narration step back so the explanation stands out
    return if (languageCode == "ar") TafsirStyle.style(text, inlineAyahColor, tint(ayahTextColor, 0x8C)) else text
  }

  companion object {
    private const val LONG_TAFSIR = 750
    private const val PREVIEW_LENGTH = 480
  }
}
