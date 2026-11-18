package net.bestia.zone.text

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.text.persistence.WorldText
import net.bestia.zone.text.persistence.WorldTextRepository
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorldTextsTest {

  private val repository = mockk<WorldTextRepository> {
    every { findById(any()) } returns Optional.empty()
  }
  private val texts = WorldTexts(repository)

  @Test
  fun `a written body is served in the language asked for`() {
    stored("de", "Hier ruht Orwen.")
    stored("en", "Here lies Orwen.")

    assertEquals(WorldTexts.Body("de", "Hier ruht Orwen."), texts.bodyOf(TEXT_ID, "de"))
  }

  @Test
  fun `a language that is not written yet falls back to English`() {
    stored("de", body = null, state = WorldText.State.WAITING)
    stored("en", "Here lies Orwen.")

    assertEquals(WorldTexts.Body("en", "Here lies Orwen."), texts.bodyOf(TEXT_ID, "de"))
  }

  @Test
  fun `a rejected text has no body`() {
    stored("en", "Here lies the wrong name.", WorldText.State.REJECTED)

    assertNull(texts.bodyOf(TEXT_ID, "en"))
  }

  private fun stored(locale: String, body: String?, state: WorldText.State = WorldText.State.WRITTEN) {
    val key = WorldText.Key(TEXT_ID, locale)
    every { repository.findById(key) } returns Optional.of(WorldText(key, body, state))
  }

  private companion object {
    const val TEXT_ID = "0123456789abcdef0123456789abcdef"
  }
}
