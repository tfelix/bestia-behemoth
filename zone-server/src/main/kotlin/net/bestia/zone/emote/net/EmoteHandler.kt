package net.bestia.zone.emote.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.emote.EmoteService
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

/** An emote fired from the client's shortcut bar or emote window. A typed `/cheer` takes the chat command instead. */
@Component
class EmoteHandler(
  private val emoteService: EmoteService,
) : TickMessageHandler<EmoteCMSG> {
  override val wire = decoder(MessageCase.EMOTE) { accountId, envelope ->
    EmoteCMSG.fromBnet(accountId, envelope.emote)
  }

  override fun handle(world: World, msg: EmoteCMSG): Boolean {
    val kind = msg.kind
    if (kind == null) {
      LOG.warn { "Player ${msg.playerId} asked for an emote this server does not know, ignoring" }
      return true
    }

    emoteService.perform(world, msg.playerId, kind)

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
