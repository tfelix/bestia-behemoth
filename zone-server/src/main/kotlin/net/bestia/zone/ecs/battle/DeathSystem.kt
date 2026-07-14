package net.bestia.zone.ecs.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.status.Exp
import net.bestia.zone.ecs.bestia.BestiaVisual
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.item.loot.LootItemEntityFactory
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

@SpringComponent
@Order(70)
class DeathSystem(
  private val experienceGainCalculator: ExperienceGainCalculator,
  private val lootItemEntityFactory: LootItemEntityFactory,
  private val deletionQueue: PersistedEntityDeletionQueue
) : System {

  override val reads: ComponentClassSet =
    setOf(Dead::class, TakenDamage::class, BestiaVisual::class, Position::class, Account::class)

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

      earnedExp.forEach { (entityId, receivedExp) ->
        LOG.debug { "Entity $entityId received $receivedExp EXP" }
        world.update(entityId, { Exp() }) { exp -> exp.value += receivedExp }
      }
    }
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
