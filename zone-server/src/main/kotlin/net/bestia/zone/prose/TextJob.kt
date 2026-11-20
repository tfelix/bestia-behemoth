package net.bestia.zone.prose

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.prose.persistence.TextJobBudget
import net.bestia.zone.prose.persistence.TextJobBudgetRepository
import net.bestia.zone.text.TextSource
import net.bestia.zone.text.WorldTexts
import net.bestia.zone.text.persistence.WorldText
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

/**
 * Writes the texts a world wants, one at a time, most important first, until none is left or the time for a
 * language is used up. The rows of the text store are its queue, so it can stop anywhere and go on at the next
 * start. Off the tick; on a cluster one node holds this job and others may lend it their model.
 */
@Service
class TextJob(
  private val sources: List<TextSource>,
  private val texts: WorldTexts,
  private val writer: TextWriter,
  private val budgets: TextJobBudgetRepository,
  private val config: ProseConfig,
  private val clock: Clock = Clock.systemUTC(),
) {

  class Report(val written: Int, val rejected: Int, val leftWaiting: Long)

  /** Asks every source what it wants and queues what is new. @return how many texts wait and still have time. */
  fun plan(): Long {
    val queued = texts.request(sources.flatMap { it.wanted() }, config.locales)
    val writable = config.locales.filter { timeLeftFor(it) > Duration.ZERO }.sumOf { texts.waiting(it) }
    LOG.info { "Queued $queued new texts; $writable wait to be written" }

    return writable
  }

  fun run(llm: LlmClient): Report {
    var written = 0
    var rejected = 0
    for (locale in config.locales) {
      while (timeLeftFor(locale) > Duration.ZERO) {
        val row = texts.nextWaiting(locale) ?: break
        val started = clock.instant()
        val result = write(row, llm)
        texts.finish(row.id, result.body, llm.model, result.attempts)
        spend(locale, Duration.between(started, clock.instant()))

        if (result.body == null) rejected++ else written++
        LOG.info { "${row.subject} [$locale]: ${if (result.body == null) "rejected" else "written"} after ${result.attempts} tries" }
      }
    }
    texts.refreshWritten()

    val left = config.locales.sumOf { texts.waiting(it) }
    LOG.info { "Text job done: $written written, $rejected rejected, $left left as templates for now" }

    return Report(written, rejected, left)
  }

  private fun write(row: WorldText, llm: LlmClient): TextWriter.Written {
    // A subject no source knows any more, such as the text of a plan that ended, is given up without a try.
    val brief = sources.firstNotNullOfOrNull { it.briefOf(row.subject) } ?: return GIVEN_UP
    if (!writer.canWriteIn(row.id.locale)) {
      return GIVEN_UP
    }

    return writer.write(brief, llm, row.id.locale)
  }

  private fun timeLeftFor(locale: String): Duration {
    val spent = budgets.findByIdOrNull(locale)?.spentMillis ?: 0

    return config.capPerLocale.minusMillis(spent)
  }

  private fun spend(locale: String, time: Duration) {
    val budget = budgets.findByIdOrNull(locale) ?: TextJobBudget(locale)
    budget.spentMillis += time.toMillis()
    budgets.save(budget)
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
    val GIVEN_UP = TextWriter.Written(null, attempts = 0)
  }
}
