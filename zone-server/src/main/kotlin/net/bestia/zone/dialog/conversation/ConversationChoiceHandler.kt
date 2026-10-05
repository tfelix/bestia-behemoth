package net.bestia.zone.dialog.conversation

import net.bestia.zone.ecs.battle.damage.DeadActionGuard
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.message.TickMessageHandler
import org.springframework.stereotype.Component

/** Answers whichever option the player picked. */
@Component
class ConversationChoiceHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val deadActionGuard: DeadActionGuard,
  private val talk: TalkService,
) : TickMessageHandler<ConversationChoiceCMSG> {

  override val handles = ConversationChoiceCMSG::class

  override fun handle(world: World, msg: ConversationChoiceCMSG): Boolean {
    val actor = connectionInfoService.getActiveEntityId(msg.playerId)
    if (deadActionGuard.refuses(world, actor, "talk")) {
      return true
    }

    talk.choose(msg.playerId, actor, msg.targetEntityId, msg.topicId)

    return true
  }
}
