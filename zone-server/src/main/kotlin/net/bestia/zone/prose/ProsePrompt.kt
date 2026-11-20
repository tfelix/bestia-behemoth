package net.bestia.zone.prose

import net.bestia.zone.text.TextBrief
import net.bestia.zone.text.WorldTexts

/**
 * How a small model is asked for a text. The order is the one that worked: hard rules as the standing
 * instruction, then the task, the only names it may use with what each is, the facts, and the word to write last.
 */
object ProsePrompt {

  val SYSTEM = """
    You are an in-world writer of short lore texts for a fantasy game: chronicles, grave stones, legends, sayings.
    Your highest priority is strict factual accuracy based ONLY on the provided FACT SHEET.

    STRICT VALIDATOR RULES:
    1. ALLOWED PROPER NOUNS ONLY: You may ONLY use proper nouns listed in the ALLOWED NAMES list. Never invent names of people, places, gods, ranks, or titles (do NOT use words like King, Lord, General, or Emperor unless they appear in the ALLOWED NAMES list).
    2. NUMBERS & YEARS: Write all years and numbers strictly as digits (e.g., 901, 406), never as words. Use ONLY numbers present in the FACT SHEET.
    3. ALLOWED CREATIVITY: You may describe weather, emotions, landscape, and unnamed soldiers/crowds to create atmosphere.
    4. STYLE: Write in simple, archaic prose. Avoid modern words.
    5. PLACEHOLDERS: A word in curly braces, like {town}, is a placeholder. The game puts a real name there later. When the task has a PLACEHOLDERS list, write each one exactly as listed, with the braces, at the place in the sentence where that name belongs (for example "the road to {town}"). Never change, translate or explain a placeholder, and never make up one.
    6. OUTPUT FORMAT: Output ONLY the final text, in the length and shape the TASK asks. Do NOT include a title, intro, notes, or markdown formatting.
  """.trimIndent()

  /**
   * A higher temperature makes a small model invent titles and names. With no repeat penalty it says the same
   * flourish in text after text.
   */
  val SAMPLING = LlmClient.Sampling(temperature = 0.6, topP = 0.85, maxTokens = 400, minP = 0.05, repeatPenalty = 1.05)

  /**
   * What is added to the task for a language other than English. The brief stays English: a brief in German
   * made the model write worse German.
   *
   * @param withPlaceholder said only for a brief that has placeholders: on any other text the example makes
   *   the model put braces around a name
   */
  private class Language(val note: String, val withPlaceholder: String)

  private val LANGUAGES = mapOf(
    "de" to Language(
      note = "Write the text in German: natural, correct German, as a German writer would put it. " +
        "Write each name of the ALLOWED NAMES list letter for letter; only its ending may follow the grammar. " +
        "If the text speaks to somebody, it says \"du\".",
      withPlaceholder = "Before the placeholder of a town write \"nach\", as in \"nach {town}\".",
    ),
  )

  fun canWriteIn(locale: String): Boolean {
    return locale == WorldTexts.FALLBACK_LOCALE || locale in LANGUAGES
  }

  /** @param avoid what was wrong with the last try. The rejected text itself is never sent back: the model copies it. */
  fun userMessage(brief: TextBrief, avoid: String? = null, locale: String = WorldTexts.FALLBACK_LOCALE): String {
    val allowed = brief.allowedNames.joinToString("\n") { nameLine(it, locale) }
    val language = LANGUAGES[locale]?.let { "\n${it.note}" + if (brief.placeholders.isEmpty()) "" else " ${it.withPlaceholder}" } ?: ""
    val facts = brief.facts.joinToString("\n") { "- $it" }
    val avoidBlock = avoid?.let { "\n\nAVOID\n$it" } ?: ""
    val placeholders = brief.placeholders.joinToString("") { "\n- ${it.token} stands for ${it.what}" }
    val placeholderBlock = if (placeholders.isEmpty()) "" else "\n\nPLACEHOLDERS (write each exactly like this, at least once):$placeholders"

    return """
      |TASK:
      |${brief.task}$language
      |
      |ALLOWED NAMES (Use exact spelling, use NO other proper nouns):
      |$allowed$placeholderBlock
      |
      |FACT SHEET:
      |$facts$avoidBlock
      |
      |Write the text now:
    """.trimMargin()
  }

  private fun nameLine(name: TextBrief.AllowedName, locale: String): String {
    val local = name.inLocale[locale] ?: return "- ${name.name} (${name.what})"

    return "- $local (the facts call it \"${name.name}\"; ${name.what})"
  }
}
