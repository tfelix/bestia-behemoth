package net.bestia.zone.prose

import net.bestia.zone.prose.host.DockerLlmHost
import net.bestia.zone.text.persistence.WorldText
import net.bestia.zone.text.persistence.WorldTextRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Writes the texts of the test world with the real model, in a container this test starts through
 * [DockerLlmHost]. Not part of the suite: it needs Docker, fetches about 5 GB on its first run and takes many
 * minutes. Run it by hand to read what the model writes:
 *
 *     PROSE_REAL=true ./gradlew :zone-server:test --tests 'net.bestia.zone.prose.RealModelManualTest' -i
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
@EnabledIfEnvironmentVariable(named = "PROSE_REAL", matches = "true")
// A CI runner has no memory for the model, whatever its variables say.
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class RealModelManualTest {

  @Autowired
  private lateinit var job: TextJob

  @Autowired
  private lateinit var rows: WorldTextRepository

  @Test
  fun `write the texts of the test world and print them`() {
    val waiting = job.plan()
    println("PROSE waiting=$waiting")

    val report = DockerLlmHost(ProseConfig()).withModel { job.run(it) }

    println("PROSE written=${report.written} rejected=${report.rejected} left=${report.leftWaiting}")
    rows.findAll().sortedBy { it.subject }.forEach { row ->
      val body = if (row.state == WorldText.State.WRITTEN) row.body else "(${row.state})"
      println("PROSE TEXT ${row.subject} [${row.id.locale}] tries=${row.attempts}\n$body\nPROSE END")
    }
  }
}
