package com.quran.labs.androidquran.feature.adhkar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationManagerCompat
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.feature.home.AppTab
import com.quran.labs.androidquran.feature.home.WithBottomBar
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import dev.zacsweers.metro.Inject

/** Hisn al-Muslim: the chapter list, a chapter's adhkar with a counter, and reminders. */
class AdhkarActivity : AppCompatActivity() {
  @Inject lateinit var adhkarStore: AdhkarStore

  /** the set being read, or null on the list */
  private var openSet by mutableStateOf<AdhkarSet?>(null)
  private var showReminders by mutableStateOf(false)
  private var notificationsAllowed by mutableStateOf(true)
  private var pendingReminder: Pair<AdhkarSession, AdhkarReminder>? = null

  private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
    notificationsAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
    pendingReminder?.let { (session, reminder) -> saveReminder(session, reminder) }
    pendingReminder = null
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)

    val startChapter = intent.getIntExtra(EXTRA_CHAPTER, -1)
    if (savedInstanceState == null && startChapter > 0) {
      val session = AdhkarSession.entries.getOrElse(intent.getIntExtra(EXTRA_SESSION, 0)) { AdhkarSession.NONE }
      openSet = AdhkarSet(startChapter, session)
    } else if (savedInstanceState != null) {
      val chapter = savedInstanceState.getInt(EXTRA_CHAPTER, -1)
      if (chapter > 0) {
        openSet = AdhkarSet(chapter, AdhkarSession.entries[savedInstanceState.getInt(EXTRA_SESSION, 0)])
      }
    }

    onBackPressedDispatcher.addCallback(this) {
      if (openSet != null) openSet = null else finish()
    }

    val chapters = AdhkarBook.chapters(this)
    setContentView(ComposeView(this).apply {
      setContent {
        QuranTheme {
          val state by adhkarStore.state.collectAsState()
          WithBottomBar(this@AdhkarActivity, AppTab.ADHKAR, showBar = openSet == null) {
          AnimatedContent(
            targetState = openSet,
            transitionSpec = {
              val forward = targetState != null
              (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            label = "adhkarScreen"
          ) { set ->
            val chapter = set?.let { s -> chapters.firstOrNull { it.id == s.chapterId } }
            if (set != null && chapter != null) {
              AdhkarReader(
                chapter = chapter,
                initialSession = if (chapter.id == AdhkarBook.MORNING_EVENING) {
                  set.session.takeIf { it != AdhkarSession.NONE } ?: AdhkarSession.now()
                } else {
                  AdhkarSession.NONE
                },
                onFinish = { done ->
                  adhkarStore.markDone(done)
                  openSet = null
                },
                onBack = { openSet = null }
              )
            } else {
              AdhkarHome(
                state = state,
                chapters = chapters,
                onOpen = { openSet = it },
                onToggleFavorite = { adhkarStore.toggleFavorite(it) },
                onReminders = {
                  notificationsAllowed = NotificationManagerCompat.from(this@AdhkarActivity).areNotificationsEnabled()
                  showReminders = true
                },
                onBack = { finish() }
              )
            }
          }
          }
          if (showReminders) {
            RemindersDialog(
              state = state,
              notificationsAllowed = notificationsAllowed,
              onChange = { session, reminder -> onReminderChanged(session, reminder) },
              onDismiss = { showReminders = false }
            )
          }
        }
      }
    })
  }

  override fun onResume() {
    super.onResume()
    adhkarStore.refresh()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    openSet?.let {
      outState.putInt(EXTRA_CHAPTER, it.chapterId)
      outState.putInt(EXTRA_SESSION, it.session.ordinal)
    }
  }

  private fun onReminderChanged(session: AdhkarSession, reminder: AdhkarReminder) {
    val needsPermission = reminder.enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      !NotificationManagerCompat.from(this).areNotificationsEnabled()
    if (needsPermission) {
      pendingReminder = session to reminder
      askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      saveReminder(session, reminder)
    }
  }

  private fun saveReminder(session: AdhkarSession, reminder: AdhkarReminder) {
    adhkarStore.setReminder(session, reminder)
    AdhkarReminders.schedule(this, session, reminder)
  }

  companion object {
    private const val EXTRA_CHAPTER = "chapter"
    private const val EXTRA_SESSION = "session"

    fun intent(context: Context, chapterId: Int? = null, session: AdhkarSession = AdhkarSession.NONE): Intent =
      Intent(context, AdhkarActivity::class.java).apply {
        if (chapterId != null) {
          putExtra(EXTRA_CHAPTER, chapterId)
          putExtra(EXTRA_SESSION, session.ordinal)
        }
      }
  }
}
