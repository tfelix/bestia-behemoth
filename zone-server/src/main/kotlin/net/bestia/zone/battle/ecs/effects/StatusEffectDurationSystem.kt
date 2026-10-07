package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/**
 * Ticks down every active effect's remaining duration and removes expired ones, then runs the effects that
 * act over time. Ordered before [StatusValueRecalcSystem] so an effect that just expired is already gone
 * before status values get rebuilt.
 */
@SpringComponent
class StatusEffectDurationSystem(
  private val statusEffectService: StatusEffectService,
  private val definitions: StatusEffectDefinitionRegistry,
  private val scripts: StatusEffectScriptRegistry,
) : System {
  override val phase = Phase.STATUS

  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val writes: ComponentClassSet = setOf(StatusEffects::class, IsStatusValueDirty::class)

  override fun update(world: World, deltaTime: Float) {
    val due = mutableListOf<DueTick>()

    world.query(StatusEffects::class).each { id ->
      val effects = get<StatusEffects>()

      // tickDown marks the component dirty itself if any effect expired.
      val expired = effects.tickDown(deltaTime)
      if (expired) {
        world.add(id, IsStatusValueDirty)
      }

      for (effect in effects.activeEffects) {
        collectDueTicks(id, effect, deltaTime, due)
      }
    }

    // After every system of this tick, because a script may touch any entity and no declared read covers that.
    if (due.isNotEmpty()) {
      world.defer { runTicks(world, due) }
    }
  }

  private fun collectDueTicks(hostId: EntityId, effect: ActiveStatusEffect, deltaTime: Float, due: MutableList<DueTick>) {
    val script = scriptOf(effect) ?: return
    val interval = script.tickIntervalSeconds ?: return

    effect.sinceLastTick += deltaTime
    while (effect.sinceLastTick >= interval) {
      effect.sinceLastTick -= interval
      due.add(DueTick(hostId, effect.level, script))
    }
  }

  private fun scriptOf(effect: ActiveStatusEffect): StatusEffectScript? {
    val definition = definitions.findById(effect.definitionId) ?: return null

    return scripts.get(definition.script)
  }

  private fun runTicks(world: World, due: List<DueTick>) {
    for (tick in due) {
      if (!world.isAlive(tick.hostId)) {
        continue
      }

      tick.script.onTick(StatusEffectTickContext(world, tick.hostId, tick.level, statusEffectService))
    }
  }

  private class DueTick(val hostId: EntityId, val level: Int, val script: StatusEffectScript)
}
