package net.bestia.zone.guild.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.Removable
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/**
 * The guild a master is in and its rank's title, for the name tag everyone nearby sees on hover. On the master
 * and its bestias; [GuildTagSystem] keeps it in step with the master's [GuildMembership].
 */
data class GuildTag(
  val guildId: Long,
  val guildName: String,
  val rankTitle: String,
) : Component, Removable {
  override val dirtyFlag = DirtyFlag()

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return GuildTagComponentSMSG(entityId, guildId, guildName, rankTitle, removed)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.PublicInRange
  }

  companion object {
    fun of(membership: GuildMembership): GuildTag {
      return GuildTag(membership.guildId, membership.guildName, membership.rankTitle)
    }
  }
}
