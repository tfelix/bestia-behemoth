package net.bestia.zone.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.battle.damage.Damage
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.battle.AttackEntityCMSG
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * Handles attack requests from players to target entities.
 * This processes the attack action and initiates combat mechanics.
 */
@Component
class AttackEntityHandler(
  private val messageProcessor: OutMessageProcessor,
  private val world: WorldView,
  private val masterResolver: MasterResolver
) : InMessageProcessor.IncomingMessageHandler<AttackEntityCMSG> {
  override val handles = AttackEntityCMSG::class

  override fun handle(msg: AttackEntityCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    // Hacky test implementation
    val masterEntityId = masterResolver.getSelectedMasterEntityIdByAccountId(msg.playerId)
      ?: return true

    if (!checkAttackAvailable(masterEntityId, msg.usedAttackId, msg.usedSkillLevel)) {
      return true
    }

    val damageTaken = Random.nextInt(1, 7)

    world.modify(msg.targetEntityId) { id ->
      val damage = get(id, Damage::class) ?: add(id, Damage())
      damage.add(damageTaken, masterEntityId)
    }

    val position = world.read { getOrThrow(masterEntityId, Position::class).toVec3L() }

    val damageMsg = DamageEntitySMSG(
      entityId = msg.targetEntityId,
      sourceEntityId = masterEntityId,
      attackId = 0,
      div = 1,
      damage = damageTaken,
      skillLevel = 1,
      type = DamageEntitySMSG.DamageType.NORMAL
    )
    messageProcessor.sendToAllPlayersInRange(position, damageMsg)

    return true
  }

  private fun checkAttackAvailable(
    attackingEntityId: EntityId,
    usedSkillId: Long,
    usedSkillLevel: Int
  ): Boolean {
    // Basic attack is always possible
    return if (usedSkillId == 0L) {
      true
    } else {
      world.read {
        getOrThrow(attackingEntityId, KnownSkills::class).knowsSkill(usedSkillId, usedSkillLevel)
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
