package net.bestia.zone.battle.ecs.status

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.update
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * Charges the labour in the [LabourLedger] against each worker's stamina, and leaves it `WORN_OUT`, so that a
 * worker must eat to keep going: the debuff stops rest from bringing stamina back. See [StaminaRegenSystem].
 * Wild creatures carry a placeholder stamina nothing spends, so their work is free.
 */
@SpringComponent
class WorkStrainSystem(
  private val config: StaminaConfig,
  private val statusEffects: StatusEffectService,
  private val labour: LabourLedger,
) : System {
  override val phase = Phase.RECOVERY

  // Before regeneration, so the debuff already holds back the stamina regenerated in the same tick.
  override val before = setOf(StaminaRegenSystem::class)

  override val schedule: Schedule = Schedule.EverySeconds(1f)

  // Nature and CombatStats are what the status service weighs a debuff's immunity by.
  override val reads: ComponentClassSet =
    setOf(Dead::class, Master::class, OwnedBestia::class, Nature::class, CombatStats::class)
  override val writes: ComponentClassSet =
    setOf(WorkStrain::class, Stamina::class, StatusEffects::class, IsStatusValueDirty::class)

  override fun update(world: World, deltaTime: Float) {
    labour.drain().forEach { (workerId, seconds) ->
      if (feelsWork(world, workerId)) strain(world, workerId, seconds)
    }
  }

  private fun feelsWork(world: World, id: EntityId): Boolean {
    val alive = world.isAlive(id) && !world.has(id, Dead::class)

    return alive && (world.has(id, Master::class) || world.has(id, OwnedBestia::class))
  }

  private fun strain(world: World, workerId: EntityId, seconds: Float) {
    world.update(workerId, default = { WorkStrain() }) { strain ->
      strain.owedStamina += seconds * config.staminaPerLabourHour / SECONDS_PER_HOUR
      val whole = strain.owedStamina.toInt()
      if (whole > 0) {
        world.get(workerId, Stamina::class)?.let { it.current -= whole }
        strain.owedStamina -= whole
      }
    }
    statusEffects.applyEffect(world, workerId, StatusEffectId.WORN_OUT, level = 1)
  }

  private companion object {
    const val SECONDS_PER_HOUR = 3600.0
  }
}
