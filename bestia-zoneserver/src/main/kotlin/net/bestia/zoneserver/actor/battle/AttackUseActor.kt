package net.bestia.zoneserver.actor.battle

import mu.KotlinLogging
import net.bestia.messages.attack.AttackUseMessage
import net.bestia.messages.entity.EntityDamageMessage
import net.bestia.messages.entity.EntitySkillUseMessage
import net.bestia.model.battle.Attack
import net.bestia.model.battle.AttackRepository
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendClientsInRangeActor
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.battle.BattleService

private val LOG = KotlinLogging.logger { }

/**
 * Transforms the incoming player attack message into a entity skill message
 * after some sanity checks.
 *
 * @author Thomas Felix
 */
@Actor
class AttackUseActor(
    private val battleService: BattleService,
    private val attackDao: AttackRepository,
    private val messageApi: MessageApi
) : DynamicMessageRoutingActor() {

  private val transformAtkMsg = SpringExtension.actorOf(context, AttackPlayerUseActor::class.java)
  private val sendActiveRange = SpringExtension.actorOf(context, SendClientsInRangeActor::class.java)

  override fun createReceive(builder: BuilderFacade) {
    builder.matchRedirect(AttackUseMessage::class.java, this::handleAttackMessage)
    builder.matchRedirect(EntitySkillUseMessage::class.java, this::handleEntitySkillMessage)
  }

  /**
   * This message is received directly from the clients and must be translated
   * first.
   *
   * @param msg
   */
  private fun handleAttackMessage(msg: AttackUseMessage) {
    LOG.debug("Received message: {}.", msg)
    transformAtkMsg.tell(msg, self)
  }

  /**
   * Handles an attack by an entity to the ground or another entity.
   *
   * @param msg
   * The message describing the attack.
   */
  private fun handleEntitySkillMessage(msg: EntitySkillUseMessage) {
    LOG.debug { "Use skill: $msg" }

    val attack = attackDao.findOneOrThrow(msg.attackId)

    if (msg.targetEntityId != null) {
      handleEntityAttack(attack, msg.sourceEntityId, msg.targetEntityId!!)
    } else {
      LOG.warn { "Attackmode Currently not supported." }
    }
  }

  private fun handleEntityAttack(attack: Attack, attackerId: Long, defenderId: Long) {
    awaitEntityResponse(messageApi, context, setOf(attackerId, defenderId)) {
      val dmg = battleService.attackEntity(attack, it[attackerId], it[defenderId])
          ?: return@awaitEntityResponse
      val dmgMsg = EntityDamageMessage(defenderId, dmg)
      sendActiveRange.tell(dmgMsg, self)
    }
  }

  companion object {
    const val NAME = "attackUse"
  }
}
