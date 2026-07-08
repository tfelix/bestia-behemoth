package net.bestia.zone.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.ecs.battle.AvailableAttacks
import net.bestia.zone.ecs.battle.Damage
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.battle.attack.AttackEntityCMSG
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.message.processor.OutMessageProcessor
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * Handles attack requests from players to target entities.
 * This processes the attack action and initiates combat mechanics.
 */
@Component
class AttackEntityHandler(
  private val messageProcessor: OutMessageProcessor,
  private val world: World,
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
      val damage = world.get(id, Damage::class) ?: world.add(id, Damage())
      damage.add(damageTaken, masterEntityId)
    }

    val position = world.getOrThrow(masterEntityId, Position::class).toVec3L()

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
    usedAttackId: Long,
    usedSkillLevel: Int
  ): Boolean {
    // Basic attack is always possible
    return if (usedAttackId == 0L) {
      true
    } else {
      world.getOrThrow(attackingEntityId, AvailableAttacks::class)
        .knowsAttack(usedAttackId, usedSkillLevel)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
