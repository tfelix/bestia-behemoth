package net.bestia.zone.prop.ecs.construction

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.util.EntityId

/** Who may put work into a site. Anybody may help with a player's; only the guild works on a guild's own. */
object SiteWorkers {

  val READS: ComponentClassSet = setOf(GuildOwned::class, GuildMembership::class, Account::class)

  fun mayWork(world: World, workerId: EntityId, siteId: EntityId): Boolean {
    val guildId = world.get(siteId, GuildOwned::class)?.guildId ?: return true

    return guildOf(world, workerId) == guildId
  }

  /** A master carries its guild; a bestia's is its owner's, found among the masters in the world. */
  private fun guildOf(world: World, workerId: EntityId): Long? {
    world.get(workerId, GuildMembership::class)?.let { return it.guildId }
    val accountId = world.get(workerId, Account::class)?.accountId ?: return null

    var guildId: Long? = null
    world.query(GuildMembership::class, Account::class).each { _ ->
      if (get<Account>().accountId == accountId) guildId = get<GuildMembership>().guildId
    }

    return guildId
  }
}
