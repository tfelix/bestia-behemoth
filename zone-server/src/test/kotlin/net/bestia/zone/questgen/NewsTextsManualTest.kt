package net.bestia.zone.questgen

import net.bestia.zone.prose.LlmClient
import net.bestia.zone.prose.ProseConfig
import net.bestia.zone.prose.ProsePrompt
import net.bestia.zone.prose.ProseValidator
import net.bestia.zone.prose.TextTranslator
import net.bestia.zone.prose.TextWriter
import net.bestia.zone.prose.host.DockerLlmHost
import net.bestia.zone.questgen.text.NewsOfferTextSource
import net.bestia.zone.text.TextBrief
import net.bestia.zone.text.TextSubject
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
 * `PROSE_DIRECT_GERMAN=true` also writes each text in German at once, to compare with the translation.
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
        if (System.getenv("PROSE_DIRECT_GERMAN") == "true") {
          printDirect(wanted.subject, "de-direct", llm) { GermanDirect.fromEnglishBrief(brief) }
          printDirect(wanted.subject, "de-german-brief", llm) { GermanDirect.fromGermanBrief(wanted.subject) }
        }
      }
      println("NEWS TOTAL model=${config.llm.model} seconds=${"%.0f".format(llm.seconds)} calls=${llm.calls} words=$words")
    }
  }

  private fun print(subject: String, locale: String, written: TextWriter.Written, seconds: Double) {
    val body = written.body ?: "(REJECTED)"
    println("NEWS TEXT $subject [$locale] tries=${written.attempts} seconds=${"%.1f".format(seconds)}\n$body\nNEWS END")
  }

  /**
   * German written at once, with no English text in between. The validator finds names by English words, so
   * it cannot read German: here only the placeholder is checked.
   */
  private fun printDirect(subject: String, label: String, llm: Tuned, prompt: () -> Pair<String, String>) {
    val (system, user) = prompt()
    val before = llm.seconds
    val text = llm.chat(system, user, ProsePrompt.SAMPLING).trim().removeSurrounding("\"").trim()
    val body = if (NewsOfferTextSource.TOWN in text) text else "(REJECTED) $text"
    println("NEWS TEXT $subject [$label] tries=1 seconds=${"%.1f".format(llm.seconds - before)}\n$body\nNEWS END")
  }

  /** The two ways to ask for German at once: the English brief with one more line, or a brief in German. */
  private object GermanDirect {

    fun fromEnglishBrief(brief: TextBrief): Pair<String, String> {
      val german = brief.copy(
        task = brief.task + " Write the text in German: natural, correct German as a villager speaks it. " +
          "Address the traveller as \"du\". Write \"nach {town}\".",
      )

      return ProsePrompt.SYSTEM to ProsePrompt.userMessage(german)
    }

    fun fromGermanBrief(subject: String): Pair<String, String> {
      val (kind, variant) = TextSubject.newsPartsOf(subject)!!
      val user = """
        |AUFGABE:
        |Ein Dorfbewohner spricht mit einem Reisenden. Eine Nachricht hat seinen Ort erreicht, und ein Ort in der Nähe hat sie noch nicht gehört. Schreibe, was der Dorfbewohner sagt: 2 bis 4 Sätze, 35 bis 70 Wörter, ohne Begrüßung. Er erzählt, was geschehen ist, so wie es in den FAKTEN steht, und bittet den Reisenden, die Nachricht in den anderen Ort zu tragen. Dieser Ort ist {town}. Füge nichts hinzu, was nicht in den FAKTEN steht. Sprich den Reisenden mit "du" an. Es spricht ${VOICES[variant % VOICES.size]}.
        |
        |PLATZHALTER (genau so schreiben, mindestens einmal):
        |- {town} steht für den Namen des Ortes, in den die Nachricht getragen werden soll
        |
        |FAKTEN:
        |${FACTS.getValue(kind).joinToString("\n") { "- $it" }}
        |
        |Schreibe jetzt den Text:
      """.trimMargin()

      return SYSTEM to user
    }

    val SYSTEM = """
      Du schreibst kurze Texte für ein Fantasy-Spiel: was einfache Leute in einem Dorf sagen.
      Am wichtigsten ist, dass alles stimmt: Schreibe NUR, was in den FAKTEN steht.

      REGELN:
      1. KEINE NAMEN: Erfinde keine Namen von Personen, Orten, Göttern oder Titeln.
      2. SPRACHE: Schreibe einfaches, natürliches und fehlerfreies Deutsch, so wie man es spricht. Kurze Sätze. Keine seltenen oder altertümlichen Wörter.
      3. PLATZHALTER: Ein Wort in geschweiften Klammern wie {town} ist ein Platzhalter. Das Spiel setzt dort später einen Ortsnamen ein. Schreibe ihn unverändert mit den Klammern an die Stelle im Satz, an die der Name gehört (zum Beispiel "nach {town}").
      4. AUSGABE: Gib NUR den fertigen Text aus. Kein Titel, keine Anmerkungen, keine Formatierung.
    """.trimIndent()

    val FACTS = mapOf(
      "BOSS_SLAIN" to listOf(
        "Eine große Bestie, die die Leute fürchteten, wurde getötet, nicht weit vom Ort des Sprechers.",
        "Der Sprecher hat es nicht gesehen. Andere sind hingegangen und haben davon erzählt.",
        "Der Sprecher weiß nicht, wer sie getötet hat.",
      ),
      "TOWN_FELL" to listOf(
        "Ein Ort nicht weit vom Ort des Sprechers wurde zerstört. Kein Haus steht mehr.",
        "Einige seiner Bewohner sind entkommen.",
        "Der Sprecher weiß nicht, wer es getan hat.",
      ),
    )

    val VOICES = listOf(
      "ein alter Bauer, der langsam spricht", "eine Wirtin, die viel zu tun hat", "eine besorgte Mutter",
      "ein müder Wächter", "ein Händler, der Verwandte in dem anderen Ort hat",
      "ein junger Hirte, der noch erschüttert ist", "ein Priester, der ruhig spricht", "ein Schmied, der wenig Worte macht",
      "eine Fischerin, die es auf dem Markt gehört hat", "eine Witwe, die so etwas schon erlebt hat",
    )
  }

  private fun wordsOf(text: String): Int {
    return text.split(Regex("\\s+")).count { it.isNotBlank() }
  }
}
