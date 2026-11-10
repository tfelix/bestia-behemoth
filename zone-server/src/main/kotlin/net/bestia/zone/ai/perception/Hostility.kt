package net.bestia.zone.ai.perception

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId

/**
 * Whom a creature sees as an enemy, until real factions exist (the profile's `faction` is not read yet).
 *
 * Masters are everyone's enemy. What a player owns is the wild's enemy too, so a bestia out on its own can be
 * spotted and attacked; another player's bestia is not, or two parked bestias would fight on sight.
 */
object Hostility {

  val READS: ComponentClassSet = setOf(Master::class, OwnedBestia::class, StatusEffects::class, AiAgent::class)

  fun isHostile(world: World, self: EntityId, candidate: EntityId): Boolean {
    // A controlled creature has changed sides: it hunts the wild it came from and spares its controller's side.
    if (isControlled(world, self)) return isWild(world, candidate) && !isControlled(world, candidate)
    if (isControlled(world, candidate)) return isWild(world, self)

    if (world.has(candidate, Master::class)) return true

    return isWild(world, self) && world.has(candidate, OwnedBestia::class)
  }

  /** What a guard drives out of its area: whatever it sees as hostile anyway, and the wild that hunts on sight. */
  fun isIntruder(world: World, guard: EntityId, candidate: EntityId): Boolean {
    if (isHostile(world, guard, candidate)) return true

    return isWild(world, candidate) && !isControlled(world, candidate) && huntsOnSight(world, candidate)
  }

  /** A wolf rather than a deer: a grazer that only fights back is no intruder until it attacks. */
  private fun huntsOnSight(world: World, id: EntityId): Boolean {
    return world.get(id, AiAgent::class)?.goals?.any { it.name == BestiaDomain.Goals.KILL_ENEMY.name } == true
  }

  private fun isWild(world: World, id: EntityId): Boolean {
    return !world.has(id, OwnedBestia::class) && !world.has(id, Master::class)
  }

  private fun isControlled(world: World, id: EntityId): Boolean {
    return world.get(id, StatusEffects::class)?.hasEffect(StatusEffectId.CONTROLLED.id) ?: false
  }
}
