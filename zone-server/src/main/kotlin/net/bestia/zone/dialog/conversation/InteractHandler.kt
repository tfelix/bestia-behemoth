package net.bestia.zone.dialog.conversation

import net.bestia.zone.ecs.battle.damage.DeadActionGuard
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.message.InMessageProcessor
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
) : InMessageProcessor.IncomingMessageHandler<InteractCMSG> {

  override val handles = InteractCMSG::class

  override fun handle(msg: InteractCMSG): Boolean {
    val actor = connectionInfoService.getActiveEntityId(msg.playerId)
    if (deadActionGuard.refuses(actor, "talk")) {
      return true
    }

    talk.open(msg.playerId, actor, msg.targetEntityId)

    return true
  }
}
