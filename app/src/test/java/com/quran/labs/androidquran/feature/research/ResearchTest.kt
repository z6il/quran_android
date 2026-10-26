package com.quran.labs.androidquran.feature.research

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class ResearchTest {

  @Test
  fun targetsRoundTrip() {
    assertThat(LinkTarget.Ayah.parse("2:255")).isEqualTo(LinkTarget.Ayah(2, 255))
    assertThat(LinkTarget.Ayah.parse("2:255-257")).isEqualTo(LinkTarget.Ayah(2, 255, 257))
    assertThat(LinkTarget.Ayah(2, 255, 257).ref).isEqualTo("2:255-257")
    assertThat(LinkTarget.Ayah.parse("2:257-255")).isNull()
    assertThat(LinkTarget.Hadith.parse("bukhari:1")).isEqualTo(LinkTarget.Hadith("bukhari", "1"))
    assertThat(LinkTarget.of("idea", "12")).isEqualTo(LinkTarget.Idea(12))
    assertThat(LinkTarget.of("other", "x")).isNull()
  }

  @Test
  fun tagsSplitOnEitherComma() {
    assertThat(parseTags("tawhid, sabr ، tawhid,, ")).containsExactly("tawhid", "sabr").inOrder()
  }

  @Test
  fun searchFoldingMatchesTheIndex() {
    // same folding as the script that built the index
    assertThat(HadithLibrary.normalizeArabic("إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ")).isEqualTo("انما الاعمال بالنيات")
    assertThat(HadithLibrary.normalizeArabic("صَلاَةٌ")).isEqualTo("صلاه")
  }

  @Test
  fun hadithSearchUsesTheIndex() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<android.content.Context>()
      // a two-hadith library laid out like the real download
      val file = java.io.File(java.io.File(context.filesDir, "hadith").apply { mkdirs() }, "hadith.db")
      android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
        db.execSQL("CREATE TABLE collections(slug TEXT PRIMARY KEY, name_ar TEXT, name_en TEXT, position INTEGER, count INTEGER)")
        db.execSQL("CREATE TABLE hadith(id INTEGER PRIMARY KEY, collection TEXT, number TEXT, book TEXT, chapter_ar TEXT, text_ar TEXT, matn_ar TEXT, grade TEXT)")
        db.execSQL("CREATE VIRTUAL TABLE hadith_fts USING fts4(content=\"\", norm)")
        db.execSQL("INSERT INTO collections VALUES('bukhari','صحيح البخاري','Sahih al-Bukhari',0,1),('tirmidhi','جامع الترمذي','Jami at-Tirmidhi',1,1)")
        val texts = listOf("إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ", "لاَ تُقْبَلُ صَلاَةٌ بِغَيْرِ طُهُورٍ")
        db.execSQL("INSERT INTO hadith VALUES(1,'bukhari','1','1','',?,?,'')", arrayOf(texts[0], texts[0]))
        db.execSQL("INSERT INTO hadith VALUES(2,'tirmidhi','1','1','',?,'','Sahih (Darussalam)')", arrayOf<Any>(texts[1]))
        texts.forEachIndexed { i, t ->
          db.execSQL("INSERT INTO hadith_fts(docid, norm) VALUES(?, ?)", arrayOf<Any>(i + 1, HadithLibrary.normalizeArabic(t)))
        }
      }
      val library = HadithLibrary(context)
      assertThat(library.collections().map { it.slug }).containsExactly("bukhari", "tirmidhi").inOrder()
      // no diacritics, any word order, and the last word as a prefix
      assertThat(library.search("الاعمال انما").map { it.collection + it.number }).containsExactly("bukhari1")
      assertThat(library.search("صلاه طهو").single().grade).isEqualTo("Sahih (Darussalam)")
      assertThat(library.search("صلاة", collection = "bukhari")).isEmpty()
      // a number alone finds that hadith number, Western or Arabic-Indic digits
      assertThat(library.search("1").map { it.collection }).containsExactly("bukhari", "tirmidhi").inOrder()
      assertThat(library.search("١", collection = "tirmidhi").map { it.collection }).containsExactly("tirmidhi")
      assertThat(library.hadith(LinkTarget.Hadith("tirmidhi", "1"))!!.displayText).contains("طُهُورٍ")
    }
  }

  @Test
  fun ideasAndLinks() {
    runBlocking { ideasAndLinksBody() }
  }

  private suspend fun ideasAndLinksBody() {
    val store = ResearchStore(ApplicationProvider.getApplicationContext())
    val sabr = store.createIdea("Patience", tags = listOf("sabr"))
    val trial = store.createIdea("Trials")
    store.addLink(sabr, LinkTarget.Ayah(2, 153), Relation.SUPPORTS)
    store.addLink(sabr, LinkTarget.Hadith("muslim", "2999"), Relation.EXPLAINS)
    store.addLink(sabr, LinkTarget.Idea(trial), Relation.RELATED)
    // linking the same verse again updates the relation instead of adding a second link
    store.addLink(sabr, LinkTarget.Ayah(2, 153), Relation.EXPLAINS)
    // an idea can't link to itself
    store.addLink(sabr, LinkTarget.Idea(sabr), Relation.RELATED)

    val links = store.links(sabr)
    assertThat(links).hasSize(3)
    assertThat(links.first { it.target == LinkTarget.Ayah(2, 153) }.relation).isEqualTo(Relation.EXPLAINS)
    val summary = store.ideas.value.first { it.idea.id == sabr }
    assertThat(listOf(summary.ayat, summary.hadith, summary.ideas)).containsExactly(1, 1, 1).inOrder()
    assertThat(store.ideasLinking(LinkTarget.Ayah(2, 153)).map { it.id }).containsExactly(sabr)

    // deleting an idea also removes links that pointed at it
    store.deleteIdea(trial)
    assertThat(store.links(sabr).map { it.target }).doesNotContain(LinkTarget.Idea(trial))
    assertThat(store.ideas.value.map { it.idea.id }).containsExactly(sabr)
  }

  @Test
  fun sharingRoundTrips() {
    runBlocking { sharingBody() }
  }

  private suspend fun sharingBody() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val mine = ResearchStore(context)
    val patience = mine.createIdea("Patience", "notes", listOf("sabr"))
    val trials = mine.createIdea("Trials")
    mine.addLink(patience, LinkTarget.Ayah(2, 153), Relation.SUPPORTS)
    mine.addLink(patience, LinkTarget.Idea(trials), Relation.RELATED)
    mine.addNote(patience, "Patience is half of faith.", "Ibn al-Qayyim", Relation.EXPLAINS)
    val comment = mine.links(patience).first { it.target is LinkTarget.Ayah }
    mine.updateLink(comment, Relation.SUPPORTS, "the key verse")

    // sharing one idea brings the idea it links to along
    val bundle = ResearchBundle.parse(mine.export(setOf(patience), "abdo").toJson().toString())!!
    assertThat(bundle.author).isEqualTo("abdo")
    assertThat(bundle.ideas.map { it.title }).containsExactly("Patience", "Trials")
    val shared = bundle.ideas.first { it.title == "Patience" }
    assertThat(shared.author).isEqualTo("abdo")
    assertThat(shared.links.first { it.kind == "ayah" }.note).isEqualTo("the key verse")
    assertThat(shared.links.first { it.kind == "note" }.text).isEqualTo("Patience is half of faith.")

    // into someone else's phone: a fresh database
    context.deleteDatabase("research.db")
    val theirs = ResearchStore(context)
    assertThat(theirs.import(bundle, "friend")).isEqualTo(2)
    val imported = theirs.ideas.value.map { it.idea }
    assertThat(imported.map { it.author }.toSet()).containsExactly("abdo")
    val id = imported.first { it.title == "Patience" }.id
    val links = theirs.links(id)
    assertThat(links).hasSize(3)
    val toTrials = links.first { it.target is LinkTarget.Idea }.target as LinkTarget.Idea
    assertThat(imported.first { it.id == toTrials.id }.title).isEqualTo("Trials")
    val note = links.first { it.target is LinkTarget.Note }.target as LinkTarget.Note
    assertThat(theirs.notes()[note.uid]?.source).isEqualTo("Ibn al-Qayyim")

    // importing the same file again updates instead of adding twice
    theirs.import(bundle, "friend")
    assertThat(theirs.ideas.value).hasSize(2)
    assertThat(theirs.links(id)).hasSize(3)
    assertThat(theirs.notes()).hasSize(1)

    // removing the link drops the note it pointed at
    theirs.removeLink(theirs.links(id).first { it.target is LinkTarget.Note })
    assertThat(theirs.notes()).isEmpty()

    assertThat(ResearchBundle.parse("{\"ideas\": []}")).isNull()
    assertThat(ResearchBundle.parse("not json")).isNull()
  }

  @Test
  fun upgradesTheFirstDatabase() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    context.deleteDatabase("research.db")
    // version 1, as 3.6.4-7 shipped it
    android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("research.db").apply { parentFile?.mkdirs() }, null).use { db ->
      db.execSQL("CREATE TABLE ideas(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, body TEXT NOT NULL DEFAULT '', tags TEXT NOT NULL DEFAULT '', created INTEGER, updated INTEGER)")
      db.execSQL("CREATE TABLE links(id INTEGER PRIMARY KEY AUTOINCREMENT, idea_id INTEGER NOT NULL, kind TEXT NOT NULL, ref TEXT NOT NULL, relation TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', created INTEGER)")
      db.execSQL("INSERT INTO ideas(title, created, updated) VALUES ('Old', 1, 1)")
      db.version = 1
    }
    runBlocking {
      val store = ResearchStore(context)
      store.refresh()
      assertThat(store.ideas.value.map { it.idea.title }).containsExactly("Old")
      val bundle = store.export(null, "abdo")
      assertThat(bundle.ideas.single().uid).isNotEmpty()
      store.addNote(store.ideas.value.single().idea.id, "text", "", Relation.RELATED)
      assertThat(store.notes()).hasSize(1)
    }
  }
}
