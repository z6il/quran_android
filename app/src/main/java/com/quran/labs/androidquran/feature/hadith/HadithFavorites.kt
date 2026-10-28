package com.quran.labs.androidquran.feature.hadith

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.feature.research.LinkTarget
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** The hadith the reader starred, newest first. Kept apart from the downloaded library. */
@SingleIn(AppScope::class)
class HadithFavorites @Inject constructor(@ApplicationContext context: Context) {
  private val helper = object : SQLiteOpenHelper(context, "hadith_favorites.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(
        "CREATE TABLE favorites(collection TEXT NOT NULL, number TEXT NOT NULL, added INTEGER NOT NULL, " +
          "PRIMARY KEY(collection, number))"
      )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
  }

  private val mutable = MutableStateFlow<List<LinkTarget.Hadith>>(emptyList())
  /** newest first */
  val favorites: StateFlow<List<LinkTarget.Hadith>> = mutable.asStateFlow()

  suspend fun refresh() = withContext(Dispatchers.IO) {
    mutable.value = helper.readableDatabase.rawQuery("SELECT collection, number FROM favorites ORDER BY added DESC", null).use { c ->
      buildList { while (c.moveToNext()) add(LinkTarget.Hadith(c.getString(0), c.getString(1))) }
    }
  }

  suspend fun toggle(target: LinkTarget.Hadith) = withContext(Dispatchers.IO) {
    val db = helper.writableDatabase
    val removed = db.delete("favorites", "collection = ? AND number = ?", arrayOf(target.collection, target.number))
    if (removed == 0) {
      db.insert("favorites", null, ContentValues().apply {
        put("collection", target.collection)
        put("number", target.number)
        put("added", System.currentTimeMillis())
      })
    }
    refresh()
  }
}
