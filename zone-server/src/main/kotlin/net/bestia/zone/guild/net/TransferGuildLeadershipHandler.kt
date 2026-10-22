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
class TransferGuildLeadershipHandler(
  private val guildService: GuildService,
  private val publisher: GuildInfoPublisher,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<TransferGuildLeadershipCMSG> {

  override val wire = decoder(MessageCase.TRANSFER_GUILD_LEADERSHIP) { accountId, envelope ->
    TransferGuildLeadershipCMSG.fromBnet(accountId, envelope.transferGuildLeadership)
  }

  override fun handle(msg: TransferGuildLeadershipCMSG): Boolean {
    try {
      val guildId = guildService.transferLeadership(msg.playerId, msg.masterId)

      publisher.request(guildId)
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}
