package com.quran.labs.androidquran.feature.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.feature.research.arabicFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Books' covers: an EPUB's own, a PDF's first page, or a picture the reader chose, kept small
 * in the app. A book without one gets a cover drawn for it.
 */
object BookCovers {
  private const val WIDTH = 420

  fun file(context: Context, book: String) = File(context.filesDir, "library/covers/" + book.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".jpg")

  fun save(context: Context, book: String, bitmap: Bitmap) {
    val target = file(context, book)
    target.parentFile!!.mkdirs()
    val scaled = if (bitmap.width > WIDTH) Bitmap.createScaledBitmap(bitmap, WIDTH, bitmap.height * WIDTH / bitmap.width, true) else bitmap
    target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
  }

  /** an EPUB's cover, or a PDF's first page */
  fun fromBook(context: Context, book: LibraryBook) {
    runCatching {
      when (book.kind) {
        BookKind.EPUB -> EpubBook.open(File(book.file)).use { epub ->
          val path = epub.cover ?: return
          epub.open(path)?.use { input -> decode(input.readBytes())?.let { save(context, book.id, it) } }
        }
        BookKind.PDF -> fromPdfPage(context, book, 0)
        BookKind.TEXT -> Unit
      }
    }
  }

  fun fromPdfPage(context: Context, book: LibraryBook, page: Int) {
    runCatching { PdfDocument(File(book.file)).use { pdf -> save(context, book.id, pdf.render(page.coerceIn(0, pdf.pageCount - 1), WIDTH)) } }
  }

  /** a picture the reader picked */
  fun fromPicture(context: Context, book: String, uri: Uri): Boolean = runCatching {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
    val bitmap = decode(bytes) ?: return false
    save(context, book, bitmap)
    true
  }.getOrDefault(false)

  fun remove(context: Context, book: String) {
    file(context, book).delete()
  }

  private fun decode(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= WIDTH) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
  }
}

/** a cover's colour: a deep shade, the same for a book each time */
private val SHADES = listOf(0xFF1E4D3A, 0xFF6B1E2E, 0xFF1F3A5F, 0xFF5B3A1E, 0xFF1D4E57, 0xFF4A2545, 0xFF2F3E1E, 0xFF3B2A4F)
private val GOLD = Color(0xFFE2C27A)

private fun shadeOf(seed: String, shelf: Shelf?): Color =
  Color(SHADES[((shelf?.ordinal ?: 0) * 3 + seed.hashCode().mod(3) + seed.length) % SHADES.size])

/**
 * A book's cover at [width]: its picture, or one drawn for it, deep coloured with a gold frame,
 * eight-pointed stars in the corners and its title in the middle.
 */
@Composable
internal fun BookCover(id: String, title: String, author: String, shelf: Shelf?, width: Dp, modifier: Modifier = Modifier, version: Int = 0) {
  val context = LocalContext.current
  val picture by produceState<ImageBitmap?>(null, id, version) {
    value = withContext(Dispatchers.IO) {
      BookCovers.file(context, id).takeIf { it.exists() }?.let { f -> BitmapFactory.decodeFile(f.path)?.asImageBitmap() }
    }
  }
  Surface(shape = RoundedCornerShape(6.dp), shadowElevation = 3.dp, modifier = modifier.padding(0.dp)) {
    Box(Modifier.fillMaxWidth().aspectRatio(0.68f)) {
      val p = picture
      if (p != null) {
        Image(p, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
      } else {
        val shade = shadeOf(id, shelf)
        Canvas(Modifier.fillMaxSize()) { drawCover(shade) }
        Column(Modifier.fillMaxSize().padding(width * 0.14f), horizontalAlignment = Alignment.CenterHorizontally) {
          Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
              title,
              style = TextStyle(
                fontFamily = arabicFont(), color = GOLD, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                fontSize = (width.value / 7.2f).sp, lineHeight = (width.value / 4.6f).sp
              ),
              maxLines = 4, overflow = TextOverflow.Ellipsis
            )
          }
          if (author.isNotBlank() && width >= 80.dp) {
            Text(
              author,
              style = TextStyle(fontFamily = arabicFont(), color = GOLD.copy(alpha = 0.8f), textAlign = TextAlign.Center, fontSize = (width.value / 13f).sp),
              maxLines = 1, overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }
}

private fun DrawScope.drawCover(shade: Color) {
  drawRect(shade)
  // a spine's shadow down the right, where an Arabic book is bound
  drawRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset(size.width * 0.93f, 0f), size = Size(size.width * 0.07f, size.height))
  val m = size.width * 0.07f
  val stroke = size.width * 0.012f
  drawRect(GOLD, topLeft = Offset(m, m), size = Size(size.width - 2 * m, size.height - 2 * m), style = Stroke(stroke))
  val m2 = m + size.width * 0.035f
  drawRect(GOLD.copy(alpha = 0.6f), topLeft = Offset(m2, m2), size = Size(size.width - 2 * m2, size.height - 2 * m2), style = Stroke(stroke * 0.6f))
  val r = size.width * 0.05f
  listOf(Offset(m2, m2), Offset(size.width - m2, m2), Offset(m2, size.height - m2), Offset(size.width - m2, size.height - m2)).forEach { star(it, r) }
  star(Offset(size.width / 2, size.height * 0.16f), r * 1.4f)
}

/** an eight-pointed star: two squares, one turned */
private fun DrawScope.star(centre: Offset, r: Float) {
  for (turn in 0..1) {
    val path = Path()
    for (k in 0..3) {
      val a = Math.toRadians(45.0 * turn + 90.0 * k)
      val p = Offset(centre.x + (r * cos(a)).toFloat(), centre.y + (r * sin(a)).toFloat())
      if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, GOLD)
  }
}
