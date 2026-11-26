package net.bestia.zone.worldevent.rift

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.ground.fire.GroundFireService
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Component

/**
 * A rift wave that reaches a town where no master stands sets its fields alight, once per wave. The scorch is what
 * the town loses: its trades work less until the ground grows back, see `SettlementDamage`. A town burns; it does not
 * fall. On the tick, from [RiftHordes].
 */
@Component
class WaveFires(
  private val fires: GroundFireService,
  private val sites: SettlementSiteIndex,
) {

  /** Rifts whose latest wave already lit a fire. */
  private val lit = HashSet<Long>()

  fun newWave(riftId: Long) {
    lit.remove(riftId)
  }

  fun tend(world: World, riftId: Long, members: List<EntityId>) {
    if (riftId in lit) return

    val masters = masterPositions(world)
    val torch = members.asSequence()
      .filter { world.isAlive(it) }
      .mapNotNull { member -> world.get(member, Position::class)?.toVec3L()?.let { member to it } }
      .firstOrNull { (_, at) -> isInTown(at) && masters.none { it.distance(at) <= GUARD_METRES } }
      ?: return

    val (member, at) = torch
    if (fires.ignite(at, FIRE_RADIUS_TILES, casterId = member, skillId = NO_SKILL, skillLevel = 1) != null) {
      lit += riftId
    }
  }

  fun forget(riftId: Long) {
    lit.remove(riftId)
  }

  private fun isInTown(at: Vec3L): Boolean {
    return sites.siteCovering(at.x, at.y) != null
  }

  private fun masterPositions(world: World): List<Vec3L> {
    val found = ArrayList<Vec3L>()
    world.query(Master::class, Position::class).each { _ -> found += get<Position>().toVec3L() }
    return found
  }

  private companion object {
    /** A master this near stands between the wave and the fields. */
    const val GUARD_METRES = 150.0

    /** A small fire: the grass carries it from there, as far as the weather lets it. */
    const val FIRE_RADIUS_TILES = 2L

    const val NO_SKILL = 0L
  }
}
