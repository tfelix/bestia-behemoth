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
class SetGuildMotdHandler(
  private val guildService: GuildService,
  private val publisher: GuildInfoPublisher,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<SetGuildMotdCMSG> {

  override val wire = decoder(MessageCase.SET_GUILD_MOTD) { accountId, envelope ->
    SetGuildMotdCMSG.fromBnet(accountId, envelope.setGuildMotd)
  }

  override fun handle(msg: SetGuildMotdCMSG): Boolean {
    try {
      val guildId = guildService.setMotd(msg.playerId, msg.title, msg.body)

      publisher.request(guildId)
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}
