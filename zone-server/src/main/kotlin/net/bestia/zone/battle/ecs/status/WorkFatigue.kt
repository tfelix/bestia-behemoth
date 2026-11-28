package net.bestia.zone.battle.ecs.status

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

/**
 * What work costs an owned bestia in stamina: each prop it picks, each voxel it mines, each thing it makes at a
 * station. Its `staminaDrainReduction` - a worker's meal, a tough body - takes its share off, as for the weather.
 *
 * Only an owned bestia pays. A master's own work is paid for in its time at the keyboard, and a wild bestia does
 * not work.
 */
@Component
class WorkFatigue(
  private val config: StaminaConfig,
) {

  enum class Work { GATHER, MINE }

  fun charge(world: World, workerId: EntityId, work: Work) {
    charge(world, workerId, when (work) {
      Work.GATHER -> config.work.gather
      Work.MINE -> config.work.mine
    }.toDouble())
  }

  /** One item made at a station in [craftSeconds]. */
  fun chargeCraft(world: World, workerId: EntityId, craftSeconds: Float) {
    charge(world, workerId, config.work.craftPerTenSeconds * craftSeconds / TEN_SECONDS)
  }

  private fun charge(world: World, workerId: EntityId, points: Double) {
    if (points <= 0.0 || !world.has(workerId, OwnedBestia::class)) return
    val stamina = world.get(workerId, Stamina::class) ?: return

    val reduction = world.get(workerId, CombatStats::class)?.values?.staminaDrainReduction ?: 0
    val cost = (points * (100 - reduction.coerceIn(0, 100)) / 100).roundToInt().coerceAtLeast(1)

    stamina.current -= cost
  }

  companion object {
    private const val TEN_SECONDS = 10f

    val READS: ComponentClassSet = setOf(OwnedBestia::class, CombatStats::class)
    val WRITES: ComponentClassSet = setOf(Stamina::class)
  }
}
