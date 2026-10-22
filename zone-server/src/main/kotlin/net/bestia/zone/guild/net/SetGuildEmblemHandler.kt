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
class SetGuildEmblemHandler(
  private val guildService: GuildService,
  private val publisher: GuildInfoPublisher,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<SetGuildEmblemCMSG> {

  override val wire = decoder(MessageCase.SET_GUILD_EMBLEM) { accountId, envelope ->
    SetGuildEmblemCMSG.fromBnet(accountId, envelope.setGuildEmblem)
  }

  override fun handle(msg: SetGuildEmblemCMSG): Boolean {
    try {
      val guildId = guildService.setEmblem(msg.playerId, msg.png)

      publisher.request(guildId)
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}
