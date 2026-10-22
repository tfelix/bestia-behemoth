package net.bestia.zone.guild.ecs

import net.bestia.bnet.proto.GuildTagComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class GuildTagComponentSMSG(
  override val entityId: Long,
  val guildId: Long,
  val guildName: String,
  val rankTitle: String,
  val emblemHash: String,
  val removed: Boolean,
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val tag = GuildTagComponentSMSGProto.GuildTagComponentSMSG.newBuilder()
      .setGuildId(guildId)
      .setGuildName(guildName)
      .setRankTitle(rankTitle)
      .setRemoved(removed)
      .setEmblemHash(emblemHash)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setGuildTag(tag))
  }
}
