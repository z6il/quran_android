package com.quran.labs.androidquran.feature.research

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** The reader's ideas and the links from them to verses, hadith and other ideas. */
@SingleIn(AppScope::class)
class ResearchStore @Inject constructor(
  @ApplicationContext context: Context
) {
  private val helper = Helper(context)
  private val mutableIdeas = MutableStateFlow<List<IdeaSummary>>(emptyList())

  /** every idea, most recently changed first */
  val ideas: StateFlow<List<IdeaSummary>> = mutableIdeas.asStateFlow()

  suspend fun refresh() = withContext(Dispatchers.IO) { mutableIdeas.value = loadSummaries() }

  suspend fun idea(id: Long): Idea? = withContext(Dispatchers.IO) {
    helper.readableDatabase.rawQuery("SELECT id, title, body, tags, updated FROM ideas WHERE id = ?", arrayOf(id.toString()))
      .use { c -> if (c.moveToFirst()) c.toIdea() else null }
  }

  suspend fun createIdea(title: String, body: String = "", tags: List<String> = emptyList()): Long =
    withContext(Dispatchers.IO) {
      val now = System.currentTimeMillis()
      val id = helper.writableDatabase.insert("ideas", null, ContentValues().apply {
        put("title", title.trim())
        put("body", body)
        put("tags", tags.joinToString(","))
        put("created", now)
        put("updated", now)
      })
      mutableIdeas.value = loadSummaries()
      id
    }

  suspend fun updateIdea(id: Long, title: String, body: String, tags: List<String>) = withContext(Dispatchers.IO) {
    helper.writableDatabase.update("ideas", ContentValues().apply {
      put("title", title.trim())
      put("body", body)
      put("tags", tags.joinToString(","))
      put("updated", System.currentTimeMillis())
    }, "id = ?", arrayOf(id.toString()))
    mutableIdeas.value = loadSummaries()
  }

  /** deletes the idea, its links, and links from other ideas to it */
  suspend fun deleteIdea(id: Long) = withContext(Dispatchers.IO) {
    val db = helper.writableDatabase
    db.beginTransaction()
    try {
      db.delete("links", "idea_id = ?", arrayOf(id.toString()))
      db.delete("links", "kind = ? AND ref = ?", arrayOf(LinkTarget.Idea.KIND, id.toString()))
      db.delete("ideas", "id = ?", arrayOf(id.toString()))
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    mutableIdeas.value = loadSummaries()
  }

  suspend fun links(ideaId: Long): List<IdeaLink> = withContext(Dispatchers.IO) {
    helper.readableDatabase.rawQuery(
      "SELECT id, idea_id, kind, ref, relation, note FROM links WHERE idea_id = ? ORDER BY created",
      arrayOf(ideaId.toString())
    ).use { c ->
      buildList {
        while (c.moveToNext()) {
          val target = LinkTarget.of(c.getString(2), c.getString(3)) ?: continue
          add(IdeaLink(c.getLong(0), c.getLong(1), target, Relation.of(c.getString(4)), c.getString(5)))
        }
      }
    }
  }

  /** every link, for drawing the map of how ideas connect */
  suspend fun allLinks(): List<IdeaLink> = withContext(Dispatchers.IO) {
    helper.readableDatabase.rawQuery("SELECT id, idea_id, kind, ref, relation, note FROM links", null).use { c ->
      buildList {
        while (c.moveToNext()) {
          val target = LinkTarget.of(c.getString(2), c.getString(3)) ?: continue
          add(IdeaLink(c.getLong(0), c.getLong(1), target, Relation.of(c.getString(4)), c.getString(5)))
        }
      }
    }
  }

  /** links an idea to a target; linking the same target twice updates the relation */
  suspend fun addLink(ideaId: Long, target: LinkTarget, relation: Relation, note: String = "") =
    withContext(Dispatchers.IO) {
      if (target is LinkTarget.Idea && target.id == ideaId) return@withContext
      val db = helper.writableDatabase
      val values = ContentValues().apply {
        put("idea_id", ideaId)
        put("kind", target.kind)
        put("ref", target.ref)
        put("relation", relation.key)
        put("note", note)
        put("created", System.currentTimeMillis())
      }
      val updated = db.update(
        "links", values, "idea_id = ? AND kind = ? AND ref = ?",
        arrayOf(ideaId.toString(), target.kind, target.ref)
      )
      if (updated == 0) db.insert("links", null, values)
      touch(db, ideaId)
      mutableIdeas.value = loadSummaries()
    }

  suspend fun removeLink(link: IdeaLink) = withContext(Dispatchers.IO) {
    val db = helper.writableDatabase
    db.delete("links", "id = ?", arrayOf(link.id.toString()))
    touch(db, link.ideaId)
    mutableIdeas.value = loadSummaries()
  }

  /** the ideas that link to [target], e.g. to show beside a verse */
  suspend fun ideasLinking(target: LinkTarget): List<Idea> = withContext(Dispatchers.IO) {
    helper.readableDatabase.rawQuery(
      """SELECT DISTINCT i.id, i.title, i.body, i.tags, i.updated FROM ideas i
         JOIN links l ON l.idea_id = i.id WHERE l.kind = ? AND l.ref = ? ORDER BY i.updated DESC""",
      arrayOf(target.kind, target.ref)
    ).use { c -> buildList { while (c.moveToNext()) add(c.toIdea()) } }
  }

  private fun touch(db: SQLiteDatabase, ideaId: Long) {
    db.update("ideas", ContentValues().apply { put("updated", System.currentTimeMillis()) }, "id = ?", arrayOf(ideaId.toString()))
  }

  private fun loadSummaries(): List<IdeaSummary> =
    helper.readableDatabase.rawQuery(
      """SELECT i.id, i.title, i.body, i.tags, i.updated,
           SUM(l.kind = 'ayah'), SUM(l.kind = 'hadith'), SUM(l.kind = 'idea')
         FROM ideas i LEFT JOIN links l ON l.idea_id = i.id
         GROUP BY i.id ORDER BY i.updated DESC""",
      null
    ).use { c ->
      buildList {
        while (c.moveToNext()) add(IdeaSummary(c.toIdea(), c.getInt(5), c.getInt(6), c.getInt(7)))
      }
    }

  private fun android.database.Cursor.toIdea() =
    Idea(getLong(0), getString(1), getString(2), parseTags(getString(3)), getLong(4))

  private class Helper(context: Context) : SQLiteOpenHelper(context, "research.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(
        """CREATE TABLE ideas(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL,
           body TEXT NOT NULL DEFAULT '', tags TEXT NOT NULL DEFAULT '', created INTEGER, updated INTEGER)"""
      )
      db.execSQL(
        """CREATE TABLE links(id INTEGER PRIMARY KEY AUTOINCREMENT, idea_id INTEGER NOT NULL, kind TEXT NOT NULL,
           ref TEXT NOT NULL, relation TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', created INTEGER)"""
      )
      db.execSQL("CREATE INDEX links_idea ON links(idea_id)")
      db.execSQL("CREATE INDEX links_target ON links(kind, ref)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
  }
}
