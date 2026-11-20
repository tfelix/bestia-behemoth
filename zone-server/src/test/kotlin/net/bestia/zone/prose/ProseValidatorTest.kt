package net.bestia.zone.prose

import net.bestia.zone.prose.ProseValidator.Type
import net.bestia.zone.text.TextBrief
import net.bestia.zone.text.TextBrief.AllowedName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProseValidatorTest {

  private val validator = ProseValidator()

  private val brief = TextBrief(
    task = "Write the epitaph carved on the grave of Temujin the Counter.",
    allowedNames = listOf(
      AllowedName("Temujin the Counter", "scholar of the Saryhorde, a man, 141-206"),
      AllowedName("Saryhorde", "a people"),
      AllowedName("Grimhold", "a town"),
    ),
    facts = listOf("born: 141", "died: 206", "141: Temujin the Counter rises as a scholar of the Saryhorde at Grimhold"),
    mustName = listOf("Temujin the Counter"),
  )

  @Test
  fun `a text that keeps to its brief passes`() {
    val text = """
      Here lies Temujin the Counter.
      Born 141, gone 206.
      Scholar of the Saryhorde, who rose at Grimhold.
      O traveller, count thy days as he did.
    """.trimIndent()

    assertEquals(emptyList(), problemsOf(text))
  }

  @Test
  fun `an invented name, a title and a number from nowhere are found`() {
    assertEquals(listOf(Type.NAME to "Eldoria"), problemsOf("Temujin the Counter came from Eldoria."))
    assertEquals(listOf(Type.TITLE to "King Temujin the Counter"), problemsOf("Here rests King Temujin the Counter."))
    assertEquals(listOf(Type.NUMBER to "952"), problemsOf("Temujin the Counter died in 952."))
  }

  @Test
  fun `a number in words is checked like digits, a small count is style`() {
    assertEquals(listOf(Type.NUMBER to "four hundred and eight"), problemsOf("Temujin the Counter saw four hundred and eight winters."))
    assertEquals(emptyList(), problemsOf("Temujin the Counter served two peoples and was born in 141."))
  }

  @Test
  fun `wrong letter case and a sentence without a capital are found`() {
    assertTrue(Type.CASE to "saryhorde" in problemsOf("Temujin the Counter of the saryhorde rests here."))
    assertTrue(Type.LOWERCASE to "he" in problemsOf("Temujin the Counter rests here. he was wise."))
  }

  @Test
  fun `the lead must be named`() {
    assertEquals(listOf(Type.MISSING to "Temujin the Counter"), problemsOf("A scholar of the Saryhorde rests here."))
  }

  @Test
  fun `two allowed names joined by of pass, and a common word may start a sentence`() {
    assertEquals(emptyList(), problemsOf("Beneath this stone lies Temujin the Counter of Grimhold. Mourned by the Saryhorde."))
  }

  @Test
  fun `the feedback names each problem for the next try`() {
    val feedback = validator.feedback(validator.check("Here rests King Temujin the Counter, who died in Eldoria in 952.", brief))

    assertTrue("\"Eldoria\" is not a name in the fact sheet" in feedback, feedback)
    assertTrue("The number 952 is not in the fact sheet" in feedback, feedback)
    assertTrue("Do not add the title" in feedback, feedback)
  }

  private fun problemsOf(text: String): List<Pair<Type, String>> {
    return validator.check(text, brief).map { it.type to it.value }
  }

  @Test
  fun `a text must hold the places its brief leaves open, and no others`() {
    val open = TextBrief(
      task = "Ask for news to be carried.", allowedNames = emptyList(), facts = emptyList(),
      placeholders = listOf(TextBrief.Placeholder("town", "the name of a town")),
    )

    assertEquals(emptyList(), validator.check("Will you carry the word to {town}?", open).map { it.value })
    assertEquals(listOf("{town}"), validator.check("Will you carry the word?", open).map { it.value })
    assertEquals(listOf("{name}"), validator.check("Tell {name} in {town}.", open).map { it.value })
  }

  @Test
  fun `a German text passes with its nouns in capitals and a name bent by grammar`() {
    val text = "Hier ruht Temujin der Zähler, Gelehrter der Saryhorde. Geboren 141, gestorben 206 in Grimholds Mauern."

    assertEquals(emptyList(), validator.checkIn("de", text, germanBrief).map { it.type to it.value })
  }

  @Test
  fun `a German text is rejected for a number, a placeholder or a lead the brief does not have`() {
    val text = "Hier ruht ein Gelehrter der Saryhorde aus {town}. Er starb 207."

    val problems = validator.checkIn("de", text, germanBrief).map { it.type to it.value }

    assertEquals(listOf(Type.NUMBER to "207", Type.MISSING to "Temujin der Zähler", Type.PLACEHOLDER to "{town}"), problems)
  }

  private val germanBrief = brief.copy(
    allowedNames = brief.allowedNames.map { name ->
      if (name.name == "Temujin the Counter") name.copy(inLocale = mapOf("de" to "Temujin der Zähler")) else name
    },
  )
}
