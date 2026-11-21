package net.bestia.zone.identity.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Which guild an entity acts for: a master's own, or for a bestia the guild of the master that owns it. */
object GuildAffiliation {

  val READS: ComponentClassSet = setOf(GuildMembership::class, Account::class)

  fun membershipOf(world: World, entityId: EntityId): GuildMembership? {
    world.get(entityId, GuildMembership::class)?.let { return it }
    val accountId = world.get(entityId, Account::class)?.accountId ?: return null

    var membership: GuildMembership? = null
    world.query(GuildMembership::class, Account::class).each { _ ->
      if (get<Account>().accountId == accountId) membership = get<GuildMembership>()
    }

    return membership
  }
}
