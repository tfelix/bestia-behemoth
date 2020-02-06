package net.bestia.zoneserver.actor.battle

import mu.KotlinLogging
import net.bestia.messages.entity.DamageType
import net.bestia.messages.entity.EntityDamage
import net.bestia.messages.entity.SkillUseEntity
import net.bestia.messages.entity.SkillUsePosition
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
import java.lang.IllegalStateException

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

    val attack = attackDao.findOneOrThrow(msg.attackId)

    handleEntityAttack(attack, msg.entityId, msg.targetEntityId)
  }

  /**
   * Handles an attack by an entity to the ground or another entity.
   *
   * @param msg
   * The message describing the attack.
   */
  private fun handleAttackOnPosition(msg: SkillUsePosition) {
    LOG.debug("Received message: {}.", msg)
    throw IllegalStateException("Attack not supported")
  }

  private fun handleEntityAttack(attack: Attack, attackerId: Long, defenderId: Long) {
    awaitEntityResponse(messageApi, context, setOf(attackerId, defenderId)) {

      val dmg = battleService.attackEntity(attack, it[attackerId], it[defenderId])
          ?: return@awaitEntityResponse

      if (dmg.size == 1) {
        val dmgMsg = EntityDamage(defenderId, dmg.single().damage, DamageType.DAMAGE)
        sendActiveRange.tell(dmgMsg, self)
      } else {
        throw IllegalStateException("Multi DMG not supported")
      }
    }
  }

  companion object {
    const val NAME = "attackUse"
  }
}
