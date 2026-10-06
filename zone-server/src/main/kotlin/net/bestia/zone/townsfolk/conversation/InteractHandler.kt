package net.bestia.zone.townsfolk.conversation

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.ecs.core.World
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

/**
 * Opens a conversation with whoever was clicked.
 *
 * The acting entity comes from the session and never from the message; the id the client sends names
 * the *target*.
 */
@Component
class InteractHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val deadActionGuard: DeadActionGuard,
  private val talk: TalkService,
) : TickMessageHandler<InteractCMSG> {

  override val wire = decoder(MessageCase.INTERACT) { accountId, envelope ->
    InteractCMSG.fromBnet(accountId, envelope.interact)
  }

  override fun handle(world: World, msg: InteractCMSG): Boolean {
    val actor = connectionInfoService.getActiveEntityId(msg.playerId)
    if (deadActionGuard.refuses(world, actor, "talk")) {
      return true
    }

    talk.open(msg.playerId, actor, msg.targetEntityId)

    return true
  }
}
