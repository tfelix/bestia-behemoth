package net.bestia.zone.skill

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId

/**
 * Who a party-wide skill reaches: every party member in visible range and the bestias they own. A caster without a
 * party reaches only itself and its own master's bestias. Walks the players' entities rather than the spatial index,
 * like [GuildMates]: a few hundred of them, against a box per member.
 */
object PartyMates {

  /** How far "in visible range" is: about what the camera shows around the caster. */
  const val VISIBLE_RANGE_METRES = 30L

  val reads: ComponentClassSet = setOf(PartyMembership::class, Master::class, OwnedBestia::class, Account::class, Position::class)

  /** Every master and bestia of [casterId]'s party within [radiusMetres] of [centre], the caster included. */
  fun near(world: World, casterId: EntityId, centre: Vec3L, radiusMetres: Long = VISIBLE_RANGE_METRES): List<EntityId> {
    val casterMasterId = masterIdOf(world, casterId) ?: return listOf(casterId)
    val masters = partyMasterIdsOf(world, casterMasterId) + casterMasterId

    val near = mutableListOf<EntityId>()
    world.query(Account::class, Position::class).each { id ->
      if (masterIdOf(world, id) in masters && isWithin(centre, get<Position>(), radiusMetres)) {
        near.add(id)
      }
    }

    return near
  }

  private fun masterIdOf(world: World, entityId: EntityId): Long? {
    return world.get(entityId, Master::class)?.masterId ?: world.get(entityId, OwnedBestia::class)?.masterId
  }

  /** The party is kept on the master's entity; a bestia's caster finds it there. */
  private fun partyMasterIdsOf(world: World, masterId: Long): Set<Long> {
    var members = emptySet<Long>()
    world.query(Master::class, PartyMembership::class).each { _ ->
      if (get<Master>().masterId == masterId) {
        members = get<PartyMembership>().members.map { it.masterId }.toSet()
      }
    }

    return members
  }

  /** Height is ignored, as for a guild skill. */
  private fun isWithin(centre: Vec3L, position: Position, radiusMetres: Long): Boolean {
    val dx = position.x - centre.x
    val dy = position.y - centre.y

    return dx * dx + dy * dy <= radiusMetres * radiusMetres
  }
}
