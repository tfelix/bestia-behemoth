package net.bestia.zone.world.fire

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.GenRng
import net.bestia.zone.ecs.core.World
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.environment.weather.WeatherService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Grass fires: where they are burning, where they spread to, and what they leave behind.
 *
 * ### Two ways in, because two kinds of caller
 *
 * The live state is a plain `HashMap` and this is a tick-thread service, so every entry point has to say how it
 * gets there.
 *
 * [ignite] is **immediate and requires the world lock** - either the tick thread itself, or a
 * `world.read`/`world.modify` scope, which the tick holds for its whole duration. That is how a skill reaches
 * it: `BudgetedSkillWorld.igniteGroundFire` wraps it exactly as it wraps `spawnAreaEffect`, and a skill needs
 * the answer straight away to know whether anything caught.
 *
 * [requestIgnition] is for a caller with **no lock at all** - a chat command on a Netty worker thread - and is
 * drained at the top of the next [step]. `Command`/`CommandQueue` would be the framework answer, and this is
 * not it for one reason: that queue has no production implementations yet, and a debug command is a poor place
 * to be the first exercise of an untested mechanism.
 *
 * ### Determinism
 *
 * Every roll goes through `GenRng.hashUnit` over `(seed, fireId, cell, stepIndex)` rather than a `Random`, so
 * a fire is reproducible and a test can pin its shape byte for byte. That is the same discipline the world
 * generator holds itself to, and it is what makes "does this spread downwind" a testable question rather than
 * an observation.
 */
@Service
class GroundFireService(
  private val config: GroundFireConfig,
  private val fuel: BurnableGround,
  private val weather: WeatherService,
  private val scorch: ScorchRegistry,
  private val overlay: GroundOverlayService,
  private val damage: GroundFireDamage,
  private val worldService: WorldService,
  private val clock: BestiaClock,
) {

  private val fires = LinkedHashMap<Long, GroundFire>()

  /** Ignitions offered by threads holding no lock. See the class note. */
  private val requested = java.util.concurrent.ConcurrentLinkedQueue<Requested>()

  private var nextId = 1L
  private var stepIndex = 0L

  private class Requested(
    val centre: Vec3L,
    val radiusTiles: Long,
    val casterId: EntityId,
    val skillId: Long,
    val skillLevel: Int
  )

  val activeFires get() = fires.size
  val burningCells get() = fires.values.sumOf { it.burning.size }

  /** Whether anything at all needs stepping, so the system can early-return. */
  val isIdle get() = fires.isEmpty() && requested.isEmpty()

  /**
   * Asks for a fire from a thread that holds no world lock; it starts on the next [step].
   *
   * Deliberately returns nothing. The caller is off the tick thread, so "did it catch" is not a question that
   * can be answered synchronously - and a debug trigger that pretended otherwise would report success for a
   * fire that never started.
   */
  fun requestIgnition(centre: Vec3L, radiusTiles: Long, casterId: EntityId, skillId: Long, skillLevel: Int) {
    requested.add(Requested(centre, radiusTiles, casterId, skillId, skillLevel))
  }

  /**
   * Starts a fire centred on a tile, on whatever ground within [radiusTiles] will take it.
   *
   * @param radiusTiles a **radius**, not a cube edge. The conversion to `AreaEffect`'s edge happens once, in
   *   [GroundFireDamage], and this is the only place a radius is named - the `edge = radius * 2` confusion is
   *   documented as a real hazard in this codebase and one lattice with one convention is how it is avoided.
   * @return the new fire's id, or null if nothing there would burn or the concurrent-fire cap refused it
   */
  fun ignite(centre: Vec3L, radiusTiles: Long, casterId: EntityId, skillId: Long, skillLevel: Int): Long? {
    if (fires.size >= config.maxConcurrentFires) {
      LOG.warn {
        "refusing to ignite at (${centre.x}, ${centre.y}): ${fires.size} fires already burning, " +
            "cap is ${config.maxConcurrentFires}"
      }
      return null
    }

    val fire = GroundFire(
      id = nextId,
      casterId = casterId,
      skillId = skillId,
      skillLevel = skillLevel,
      startedAtSecond = clock.now().absoluteSecond
    )

    for (dy in -radiusTiles..radiusTiles) {
      for (dx in -radiusTiles..radiusTiles) {
        val x = centre.x + dx
        val y = centre.y + dy
        if (fuel.fuelAt(x, y) <= 0.0) continue
        if (isScorched(x, y)) continue
        fire.ignite(x, y)
      }
    }

    if (fire.isOut) return null

    nextId++
    fires[fire.id] = fire
    return fire.id
  }

  /**
   * Advances every fire whose accumulator has come due, under [GroundFireConfig.cellStepBudgetPerTick].
   *
   * A fire that misses its turn keeps its accumulator, so a step is deferred rather than lost - the property
   * `AreaEffectSystem` gets from its own `while` loop, and the reason the budget is safe to apply here.
   */
  fun step(world: World, deltaTime: Float) {
    while (true) {
      val request = requested.poll() ?: break
      ignite(request.centre, request.radiusTiles, request.casterId, request.skillId, request.skillLevel)
    }

    if (fires.isEmpty()) return

    var budget = config.cellStepBudgetPerTick
    var skipped = 0

    val finished = ArrayList<Long>()

    for (fire in fires.values) {
      fire.ageSeconds += deltaTime
      fire.sinceLastStep += deltaTime
      fire.sinceLastDamage += deltaTime

      if (fire.ageSeconds >= config.maxLifetimeSeconds) {
        LOG.warn {
          "fire ${fire.id} hit the ${config.maxLifetimeSeconds}s lifetime cap with ${fire.burning.size} " +
              "cells still alight; it should have run out of fuel or been rained on long before now"
        }
        extinguish(fire)
        finished.add(fire.id)
        continue
      }

      if (fire.sinceLastDamage >= config.damageIntervalSeconds) {
        fire.sinceLastDamage = 0f
        damage.apply(world, fire)
      }

      if (fire.sinceLastStep < config.stepSeconds) continue

      if (fire.burning.size > budget) {
        skipped++
        continue
      }

      fire.sinceLastStep = 0f
      budget -= fire.burning.size
      advance(fire)

      if (fire.isOut) finished.add(fire.id)
    }

    finished.forEach { fires.remove(it) }
    stepIndex++

    if (skipped > 0) {
      LOG.debug { "$skipped fire(s) deferred a step: ${config.cellStepBudgetPerTick} cell budget spent" }
    }
  }

  /** One cellular-automaton step: cells age out into scorch, then survivors try their neighbours. */
  private fun advance(fire: GroundFire) {
    val here = weather.at(fire.centreX, fire.centreY, ELEVATION_METRES, clock.now())
    val state = here.state

    // A downpour puts a fire out rather than merely stopping its spread, which is what makes running for the
    // rain a real answer to being on fire.
    if (state.intensity >= config.downpourIntensity) {
      LOG.debug { "fire ${fire.id} extinguished by rain at intensity ${"%.2f".format(state.intensity)}" }
      extinguish(fire)
      return
    }

    // `windDirection` is the direction of *travel* - 0 pointing east, counter-clockwise - so the downwind
    // neighbour lies along +(cos, sin). The bearing the weather *arrives from* is the opposite one, and
    // getting that backwards makes every fire run upwind.
    val windX = cos(state.windDirection)
    val windY = sin(state.windDirection)
    val windStrength = (state.windSpeed / config.windReferenceSpeed).coerceIn(0.0, 1.0)

    val burntOut = ArrayList<Long>()
    val caught = ArrayList<Long>()
    var dropped = 0

    for ((cell, steps) in fire.burning) {
      val x = GroundFire.unpackX(cell)
      val y = GroundFire.unpackY(cell)

      if (steps + 1 >= config.burnSteps) {
        burntOut.add(cell)
        continue
      }
      fire.burning[cell] = steps + 1

      for (direction in NEIGHBOURS) {
        // Both caps counted against what this step has *already* accepted, not against the state at the top of
        // the cell. Checked once per cell instead, each surviving cell could add up to eight more past the
        // limit, so the overshoot grew with the width of the front rather than being bounded at all.
        if (fire.everIgnited + caught.size >= config.maxBurningCellsPerFire) break
        if (caught.size >= config.maxIgnitionsPerStep) {
          dropped++
          break
        }

        val nx = x + direction.dx
        val ny = y + direction.dy
        if (fire.isBurning(nx, ny) || isScorched(nx, ny)) continue

        val fuelHere = fuel.fuelAt(nx, ny)
        if (fuelHere <= 0.0) continue

        val downwind = (direction.ux * windX + direction.uy * windY).coerceAtLeast(0.0)
        val windTerm = 1.0 + config.windGain * downwind * windStrength
        val suppression = (1.0 - config.rainGain * state.intensity).coerceAtLeast(0.0)

        val chance = config.baseIgnition * fuelHere * state.dryness * windTerm * suppression
        if (GenRng.hashUnit(seed(), fire.id, GroundFire.pack(nx, ny), stepIndex) < chance) {
          caught.add(GroundFire.pack(nx, ny))
        }
      }
    }

    burntOut.forEach { fire.burning.remove(it) }
    scorchAll(fire, burntOut)

    // Re-read per cell rather than once, for the reason above: a batch checked against a single snapshot can
    // step over the ceiling by the size of the batch.
    for (cell in caught) {
      if (burningCells >= config.maxBurningCellsTotal) {
        LOG.warn { "global burning-cell cap ${config.maxBurningCellsTotal} reached; fires will stop spreading" }
        break
      }
      fire.ignite(GroundFire.unpackX(cell), GroundFire.unpackY(cell))
    }

    if (dropped > 0) {
      LOG.debug { "fire ${fire.id} deferred ignitions on $dropped cell(s): step cap ${config.maxIgnitionsPerStep}" }
    }
    if (fire.everIgnited >= config.maxBurningCellsPerFire) {
      LOG.info {
        "fire ${fire.id} reached the ${config.maxBurningCellsPerFire}-cell cap; it will burn down what it " +
            "holds and stop spreading"
      }
    }

    markDirty(fire)
  }

  /** Every burning cell becomes scorch immediately, which is what an extinguished fire leaves behind. */
  private fun extinguish(fire: GroundFire) {
    scorchAll(fire, fire.burning.keys.toList())
    fire.burning.clear()
    markDirty(fire)
  }

  /**
   * Records burnt-out cells against the scorch store, grouped by the column they fall in.
   *
   * Stamped with the **fire's** start rather than now, so every column one fire touched shares a window and a
   * multi-chunk scar heals as one scar rather than in chunk-shaped steps. See [ScorchMark].
   */
  private fun scorchAll(fire: GroundFire, cells: List<Long>) {
    if (cells.isEmpty()) return

    val chunkSize = worldService.config.chunkSize
    val byColumn = HashMap<Long, ColumnMask>()

    for (cell in cells) {
      val x = GroundFire.unpackX(cell)
      val y = GroundFire.unpackY(cell)
      val chunkX = Math.floorDiv(x, chunkSize.toLong()).toInt()
      val chunkY = Math.floorDiv(y, chunkSize.toLong()).toInt()
      val column = ScorchRegistry.columnKeyOf(chunkX, chunkY)

      byColumn.getOrPut(column) { ColumnMask(chunkSize) }.set(
        Math.floorMod(x, chunkSize.toLong()).toInt(),
        Math.floorMod(y, chunkSize.toLong()).toInt()
      )
    }

    byColumn.forEach { (column, mask) ->
      scorch.burn(column, mask, fire.startedAtSecond)
      overlay.markDirty(column)
    }
  }

  /** Every column the fire's live cells fall in, so the burning mask is re-announced. */
  private fun markDirty(fire: GroundFire) {
    val chunkSize = worldService.config.chunkSize
    val seen = HashSet<Long>()

    for (cell in fire.burning.keys) {
      val chunkX = Math.floorDiv(GroundFire.unpackX(cell), chunkSize.toLong()).toInt()
      val chunkY = Math.floorDiv(GroundFire.unpackY(cell), chunkSize.toLong()).toInt()
      if (seen.add(ScorchRegistry.columnKeyOf(chunkX, chunkY))) {
        overlay.markDirty(ScorchRegistry.columnKeyOf(chunkX, chunkY))
      }
    }
  }

  /** The burning cells of one chunk column, for the overlay. Empty when nothing there is alight. */
  fun burningIn(chunkX: Int, chunkY: Int): ColumnMask? {
    if (fires.isEmpty()) return null

    val chunkSize = worldService.config.chunkSize
    val originX = chunkX.toLong() * chunkSize
    val originY = chunkY.toLong() * chunkSize

    var mask: ColumnMask? = null
    for (fire in fires.values) {
      for (cell in fire.burning.keys) {
        val localX = GroundFire.unpackX(cell) - originX
        val localY = GroundFire.unpackY(cell) - originY
        if (localX !in 0 until chunkSize || localY !in 0 until chunkSize) continue

        val target = mask ?: ColumnMask(chunkSize).also { mask = it }
        target.set(localX.toInt(), localY.toInt())
      }
    }
    return mask
  }

  private fun isScorched(voxelX: Long, voxelY: Long): Boolean {
    val chunkSize = worldService.config.chunkSize
    val chunkX = Math.floorDiv(voxelX, chunkSize.toLong()).toInt()
    val chunkY = Math.floorDiv(voxelY, chunkSize.toLong()).toInt()

    val scar = scorch.scarOf(ScorchRegistry.columnKeyOf(chunkX, chunkY)) ?: return false

    // `visible`, not `mask`: ground that has healed may burn again, which is what makes a scar temporary
    // rather than a permanent firebreak.
    return scar.visible[
      Math.floorMod(voxelX, chunkSize.toLong()).toInt(),
      Math.floorMod(voxelY, chunkSize.toLong()).toInt()
    ]
  }

  private fun seed(): Long = worldService.record.seed

  /** The eight neighbours, with the **normalised** offset each one lies along. */
  private class Neighbour(val dx: Long, val dy: Long) {
    val ux: Double
    val uy: Double

    init {
      val length = sqrt((dx * dx + dy * dy).toDouble())
      ux = dx / length
      uy = dy / length
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    /** Sea level. Only feeds the temperature, which only decides rain versus snow - `RainAccumulator`'s note. */
    const val ELEVATION_METRES = 0.0

    /**
     * The eight neighbours.
     *
     * **The offsets are normalised**, which is what stops the front coming out a diamond. Dotted unnormalised
     * against the wind, a diagonal scores `±1 ±1` against a cardinal's `±1`, so the diagonals would spread
     * about 1.4 times too readily and the fire would grow as a rotated square. Normalising gives them `1/√2`,
     * which is the actual projection. The unnormalised version looks perfectly reasonable and is wrong.
     */
    val NEIGHBOURS = listOf(
      Neighbour(1, 0), Neighbour(-1, 0), Neighbour(0, 1), Neighbour(0, -1),
      Neighbour(1, 1), Neighbour(1, -1), Neighbour(-1, 1), Neighbour(-1, -1)
    )
  }
}
