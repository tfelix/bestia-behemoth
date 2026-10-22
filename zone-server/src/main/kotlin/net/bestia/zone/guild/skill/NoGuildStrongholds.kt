package net.bestia.zone.guild.skill

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** No guild can found a Stronghold yet, so nobody is ever inside their own. */
@Component
class NoGuildStrongholds : GuildStrongholds {

  override fun isInOwnStronghold(world: World, entityId: EntityId, guildId: Long): Boolean {
    return false
  }
}
