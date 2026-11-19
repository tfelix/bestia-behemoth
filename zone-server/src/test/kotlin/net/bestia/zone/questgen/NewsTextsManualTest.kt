package net.bestia.zone.questgen

import net.bestia.zone.prose.ProseConfig
import net.bestia.zone.prose.ProseValidator
import net.bestia.zone.prose.TextTranslator
import net.bestia.zone.prose.TextWriter
import net.bestia.zone.prose.host.DockerLlmHost
import net.bestia.zone.questgen.text.NewsOfferTextSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Writes the texts for carried news with the real model and prints them, in English and German. Not part of
 * the suite, see `RealModelManualTest` for what it needs:
 *
 *     PROSE_REAL=true ./gradlew :zone-server:cleanTest :zone-server:test --tests 'net.bestia.zone.questgen.NewsTextsManualTest' -i
 */
@EnabledIfEnvironmentVariable(named = "PROSE_REAL", matches = "true")
class NewsTextsManualTest {

  @Test
  fun `write the news texts and print them`() {
    val config = ProseConfig()
    val validator = ProseValidator()
    val writer = TextWriter(validator, config)
    val translator = TextTranslator(validator, config)
    val source = NewsOfferTextSource(QuestgenConfig())

    DockerLlmHost(config).withModel { llm ->
      source.wanted().forEach { wanted ->
        val brief = source.briefOf(wanted.subject)!!
        val english = writer.write(brief, llm)
        println("NEWS TEXT ${wanted.subject} [en] tries=${english.attempts}\n${english.body ?: "(REJECTED)"}\nNEWS END")
        val body = english.body ?: return@forEach
        val german = translator.translate(body, brief, "de", llm)
        println("NEWS TEXT ${wanted.subject} [de] tries=${german.attempts}\n${german.body ?: "(REJECTED)"}\nNEWS END")
      }
    }
  }
}
