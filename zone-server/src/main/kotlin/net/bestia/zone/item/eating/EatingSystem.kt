package net.bestia.zone.item.eating

import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.effects.StatusEffectDurationSystem
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/** Counts down each meal being eaten and hands the finished one to [EatingService], like `DiggingSystem` does. */
@SpringComponent
class EatingSystem(
  private val eatingService: EatingService,
) : System {
  // In STATUS and before the effects run down, so a meal eaten this tick counts in this tick's status values.
  override val phase = Phase.STATUS
  override val before = setOf(StatusEffectDurationSystem::class)

  /** Laying on an effect asks who the eater is and how strong. */
  override val reads: ComponentClassSet = setOf(CombatStats::class, Level::class)

  /** A finished meal leaves the bag, moves the vitals and lays on its buff. */
  override val writes: ComponentClassSet = setOf(
    Eating::class, Inventory::class, Health::class, Stamina::class, Mana::class, StatusEffects::class,
    IsStatusValueDirty::class,
  )

  override fun update(world: World, deltaTime: Float) {
    var finished: MutableList<Pair<EntityId, Eating>>? = null

    world.query(Eating::class).each { id ->
      val eating = get<Eating>()
      eating.countdown(deltaTime)

      if (eating.hasElapsed()) {
        (finished ?: mutableListOf<Pair<EntityId, Eating>>().also { finished = it }).add(id to eating)
      }
    }

    finished?.forEach { (id, eating) ->
      world.remove(id, Eating::class)
      eatingService.finish(world, id, eating)
    }
  }
}
