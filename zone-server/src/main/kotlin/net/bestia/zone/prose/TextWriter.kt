package net.bestia.zone.prose

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.text.TextBrief
import org.springframework.stereotype.Component

/** Has a model write one text and takes it only when the validator finds nothing wrong with it. */
@Component
class TextWriter(
  private val validator: ProseValidator,
  private val config: ProseConfig,
) {

  /** [body] is null when no try passed. */
  class Written(val body: String?, val attempts: Int)

  fun write(brief: TextBrief, llm: LlmClient): Written {
    var avoid: String? = null
    repeat(config.maxAttempts) { attempt ->
      val text = clean(llm.chat(ProsePrompt.SYSTEM, ProsePrompt.userMessage(brief, avoid), ProsePrompt.SAMPLING))
      val problems = validator.check(text, brief)
      if (problems.isEmpty()) {
        return Written(text, attempts = attempt + 1)
      }

      avoid = validator.feedback(problems)
      LOG.info { "Try ${attempt + 1} rejected: ${problems.joinToString { "${it.type} '${it.value}'" }}" }
    }

    return Written(null, config.maxAttempts)
  }

  /** A model sometimes wraps its whole answer in quotes. */
  private fun clean(answer: String): String {
    return answer.trim().removeSurrounding("\"").trim()
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
