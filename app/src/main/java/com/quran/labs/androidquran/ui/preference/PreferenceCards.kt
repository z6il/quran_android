package com.quran.labs.androidquran.ui.preference

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.quran.labs.androidquran.R

/**
 * Draws each section of a list (the rows between two headers) on one rounded card over a
 * tinted page, instead of a flat list with dividers. Used by settings and the translations list.
 */
class SectionCardDecoration(
  private val isCategory: (position: Int) -> Boolean,
  density: Float,
  cardColor: Int
) : RecyclerView.ItemDecoration() {
  private val margin = (16 * density).toInt()
  private val radius = 20 * density
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardColor }
  private val rect = RectF()

  private fun isRow(position: Int, count: Int) = position in 0 until count && !isCategory(position)

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    val position = parent.getChildAdapterPosition(view)
    val count = parent.adapter?.itemCount ?: 0
    if (!isRow(position, count)) return
    outRect.left = margin
    outRect.right = margin
    // room inside the card above its first row and below its last
    if (!isRow(position - 1, count)) outRect.top = margin / 2
    if (!isRow(position + 1, count)) outRect.bottom = margin / 2
  }

  override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    val count = parent.adapter?.itemCount ?: return
    val left = parent.paddingLeft + margin.toFloat()
    val right = parent.width - parent.paddingRight - margin.toFloat()
    var i = 0
    while (i < parent.childCount) {
      val first = parent.getChildAt(i)
      val firstPosition = parent.getChildAdapterPosition(first)
      if (!isRow(firstPosition, count)) {
        i++
        continue
      }
      var top = first.top + first.translationY - margin / 2f
      var bottom = first.bottom + first.translationY
      var lastPosition = firstPosition
      var j = i + 1
      while (j < parent.childCount) {
        val child = parent.getChildAt(j)
        val position = parent.getChildAdapterPosition(child)
        if (!isRow(position, count) || position != lastPosition + 1) break
        bottom = child.bottom + child.translationY
        lastPosition = position
        j++
      }
      bottom += margin / 2f
      // a card running off screen keeps its corners out of sight
      if (isRow(firstPosition - 1, count)) top -= radius
      if (isRow(lastPosition + 1, count)) bottom += radius
      rect.set(left, top, right, bottom)
      c.drawRoundRect(rect, radius, radius, paint)
      i = j
    }
  }
}

/** Card sections, a tinted page, and no dividers, for [fragment]'s list. */
fun styleAsCards(fragment: PreferenceFragmentCompat) {
  val list = fragment.listView ?: return
  val context = list.context
  fragment.setDivider(null)
  list.setBackgroundColor(ContextCompat.getColor(context, R.color.settings_page))
  list.addItemDecoration(
    SectionCardDecoration(
      isCategory = { position -> (list.adapter as? PreferenceGroupAdapter)?.getItem(position) is PreferenceCategory },
      density = context.resources.displayMetrics.density,
      cardColor = ContextCompat.getColor(context, R.color.settings_card)
    )
  )
}
