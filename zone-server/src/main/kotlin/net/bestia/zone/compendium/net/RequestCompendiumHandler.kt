package net.bestia.zone.compendium.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.compendium.CompendiumPublisher
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

/** The client asks for the compendium when its window opens. */
@Component
class RequestCompendiumHandler(
  private val publisher: CompendiumPublisher,
) : IoMessageHandler<RequestCompendiumCMSG> {

  override val wire = decoder(MessageCase.REQUEST_COMPENDIUM) { accountId, envelope ->
    RequestCompendiumCMSG.fromBnet(accountId, envelope.requestCompendium)
  }

  override fun handle(msg: RequestCompendiumCMSG): Boolean {
    publisher.sendTo(msg.playerId)

    return true
  }
}
