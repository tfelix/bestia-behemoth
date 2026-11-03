package net.bestia.zone.townsfolk.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.townsfolk.persistence.TownsfolkDeath
import net.bestia.zone.townsfolk.persistence.TownsfolkDeathRepository
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Service

/**
 * Who was killed, until somebody takes their place. The person is re-derived from the seed like everybody
 * else; only the death is stored, so one griefer cannot empty a town for good.
 *
 * Tick-thread only, like `RumourRegistry`. Writes go behind through [AsyncJobExecutor], keyed on the identity.
 */
@Service
class TownsfolkDeaths(
  private val repository: TownsfolkDeathRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldService: WorldService,
  private val clock: BestiaClock,
) {

  private val replacedOnDay = HashMap<Long, Double>()

  val size: Int
    get() {
      return replacedOnDay.size
    }

  fun died(identity: Long) {
    val death = TownsfolkDeath(
      identity = identity,
      replacedOnDay = clock.now().absoluteDay + REPLACEMENT_DAYS,
      worldShapeVersion = worldService.record.shapeVersion,
      pipelineVersion = worldService.record.pipelineVersion,
    )
    replacedOnDay[identity] = death.replacedOnDay

    asyncJobExecutor.submit(identity) { repository.save(death) }
  }

  /** Whether [identity] is still dead. Forgets the death once their replacement is due. */
  fun isDead(identity: Long): Boolean {
    val until = replacedOnDay[identity] ?: return false
    if (clock.now().absoluteDay < until) {
      return true
    }

    replacedOnDay.remove(identity)
    asyncJobExecutor.submit(identity) { repository.deleteById(identity) }

    return false
  }

  /** Boot-time only. Discards rows of a different world, as `RumourRegistry.loadAll` does. */
  fun loadAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion

    val (valid, orphaned) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion
    }

    valid.forEach { replacedOnDay[it.identity] = it.replacedOnDay }

    if (orphaned.isNotEmpty()) {
      repository.deleteAll(orphaned)
      LOG.warn { "${orphaned.size} townsfolk death(s) do not belong to this world; discarded" }
    }

    LOG.info { "Loaded $size townsfolk death(s)" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    /** An apprentice takes over in a few days (settlements.md, "What You Can Break"). */
    const val REPLACEMENT_DAYS = 3.0
  }
}
