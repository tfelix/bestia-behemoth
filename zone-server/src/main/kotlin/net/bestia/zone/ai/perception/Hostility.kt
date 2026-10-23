package net.bestia.zone.ai.perception

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

  val READS: ComponentClassSet = setOf(Master::class, OwnedBestia::class)

  fun isHostile(world: World, self: EntityId, candidate: EntityId): Boolean {
    if (world.has(candidate, Master::class)) return true

    val isWild = !world.has(self, OwnedBestia::class)
    return isWild && world.has(candidate, OwnedBestia::class)
  }
}
