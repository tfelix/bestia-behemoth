package net.bestia.zone.text

import net.bestia.zone.text.persistence.WorldText
import net.bestia.zone.text.persistence.WorldTextRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

/** The written bodies behind a [TextRef.textId], and the queue of the ones still wanted. */
@Service
class WorldTexts(private val repository: WorldTextRepository) {

  /** [locale] is the language of [text], which is not always the one asked for. */
  data class Body(val locale: String, val text: String)

  /** The text id of every subject with an English body. Read on the tick, written by [refreshWritten]. */
  private val writtenIdBySubject = ConcurrentHashMap<String, String>()

  /** The body in [locale], or the English one while that language is not written. */
  @Transactional(readOnly = true)
  fun bodyOf(textId: String, locale: String): Body? {
    return written(textId, locale) ?: written(textId, FALLBACK_LOCALE)
  }

  /**
   * [template] with the id of the text written about [subject], when there is one. A client then shows the
   * template at once and the written text when it has loaded it. Safe on the tick.
   */
  fun attach(template: TextRef, subject: String): TextRef {
    val textId = writtenIdBySubject[subject] ?: return template

    return template.copy(textId = textId)
  }

  /** Makes a waiting row for every wanted text that has none, in each of [locales]. @return the rows made. */
  @Transactional
  fun request(wanted: List<TextSource.Wanted>, locales: List<String>): Int {
    val known = repository.findAll().groupBy { it.subject }
    val missing = wanted.flatMap { want ->
      val rows = known[want.subject].orEmpty()
      // One id for a subject in every language, so a client asks for "this text" and names its language.
      val textId = rows.firstOrNull()?.id?.textId ?: newTextId()
      locales.filter { locale -> rows.none { it.id.locale == locale } }
        .map { locale -> WorldText(WorldText.Key(textId, locale), want.subject, want.priority) }
    }
    repository.saveAll(missing)

    return missing.size
  }

  /** The waiting text of [locale] to write next, or null when none is left. */
  @Transactional(readOnly = true)
  fun nextWaiting(locale: String): WorldText? {
    return repository.findFirstByIdLocaleAndStateOrderByPriorityAscSubjectAsc(locale, WorldText.State.WAITING)
  }

  @Transactional(readOnly = true)
  fun waiting(locale: String): Long {
    return repository.countByIdLocaleAndState(locale, WorldText.State.WAITING)
  }

  /** Stores what the job made of a waiting text: a [body], or none when the model did not get it right. */
  @Transactional
  fun finish(key: WorldText.Key, body: String?, model: String, attempts: Int) {
    val row = repository.findById(key).getOrNull() ?: return
    row.body = body
    row.state = if (body == null) WorldText.State.REJECTED else WorldText.State.WRITTEN
    row.model = model
    row.attempts = attempts
  }

  /** Reads again which subjects have a written text. Called at boot and when the job has written. */
  @Transactional(readOnly = true)
  fun refreshWritten() {
    val written = repository.findAllByIdLocaleAndState(FALLBACK_LOCALE, WorldText.State.WRITTEN)
      .associate { it.subject to it.id.textId }
    writtenIdBySubject.keys.retainAll(written.keys)
    writtenIdBySubject.putAll(written)
  }

  private fun written(textId: String, locale: String): Body? {
    val row = repository.findById(WorldText.Key(textId, locale)).getOrNull() ?: return null
    val body = row.body?.takeIf { row.state == WorldText.State.WRITTEN } ?: return null

    return Body(locale, body)
  }

  private fun newTextId(): String {
    return UUID.randomUUID().toString().replace("-", "")
  }

  companion object {
    const val FALLBACK_LOCALE = "en"

    private val TEXT_ID = Regex("^[0-9a-f]{32}$")
    private val LOCALE = Regex("^[a-z]{2}$")

    fun isTextId(candidate: String): Boolean {
      return TEXT_ID.matches(candidate)
    }

    fun isLocale(candidate: String): Boolean {
      return LOCALE.matches(candidate)
    }
  }
}
