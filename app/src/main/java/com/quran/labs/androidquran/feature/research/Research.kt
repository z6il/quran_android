package com.quran.labs.androidquran.feature.research

/** How a linked verse, hadith or idea bears on an idea. */
enum class Relation(val key: String) {
  EXPLAINS("explains"), SUPPORTS("supports"), RELATED("related"), CONTRASTS("contrasts");

  companion object {
    fun of(key: String) = entries.firstOrNull { it.key == key } ?: RELATED
  }
}

/** What a link points at. */
sealed interface LinkTarget {
  val kind: String
  val ref: String

  /** a verse or a run of verses in one sura, e.g. 2:255 or 2:255-257 */
  data class Ayah(val sura: Int, val ayah: Int, val endAyah: Int = ayah) : LinkTarget {
    override val kind get() = KIND
    override val ref get() = if (endAyah == ayah) "$sura:$ayah" else "$sura:$ayah-$endAyah"

    companion object {
      const val KIND = "ayah"
      fun parse(ref: String): Ayah? {
        val m = Regex("""(\d+):(\d+)(?:-(\d+))?""").matchEntire(ref.trim()) ?: return null
        val sura = m.groupValues[1].toInt()
        val ayah = m.groupValues[2].toInt()
        val end = m.groupValues[3].toIntOrNull() ?: ayah
        return if (end >= ayah) Ayah(sura, ayah, end) else null
      }
    }
  }

  /** a hadith by collection slug and number, e.g. bukhari:1 */
  data class Hadith(val collection: String, val number: String) : LinkTarget {
    override val kind get() = KIND
    override val ref get() = "$collection:$number"

    companion object {
      const val KIND = "hadith"
      fun parse(ref: String): Hadith? {
        val i = ref.indexOf(':')
        return if (i <= 0 || i == ref.lastIndex) null else Hadith(ref.substring(0, i), ref.substring(i + 1))
      }
    }
  }

  data class Idea(val id: Long) : LinkTarget {
    override val kind get() = KIND
    override val ref get() = id.toString()

    companion object {
      const val KIND = "idea"
    }
  }

  /** a quote or finding from someone's research (a scholar, a book, or the reader's own), kept by uid */
  data class Note(val uid: String) : LinkTarget {
    override val kind get() = KIND
    override val ref get() = uid

    companion object {
      const val KIND = "note"
    }
  }

  companion object {
    fun of(kind: String, ref: String): LinkTarget? = when (kind) {
      Ayah.KIND -> Ayah.parse(ref)
      Hadith.KIND -> Hadith.parse(ref)
      Idea.KIND -> ref.toLongOrNull()?.let(::Idea)
      Note.KIND -> ref.takeIf { it.isNotBlank() }?.let(::Note)
      else -> null
    }
  }
}

data class Idea(
  val id: Long,
  val title: String,
  val body: String,
  val tags: List<String>,
  val updated: Long,
  /** who wrote it, for ideas imported from someone else; blank for the reader's own */
  val author: String = ""
)

data class IdeaLink(
  val id: Long,
  val ideaId: Long,
  val target: LinkTarget,
  val relation: Relation,
  val note: String
)

/** What a note source says, and where it comes from (e.g. "Ibn Kathir, Tafsir 1/250"). */
data class NoteSource(val uid: String, val text: String, val source: String)

/** An idea with how many verses, hadith, other ideas and notes it links to, for lists. */
data class IdeaSummary(val idea: Idea, val ayat: Int, val hadith: Int, val ideas: Int, val notes: Int = 0)

/** "a, b , ,c" -> [a, b, c], keeping the order and dropping repeats */
fun parseTags(text: String): List<String> =
  text.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
