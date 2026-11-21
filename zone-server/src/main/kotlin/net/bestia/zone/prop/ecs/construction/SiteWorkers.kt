package net.bestia.zone.prop.ecs.construction

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.GuildAffiliation
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.util.EntityId

/** Who may put work into a site. Anybody may help with a player's; only the guild works on a guild's own. */
object SiteWorkers {

  val READS: ComponentClassSet = setOf(GuildOwned::class) + GuildAffiliation.READS

  fun mayWork(world: World, workerId: EntityId, siteId: EntityId): Boolean {
    val guildId = world.get(siteId, GuildOwned::class)?.guildId ?: return true

    return GuildAffiliation.membershipOf(world, workerId)?.guildId == guildId
  }
}
