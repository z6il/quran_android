package com.quran.labs.androidquran.feature.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** a newer build on the fork's releases page */
data class AppRelease(val build: Int, val name: String, val notes: String, val apkUrl: String, val size: Long)

/**
 * Updates from the fork's GitHub releases: asks for the latest release, compares its build
 * number ("v3.6.4-z6il.18" -> 18) with this one, downloads its APK and hands it to Android's
 * installer, which asks the reader to confirm.
 */
object AppUpdater {
  private const val LATEST = "https://api.github.com/repos/z6il/quran_android/releases/latest"
  private val BUILD = Regex("z6il\\.(\\d+)")
  /** automatic checks at most this often; GitHub allows 60 unauthenticated calls an hour */
  private const val CHECK_EVERY = 6 * 60 * 60 * 1000L
  private const val PREFS = "app_update"
  private const val LAST_CHECK = "last_check"
  private const val DISMISSED = "dismissed_build"

  val currentBuild: Int get() = BuildConfig.FORK_BUILD

  private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun dueForCheck(context: Context): Boolean =
    System.currentTimeMillis() - prefs(context).getLong(LAST_CHECK, 0L) > CHECK_EVERY

  /** "Later" on this build: don't offer it again on its own, only when asked */
  fun dismiss(context: Context, build: Int) = prefs(context).edit().putInt(DISMISSED, build).apply()

  fun wasDismissed(context: Context, build: Int) = prefs(context).getInt(DISMISSED, 0) >= build

  /** the latest release if it's newer than this app; null when up to date. Throws when offline. */
  suspend fun check(context: Context): AppRelease? = withContext(Dispatchers.IO) {
    val connection = URL(LATEST).openConnection() as HttpURLConnection
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    connection.connectTimeout = 15_000
    connection.readTimeout = 15_000
    try {
      if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
      val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
      prefs(context).edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply()
      val tag = json.optString("tag_name")
      val build = BUILD.find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return@withContext null
      if (build <= currentBuild) return@withContext null
      val assets = json.optJSONArray("assets") ?: return@withContext null
      val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
        .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@withContext null
      AppRelease(
        build = build,
        name = json.optString("name").ifBlank { tag },
        notes = json.optString("body"),
        apkUrl = apk.getString("browser_download_url"),
        size = apk.optLong("size")
      )
    } finally {
      connection.disconnect()
    }
  }

  /** downloads the APK into the cache, reporting 0..1; returns the file */
  suspend fun download(context: Context, release: AppRelease, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "update").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "Quran-z6il-${release.build}.apk")
    var connection = URL(release.apkUrl).openConnection() as HttpURLConnection
    // GitHub release downloads redirect to another host
    var redirects = 0
    while (connection.responseCode in 300..399 && redirects++ < 5) {
      val next = connection.getHeaderField("Location")
      connection.disconnect()
      connection = URL(next).openConnection() as HttpURLConnection
    }
    if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
    val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.size
    var read = 0L
    connection.inputStream.use { input ->
      file.outputStream().use { output ->
        val buffer = ByteArray(32 * 1024)
        while (true) {
          val n = input.read(buffer)
          if (n < 0) break
          output.write(buffer, 0, n)
          read += n
          if (total > 0) onProgress((read / total.toFloat()).coerceAtMost(1f))
        }
      }
    }
    connection.disconnect()
    if (release.size > 0 && file.length() != release.size) {
      file.delete()
      error("incomplete download")
    }
    file
  }

  /** whether Android lets this app open the installer; if not, [askPermission] first */
  fun canInstall(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

  fun askPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    }
  }

  /** opens Android's installer on the downloaded APK; the reader confirms there */
  fun install(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, context.getString(R.string.file_authority), apk)
    val intent = Intent(Intent.ACTION_VIEW)
      .setDataAndType(uri, "application/vnd.android.package-archive")
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  }
}
