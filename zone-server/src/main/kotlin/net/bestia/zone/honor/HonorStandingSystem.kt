package net.bestia.zone.honor

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.honor.ecs.Honor
import net.bestia.zone.weather.EnvironmentalExposureSystem
import org.springframework.stereotype.Component

/**
 * Brings each master's shown honor up to date as losses heal, and marks a master below zero as
 * [StatusEffectId.DISHONORED] for everybody to see, until they are not.
 */
@Component
class HonorStandingSystem(
  private val honor: HonorService,
  private val statusEffects: StatusEffectService,
) : System {
  override val phase = Phase.UPKEEP
  override val after = setOf(EnvironmentalExposureSystem::class)
  override val schedule: Schedule = Schedule.EverySeconds(10f)

  override val reads: ComponentClassSet = emptySet()
  override val writes: ComponentClassSet = setOf(Honor::class, StatusEffects::class, IsStatusValueDirty::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Honor::class).each { master ->
      val shown = honor.refresh(get<Honor>())

      if (shown < 0) {
        statusEffects.applyEffect(world, master, StatusEffectId.DISHONORED, level = 1)
      } else {
        world.get(master, StatusEffects::class)?.removeEffect(StatusEffectId.DISHONORED.id)
      }
    }
  }
}
