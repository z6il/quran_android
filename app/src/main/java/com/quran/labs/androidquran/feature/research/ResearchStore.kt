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
import java.util.UUID

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
    helper.readableDatabase.rawQuery("SELECT id, title, body, tags, updated, author FROM ideas WHERE id = ?", arrayOf(id.toString()))
      .use { c -> if (c.moveToFirst()) c.toIdea() else null }
  }

  suspend fun createIdea(title: String, body: String = "", tags: List<String> = emptyList()): Long =
    withContext(Dispatchers.IO) {
      val now = System.currentTimeMillis()
      val id = helper.writableDatabase.insert("ideas", null, ContentValues().apply {
        put("uid", UUID.randomUUID().toString())
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
      dropOrphans(db)
      dropLooseNotes(db)
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    mutableIdeas.value = loadSummaries()
  }

  suspend fun links(ideaId: Long): List<IdeaLink> = withContext(Dispatchers.IO) {
    helper.readableDatabase.rawQuery(
      "SELECT id, idea_id, kind, ref, relation, note, parent_id FROM links WHERE idea_id = ? ORDER BY created, id",
      arrayOf(ideaId.toString())
    ).use { c -> buildList { while (c.moveToNext()) c.toLink()?.let(::add) } }
  }

  /** every link, for drawing the map of how ideas connect */
  suspend fun allLinks(): List<IdeaLink> = withContext(Dispatchers.IO) {
    helper.readableDatabase.rawQuery("SELECT id, idea_id, kind, ref, relation, note, parent_id FROM links", null).use { c ->
      buildList { while (c.moveToNext()) c.toLink()?.let(::add) }
    }
  }

  /**
   * links an idea to a target, or puts it under the link [parentId]; linking the same target
   * twice in the same place updates the relation
   */
  suspend fun addLink(ideaId: Long, target: LinkTarget, relation: Relation, note: String = "", parentId: Long? = null) =
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
        if (parentId != null) put("parent_id", parentId) else putNull("parent_id")
      }
      val updated = db.update(
        "links", values, "idea_id = ? AND kind = ? AND ref = ? AND IFNULL(parent_id, 0) = CAST(? AS INTEGER)",
        arrayOf(ideaId.toString(), target.kind, target.ref, (parentId ?: 0L).toString())
      )
      if (updated == 0) db.insert("links", null, values)
      touch(db, ideaId)
      mutableIdeas.value = loadSummaries()
    }

  /** adds a note source (a quote or finding) and links [ideaId] to it */
  suspend fun addNote(ideaId: Long, text: String, source: String, relation: Relation, parentId: Long? = null) {
    val uid = UUID.randomUUID().toString()
    withContext(Dispatchers.IO) { saveNote(helper.writableDatabase, uid, text, source) }
    addLink(ideaId, LinkTarget.Note(uid), relation, parentId = parentId)
  }

  suspend fun updateNote(ideaId: Long, uid: String, text: String, source: String) = withContext(Dispatchers.IO) {
    val db = helper.writableDatabase
    saveNote(db, uid, text, source)
    touch(db, ideaId)
    mutableIdeas.value = loadSummaries()
  }

  /** every note source, by uid */
  suspend fun notes(): Map<String, NoteSource> = withContext(Dispatchers.IO) { notesIn(helper.readableDatabase) }

  private fun saveNote(db: SQLiteDatabase, uid: String, text: String, source: String) {
    db.insertWithOnConflict("notes", null, ContentValues().apply {
      put("uid", uid)
      put("text", text.trim())
      put("source", source.trim())
      put("created", System.currentTimeMillis())
    }, SQLiteDatabase.CONFLICT_REPLACE)
  }

  /** links whose parent is gone go too, and then theirs, all the way down */
  private fun dropOrphans(db: SQLiteDatabase) {
    while (true) {
      val gone = db.delete("links", "parent_id IS NOT NULL AND parent_id NOT IN (SELECT id FROM links)", null)
      if (gone == 0) break
    }
  }

  private fun android.database.Cursor.toLink(): IdeaLink? {
    val target = LinkTarget.of(getString(2), getString(3)) ?: return null
    return IdeaLink(getLong(0), getLong(1), target, Relation.of(getString(4)), getString(5), if (isNull(6)) null else getLong(6))
  }

  /** notes no link points at any more */
  private fun dropLooseNotes(db: SQLiteDatabase) {
    db.execSQL("DELETE FROM notes WHERE uid NOT IN (SELECT ref FROM links WHERE kind = 'note')")
  }

  /** changes how a link relates, and the reader's comment on it */
  suspend fun updateLink(link: IdeaLink, relation: Relation, note: String) = withContext(Dispatchers.IO) {
    val db = helper.writableDatabase
    db.update("links", ContentValues().apply {
      put("relation", relation.key)
      put("note", note.trim())
    }, "id = ?", arrayOf(link.id.toString()))
    touch(db, link.ideaId)
    mutableIdeas.value = loadSummaries()
  }

  suspend fun removeLink(link: IdeaLink) = withContext(Dispatchers.IO) {
    val db = helper.writableDatabase
    db.delete("links", "id = ?", arrayOf(link.id.toString()))
    dropOrphans(db)
    dropLooseNotes(db)
    touch(db, link.ideaId)
    mutableIdeas.value = loadSummaries()
  }

  /** the ideas that link to [target], e.g. to show beside a verse */
  suspend fun ideasLinking(target: LinkTarget): List<Idea> = withContext(Dispatchers.IO) {
    helper.readableDatabase.rawQuery(
      """SELECT DISTINCT i.id, i.title, i.body, i.tags, i.updated, i.author FROM ideas i
         JOIN links l ON l.idea_id = i.id WHERE l.kind = ? AND l.ref = ? ORDER BY i.updated DESC""",
      arrayOf(target.kind, target.ref)
    ).use { c -> buildList { while (c.moveToNext()) add(c.toIdea()) } }
  }

  /**
   * Packs [ideaIds] (every idea when null) into a file for someone else. Ideas they link to
   * come along, so the links still work on the other phone. The reader's own ideas are
   * signed with [me].
   */
  suspend fun export(ideaIds: Set<Long>?, me: String): ResearchBundle = withContext(Dispatchers.IO) {
    val db = helper.readableDatabase
    val uids = mutableMapOf<Long, String>()
    db.rawQuery("SELECT id, uid FROM ideas", null).use { c -> while (c.moveToNext()) uids[c.getLong(0)] = c.getString(1) }
    val notes = notesIn(db)
    val chosen = (ideaIds ?: uids.keys).toMutableSet()
    ideaIds?.forEach { id ->
      linksOf(db, id).forEach { (kind, ref) -> if (kind == LinkTarget.Idea.KIND) ref.toLongOrNull()?.let(chosen::add) }
    }
    val ideas = chosen.filter { it in uids }.mapNotNull { id ->
      db.rawQuery("SELECT title, body, tags, author FROM ideas WHERE id = ?", arrayOf(id.toString())).use { c ->
        if (!c.moveToFirst()) return@use null
        val rows = db.rawQuery(
          "SELECT id, idea_id, kind, ref, relation, note, parent_id FROM links WHERE idea_id = ? ORDER BY created, id",
          arrayOf(id.toString())
        ).use { l -> buildList { while (l.moveToNext()) l.toLink()?.let(::add) } }
        val under = rows.groupBy { it.parentId }
        fun shared(link: IdeaLink): SharedLink? {
          val kind = link.target.kind
          // a link to another idea only goes along when that idea does, by its uid
          val ref = if (link.target is LinkTarget.Idea) {
            (link.target as LinkTarget.Idea).id.takeIf { it in chosen }?.let { uids[it] } ?: return null
          } else {
            link.target.ref
          }
          val note = if (kind == LinkTarget.Note.KIND) notes[ref] ?: return null else null
          return SharedLink(
            kind, ref, link.relation, link.note, note?.text.orEmpty(), note?.source.orEmpty(),
            under[link.id].orEmpty().mapNotNull(::shared)
          )
        }
        val links = under[null].orEmpty().mapNotNull(::shared)
        SharedIdea(uids.getValue(id), c.getString(0), c.getString(1), parseTags(c.getString(2)), c.getString(3).ifBlank { me }, links)
      }
    }
    ResearchBundle(me, System.currentTimeMillis(), ideas)
  }

  /** Adds the ideas in [bundle]; an idea already here (same uid) is replaced by the file's copy. */
  suspend fun import(bundle: ResearchBundle, me: String): Int = withContext(Dispatchers.IO) {
    val db = helper.writableDatabase
    db.beginTransaction()
    try {
      val now = System.currentTimeMillis()
      val ids = bundle.ideas.associate { idea ->
        val values = ContentValues().apply {
          put("title", idea.title)
          put("body", idea.body)
          put("tags", idea.tags.joinToString(","))
          // the reader's own ideas coming back stay theirs
          put("author", if (idea.author == me) "" else idea.author)
          put("updated", now)
        }
        val existing = ideaWithUid(db, idea.uid)
        val id = if (existing != null) {
          db.update("ideas", values, "id = ?", arrayOf(existing.toString()))
          db.delete("links", "idea_id = ?", arrayOf(existing.toString()))
          existing
        } else {
          values.put("uid", idea.uid)
          values.put("created", now)
          db.insert("ideas", null, values)
        }
        idea.uid to id
      }
      bundle.ideas.forEach { idea ->
        val id = ids.getValue(idea.uid)
        fun insert(link: SharedLink, parent: Long?) {
          val ref = if (link.kind == LinkTarget.Idea.KIND) {
            val other = ids[link.ref] ?: ideaWithUid(db, link.ref)
            if (other == null || other == id) return
            other.toString()
          } else {
            if (link.kind == LinkTarget.Note.KIND) saveNote(db, link.ref, link.text, link.source)
            link.ref
          }
          val linkId = db.insert("links", null, ContentValues().apply {
            put("idea_id", id)
            put("kind", link.kind)
            put("ref", ref)
            put("relation", link.relation.key)
            put("note", link.note)
            put("created", now)
            if (parent != null) put("parent_id", parent)
          })
          link.children.forEach { insert(it, linkId) }
        }
        idea.links.forEach { insert(it, null) }
      }
      dropLooseNotes(db)
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    mutableIdeas.value = loadSummaries()
    bundle.ideas.size
  }

  private fun notesIn(db: SQLiteDatabase): Map<String, NoteSource> =
    db.rawQuery("SELECT uid, text, source FROM notes", null).use { c ->
      buildMap { while (c.moveToNext()) put(c.getString(0), NoteSource(c.getString(0), c.getString(1), c.getString(2))) }
    }

  private fun ideaWithUid(db: SQLiteDatabase, uid: String): Long? =
    db.rawQuery("SELECT id FROM ideas WHERE uid = ?", arrayOf(uid)).use { c -> if (c.moveToFirst()) c.getLong(0) else null }

  private fun linksOf(db: SQLiteDatabase, ideaId: Long): List<Pair<String, String>> =
    db.rawQuery("SELECT kind, ref FROM links WHERE idea_id = ?", arrayOf(ideaId.toString())).use { c ->
      buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
    }

  private fun touch(db: SQLiteDatabase, ideaId: Long) {
    db.update("ideas", ContentValues().apply { put("updated", System.currentTimeMillis()) }, "id = ?", arrayOf(ideaId.toString()))
  }

  private fun loadSummaries(): List<IdeaSummary> =
    helper.readableDatabase.rawQuery(
      """SELECT i.id, i.title, i.body, i.tags, i.updated, i.author,
           SUM(l.kind = 'ayah'), SUM(l.kind = 'hadith'), SUM(l.kind = 'idea'), SUM(l.kind = 'note')
         FROM ideas i LEFT JOIN links l ON l.idea_id = i.id
         GROUP BY i.id ORDER BY i.updated DESC""",
      null
    ).use { c ->
      buildList {
        while (c.moveToNext()) add(IdeaSummary(c.toIdea(), c.getInt(6), c.getInt(7), c.getInt(8), c.getInt(9)))
      }
    }

  private fun android.database.Cursor.toIdea() =
    Idea(getLong(0), getString(1), getString(2), parseTags(getString(3)), getLong(4), getString(5) ?: "")

  private class Helper(context: Context) : SQLiteOpenHelper(context, "research.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(
        """CREATE TABLE ideas(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL,
           body TEXT NOT NULL DEFAULT '', tags TEXT NOT NULL DEFAULT '', created INTEGER, updated INTEGER,
           uid TEXT, author TEXT NOT NULL DEFAULT '')"""
      )
      db.execSQL("CREATE UNIQUE INDEX ideas_uid ON ideas(uid)")
      createNotes(db)
      db.execSQL(
        """CREATE TABLE links(id INTEGER PRIMARY KEY AUTOINCREMENT, idea_id INTEGER NOT NULL, kind TEXT NOT NULL,
           ref TEXT NOT NULL, relation TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', created INTEGER,
           parent_id INTEGER)"""
      )
      db.execSQL("CREATE INDEX links_idea ON links(idea_id)")
      db.execSQL("CREATE INDEX links_target ON links(kind, ref)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
      if (oldVersion < 2) {
        // uids and authors, for sharing
        db.execSQL("ALTER TABLE ideas ADD COLUMN uid TEXT")
        db.execSQL("ALTER TABLE ideas ADD COLUMN author TEXT NOT NULL DEFAULT ''")
        db.rawQuery("SELECT id FROM ideas", null).use { c ->
          while (c.moveToNext()) {
            db.update("ideas", ContentValues().apply { put("uid", UUID.randomUUID().toString()) }, "id = ?", arrayOf(c.getLong(0).toString()))
          }
        }
        db.execSQL("CREATE UNIQUE INDEX ideas_uid ON ideas(uid)")
        createNotes(db)
      }
      if (oldVersion < 3) {
        // links under links
        db.execSQL("ALTER TABLE links ADD COLUMN parent_id INTEGER")
      }
    }

    private fun createNotes(db: SQLiteDatabase) {
      db.execSQL("CREATE TABLE notes(uid TEXT PRIMARY KEY, text TEXT NOT NULL, source TEXT NOT NULL DEFAULT '', created INTEGER)")
    }
  }
}
