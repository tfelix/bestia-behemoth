package net.bestia.zone.stronghold

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.worldgen.stronghold.EmpyriteSeeds
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.stronghold.persistence.EmpyriteSeedState
import net.bestia.zone.stronghold.persistence.EmpyriteSeedStateRepository
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.time.Clock
import kotlin.math.hypot

/**
 * The world's Empyrite Seeds as the game has woken them. The pool is the world generator's; which seeds woke and
 * which were skipped is kept here in memory and written behind. Tick thread only, after [restoreAll].
 */
@Service
class EmpyriteSeedRegistry(
  private val worldService: WorldService,
  private val towns: TownRegistry,
  private val config: StrongholdConfig,
  private val repository: EmpyriteSeedStateRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val clock: Clock = Clock.systemUTC(),
) {

  private val pool: List<EmpyriteSeed> by lazy { EmpyriteSeeds.of(worldService.generated) }

  private val decided = HashMap<Int, EmpyriteSeedState>()

  fun restoreAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion
    val (current, stale) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion
    }

    if (stale.isNotEmpty()) {
      repository.deleteAll(stale)
      LOG.warn { "Dropped ${stale.size} Empyrite Seed states of another world or pipeline" }
    }

    decided.clear()
    current.associateByTo(decided) { it.rank }
    LOG.info { "Restored ${awake().size} awake Empyrite Seeds of a pool of ${pool.size}" }
  }

  /** Awake seeds, best first. */
  fun awake(): List<EmpyriteSeed> {
    return pool.filter { decided[it.rank]?.awake == true }
  }

  /** Whether an awake seed lies within [metres] of [position], measured flat. */
  fun isAwakeSeedWithin(position: Vec3L, metres: Double): Boolean {
    val voxel = worldService.config.voxelSize
    return awake().any { hypot(it.x - position.x * voxel, it.y - position.y * voxel) < metres }
  }

  /**
   * Wakes the best sleeping seeds until [target] are awake. A seed by a player town is skipped for good: the docs
   * promise it never wakes there, and a town can stand for years.
   *
   * Awake seeds never go back to sleep, so a smaller [target] than before changes nothing.
   */
  fun wakeUpTo(target: Int) {
    var awakeCount = decided.values.count { it.awake }

    for (seed in pool) {
      if (awakeCount >= target) return
      if (seed.rank in decided) continue

      val wakes = !isByTown(seed)
      decide(seed, wakes)
      if (wakes) awakeCount++
    }

    if (awakeCount < target) {
      LOG.warn { "Only $awakeCount Empyrite Seeds can be awake, $target are wanted: the pool of ${pool.size} is spent" }
    }
  }

  private fun isByTown(seed: EmpyriteSeed): Boolean {
    val voxel = worldService.config.voxelSize

    return towns.all()
      .filter { it.isHeld }
      .any { hypot(it.sign.x * voxel - seed.x, it.sign.y * voxel - seed.y) < config.settlementClearanceMetres }
  }

  private fun decide(seed: EmpyriteSeed, wakes: Boolean) {
    val state = EmpyriteSeedState(
      rank = seed.rank,
      awake = wakes,
      decidedAt = clock.instant(),
      worldShapeVersion = worldService.record.shapeVersion,
      pipelineVersion = worldService.record.pipelineVersion
    )
    decided[seed.rank] = state

    asyncJobExecutor.submit(JOB_KEY) {
      repository.save(state)
    }

    if (wakes) {
      LOG.info { "Empyrite Seed ${seed.rank} (${seed.name}) woke at (${seed.x.toInt()}, ${seed.y.toInt()})" }
    } else {
      LOG.info { "Empyrite Seed ${seed.rank} (${seed.name}) was skipped: a player town stands by it" }
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** One key for every write, so they land in the order they were decided. */
    const val JOB_KEY = "empyrite-seeds"
  }
}
