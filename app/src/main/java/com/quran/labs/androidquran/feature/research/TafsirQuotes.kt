package com.quran.labs.androidquran.feature.research

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** a passage the reader quoted from a tafsir into their research, and the note it became */
data class TafsirQuote(val noteUid: String, val tafsir: String, val sura: Int, val ayah: Int, val text: String)

/**
 * Where each tafsir quote came from, so the tafsir can highlight what was quoted. The quote
 * itself lives in the research as a note; this only remembers its place in the tafsir.
 */
class TafsirQuotes private constructor(context: Context) {
  private val helper = object : SQLiteOpenHelper(context, "tafsir_quotes.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(
        "CREATE TABLE quotes(note_uid TEXT PRIMARY KEY, tafsir TEXT NOT NULL, sura INTEGER NOT NULL, " +
          "ayah INTEGER NOT NULL, text TEXT NOT NULL, created INTEGER NOT NULL)"
      )
      db.execSQL("CREATE INDEX quotes_place ON quotes(tafsir, sura, ayah)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
  }

  fun add(quote: TafsirQuote) {
    helper.writableDatabase.insertWithOnConflict("quotes", null, ContentValues().apply {
      put("note_uid", quote.noteUid)
      put("tafsir", quote.tafsir)
      put("sura", quote.sura)
      put("ayah", quote.ayah)
      put("text", quote.text)
      put("created", System.currentTimeMillis())
    }, SQLiteDatabase.CONFLICT_REPLACE)
  }

  /** quotes from this verse's tafsir; a handful at most, so fine on the main thread */
  fun forAyah(tafsir: String, sura: Int, ayah: Int): List<TafsirQuote> =
    helper.readableDatabase.rawQuery(
      "SELECT note_uid, text FROM quotes WHERE tafsir = ? AND sura = ? AND ayah = ? ORDER BY created",
      arrayOf(tafsir, sura.toString(), ayah.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(TafsirQuote(c.getString(0), tafsir, sura, ayah, c.getString(1))) } }

  fun remove(noteUid: String) {
    helper.writableDatabase.delete("quotes", "note_uid = ?", arrayOf(noteUid))
  }

  companion object {
    @Volatile private var instance: TafsirQuotes? = null

    fun get(context: Context): TafsirQuotes =
      instance ?: synchronized(this) { instance ?: TafsirQuotes(context.applicationContext).also { instance = it } }
  }
}
