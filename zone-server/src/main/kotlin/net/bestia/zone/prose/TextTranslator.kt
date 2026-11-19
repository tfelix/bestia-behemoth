package net.bestia.zone.prose

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.text.TextBrief
import org.springframework.stereotype.Component

/**
 * Has the same model put an English text into another language, and takes the result only when the names and
 * the numbers survived. A translation engine was faster and got the words of a lore text wrong.
 */
@Component
class TextTranslator(
  private val validator: ProseValidator,
  private val config: ProseConfig,
) {

  fun canTranslateTo(locale: String): Boolean {
    return locale in PROMPTS
  }

  /** @param brief what the English text was written from: its names say what must survive, and how [locale] writes them */
  fun translate(english: String, brief: TextBrief, locale: String, llm: LlmClient): TextWriter.Written {
    val prompt = PROMPTS.getValue(locale)
    val glossary = brief.allowedNames
      .filter { it.name in english }
      .mapNotNull { name -> name.inLocale[locale]?.let { name.name to it } }

    repeat(config.maxAttempts) { attempt ->
      val text = llm.chat(system = null, prompt.userMessage(english, glossary), TRANSLATION_SAMPLING).trim().removeSurrounding("\"").trim()
      val lost = lostIn(text, english, brief, locale)
      if (lost.isEmpty()) {
        return TextWriter.Written(text, attempts = attempt + 1)
      }
      LOG.info { "Translation try ${attempt + 1} rejected, it lost: $lost" }
    }

    return TextWriter.Written(null, config.maxAttempts)
  }

  /**
   * What the translation dropped. A name may be bent by grammar ("Batus", "dem Unsichtbaren Weber"), so a name
   * is looked for by its made-up words, which no grammar changes, not as a whole.
   */
  private fun lostIn(translation: String, english: String, brief: TextBrief, locale: String): List<String> {
    val lostWords = brief.allowedNames
      .filter { it.name in english }
      .flatMap { name -> madeUpWordsOf(name.name, name.inLocale[locale]) }
      .distinct()
      .filterNot { it in translation }
    val lostNumbers = NUMBER.findAll(english).map { it.value }.distinct().filterNot { it in translation }.toList()
    // The game fills these in later, so they must stay as they are.
    val lostPlaceholders = PLACEHOLDER.findAll(english).map { it.value }.distinct().filterNot { it in translation }.toList()

    return lostWords + lostNumbers + lostPlaceholders
  }

  private fun madeUpWordsOf(english: String, local: String?): List<String> {
    val words = WORD.findAll(english).map { it.value }.filterNot { validator.isEnglishWord(it) }.toList()

    // A made-up word that the other language writes in its own way is free to change.
    return if (local == null) words else words.filter { it in local }
  }

  private class Prompt(private val instruction: String, private val glossaryLine: String, private val textLabel: String) {

    fun userMessage(english: String, glossary: List<Pair<String, String>>): String {
      val names = if (glossary.isEmpty()) "" else "\n$glossaryLine ${glossary.joinToString("; ") { "${it.first} = ${it.second}" }}\n"

      return "$instruction\n$names\n$textLabel\n\"$english\"\n"
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    /** Colder than for writing: a translation should not add anything. */
    val TRANSLATION_SAMPLING = LlmClient.Sampling(temperature = 0.2, topP = 0.9, maxTokens = 700)

    val WORD = Regex("""[\p{L}'’-]+""")
    val NUMBER = Regex("""\d+""")
    val PLACEHOLDER = Regex("""\{\w+}""")

    /** One prompt per language the job can translate to, each in its own language. */
    val PROMPTS = mapOf(
      "de" to Prompt(
        instruction = """
          Du bist ein professioneller deutscher Videospiel-Lokalisierer für ein Fantasy-MMORPG.
          Übertrage den folgenden englischen Text in natürliches, atmosphärisches und idiomatisches Deutsch.

          Regeln:
          1. Übersetze NICHT Wort für Wort, sondern formuliere so, wie es in einem deutschen Fantasy-Roman stehen würde.
          2. Verwende bei Fraktionen und Gruppen immer passende deutsche Artikel (z. B. "die Saryhorde").
          3. Verändere KEINE Eigennamen, Ortsnamen oder Zahlen, außer wie unten angegeben.
          4. Behalte Zeilenumbrüche bei.
          5. Ein Wort in geschweiften Klammern wie {town} ist ein Platzhalter. Das Spiel setzt dort später einen Namen ein. Übernimm jeden Platzhalter unverändert mit den Klammern und stelle ihn an die Stelle im Satz, an die der Name gehört (z. B. "nach {town}").
          6. Gib ausschließlich den fertigen deutschen Text ohne Kommentare aus.
        """.trimIndent(),
        glossaryLine = "Verwende für diese Namen genau die deutsche Form (beuge sie, wo die Grammatik es verlangt):",
        textLabel = "Englischer Text:",
      ),
    )
  }
}
