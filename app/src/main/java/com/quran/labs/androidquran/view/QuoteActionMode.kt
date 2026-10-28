package com.quran.labs.androidquran.view

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import com.quran.labs.androidquran.R

/** adds "Add to research" beside Copy and Share when text is selected in a tafsir */
class QuoteActionMode(private val view: TextView, private val onQuote: (String) -> Unit) : ActionMode.Callback {
  override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
    menu.add(Menu.NONE, R.id.quote_to_research, 0, R.string.research_quote_tafsir)
      .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    return true
  }

  override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

  override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
    if (item.itemId != R.id.quote_to_research) return false
    val start = minOf(view.selectionStart, view.selectionEnd).coerceAtLeast(0)
    val end = maxOf(view.selectionStart, view.selectionEnd)
    val selected = view.text.subSequence(start, end).toString().trim()
    mode.finish()
    if (selected.isNotEmpty()) onQuote(selected)
    return true
  }

  override fun onDestroyActionMode(mode: ActionMode) = Unit
}
