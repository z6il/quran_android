package com.quran.labs.androidquran.feature.library

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import org.json.JSONObject
import java.io.Closeable
import java.nio.FloatBuffer
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * OCR with OpenITI's model for printed Arabic script (Benjamin Kiessling, trained on the
 * OpenITI corpus, CC0), the recogniser Kraken uses, exported to ONNX. Tesseract finds the
 * lines; each line is straightened and scaled the way Kraken does it, read by the model, and
 * its letters put back in reading order. Word boxes come from where each letter was read.
 */
class OpenItiOcr(context: Context) : Closeable {
  private val env = OrtEnvironment.getEnvironment()
  private val session: OrtSession = env.createSession(
    OcrModels.file(context, OcrModels.files(TextSource.OPENITI).last()).path,
    OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
  )
  private val labels: List<String> = context.assets.open("openiti_labels.json").bufferedReader().use { r ->
    val a = JSONObject(r.readText()).getJSONArray("labels")
    List(a.length()) { a.getString(it) }
  }

  /** a page drawn at about 300 dpi; its lines found by Tesseract, read by the model */
  fun recognise(api: TessBaseAPI, pdf: PdfDocument, page: Int): List<PdfWord> {
    val bitmap = pdf.render(page, WIDTH)
    try {
      val w = bitmap.width
      val h = bitmap.height
      val grey = grey(bitmap)
      val ink = otsu(grey)
      api.setImage(bitmap)
      val lines = api.textlines.let { pixa ->
        try {
          pixa.boxRects
        } finally {
          pixa.recycle()
        }
      }
      api.clear()
      val words = mutableListOf<PdfWord>()
      // top to bottom; a line is read right to left
      lines.sortedBy { it.top }.forEach { r ->
        if (r.height() < 8 || r.width() < 8) return@forEach
        readLine(grey, ink, w, h, r).forEach { (text, left, right) ->
          words += PdfWord(text, left / w.toFloat(), r.top / h.toFloat(), right / w.toFloat(), r.bottom / h.toFloat())
        }
      }
      return fixCommas(words)
    } finally {
      bitmap.recycle()
    }
  }

  /** one line's words in reading order, each with its left and right on the page */
  private fun readLine(grey: IntArray, threshold: Int, pageW: Int, pageH: Int, box: Rect): List<Triple<String, Float, Float>> {
    // half a line's height either side, as the model saw lines in training
    val margin = box.height() / 2
    val x0 = max(0, box.left - margin)
    val x1 = min(pageW, box.right + margin)
    val y0 = max(0, box.top)
    val y1 = min(pageH, box.bottom)
    val w = x1 - x0
    val h = y1 - y0
    // black on white, as Kraken binarises
    val line = FloatArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
      line[y * w + x] = if (grey[(y0 + y) * pageW + x0 + x] < threshold) 0f else 255f
    }
    val normalised = normalise(line, w, h) ?: return emptyList()
    val input = normalised.pixels
    val width = input.size / HEIGHT
    val probs = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 1, HEIGHT.toLong(), width.toLong())).use { tensor ->
      session.run(mapOf("line" to tensor)).use { result ->
        @Suppress("UNCHECKED_CAST")
        (result[0].value as Array<Array<Array<FloatArray>>>)[0]
      }
    }
    // CTC: the best label in each frame, repeats and blanks dropped; where each letter was read
    val frames = probs[0][0].size
    val letters = StringBuilder()
    val at = mutableListOf<Float>()
    var previous = 0
    for (f in 0 until frames) {
      var best = 0
      var score = Float.NEGATIVE_INFINITY
      for (k in probs.indices) {
        val v = probs[k][0][f]
        if (v > score) {
          score = v
          best = k
        }
      }
      if (best != previous && best != 0) {
        val label = labels.getOrElse(best) { "" }
        // a frame's centre, back on the page: past the padding, unscaled, from the crop's left
        val x = ((f + 0.5f) * width / frames - PAD) / normalised.scale + x0
        label.forEach {
          letters.append(it)
          at += x
        }
      }
      previous = best
    }
    // the model reads left to right: words split there, then each put in reading order
    val step = width.toFloat() / frames / normalised.scale
    val words = mutableListOf<Triple<String, Float, Float>>()
    var i = 0
    while (i < letters.length) {
      while (i < letters.length && letters[i] == ' ') i++
      val start = i
      while (i < letters.length && letters[i] != ' ') i++
      if (i > start) {
        val text = readingOrder(letters.substring(start, i))
        if (text.isNotBlank()) words += Triple(text, at[start] - step, at[i - 1] + step)
      }
    }
    val arabic = words.sumOf { (t, _, _) -> t.count { it in '؀'..'ۿ' } } >= words.sumOf { (t, _, _) -> t.count { it.isLetter() && it !in '؀'..'ۿ' } }
    return if (arabic) words.sortedByDescending { it.second } else words.sortedBy { it.second }
  }

  internal class Normalised(val pixels: FloatArray, val scale: Float)

  override fun close() {
    session.close()
  }

  companion object {
  /**
     * Kraken's centre normalisation: the ink's centre in each column (a blurred peak, smoothed
     * along the line), a band of 1 + 4 mean deviations either side, scaled to the model's height,
     * padded, inverted so ink is 1.
     */
    internal fun normalise(line: FloatArray, w: Int, h: Int): Normalised? {
      val ink = FloatArray(w * h) { (255f - line[it]) / 255f }
      if (ink.none { it > 0f }) return null
      // as Kraken: half a line's height up and down, a whole one along the line
      val smooth = gaussian(ink, w, h, sigmaX = h * 1.0f, sigmaY = h * 0.5f)
      val centre = FloatArray(w)
      for (x in 0 until w) {
        var best = 0
        var score = -1f
        for (y in 0 until h) {
          val v = smooth[y * w + x]
          if (v > score) {
            score = v
            best = y
          }
        }
        centre[x] = best.toFloat()
      }
      val smoothed = gaussian(centre, w, 1, h * 0.3f, 0f)
      val c = IntArray(w) { smoothed[it].roundToInt() }
      var deviation = 0.0
      var count = 0
      for (y in 0 until h) for (x in 0 until w) if (ink[y * w + x] > 0f) {
        deviation += abs(y - c[x])
        count++
      }
      val r = (1 + 4 * (deviation / max(count, 1))).toInt().coerceAtLeast(2)
      val bandH = 2 * r
      val band = FloatArray(w * bandH)
      for (x in 0 until w) for (y in 0 until bandH) {
        val sy = c[x] - r + y
        band[y * w + x] = if (sy in 0 until h) line[sy * w + x] else 255f
      }
      val scale = HEIGHT / bandH.toFloat()
      val sw = max(1, (w * scale).roundToInt())
      val outW = sw + 2 * PAD
      val out = FloatArray(HEIGHT * outW)
      for (y in 0 until HEIGHT) {
        val fy = ((y + 0.5f) / scale - 0.5f).coerceIn(0f, bandH - 1f)
        val y0 = fy.toInt()
        val y1 = min(y0 + 1, bandH - 1)
        val dy = fy - y0
        for (x in 0 until outW) {
          val v = if (x < PAD || x >= PAD + sw) 255f else {
            val fx = ((x - PAD + 0.5f) / scale - 0.5f).coerceIn(0f, w - 1f)
            val x0 = fx.toInt()
            val x1 = min(x0 + 1, w - 1)
            val dx = fx - x0
            val top = band[y0 * w + x0] * (1 - dx) + band[y0 * w + x1] * dx
            val bottom = band[y1 * w + x0] * (1 - dx) + band[y1 * w + x1] * dx
            top * (1 - dy) + bottom * dy
          }
          out[y * outW + x] = 1f - v / 255f
        }
      }
      return Normalised(out, scale)
    }

    private const val WIDTH = 2200
    private const val HEIGHT = 120
    private const val PAD = 16

    private fun grey(bitmap: Bitmap): IntArray {
      val w = bitmap.width
      val h = bitmap.height
      val px = IntArray(w * h)
      bitmap.getPixels(px, 0, w, 0, 0, w, h)
      for (i in px.indices) {
        val p = px[i]
        px[i] = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
      }
      return px
    }

    /** the grey that best splits ink from paper */
    internal fun otsu(grey: IntArray): Int {
      val histogram = IntArray(256)
      grey.forEach { histogram[it.coerceIn(0, 255)]++ }
      val total = grey.size.toDouble()
      var sum = 0.0
      for (i in 0 until 256) sum += i * histogram[i].toDouble()
      var sumB = 0.0
      var weightB = 0.0
      var best = 0.0
      var threshold = 128
      for (t in 0 until 256) {
        weightB += histogram[t]
        if (weightB == 0.0) continue
        val weightF = total - weightB
        if (weightF == 0.0) break
        sumB += t * histogram[t].toDouble()
        val meanB = sumB / weightB
        val meanF = (sum - sumB) / weightF
        val between = weightB * weightF * (meanB - meanF) * (meanB - meanF)
        if (between > best) {
          best = between
          threshold = t
        }
      }
      return threshold + 1
    }

    /** a Gaussian blur as three box blurs along each axis; sigma 0 leaves that axis */
    internal fun gaussian(a: FloatArray, w: Int, h: Int, sigmaX: Float, sigmaY: Float): FloatArray {
      var out = a
      val kx = boxSize(sigmaX)
      val ky = boxSize(sigmaY)
      repeat(3) { if (kx > 1) out = boxX(out, w, h, kx) }
      repeat(3) { if (ky > 1) out = boxY(out, w, h, ky) }
      return out
    }

    private fun boxSize(sigma: Float) = if (sigma <= 0f) 1 else sqrt(12.0 * sigma * sigma / 3 + 1).roundToInt().coerceAtLeast(1)

    private fun boxX(a: FloatArray, w: Int, h: Int, k: Int): FloatArray {
      val out = FloatArray(a.size)
      val half = k / 2
      for (y in 0 until h) {
        var sum = 0f
        val row = y * w
        // a running sum over the window, zero outside the line
        for (x in -half until w + half) {
          val enter = x + half
          if (enter in 0 until w) sum += a[row + enter]
          val leave = x - half - 1
          if (leave in 0 until w) sum -= a[row + leave]
          if (x in 0 until w) out[row + x] = sum / k
        }
      }
      return out
    }

    private fun boxY(a: FloatArray, w: Int, h: Int, k: Int): FloatArray {
      val out = FloatArray(a.size)
      val half = k / 2
      for (x in 0 until w) {
        var sum = 0f
        for (y in -half until h + half) {
          val enter = y + half
          if (enter in 0 until h) sum += a[enter * w + x]
          val leave = y - half - 1
          if (leave in 0 until h) sum -= a[leave * w + x]
          if (y in 0 until h) out[y * w + x] = sum / k
        }
      }
      return out
    }

    private val MIRROR = mapOf('(' to ')', ')' to '(', '[' to ']', ']' to '[', '{' to '}', '}' to '{', '«' to '»', '»' to '«', '<' to '>', '>' to '<')

    /**
     * The model's left-to-right letters in reading order, as Kraken's bidi does for an Arabic
     * line: runs read right to left with brackets turned round; numbers and Latin keep their order.
     */
    internal fun readingOrder(visual: String): String {
      val n = visual.length
      if (n == 0) return visual
      val dir = IntArray(n) { Character.getDirectionality(visual[it]).toInt() }
      fun strongLtr(d: Int) = d == Character.DIRECTIONALITY_LEFT_TO_RIGHT.toInt() ||
        d == Character.DIRECTIONALITY_EUROPEAN_NUMBER.toInt() || d == Character.DIRECTIONALITY_ARABIC_NUMBER.toInt()
      fun joins(d: Int) = d == Character.DIRECTIONALITY_COMMON_NUMBER_SEPARATOR.toInt() ||
        d == Character.DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR.toInt() || d == Character.DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR.toInt()
      val ltr = BooleanArray(n) { strongLtr(dir[it]) }
      // a separator between two numbers belongs to them (3/188, 1.5); a mark goes with its letter
      for (i in 1 until n - 1) if (!ltr[i] && joins(dir[i]) && ltr[i - 1] && ltr[i + 1]) ltr[i] = true
      for (i in 1 until n) if (dir[i] == Character.DIRECTIONALITY_NONSPACING_MARK.toInt()) ltr[i] = ltr[i - 1]
      val runs = mutableListOf<Pair<Boolean, String>>()
      var i = 0
      while (i < n) {
        var j = i
        while (j < n && ltr[j] == ltr[i]) j++
        runs += ltr[i] to visual.substring(i, j)
        i = j
      }
      val out = StringBuilder()
      runs.asReversed().forEach { (isLtr, text) ->
        if (isLtr) out.append(text) else for (k in text.length - 1 downTo 0) out.append(MIRROR[text[k]] ?: text[k])
      }
      return Normalizer.normalize(out, Normalizer.Form.NFC)
    }
  }
}
