package net.bestia.zone.guild.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.guild.GuildInfoPublisher
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

/** The client asks for its guild whenever its guild window loads; outside a guild it is answered with an empty one. */
@Component
class RequestGuildInfoHandler(
  private val publisher: GuildInfoPublisher,
) : IoMessageHandler<RequestGuildInfoCMSG> {

  override val wire = decoder(MessageCase.REQUEST_GUILD_INFO) { accountId, envelope ->
    RequestGuildInfoCMSG.fromBnet(accountId, envelope.requestGuildInfo)
  }

  override fun handle(msg: RequestGuildInfoCMSG): Boolean {
    publisher.sendTo(msg.playerId)

    return true
  }
}
