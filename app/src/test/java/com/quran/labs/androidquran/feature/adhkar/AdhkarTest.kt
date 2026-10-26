package com.quran.labs.androidquran.feature.adhkar

import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar

// Robolectric for org.json, which the app parses the book with
@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class AdhkarTest {
  private val chapters by lazy {
    // the same file the app ships, read from the source tree
    val file = listOf("src/main/assets/adhkar.json", "app/src/main/assets/adhkar.json").map(::File).first { it.exists() }
    AdhkarBook.parse(file.readText())
  }

  @Test
  fun bundledBookIsComplete() {
    assertThat(chapters).hasSize(132)
    assertThat(chapters.sumOf { it.adhkar.size }).isEqualTo(268)
    assertThat(chapters.all { c -> c.adhkar.all { it.text.isNotBlank() && it.count >= 1 } }).isTrue()
  }

  @Test
  fun morningAndEveningCounts() {
    val morning = chapters.first { it.id == AdhkarBook.MORNING_EVENING }.adhkar.associateBy { it.number }
    assertThat(morning.getValue("91").count).isEqualTo(100) // subhanallahi wa bihamdihi
    assertThat(morning.getValue("80").count).isEqualTo(4)
    assertThat(morning.getValue("83").count).isEqualTo(7)
  }

  @Test
  fun phasedCountsAddUp() {
    val all = chapters.flatMap { it.adhkar }
    val phased = all.filter { it.phases.isNotEmpty() }
    assertThat(phased.map { it.number }).containsExactly("31", "69", "106", "217", "243")
    phased.forEach { assertThat(it.phases.sumOf { p -> p.count }).isEqualTo(it.count) }
    // a statement of reward, not a count to repeat
    assertThat(all.first { it.number == "219" }.count).isEqualTo(1)
  }

  @Test
  fun phaseAtWalksThroughTheParts() {
    val tasbih = chapters.flatMap { it.adhkar }.first { it.number == "106" } // 33, 33, 34
    assertThat(tasbih.phaseAt(0)!!.let { it.first.count to it.second }).isEqualTo(33 to 0)
    assertThat(tasbih.phaseAt(33)!!.second).isEqualTo(0)
    assertThat(tasbih.phaseAt(70)!!.let { it.first.count to it.second }).isEqualTo(34 to 4)
    assertThat(tasbih.phaseAt(100)).isNull()
  }

  @Test
  fun setKeysTellMorningFromEvening() {
    assertThat(AdhkarSet(27, AdhkarSession.MORNING).key).isEqualTo("27m")
    assertThat(AdhkarSet(27, AdhkarSession.EVENING).key).isEqualTo("27e")
    assertThat(AdhkarSet(28).key).isEqualTo("28")
    assertThat(AdhkarSession.forHour(6)).isEqualTo(AdhkarSession.MORNING)
    assertThat(AdhkarSession.forHour(17)).isEqualTo(AdhkarSession.EVENING)
  }

  @Test
  fun reminderIsTodayIfStillAheadElseTomorrow() {
    val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 26, 6, 0, 0); set(Calendar.MILLISECOND, 0) }
    assertThat(AdhkarReminders.millisUntil(6 * 60 + 30, now)).isEqualTo(30 * 60_000L)
    assertThat(AdhkarReminders.millisUntil(6 * 60, now)).isEqualTo(24 * 3_600_000L)
    assertThat(AdhkarReminders.millisUntil(5 * 60, now)).isEqualTo(23 * 3_600_000L)
  }
}
