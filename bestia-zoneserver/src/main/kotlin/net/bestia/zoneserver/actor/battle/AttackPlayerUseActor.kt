package net.bestia.zoneserver.actor.battle

import net.bestia.messages.attack.AttackUseMessage
import net.bestia.messages.entity.EntitySkillUseMessage
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.entity.PlayerEntityService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

/**
 * This actor simply performs some safety checks for incoming player attack
 * messages and then returns a appropriate [EntitySkillUseMessage] back to
 * the sender which will use it to perform the skill/attack.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class AttackPlayerUseActor(
        private val playerEntityService: PlayerEntityService
) : BaseClientMessageRouteActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder.match(AttackUseMessage::class.java, this::handleAttackMessage)
  }

  /**
   * Transforms the message from the player into usable message and replies it
   * back.
   *
   * @param msg
   */
  private fun handleAttackMessage(msg: AttackUseMessage) {
    val pbe = playerEntityService.getActivePlayerEntity(msg.accountId)

    // Does the target of the attack matches the target provided in the
    // message?
    // If this is not the case by the user send data dont perform the
    // attack.
    val skillMsg = EntitySkillUseMessage(pbe!!.id,
            msg.attackId,
            msg.targetEntityId)

    sender.tell(skillMsg, self)
  }

  companion object {
    const val NAME = "attackPlayerUse"
  }
}
