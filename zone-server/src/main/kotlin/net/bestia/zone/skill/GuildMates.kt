package net.bestia.zone.skill

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/**
 * Who a guild skill reaches: the masters and bestias of a guild's members that are in the world. Walks the
 * players' entities rather than the spatial index - a few hundred of them, against a box per member.
 */
object GuildMates {

  val reads: ComponentClassSet = setOf(
    GuildMembership::class, Master::class, OwnedBestia::class, Account::class, ActivePlayer::class, Position::class,
  )

  /** Everything of [guildId]'s members within [radiusMetres] of [centre]. Height is ignored. */
  fun near(world: World, guildId: Long, centre: Vec3L, radiusMetres: Long): List<EntityId> {
    val masters = mastersOf(world, guildId)
    val near = mutableListOf<EntityId>()

    world.query(Account::class, Position::class).each { id ->
      val masterId = world.get(id, Master::class)?.masterId ?: world.get(id, OwnedBestia::class)?.masterId
      if (masterId in masters && isWithin(centre, get<Position>(), radiusMetres)) {
        near.add(id)
      }
    }

    return near
  }

  /** What each member of [guildId] who is being played plays right now. */
  fun playedBy(world: World, guildId: Long): Map<AccountId, EntityId> {
    val accounts = mutableSetOf<AccountId>()
    world.query(GuildMembership::class, Account::class).each { _ ->
      if (get<GuildMembership>().guildId == guildId) {
        accounts.add(get<Account>().accountId)
      }
    }

    val played = mutableMapOf<AccountId, EntityId>()
    world.query(ActivePlayer::class, Account::class).each { id ->
      val accountId = get<Account>().accountId
      if (accountId in accounts) {
        played[accountId] = id
      }
    }

    return played
  }

  private fun mastersOf(world: World, guildId: Long): Set<Long> {
    val masters = mutableSetOf<Long>()
    world.query(GuildMembership::class, Master::class).each { _ ->
      if (get<GuildMembership>().guildId == guildId) {
        masters.add(get<Master>().masterId)
      }
    }

    return masters
  }

  private fun isWithin(centre: Vec3L, at: Position, radiusMetres: Long): Boolean {
    val dx = at.x - centre.x
    val dy = at.y - centre.y

    return dx * dx + dy * dy <= radiusMetres * radiusMetres
  }
}
