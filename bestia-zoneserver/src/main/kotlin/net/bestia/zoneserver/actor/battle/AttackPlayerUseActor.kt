package net.bestia.zoneserver.actor.battle

import net.bestia.messages.attack.AttackUseMessage
import net.bestia.messages.entity.EntitySkillUseMessage
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.entity.PlayerEntityService

/**
 * This actor simply performs some safety checks for incoming player attack
 * messages and then returns a appropriate [EntitySkillUseMessage] back to
 * the sender which will use it to perform the skill/attack.
 *
 * @author Thomas Felix
 */
@Actor
class AttackPlayerUseActor(
    private val playerEntityService: PlayerEntityService
) : DynamicMessageRoutingActor() {

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
    val activeEntityId = playerEntityService.getActivePlayerEntityId(msg.accountId) ?: return

    // Does the target of the attack matches the target provided in the
    // message? If this is not the case by the user send data dont perform the attack.
    val skillMsg = EntitySkillUseMessage(activeEntityId,
        msg.attackId,
        msg.targetEntityId)

    sender.tell(skillMsg, self)
  }

  companion object {
    const val NAME = "attackPlayerUse"
  }
}
