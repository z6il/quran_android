package com.quran.labs.androidquran.feature.prayer

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.quran.labs.androidquran.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The adhan of Mohammed bin Marwan Qassas, downloaded once from the fork's releases (like the
 * hadith library) and kept with the app's files, where the alert plays it from.
 */
object AdhanDownload {
  const val URL = "https://github.com/z6il/quran_android/releases/download/adhan-v1/adhan_qassas.mp3"
  /** what [download] answers when the file isn't on the server (yet) */
  const val NOT_PUBLISHED = "not published"

  private fun file(context: Context) = File(File(context.filesDir, "prayer"), "adhan_qassas.mp3")

  fun isDownloaded(context: Context) = file(context).let { it.exists() && it.length() > 0 }

  fun uri(context: Context): Uri = FileProvider.getUriForFile(context, context.getString(R.string.file_authority), file(context))

  /** downloads the adhan, reporting 0..1 as it goes; null when it worked, otherwise why not */
  suspend fun download(context: Context, onProgress: (Float) -> Unit): String? = withContext(Dispatchers.IO) {
    val target = file(context)
    val tmp = File(target.parentFile!!.apply { mkdirs() }, "adhan_qassas.part")
    try {
      var connection = URL(URL).openConnection() as HttpURLConnection
      // GitHub release downloads redirect to another host
      var redirects = 0
      while (connection.responseCode in 300..399 && redirects++ < 5) {
        val next = connection.getHeaderField("Location")
        connection.disconnect()
        connection = URL(next).openConnection() as HttpURLConnection
      }
      if (connection.responseCode == 404) return@withContext NOT_PUBLISHED
      if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
      val total = connection.contentLengthLong.takeIf { it > 0 }
      var read = 0L
      connection.inputStream.use { input ->
        tmp.outputStream().use { output ->
          val buffer = ByteArray(16 * 1024)
          while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
            read += n
            if (total != null) onProgress((read / total.toFloat()).coerceAtMost(1f))
          }
        }
      }
      connection.disconnect()
      if (!tmp.renameTo(target)) error("couldn't save")
      null
    } catch (e: Exception) {
      tmp.delete()
      e.message ?: e.javaClass.simpleName
    }
  }
}
