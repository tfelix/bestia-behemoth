package net.bestia.zone.guild

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World

/** What a guild leaves behind in the slices above this one, such as its Strongholds, when it disbands. */
interface GuildDisbanding {

  /** Why the guild may not disband yet, or null. Under the world's lease. */
  fun refusal(world: World, guildId: Long): OpError?

  /** The guild is gone. On the tick. */
  fun disbanded(world: World, guildId: Long, guildName: String)
}
