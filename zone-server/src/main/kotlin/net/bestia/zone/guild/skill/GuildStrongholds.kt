package net.bestia.zone.guild.skill

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Where the guilds' Strongholds are. The skills that work only inside one ask here. */
interface GuildStrongholds {
  fun isInOwnStronghold(world: World, entityId: EntityId, guildId: Long): Boolean
}
