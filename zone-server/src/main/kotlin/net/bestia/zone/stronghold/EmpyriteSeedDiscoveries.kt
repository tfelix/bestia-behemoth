package net.bestia.zone.stronghold

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.stronghold.persistence.EmpyriteSeedDiscovery
import net.bestia.zone.stronghold.persistence.EmpyriteSeedDiscoveryRepository
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.time.Clock

/** Which master has discovered which Empyrite Seed. In memory, written behind. Tick thread only, after [restoreAll]. */
@Service
class EmpyriteSeedDiscoveries(
  private val worldService: WorldService,
  private val repository: EmpyriteSeedDiscoveryRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val clock: Clock = Clock.systemUTC(),
) {

  private val discovered = HashSet<EmpyriteSeedDiscovery.Key>()

  fun restoreAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion
    val (current, stale) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion
    }

    if (stale.isNotEmpty()) {
      repository.deleteAll(stale)
      LOG.warn { "Dropped ${stale.size} Empyrite Seed discoveries of another world or pipeline" }
    }

    discovered.clear()
    current.mapTo(discovered) { it.id }
  }

  fun isDiscovered(masterId: Long, rank: Int): Boolean {
    return EmpyriteSeedDiscovery.Key(masterId, rank) in discovered
  }

  /** Whether anybody discovered the seed: from then on it is on every map that charts its spot. */
  fun isDiscoveredByAnyone(rank: Int): Boolean {
    return discovered.any { it.rank == rank }
  }

  /** @return false when [masterId] knew the seed already */
  fun discover(masterId: Long, rank: Int): Boolean {
    val key = EmpyriteSeedDiscovery.Key(masterId, rank)
    if (!discovered.add(key)) return false

    val row = EmpyriteSeedDiscovery(
      id = key.copy(),
      discoveredAt = clock.instant(),
      worldShapeVersion = worldService.record.shapeVersion,
      pipelineVersion = worldService.record.pipelineVersion
    )
    asyncJobExecutor.submit(JOB_KEY) {
      repository.save(row)
    }

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    const val JOB_KEY = "empyrite-seed-discoveries"
  }
}
