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
class DisbandGuildHandler(
  private val guildService: GuildService,
  private val publisher: GuildInfoPublisher,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<DisbandGuildCMSG> {

  override val wire = decoder(MessageCase.DISBAND_GUILD) { accountId, envelope ->
    DisbandGuildCMSG.fromBnet(accountId, envelope.disbandGuild)
  }

  override fun handle(msg: DisbandGuildCMSG): Boolean {
    try {
      val disbanded = guildService.disband(msg.playerId, msg.confirmName)

      publisher.disbanded(disbanded.guildId, disbanded.members)
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}
