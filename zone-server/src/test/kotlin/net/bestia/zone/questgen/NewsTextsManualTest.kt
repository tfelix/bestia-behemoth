package net.bestia.zone.questgen

import net.bestia.zone.prose.LlmClient
import net.bestia.zone.prose.ProseConfig
import net.bestia.zone.prose.ProseValidator
import net.bestia.zone.prose.TextTranslator
import net.bestia.zone.prose.TextWriter
import net.bestia.zone.prose.host.DockerLlmHost
import net.bestia.zone.questgen.text.NewsOfferTextSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.util.unit.DataSize
import kotlin.time.measureTimedValue

/**
 * Writes the texts for carried news with the real model and prints them, in English and German. Not part of
 * the suite, see `RealModelManualTest` for what it needs:
 *
 *     PROSE_REAL=true ./gradlew :zone-server:cleanTest :zone-server:test --tests 'net.bestia.zone.questgen.NewsTextsManualTest' -i
 *
 * To compare models, set `PROSE_MODEL` (as `prose.llm.model`), `PROSE_REPEAT_PENALTY` and, for a model that
 * needs more than the default, `PROSE_MEMORY_GB`. Each text is printed with the seconds it took.
 */
@EnabledIfEnvironmentVariable(named = "PROSE_REAL", matches = "true")
class NewsTextsManualTest {

  /** Answers with another repeat penalty than the job asks for, and counts the time spent in the model. */
  private class Tuned(private val llm: LlmClient, private val repeatPenalty: Double?) : LlmClient {

    var seconds = 0.0
    var calls = 0

    override val model: String
      get() = llm.model

    override fun chat(system: String?, user: String, sampling: LlmClient.Sampling): String {
      val asked = if (repeatPenalty == null) sampling else sampling.copy(repeatPenalty = repeatPenalty)
      val (answer, took) = measureTimedValue { llm.chat(system, user, asked) }
      seconds += took.inWholeMilliseconds / 1000.0
      calls++

      return answer
    }
  }

  @Test
  fun `write the news texts and print them`() {
    val defaults = ProseConfig().llm
    val config = ProseConfig(
      llm = defaults.copy(
        model = System.getenv("PROSE_MODEL") ?: defaults.model,
        modelFile = System.getenv("PROSE_MODEL_FILE")?.ifEmpty { null },
        memory = System.getenv("PROSE_MEMORY_GB")?.let { DataSize.ofGigabytes(it.toLong()) } ?: defaults.memory,
      ),
    )
    val validator = ProseValidator()
    val writer = TextWriter(validator, config)
    val translator = TextTranslator(validator, config)
    val source = NewsOfferTextSource(QuestgenConfig())

    DockerLlmHost(config).withModel { model ->
      val llm = Tuned(model, System.getenv("PROSE_REPEAT_PENALTY")?.toDouble())
      var words = 0
      source.wanted().forEach { wanted ->
        val brief = source.briefOf(wanted.subject)!!
        val before = llm.seconds
        val english = writer.write(brief, llm)
        print(wanted.subject, "en", english, llm.seconds - before)
        val body = english.body ?: return@forEach
        val afterEnglish = llm.seconds
        val german = translator.translate(body, brief, "de", llm)
        print(wanted.subject, "de", german, llm.seconds - afterEnglish)
        words += wordsOf(body) + wordsOf(german.body.orEmpty())
      }
      println("NEWS TOTAL model=${config.llm.model} seconds=${"%.0f".format(llm.seconds)} calls=${llm.calls} words=$words")
    }
  }

  private fun print(subject: String, locale: String, written: TextWriter.Written, seconds: Double) {
    val body = written.body ?: "(REJECTED)"
    println("NEWS TEXT $subject [$locale] tries=${written.attempts} seconds=${"%.1f".format(seconds)}\n$body\nNEWS END")
  }

  private fun wordsOf(text: String): Int {
    return text.split(Regex("\\s+")).count { it.isNotBlank() }
  }
}
