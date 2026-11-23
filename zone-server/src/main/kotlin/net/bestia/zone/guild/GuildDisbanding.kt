package net.bestia.zone.guild

import net.bestia.zone.ecs.core.World

/** What a guild leaves behind in the slices above this one, such as its Strongholds, when it disbands. */
interface GuildDisbanding {

  /** The guild is gone. On the tick. */
  fun disbanded(world: World, guildId: Long, guildName: String)
}
