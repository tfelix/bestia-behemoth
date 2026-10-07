package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 11 (`WARD_AURA`): the spell on a ward stone. Every pulse it gives [Warded] to
 * everything a player owns within [RADIUS_METRES] of the stone.
 */
@Component
class WardAura : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.IGNORE_IF_PRESENT

  override val tickIntervalSeconds: Float = PULSE_SECONDS

  /** The stone gets it again at every boot. */
  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return Double.POSITIVE_INFINITY
  }

  override fun onTick(context: StatusEffectTickContext) {
    val centre = standingPoint(context.world, context.hostId) ?: return

    for (id in playerOwnedNear(context.world, centre)) {
      context.applyEffect(id, StatusEffectId.WARDED, level = 1)
    }
  }

  /**
   * Walks what players own rather than an area query: a few hundred entities, where a box this wide would visit
   * tens of thousands of grid cells.
   */
  private fun playerOwnedNear(world: World, centre: Vec3L): List<EntityId> {
    val near = mutableListOf<EntityId>()
    val collect = { id: EntityId ->
      val at = standingPoint(world, id)
      if (at != null && isWithinRadius(centre, at)) {
        near.add(id)
      }
    }

    world.each(Account::class) { id, _ -> collect(id) }
    world.each(PlayerStructureIdentity::class) { id, _ -> collect(id) }

    return near
  }

  /** A station is a static entity and stands in [PropPose] until something promotes it. */
  private fun standingPoint(world: World, id: EntityId): Vec3L? {
    return world.get(id, Position::class)?.toVec3L() ?: world.get(id, PropPose::class)?.position
  }

  /** Height is ignored: the field reaches down a mine shaft and up a tower alike. */
  private fun isWithinRadius(centre: Vec3L, at: Vec3L): Boolean {
    val dx = at.x - centre.x
    val dy = at.y - centre.y

    return dx * dx + dy * dy <= RADIUS_METRES * RADIUS_METRES
  }

  private companion object {
    const val PULSE_SECONDS = 2f
    const val RADIUS_METRES = 3_500L
  }
}
