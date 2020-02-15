package net.bestia.zoneserver.actor.battle

import mu.KotlinLogging
import net.bestia.messages.entity.DamageType
import net.bestia.messages.entity.EntityDamage
import net.bestia.messages.entity.SkillUseEntity
import net.bestia.messages.entity.SkillUsePosition
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendClientsInRangeActor
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.battle.BattleService
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.AttackListComponent

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
    private val messageApi: MessageApi
) : DynamicMessageRoutingActor() {

  private val sendActiveRange = SpringExtension.actorOf(context, SendClientsInRangeActor::class.java)

  override fun createReceive(builder: BuilderFacade) {
    builder.matchRedirect(SkillUseEntity::class.java, this::handleAttackOnEntity)
    builder.matchRedirect(SkillUsePosition::class.java, this::handleAttackOnPosition)
  }

  /**
   * This message is received directly from the clients and must be translated
   * first.
   *
   * @param msg
   */
  private fun handleAttackOnEntity(msg: SkillUseEntity) {
    LOG.debug { "Use skill: $msg" }

    val attackerId = msg.entityId
    val defenderId = msg.targetEntityId

    awaitEntityResponse(messageApi, context, setOf(attackerId, defenderId)) {
      val attacker = it[attackerId]
      verifyAttackerKnowsAttack(attacker, msg.attackId)

      val dmgs = battleService.attackEntity(msg.attackId, it[attackerId], it[defenderId])

      dmgs.forEach { dmg ->
        val dmgMsg = EntityDamage(defenderId, dmg.amount, DamageType.DAMAGE)
        sendActiveRange.tell(dmgMsg, self)
      }
    }
  }

  /**
   * Handles an attack to the ground by calling the attack script.
   *
   * @param msg
   * The message describing the attack.
   */
  private fun handleAttackOnPosition(msg: SkillUsePosition) {
    LOG.trace { "handleAttackOnPosition: $msg" }

    awaitEntityResponse(messageApi, context, msg.entityId) { attacker ->
      verifyAttackerKnowsAttack(attacker, msg.attackId)

      val pos = Vec3(msg.x, msg.y, msg.z)
      battleService.attackGround(msg.attackId, attacker, pos)
    }
  }

  private fun verifyAttackerKnowsAttack(attacker: Entity, attackId: Long) {
    val knownAttackComp = attacker.getComponent(AttackListComponent::class.java)
    check(!knownAttackComp.knownAttacks.contains(attackId)) {
      "Attacker $attacker does not know attack ${attackId} only: $knownAttackComp"
    }
  }

  companion object {
    const val NAME = "attackUse"
  }
}
