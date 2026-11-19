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
