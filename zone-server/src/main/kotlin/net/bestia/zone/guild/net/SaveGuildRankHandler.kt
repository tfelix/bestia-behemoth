package net.bestia.zone.guild.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.guild.GuildException
import net.bestia.zone.guild.GuildInfoPublisher
import net.bestia.zone.guild.GuildService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class SaveGuildRankHandler(
  private val guildService: GuildService,
  private val publisher: GuildInfoPublisher,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<SaveGuildRankCMSG> {

  override val wire = decoder(MessageCase.SAVE_GUILD_RANK) { accountId, envelope ->
    SaveGuildRankCMSG.fromBnet(accountId, envelope.saveGuildRank)
  }

  override fun handle(msg: SaveGuildRankCMSG): Boolean {
    try {
      val guildId = guildService.saveRank(
        accountId = msg.playerId,
        rankId = msg.rankId,
        rawName = msg.name,
        taxPercent = msg.taxPercent,
        canInvite = msg.canInvite,
        canKick = msg.canKick,
        canManageRanks = msg.canManageRanks,
        canUseGuildSkills = msg.canUseGuildSkills,
      )

      publisher.request(guildId)
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}
