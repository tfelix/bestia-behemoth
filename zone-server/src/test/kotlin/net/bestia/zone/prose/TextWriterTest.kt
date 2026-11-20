package net.bestia.zone.prose

import net.bestia.zone.text.TextBrief
import net.bestia.zone.text.TextBrief.AllowedName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextWriterTest {

  private val writer = TextWriter(ProseValidator(), ProseConfig(maxAttempts = 3))

  private val brief = TextBrief(
    task = "Write the epitaph of Orwen.",
    allowedNames = listOf(AllowedName("Orwen", "a smith, 712-780")),
    facts = listOf("born: 712", "died: 780"),
    mustName = listOf("Orwen"),
  )

  @Test
  fun `a text that passes at once is taken`() {
    val llm = ScriptedLlm("\"Here lies Orwen, 712-780.\"")

    val written = writer.write(brief, llm)

    assertEquals("Here lies Orwen, 712-780." to 1, written.body to written.attempts)
    assertTrue("ALLOWED NAMES" in llm.asked.single() && "- Orwen (a smith, 712-780)" in llm.asked.single())
  }

  @Test
  fun `a rejected text is asked for again with what was wrong, and never shown back to the model`() {
    val llm = ScriptedLlm("Here lies Orwen of Eldoria, 712-780.", "Here lies Orwen, 712-780.")

    val written = writer.write(brief, llm)

    assertEquals("Here lies Orwen, 712-780." to 2, written.body to written.attempts)
    val retry = llm.asked[1]
    assertTrue("AVOID" in retry && "\"Orwen of Eldoria\" is not a name" in retry, retry)
    assertFalse("Here lies Orwen of Eldoria" in retry, "the model copies a text it is shown")
  }

  @Test
  fun `German is written from the same brief, with the name as German writes it`() {
    val weaver = AllowedName("the Unseen Weaver", "a god", inLocale = mapOf("de" to "der Unsichtbare Weber"))
    val llm = ScriptedLlm("Hier ruht Orwen, 712-780, Schmied im Dienst des Unsichtbaren Webers.")

    val written = writer.write(brief.copy(allowedNames = brief.allowedNames + weaver), llm, locale = "de")

    assertEquals(1, written.attempts, "the nouns in capitals are no made-up names")
    val asked = llm.asked.single()
    assertTrue("{town}" !in asked, "an example placeholder ends up in a text that has none")
    assertTrue("Write the text in German" in asked && "- der Unsichtbare Weber (the facts call it \"the Unseen Weaver\"; a god)" in asked, asked)
  }

  @Test
  fun `a German text that lost its lead is asked for again`() {
    val llm = ScriptedLlm("Hier ruht ein Schmied.", "Hier ruht Orwen.")

    val written = writer.write(brief, llm, locale = "de")

    assertEquals("Hier ruht Orwen." to 2, written.body to written.attempts)
  }

  @Test
  fun `braces around a real name are taken off, and the placeholder rule is only said for a placeholder`() {
    val llm = ScriptedLlm("Here lies {Orwen}, 712-780.")

    val written = writer.write(brief, llm)

    assertEquals("Here lies Orwen, 712-780." to 1, written.body to written.attempts)
    assertFalse("{town}" in ProsePrompt.system(brief))
    val withPlaceholder = brief.copy(placeholders = listOf(TextBrief.Placeholder("town", "the town to go to")))
    assertTrue("5. PLACEHOLDERS" in ProsePrompt.system(withPlaceholder) && "6. OUTPUT FORMAT" in ProsePrompt.system(withPlaceholder))
  }

  @Test
  fun `a text the model never gets right is given up`() {
    val llm = ScriptedLlm("Eldoria.", "Eldoria.", "Eldoria.")

    val written = writer.write(brief, llm)

    assertNull(written.body)
    assertEquals(3, llm.asked.size)
  }
}

/** A model that answers with what the test wrote for it, in order. */
class ScriptedLlm(private vararg val answers: String) : LlmClient {

  val asked = ArrayList<String>()

  override val model = "scripted"

  override fun chat(system: String?, user: String, sampling: LlmClient.Sampling): String {
    asked += user

    return answers[minOf(asked.size, answers.size) - 1]
  }
}
