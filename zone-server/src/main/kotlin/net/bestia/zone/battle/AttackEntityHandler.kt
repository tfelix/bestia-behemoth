package net.bestia.zone.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.ecs.battle.AvailableAttacks
import net.bestia.zone.ecs.battle.Damage
import net.bestia.zone.ecs2.ZoneServer
import net.bestia.zone.message.AttackEntityCMSG
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * Handles attack requests from players to target entities.
 * This processes the attack action and initiates combat mechanics.
 */
@Component
class AttackEntityHandler(
  private val zoneServer: ZoneServer,
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

    // TODO: Implement attack logic
    // - Validate that the player can attack the target entity
    // - Check if the target is in range

    // TODO check if we need to go into the ECS for this or if we can do this from the outside?
    // - Process attack mechanics
    // - Send damage/combat result messages

    val damageTaken = Random.nextInt(1, 7)

    zoneServer.withEntityWriteLock(msg.targetEntityId) { entity ->
      val damage = entity.getOrDefault(Damage::class) { Damage() }
      damage.add(damageTaken, masterEntityId)
    }

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
      zoneServer.withEntityReadLockOrThrow(attackingEntityId) {
        it.getOrThrow(AvailableAttacks::class)
          .knowsAttack(usedAttackId, usedSkillLevel)
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}