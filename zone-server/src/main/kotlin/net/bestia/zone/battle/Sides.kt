package net.bestia.zone.battle

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId

/**
 * Which side of a fight an entity stands on: the players' (masters, their bestia and whatever they control) or the
 * wild's. An area heal looks after its own side and a debuff zone hurts the other one.
 */
object Sides {

  val READS: ComponentClassSet = setOf(Master::class, OwnedBestia::class, StatusEffects::class)

  fun areAllied(world: World, a: EntityId, b: EntityId): Boolean {
    return isPlayerSide(world, a) == isPlayerSide(world, b)
  }

  private fun isPlayerSide(world: World, id: EntityId): Boolean {
    return world.has(id, Master::class) || world.has(id, OwnedBestia::class) ||
        world.get(id, StatusEffects::class)?.hasEffect(StatusEffectId.CONTROLLED.id) == true
  }
}
