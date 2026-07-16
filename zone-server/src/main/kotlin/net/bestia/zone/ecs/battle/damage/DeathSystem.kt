package net.bestia.zone.ecs.battle.damage

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.exp.Exp
import net.bestia.zone.ecs.battle.exp.ExperienceGainCalculator
import net.bestia.zone.ecs.bestia.BestiaVisual
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.session.NoActiveSessionException
import net.bestia.zone.ecs.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.item.loot.LootItemEntityFactory
import net.bestia.zone.party.PartyMembership
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

@SpringComponent
@Order(70)
class DeathSystem(
  private val experienceGainCalculator: ExperienceGainCalculator,
  private val lootItemEntityFactory: LootItemEntityFactory,
  private val deletionQueue: PersistedEntityDeletionQueue,
  private val connectionInfoService: ConnectionInfoService,
) : System {

  override val reads: ComponentClassSet =
    setOf(Dead::class, TakenDamage::class, BestiaVisual::class, Position::class, Account::class, PartyMembership::class)

  override val writes: ComponentClassSet = setOf(Exp::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class).each { entityId ->
      LOG.debug { "Entity $entityId is dead" }

      assignExp(world, entityId)
      spawnLoot(world, entityId)

      // A dead entity is gone for good — drop any persisted row so it is not resurrected on reload.
      // The actual DB delete is batched off the tick thread by the persistence sync.
      deletionQueue.enqueue(entityId)

      world.destroy(entityId)
    }
  }

  private fun assignExp(
    world: World,
    entityId: EntityId
  ) {
    val damageDealer = world.get(entityId, TakenDamage::class)?.damagePercentages()
      ?: return

    val bestiaVisual = world.get(entityId, BestiaVisual::class)
      ?: return

    // Check which of those are an actual player. Every player bestia has an Account component.
    val attackingPlayerCount = damageDealer.keys
      .mapNotNull { world.get(it, Account::class)?.accountId }
      .distinct()
      .size

    // the experience calculator requires a DB lookup so we defer the call.
    world.defer {
      val earnedExp = experienceGainCalculator.calculate(
        bestiaVisual.id,
        damageDealer,
        attackingPlayerCount
      )

      earnedExp.forEach { (attackerEntityId, receivedExp) ->
        val recipients = resolvePartyExpRecipients(world, attackerEntityId)
        val share = receivedExp / recipients.size

        recipients.forEach { recipientEntityId ->
          LOG.debug { "Entity $recipientEntityId received $share EXP (party share of $receivedExp)" }
          world.update(recipientEntityId, { Exp() }) { exp -> exp.value += share }
        }
      }
    }
  }

  /** If [attackerEntityId]'s owner is in a party, splits its earned EXP share evenly across every
   * online party member's active master instead of granting it only to the attacker itself. Falls
   * back to the attacker alone when it has no owner, the owner isn't in a party, or no party
   * member (including the attacker) currently has an online master. */
  private fun resolvePartyExpRecipients(world: World, attackerEntityId: EntityId): Set<EntityId> {
    val ownerAccountId = world.get(attackerEntityId, Account::class)?.accountId
      ?: return setOf(attackerEntityId)

    val ownerMasterEntityId = try {
      connectionInfoService.getSelectedMasterEntityId(ownerAccountId)
    } catch (_: NoActiveSessionException) {
      return setOf(attackerEntityId)
    }

    val memberAccountIds = world.get(ownerMasterEntityId, PartyMembership::class)?.memberAccountIds
      ?: return setOf(attackerEntityId)

    val recipientEntityIds = memberAccountIds.mapNotNullTo(mutableSetOf()) { accountId ->
      try {
        connectionInfoService.getSelectedMasterEntityId(accountId)
      } catch (_: NoActiveSessionException) {
        null
      }
    }

    return recipientEntityIds.ifEmpty { setOf(attackerEntityId) }
  }

  private fun spawnLoot(
    world: World,
    entityId: Long,
  ) {
    val position = world.get(entityId, Position::class)?.toVec3L()
      ?: return

    val bestiaVisual = world.get(entityId, BestiaVisual::class)
      ?: return

    lootItemEntityFactory.createLootEntities(world, bestiaVisual.id, position)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
