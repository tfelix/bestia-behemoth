package net.bestia.zone.prose

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.prose.host.LlmHost
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Writes the texts of the world before the zone goes on booting. The model needs most of a small machine's
 * memory, so this runs while the zone is still small: after the world, the catalogues and the quest plans, and
 * before the entities are loaded, the tick starts and the socket opens. A world that is written boots straight
 * through.
 *
 * Whatever goes wrong here, the zone boots: a text that was not written stays its template.
 */
@Component
@Order(109)
class TextJobBootRunner(
  private val job: TextJob,
  private val host: LlmHost,
  private val config: ProseConfig,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    if (!config.enabled) {
      return
    }
    if (job.plan() == 0L) {
      return
    }

    LOG.info { "Boot waits for the texts of the world, at most ${config.capPerLocale} per language" }
    // World generation leaves a large heap behind. Give back what is garbage before the model asks for memory.
    System.gc()
    try {
      host.withModel { job.run(it) }
    } catch (e: Exception) {
      LOG.error(e) { "The text job stopped early. The zone boots with templates; the job goes on at the next start." }
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
