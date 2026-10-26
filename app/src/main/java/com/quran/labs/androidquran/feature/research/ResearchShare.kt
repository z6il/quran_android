package com.quran.labs.androidquran.feature.research

import org.json.JSONArray
import org.json.JSONObject

/**
 * Ideas packed into a file to send to someone else. Ideas are known by a uid that stays the
 * same on every phone, so importing a newer copy of a shared idea updates it instead of
 * adding it twice, and links between ideas point at uids.
 */
data class ResearchBundle(
  /** who shared it */
  val author: String,
  val exported: Long,
  val ideas: List<SharedIdea>
) {
  fun toJson(): JSONObject = JSONObject()
    .put("format", FORMAT)
    .put("version", 1)
    .put("author", author)
    .put("exported", exported)
    .put("ideas", JSONArray(ideas.map { it.toJson() }))

  companion object {
    const val FORMAT = "z6il-quran-research"

    /** null when [text] isn't a research file */
    fun parse(text: String): ResearchBundle? = runCatching {
      val o = JSONObject(text)
      if (o.optString("format") != FORMAT) return null
      val ideas = o.getJSONArray("ideas")
      ResearchBundle(
        author = o.optString("author"),
        exported = o.optLong("exported"),
        ideas = (0 until ideas.length()).mapNotNull { SharedIdea.parse(ideas.getJSONObject(it)) }
      )
    }.getOrNull()
  }
}

data class SharedIdea(
  val uid: String,
  val title: String,
  val body: String,
  val tags: List<String>,
  /** who wrote the idea, which can differ from who shared the file */
  val author: String,
  val links: List<SharedLink>
) {
  fun toJson(): JSONObject = JSONObject()
    .put("uid", uid).put("title", title).put("body", body)
    .put("tags", JSONArray(tags)).put("author", author)
    .put("links", JSONArray(links.map { it.toJson() }))

  companion object {
    fun parse(o: JSONObject): SharedIdea? {
      val uid = o.optString("uid").takeIf { it.isNotBlank() } ?: return null
      val tags = o.optJSONArray("tags")
      val links = o.optJSONArray("links")
      return SharedIdea(
        uid = uid,
        title = o.optString("title"),
        body = o.optString("body"),
        tags = if (tags == null) emptyList() else (0 until tags.length()).map { tags.getString(it) },
        author = o.optString("author"),
        links = if (links == null) emptyList() else (0 until links.length()).mapNotNull { SharedLink.parse(links.getJSONObject(it)) }
      )
    }
  }
}

/**
 * a link as shared: for another idea, [ref] is that idea's uid; a note source carries its
 * [text] and [source] with it
 */
data class SharedLink(
  val kind: String,
  val ref: String,
  val relation: Relation,
  val note: String,
  val text: String = "",
  val source: String = ""
) {
  fun toJson(): JSONObject = JSONObject().put("kind", kind).put("ref", ref).put("relation", relation.key).put("note", note)
    .apply { if (kind == LinkTarget.Note.KIND) put("text", text).put("source", source) }

  companion object {
    fun parse(o: JSONObject): SharedLink? {
      val kind = o.optString("kind")
      val ref = o.optString("ref")
      // verses and hadith must be valid references; ideas are checked against the file on import
      if (kind != LinkTarget.Idea.KIND && LinkTarget.of(kind, ref) == null) return null
      if (ref.isBlank()) return null
      val text = o.optString("text")
      if (kind == LinkTarget.Note.KIND && text.isBlank()) return null
      return SharedLink(kind, ref, Relation.of(o.optString("relation")), o.optString("note"), text, o.optString("source"))
    }
  }
}
