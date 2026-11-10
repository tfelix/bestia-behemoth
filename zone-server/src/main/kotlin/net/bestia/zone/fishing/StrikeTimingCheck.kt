package net.bestia.zone.fishing

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.update
import net.bestia.zone.fishing.ecs.StrikeTimings
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import kotlin.math.sqrt

/**
 * Logs a master whose strikes come faster or more evenly than a human manages. It only catches a naive bot - a
 * careful one adds human delay - so it never takes a catch away: a fast human must not lose one to a false alarm.
 */
@Component
class StrikeTimingCheck {

  fun record(world: World, masterEntityId: EntityId, reactionMillis: Long) {
    world.update(masterEntityId, { StrikeTimings() }) { timings ->
      timings.record(reactionMillis, keep = SAMPLES)

      val samples = timings.samples()
      if (samples.size == SAMPLES && looksScripted(samples)) {
        LOG.warn { "Entity $masterEntityId strikes like a script: ${samples.joinToString()} ms" }
        timings.clear()
      }
    }
  }

  fun looksScripted(reactionMillis: List<Long>): Boolean {
    val sorted = reactionMillis.sorted()
    val median = sorted[sorted.size / 2]
    val mean = reactionMillis.average()
    val spread = sqrt(reactionMillis.sumOf { (it - mean) * (it - mean) } / reactionMillis.size)

    return median < FASTEST_HUMAN_MEDIAN_MILLIS || spread < LEAST_HUMAN_SPREAD_MILLIS
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    const val SAMPLES = 20

    // Human visual reaction is about 200-300 ms with a spread of tens of ms. The server sees it plus a
    // network round trip, so these bounds sit well below a human and leave room for a fast one.
    const val FASTEST_HUMAN_MEDIAN_MILLIS = 150L
    const val LEAST_HUMAN_SPREAD_MILLIS = 15.0
  }
}
