package net.bestia.zone.guild.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.guild.GuildException
import net.bestia.zone.guild.GuildInfoPublisher
import net.bestia.zone.guild.GuildService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

/**
 * Founds a guild with a Founder's Seal from the master's bag. The client sends this from the seal's naming
 * popup rather than using the item, see `FoundersSealScript`.
 */
@Component
class CreateGuildHandler(
  private val guildService: GuildService,
  private val publisher: GuildInfoPublisher,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<CreateGuildCMSG> {

  override val wire = decoder(MessageCase.CREATE_GUILD) { accountId, envelope ->
    CreateGuildCMSG.fromBnet(accountId, envelope.createGuild)
  }

  override fun handle(msg: CreateGuildCMSG): Boolean {
    try {
      publisher.request(guildService.found(msg.playerId, msg.name))
    } catch (e: GuildException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}
