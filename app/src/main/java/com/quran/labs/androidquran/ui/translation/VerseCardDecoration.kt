package com.quran.labs.androidquran.ui.translation

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Draws each verse (its number, Arabic text and translations or tafsir) on one rounded card,
 * tinted and outlined while the verse is selected or playing.
 */
internal class VerseCardDecoration(
  private val rowAt: (position: Int) -> TranslationViewRow?,
  private val highlightedAyah: () -> Int,
  density: Float
) : RecyclerView.ItemDecoration() {
  private val margin = (12 * density).toInt()
  private val radius = 18 * density
  private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
  private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 1.5f * density
  }
  private val rect = RectF()

  var cardColor = 0
  var highlightColor = 0
  var strokeColor = 0

  private fun isCard(row: TranslationViewRow?) = row != null && row.type in CARD_TYPES

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    val position = parent.getChildAdapterPosition(view)
    if (position != RecyclerView.NO_POSITION && isCard(rowAt(position))) {
      outRect.left = margin
      outRect.right = margin
    }
  }

  override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    val left = parent.paddingLeft + margin.toFloat()
    val right = parent.width - parent.paddingRight - margin.toFloat()
    var i = 0
    while (i < parent.childCount) {
      val first = parent.getChildAt(i)
      val firstPosition = parent.getChildAdapterPosition(first)
      val row = rowAt(firstPosition)
      if (!isCard(row)) {
        i++
        continue
      }
      // gather the visible rows of this verse
      val ayahId = row!!.ayahInfo.ayahId
      var top = first.top + first.translationY
      var bottom = first.bottom + first.translationY
      var lastPosition = firstPosition
      var j = i + 1
      while (j < parent.childCount) {
        val child = parent.getChildAt(j)
        val position = parent.getChildAdapterPosition(child)
        val next = rowAt(position)
        if (!isCard(next) || next!!.ayahInfo.ayahId != ayahId) break
        bottom = child.bottom + child.translationY
        lastPosition = position
        j++
      }
      // a card cut off by the screen edge keeps its corners out of sight
      if (rowAt(firstPosition - 1)?.let { isCard(it) && it.ayahInfo.ayahId == ayahId } == true) top -= radius
      if (rowAt(lastPosition + 1)?.let { isCard(it) && it.ayahInfo.ayahId == ayahId } == true) bottom += radius

      val highlighted = ayahId == highlightedAyah()
      rect.set(left, top, right, bottom)
      fill.color = if (highlighted) highlightColor else cardColor
      c.drawRoundRect(rect, radius, radius, fill)
      if (highlighted) {
        stroke.color = strokeColor
        c.drawRoundRect(rect, radius, radius, stroke)
      }
      i = j
    }
  }

  companion object {
    private val CARD_TYPES = setOf(
      TranslationViewRow.Type.VERSE_NUMBER,
      TranslationViewRow.Type.QURAN_TEXT,
      TranslationViewRow.Type.TRANSLATOR,
      TranslationViewRow.Type.TRANSLATION_TEXT
    )
  }
}
