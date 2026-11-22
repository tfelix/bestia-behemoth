package net.bestia.zone.stronghold

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.worldgen.stronghold.EmpyriteSeeds
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.stronghold.persistence.EmpyriteSeedState
import net.bestia.zone.stronghold.persistence.EmpyriteSeedStateRepository
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.time.Clock
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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
  private val structures: PlayerStructureRegistry,
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

  /** Awake seeds, best first, where they woke. */
  fun awake(): List<EmpyriteSeed> {
    return pool.mapNotNull { seed ->
      val state = decided[seed.rank]?.takeIf { it.awake } ?: return@mapNotNull null
      val movedX = state.movedX
      val movedY = state.movedY

      if (movedX != null && movedY != null) seed.copy(x = movedX, y = movedY) else seed
    }
  }

  /** Whether an awake seed lies within [metres] of [position], measured flat. */
  fun isAwakeSeedWithin(position: Vec3L, metres: Double): Boolean {
    val voxel = worldService.config.voxelSize
    return awake().any { hypot(it.x - position.x * voxel, it.y - position.y * voxel) < metres }
  }

  /**
   * Wakes the best sleeping seeds until [target] are awake. A seed by a player town is skipped for good: the docs
   * promise it never wakes there, and a town can stand for years. A seed with a player's structure on its core moves to
   * the nearest free ground within reach, or is skipped too.
   *
   * Awake seeds never go back to sleep, so a smaller [target] than before changes nothing.
   *
   * @return the seeds that woke now
   */
  fun wakeUpTo(target: Int): List<EmpyriteSeed> {
    var awakeCount = decided.values.count { it.awake }
    val woken = ArrayList<EmpyriteSeed>()

    for (seed in pool) {
      if (awakeCount >= target) return woken
      if (seed.rank in decided) continue

      val spot = if (isByTown(seed)) null else freeSpotFor(seed)
      decide(seed, spot)
      if (spot != null) {
        awakeCount++
        woken.add(seed.copy(x = spot.first, y = spot.second))
      }
    }

    if (awakeCount < target) {
      LOG.warn { "Only $awakeCount Empyrite Seeds can be awake, $target are wanted: the pool of ${pool.size} is spent" }
    }

    return woken
  }

  private fun isByTown(seed: EmpyriteSeed): Boolean {
    val voxel = worldService.config.voxelSize

    return towns.all()
      .filter { it.isHeld }
      .any { hypot(it.sign.x * voxel - seed.x, it.sign.y * voxel - seed.y) < config.settlementClearanceMetres }
  }

  /**
   * Where [seed] can wake: its own spot, or the nearest spot within [StrongholdConfig.seedMoveMetres] whose core holds
   * no player's structure. Null when there is none. A structure is known by where it stands, so a little room is kept
   * round each for its footprint.
   */
  private fun freeSpotFor(seed: EmpyriteSeed): Pair<Double, Double>? {
    val voxel = worldService.config.voxelSize
    val clearance = config.coreRadiusMetres + FOOTPRINT_METRES
    val reach = config.seedMoveMetres + clearance
    val nearby = structures.all()
      .map { it.position.x * voxel to it.position.y * voxel }
      .filter { (x, y) -> hypot(x - seed.x, y - seed.y) <= reach }
    val isFree = { x: Double, y: Double -> nearby.none { (sx, sy) -> hypot(sx - x, sy - y) < clearance } }

    if (isFree(seed.x, seed.y)) return seed.x to seed.y

    for (radius in 1..config.seedMoveMetres.toInt()) {
      val steps = (2 * PI * radius).toInt().coerceAtLeast(6)
      for (step in 0 until steps) {
        val angle = 2 * PI * step / steps
        val x = seed.x + radius * cos(angle)
        val y = seed.y + radius * sin(angle)
        if (isFree(x, y)) return x to y
      }
    }

    return null
  }

  /** [spot] is where it wakes, or null when it is skipped. */
  private fun decide(seed: EmpyriteSeed, spot: Pair<Double, Double>?) {
    val wakes = spot != null
    val moved = spot?.takeIf { it != seed.x to seed.y }
    val state = EmpyriteSeedState(
      rank = seed.rank,
      awake = wakes,
      movedX = moved?.first,
      movedY = moved?.second,
      decidedAt = clock.instant(),
      worldShapeVersion = worldService.record.shapeVersion,
      pipelineVersion = worldService.record.pipelineVersion
    )
    decided[seed.rank] = state

    asyncJobExecutor.submit(JOB_KEY) {
      repository.save(state)
    }

    if (spot != null) {
      LOG.info { "Empyrite Seed ${seed.rank} (${seed.name}) woke at (${spot.first.toInt()}, ${spot.second.toInt()})" }
    } else {
      LOG.info { "Empyrite Seed ${seed.rank} (${seed.name}) was skipped: a player town stands by it, or structures fill its core" }
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** One key for every write, so they land in the order they were decided. */
    const val JOB_KEY = "empyrite-seeds"

    /** Room kept round where a structure stands, for the ground it covers. */
    const val FOOTPRINT_METRES = 3.0
  }
}
