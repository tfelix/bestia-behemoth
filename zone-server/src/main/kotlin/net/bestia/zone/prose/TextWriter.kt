package net.bestia.zone.prose

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.text.TextBrief
import net.bestia.zone.text.WorldTexts
import org.springframework.stereotype.Component

/** Has a model write one text and takes it only when the validator finds nothing wrong with it. */
@Component
class TextWriter(
  private val validator: ProseValidator,
  private val config: ProseConfig,
) {

  /** [body] is null when no try passed. */
  class Written(val body: String?, val attempts: Int)

  fun canWriteIn(locale: String): Boolean {
    return ProsePrompt.canWriteIn(locale)
  }

  /**
   * Every language is written from the brief, not from the English text: a small model translates with more
   * errors than it writes with.
   */
  fun write(brief: TextBrief, llm: LlmClient, locale: String = WorldTexts.FALLBACK_LOCALE): Written {
    var avoid: String? = null
    repeat(config.maxAttempts) { attempt ->
      val text = clean(llm.chat(ProsePrompt.system(brief), ProsePrompt.userMessage(brief, avoid, locale), ProsePrompt.SAMPLING), brief)
      val problems = problemsOf(text, brief, locale)
      if (problems.isEmpty()) {
        return Written(text, attempts = attempt + 1)
      }

      avoid = validator.feedback(problems)
      LOG.info { "Try ${attempt + 1} rejected: ${problems.joinToString { "${it.type} '${it.value}'" }}" }
    }

    return Written(null, config.maxAttempts)
  }

  private fun problemsOf(text: String, brief: TextBrief, locale: String): List<ProseValidator.Problem> {
    return if (locale == WorldTexts.FALLBACK_LOCALE) validator.check(text, brief) else validator.checkIn(locale, text, brief)
  }

  /** A model sometimes wraps its whole answer in quotes, or a name in the braces of a placeholder. */
  private fun clean(answer: String, brief: TextBrief): String {
    val names = brief.allowedNames.flatMap { listOf(it.name) + it.inLocale.values }
    val unbraced = BRACED.replace(answer) { braced -> braced.groupValues[1].takeIf { it in names } ?: braced.value }

    return unbraced.trim().removeSurrounding("\"").trim()
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
    val BRACED = Regex("""\{([^{}]*)}""")
  }
}
