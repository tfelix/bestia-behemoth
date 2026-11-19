package net.bestia.zone.prose

import net.bestia.zone.text.TextBrief

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
    5. OUTPUT FORMAT: Output ONLY the final text, in the length and shape the TASK asks. Do NOT include a title, intro, notes, or markdown formatting.
  """.trimIndent()

  /** A higher temperature makes a small model invent titles and names. */
  val SAMPLING = LlmClient.Sampling(temperature = 0.6, topP = 0.85, maxTokens = 400, minP = 0.05, repeatPenalty = 1.08)

  /** @param avoid what was wrong with the last try. The rejected text itself is never sent back: the model copies it. */
  fun userMessage(brief: TextBrief, avoid: String? = null): String {
    val allowed = brief.allowedNames.joinToString("\n") { "- ${it.name} (${it.what})" }
    val facts = brief.facts.joinToString("\n") { "- $it" }
    val avoidBlock = avoid?.let { "\n\nAVOID\n$it" } ?: ""

    return """
      |TASK:
      |${brief.task}
      |
      |ALLOWED NAMES (Use exact spelling, use NO other proper nouns):
      |$allowed
      |
      |FACT SHEET:
      |$facts$avoidBlock
      |
      |Write the text now:
    """.trimMargin()
  }
}
