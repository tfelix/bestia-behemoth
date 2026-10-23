package net.bestia.zone.travel

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.travel.knowledge.Knower
import net.bestia.zone.travel.knowledge.RouteKnowledge
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Notices a traveller that is not getting nearer.
 *
 * After [TravelConfig.stallAfter] it blames the macro edge it is on, which its knower then believes blocked
 * for [TravelConfig.failureTtl] - that is how a wall across open country, which nothing indexes, is learned.
 * After [TravelConfig.giveUpAfter] it gives up: the traveller is walled in or in a dead end.
 */
@Component
class ProgressWatchdog(
  private val knowledge: RouteKnowledge,
  private val config: TravelConfig,
) {

  /** @return why the journey has to end, or null while it is still worth trying */
  fun check(journey: Journey, knower: Knower, position: Vec3L, aim: Vec3L, deltaTime: Float): JourneyFailure? {
    val progress = journey.progress
    progress.record(aim, squaredDistance(position, aim), deltaTime)

    if (progress.idleSeconds >= seconds(config.giveUpAfter)) return JourneyFailure.STUCK

    if (progress.idleSeconds >= seconds(config.stallAfter) && !progress.stallBlamed) {
      progress.blameStall()
      journey.plan?.currentEdge?.let { knowledge.observe(knower, it, blocked = true, lasting = config.failureTtl) }
    }

    return null
  }

  /** Euclidean, so a step that only closes the shorter axis still counts as getting nearer. */
  private fun squaredDistance(a: Vec3L, b: Vec3L): Long {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
  }

  private fun seconds(duration: Duration): Float {
    return duration.toMillis() / 1000f
  }
}
