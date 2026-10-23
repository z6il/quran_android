package com.quran.data.page.provider.madani

import com.quran.data.model.audio.Qari
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.pages.common.madani.size.DefaultPageSizeCalculator
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.pages.madani.R
import com.quran.labs.androidquran.common.audio.R as audioR

class MadaniPageProvider : PageProvider {

  override fun getDataSource() = dataSource

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      DefaultPageSizeCalculator(displaySize)

  override fun getImageVersion() = 8

  override fun getImagesBaseUrl() = "$baseUrl/"

  override fun getImagesZipBaseUrl() = "$baseUrl/zips/"

  override fun getPatchBaseUrl() = "$baseUrl/patches/v"

  override fun getAyahInfoBaseUrl() = "$baseUrl/databases/ayahinfo/"

  override fun getAudioDirectoryName() = "audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = getDatabaseDirectoryName()

  override fun getDatabasesBaseUrl() = "$baseUrl/databases/"

  override fun getAudioDatabasesBaseUrl() = getDatabasesBaseUrl() + "audio/"

  override fun getImagesDirectoryName() = ""

  override fun getPreviewTitle() = R.string.madani_title

  override fun getPreviewDescription() = R.string.madani_description

  override fun getDefaultQariId(): Int = 0

  override fun getQaris(): List<Qari> {
    return listOf(
      Qari(
        0,
        audioR.string.qari_minshawi_murattal_gapless,
        url = "https://download.quranicaudio.com/quran/muhammad_siddeeq_al-minshaawee/",
        path = "minshawi_murattal",
        hasGaplessAlternative = false,
        db = "minshawi_murattal"
      ),
      Qari(
        1,
        audioR.string.qari_husary_gapless,
        url = "https://download.quranicaudio.com/quran/mahmood_khaleel_al-husaree/",
        path = "husary",
        hasGaplessAlternative = false,
        db = "husary"
      ),
      Qari(
        2,
        audioR.string.qari_basfar,
        url = "https://mirrors.quranicaudio.com/everyayah/Abdullah_Basfar_192kbps/",
        path = "2",
        hasGaplessAlternative = false,
        db = null
      ),
      gapless(3, audioR.string.qari_afasy_gapless, "mishaari_raashid_al_3afaasee", "mishari_alafasy"),
      gapless(4, audioR.string.qari_muaiqly_gapless, "maher_256", "maher_al_muaiqly"),
      gapless(5, audioR.string.qari_shuraym_gapless, "sa3ood_al-shuraym", "shuraym"),
      gapless(6, audioR.string.qari_shatri_gapless, "abu_bakr_ash-shaatree", "shatri"),
      gapless(7, audioR.string.qari_yasser_dussary_gapless, "yasser_ad-dussary", "yasser_dussary"),
      gapless(8, audioR.string.qari_qatami_gapless, "nasser_bin_ali_alqatami", "qatami"),
      gapless(9, audioR.string.qari_fares_abbad_gapless, "fares", "fares_abbad"),
      gapless(10, audioR.string.qari_hani_rifai_gapless, "rifai", "hani_rifai"),
      gapped(11, audioR.string.qari_abdulbaset, "Abdul_Basit_Murattal_192kbps"),
      gapped(12, audioR.string.qari_abdulbaset_mujawwad, "Abdul_Basit_Mujawwad_128kbps"),
      gapped(13, audioR.string.qari_suadis, "Abdurrahmaan_As-Sudais_192kbps"),
      gapped(14, audioR.string.qari_saad_al_ghamdi, "Ghamadi_40kbps"),
      gapped(15, audioR.string.qari_hudhayfi, "Hudhaify_128kbps"),
      gapped(16, audioR.string.qari_ayyoub, "Muhammad_Ayyoub_128kbps"),
      gapped(17, audioR.string.qari_jibreel, "Muhammad_Jibreel_128kbps"),
      gapped(18, audioR.string.qari_tablawy, "Mohammad_al_Tablaway_128kbps"),
      gapped(19, audioR.string.qari_minshawi_mujawwad, "Minshawy_Mujawwad_192kbps"),
      gapped(20, audioR.string.qari_husary_mujawwad, "Husary_128kbps_Mujawwad")
    )
  }

  // gapless audio files are per sura from quranicaudio; timings come from the
  // gapless database (named [db]) hosted alongside the other audio databases.
  private fun gapless(id: Int, name: Int, quranicAudioPath: String, db: String) =
    Qari(
      id,
      name,
      url = "https://download.quranicaudio.com/quran/$quranicAudioPath/",
      path = db,
      hasGaplessAlternative = false,
      db = db
    )

  // gapped audio files are per ayah from the everyayah mirror.
  private fun gapped(id: Int, name: Int, everyAyahPath: String) =
    Qari(
      id,
      name,
      url = "https://mirrors.quranicaudio.com/everyayah/$everyAyahPath/",
      path = id.toString(),
      hasGaplessAlternative = false,
      db = null
    )

  companion object {
    private const val baseUrl = "https://android.quran.com/data"
    private val dataSource by lazy { MadaniDataSource() }
  }
}
