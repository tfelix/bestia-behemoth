package net.bestia.zone.prose

import net.bestia.zone.text.TextBrief
import net.bestia.zone.text.TextBrief.AllowedName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextTranslatorTest {

  private val translator = TextTranslator(ProseValidator(), ProseConfig(maxAttempts = 2))

  private val brief = TextBrief(
    task = "Write the epitaph of Bayan the Patient.",
    allowedNames = listOf(
      AllowedName("Bayan the Patient", "a smith", inLocale = mapOf("de" to "Bayan der Geduldige")),
      AllowedName("Silberscar", "a town"),
      AllowedName("Grimhold", "a town that this text does not name"),
    ),
    facts = listOf("born: 671", "died: 736"),
  )
  private val english = "Bayan the Patient, born in 671, passed in 736.\nA smith from Silberscar."

  @Test
  fun `the model is told how the other language writes the names of this text`() {
    val llm = ScriptedLlm("Bayan der Geduldige, geboren 671, gestorben 736.\nEin Schmied aus Silberscar.")

    val written = translator.translate(english, brief, "de", llm)

    assertEquals(1, written.attempts)
    assertTrue("Bayan the Patient = Bayan der Geduldige" in llm.asked.single(), llm.asked.single())
    assertFalse("Grimhold" in llm.asked.single(), "a name the text does not use is no business of its translation")
  }

  @Test
  fun `a name bent by grammar is still the name`() {
    val llm = ScriptedLlm("Hier ruht der Schmied aus Silberscar, 671 bis 736. Man gedenkt Bayans des Geduldigen.")

    assertEquals(1, translator.translate(english, brief, "de", llm).attempts)
  }

  @Test
  fun `a translation that loses a name or a year is asked for again, and then given up`() {
    val lostTown = ScriptedLlm("Bayan der Geduldige, 671 bis 736. Ein Schmied.", "Bayan der Geduldige, 671 bis 736, aus Silberscar.")
    assertEquals(2, translator.translate(english, brief, "de", lostTown).attempts)

    val wrongYear = ScriptedLlm("Bayan der Geduldige, 671 bis 763, aus Silberscar.")
    assertNull(translator.translate(english, brief, "de", wrongYear).body)
  }

  @Test
  fun `only a language with a prompt of its own can be translated to`() {
    assertTrue(translator.canTranslateTo("de"))
    assertFalse(translator.canTranslateTo("fr"))
  }
}
