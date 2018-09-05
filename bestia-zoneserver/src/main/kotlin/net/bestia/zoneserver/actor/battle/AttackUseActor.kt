package net.bestia.zoneserver.actor.battle

import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.attack.AttackUseMessage
import net.bestia.messages.entity.EntityDamageMessage
import net.bestia.messages.entity.EntitySkillUseMessage
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendClientsInRangeActor
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.battle.BattleService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Transforms the incoming player attack message into a entity skill message
 * after some sanity checks.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class AttackUseActor(
        private val battleService: BattleService,
        private val msgHub: ActorRef
) : BaseClientMessageRouteActor() {

  private val transformAtkMsg = SpringExtension.actorOf(context, AttackPlayerUseActor::class.java)
  private val sendActiveRange = SpringExtension.actorOf(context, SendClientsInRangeActor::class.java)

  override fun createReceive(builder: BuilderFacade) {
    builder.match(AttackUseMessage::class.java, this::handleAttackMessage)
    builder.match(EntitySkillUseMessage::class.java, this::handleEntitySkillMessage)
  }

  /**
   * This message is received directly from the clients and must be translated
   * first.
   *
   * @param msg
   */
  private fun handleAttackMessage(msg: AttackUseMessage) {
    LOG.debug("Received essage: {}.", msg)
    transformAtkMsg.tell(msg, self)
  }

  /**
   * Handles an attack by an entity to the ground or another entity.
   *
   * @param msg
   * The message describing the attack.
   */
  private fun handleEntitySkillMessage(msg: EntitySkillUseMessage) {
    LOG.debug("Received skill message: {}", msg)

    if (msg.targetEntityId != 0L) {
      // Entity was targeted.
      val dmg = battleService.attackEntity(msg.attackId,
              msg.sourceEntityId,
              msg.targetEntityId)

      val dmgMsg = EntityDamageMessage(msg.targetEntityId, dmg)
      sendActiveRange.tell(dmgMsg, self)

    } else {
      LOG.warn { "Attackmode Currently not supported." }
    }
  }

  companion object {
    const val NAME = "attackUse"
  }
}
