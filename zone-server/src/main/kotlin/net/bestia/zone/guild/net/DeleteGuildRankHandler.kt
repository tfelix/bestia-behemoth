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
class DeleteGuildRankHandler(
  private val guildService: GuildService,
  private val publisher: GuildInfoPublisher,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<DeleteGuildRankCMSG> {

  override val wire = decoder(MessageCase.DELETE_GUILD_RANK) { accountId, envelope ->
    DeleteGuildRankCMSG.fromBnet(accountId, envelope.deleteGuildRank)
  }

  override fun handle(msg: DeleteGuildRankCMSG): Boolean {
    try {
      publisher.request(guildService.deleteRank(msg.playerId, msg.rankId))
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}
