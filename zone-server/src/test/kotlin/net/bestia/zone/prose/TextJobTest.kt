package net.bestia.zone.prose

import net.bestia.zone.prose.host.LlmHost
import net.bestia.zone.prose.persistence.TextJobBudgetRepository
import net.bestia.zone.text.TextBrief
import net.bestia.zone.text.TextBrief.AllowedName
import net.bestia.zone.text.TextRef
import net.bestia.zone.text.TextSource
import net.bestia.zone.text.WorldTexts
import net.bestia.zone.text.persistence.WorldTextRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The job on the real text store, with a source and a model made for the test. */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class TextJobTest {

  @Autowired
  private lateinit var texts: WorldTexts

  @Autowired
  private lateinit var rows: WorldTextRepository

  @Autowired
  private lateinit var budgets: TextJobBudgetRepository

  @Autowired
  private lateinit var writer: TextWriter

  @Autowired
  private lateinit var translator: TextTranslator

  @AfterEach
  fun forget() {
    rows.deleteAll(rows.findAll().filter { it.subject.startsWith(SUBJECT) })
    budgets.deleteAll()
    texts.refreshWritten()
  }

  @Test
  fun `the job writes what waits, most important first, and the game then finds the text`() {
    val job = jobOf(ProseConfig(), Clock.systemUTC())
    assertEquals(2, job.plan())

    val report = job.run(ScriptedLlm("Here lies Orwen, 712-780."))

    assertEquals(2 to 0, report.written to report.rejected)
    val textId = assertNotNull(texts.attach(TextRef("READABLE_NAMED_GRAVE"), "$SUBJECT:first").textId)
    assertEquals("Here lies Orwen, 712-780.", texts.bodyOf(textId, "en")!!.text)
    assertEquals(0, job.plan(), "a written text is not wanted again")
  }

  @Test
  fun `a world whose texts are written starts no model at the next boot`() {
    val job = jobOf(ProseConfig(), Clock.systemUTC())
    job.plan()
    job.run(ScriptedLlm("Here lies Orwen, 712-780."))

    // The next start of the zone: the rows are in the database, so nothing is wanted and no container starts.
    val restarted = jobOf(ProseConfig(), Clock.systemUTC())
    val noModel = object : LlmHost {
      override fun <T> withModel(work: (LlmClient) -> T): T {
        error("a written world must not start the model")
      }
    }
    TextJobBootRunner(restarted, noModel, ProseConfig(enabled = true)).run()

    assertEquals(0, restarted.plan())
  }

  @Test
  fun `when the time for a language is used up the rest stays a template, also after a restart`() {
    // Every look at the clock is a minute later, so the first text uses up a cap of half a minute.
    val job = jobOf(ProseConfig(capPerLocale = Duration.ofSeconds(30)), TickingClock(Duration.ofMinutes(1)))
    job.plan()

    val report = job.run(ScriptedLlm("Here lies Orwen, 712-780."))

    assertEquals(1 to 1L, report.written to report.leftWaiting)
    assertNull(texts.attach(TextRef("READABLE_NAMED_GRAVE"), "$SUBJECT:second").textId)
    assertEquals(0, jobOf(ProseConfig(capPerLocale = Duration.ofSeconds(30)), Clock.systemUTC()).plan(), "the time of this world is spent")
  }

  @Test
  fun `a text the model cannot get right is rejected and the job goes on`() {
    val job = jobOf(ProseConfig(), Clock.systemUTC())
    job.plan()

    val report = job.run(ScriptedLlm("Eldoria.", "Eldoria.", "Eldoria.", "Here lies Orwen, 712-780."))

    assertEquals(1 to 1, report.written to report.rejected)
    assertTrue(rows.findAllBySubject("$SUBJECT:first").single().attempts == 3)
  }

  @Test
  fun `the second language is translated from the English text, under the same id`() {
    val job = jobOf(ProseConfig(), Clock.systemUTC(), locales = listOf("en", "de"))
    assertEquals(4, job.plan())

    // Two English texts first, then their two translations.
    job.run(ScriptedLlm("Here lies Orwen, 712-780.", "Here lies Orwen, 712-780.", "Hier ruht Orwen, 712-780."))

    val textId = assertNotNull(texts.attach(TextRef("READABLE_NAMED_GRAVE"), "$SUBJECT:first").textId)
    assertEquals(WorldTexts.Body("de", "Hier ruht Orwen, 712-780."), texts.bodyOf(textId, "de"))
    assertEquals("Here lies Orwen, 712-780.", texts.bodyOf(textId, "en")!!.text)
  }

  private fun jobOf(config: ProseConfig, clock: Clock, locales: List<String> = listOf("en")): TextJob {
    return TextJob(listOf(TwoGraves), texts, writer, translator, budgets, config.copy(locales = locales), clock)
  }

  private object TwoGraves : TextSource {

    override fun wanted(): List<TextSource.Wanted> {
      return listOf(TextSource.Wanted("$SUBJECT:second", priority = 20), TextSource.Wanted("$SUBJECT:first", priority = 10))
    }

    override fun briefOf(subject: String): TextBrief? {
      return TextBrief(
        task = "Write the epitaph of Orwen.",
        allowedNames = listOf(AllowedName("Orwen", "a smith, 712-780")),
        facts = listOf("born: 712", "died: 780"),
        mustName = listOf("Orwen"),
      ).takeIf { subject.startsWith(SUBJECT) }
    }
  }

  private class TickingClock(private val step: Duration) : Clock() {

    private var now = Instant.parse("2026-11-19T12:00:00Z")

    override fun instant(): Instant {
      now = now.plus(step)

      return now
    }

    override fun getZone() = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId?) = this
  }

  private companion object {
    const val SUBJECT = "text-job-test"
  }
}
