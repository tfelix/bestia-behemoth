package net.bestia.zone.text

import net.bestia.zone.text.persistence.WorldText
import net.bestia.zone.text.persistence.WorldTextRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

/** The written bodies behind a [TextRef.textId]. */
@Service
class WorldTexts(private val repository: WorldTextRepository) {

  /** [locale] is the language of [text], which is not always the one asked for. */
  data class Body(val locale: String, val text: String)

  /** The body in [locale], or the English one while that language is not written. */
  @Transactional(readOnly = true)
  fun bodyOf(textId: String, locale: String): Body? {
    return written(textId, locale) ?: written(textId, FALLBACK_LOCALE)
  }

  private fun written(textId: String, locale: String): Body? {
    val row = repository.findById(WorldText.Key(textId, locale)).getOrNull() ?: return null
    val body = row.body?.takeIf { row.state == WorldText.State.WRITTEN } ?: return null

    return Body(locale, body)
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
