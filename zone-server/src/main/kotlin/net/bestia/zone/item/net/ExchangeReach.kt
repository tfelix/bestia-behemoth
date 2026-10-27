package net.bestia.zone.item.net

import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import kotlin.math.abs

/**
 * How close two of a master's own entities must stand to hand each other goods: side by side, closer than a
 * trade (10) but without the fiddly positioning a pickup (1) asks for. The height limit keeps a bestia on the
 * cliff top from passing things down to the master below, which the horizontal [net.bestia.zone.geometry.Vec3L.distance]
 * alone would allow.
 */
object ExchangeReach {
  const val HORIZONTAL = 5L
  const val VERTICAL = 3L

  fun within(world: World, one: EntityId, other: EntityId): Boolean {
    val onePos = world.get(one, Position::class)?.toVec3L() ?: return false
    val otherPos = world.get(other, Position::class)?.toVec3L() ?: return false

    return onePos.distance(otherPos) <= HORIZONTAL && abs(onePos.z - otherPos.z) <= VERTICAL
  }
}
