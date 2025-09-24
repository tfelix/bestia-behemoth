package net.bestia.zone.message.processor.handler

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.ecs.EntityRegistry
import net.bestia.zone.ecs.WorldAcessor
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.ecs.battle.AvailableAttacks
import net.bestia.zone.ecs.battle.Damage
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
  private val entityRegistry: EntityRegistry,
  private val zoneServer: ZoneServer,
  private val masterResolver: MasterResolver
) : InMessageProcessor.IncomingMessageHandler<AttackEntityCMSG> {
  override val handles = AttackEntityCMSG::class

  override fun handle(msg: AttackEntityCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    // TODO: Implement attack logic
    // - Validate that the player can attack the target entity
    // - Check if the target is in range
    // TODO check if we need to go into the ECS for this or if we can do this from the outside?
    // - Process attack mechanics
    // - Send damage/combat result messages

    // Hacky test implementation
    val masterEntity = masterResolver.getSelectedMasterEntityByAccountId(msg.playerId)
      ?: return true

    val availableAttackAccessor = AvailableAttacks.AvailableAttacksAccessor(masterEntity)
    zoneServer.accessWorld(availableAttackAccessor)

    if (!checkAttackAvailable(masterEntity, msg.usedAttackId, msg.usedSkillLevel)) {
      return true
    }

    val attackedEntity = entityRegistry.getEntity(msg.targetEntityId)
      ?: return true
    val addDamageAccessor = DamageAdderAccessor(Random.nextInt(1, 7), 1L, attackedEntity)
    zoneServer.accessWorld(addDamageAccessor)

    return true
  }

  private fun checkAttackAvailable(
    masterEntity: Entity,
    usedAttackId: Long,
    usedSkillLevel: Int
  ): Boolean {
    if (usedAttackId == 0L) {
      return true
    } else {
      val availableAttackAccessor = AvailableAttacks.AvailableAttacksAccessor(masterEntity)
      zoneServer.accessWorld(availableAttackAccessor)

      return availableAttackAccessor.knowsAttack(usedAttackId, usedSkillLevel)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

class DamageAdderAccessor(
  private val amount: Int,
  private val sourceEntityId: EntityId,
  private val entity: Entity
) : WorldAcessor {
  override fun doWithWorld(world: World) {
    with(world) {
      entity.configure {
        val existingDamage = it.getOrNull(Damage)

        if (existingDamage != null) {
          existingDamage.add(amount, sourceEntityId)
        } else {
          it += Damage(amount, sourceEntityId)
        }
      }
    }
  }
}