package net.bestia.zone.ecs.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.player.Account
import net.bestia.zone.ecs.status.Exp
import net.bestia.zone.ecs.bestia.BestiaVisual
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

@SpringComponent
@Order(70)
class DeathSystem(
  private val experienceCalculator: ExperienceCalculator,
) : System {

  override val reads: ComponentClassSet =
    setOf(Dead::class, TakenDamage::class, BestiaVisual::class, Position::class, Account::class)

  override val writes: ComponentClassSet = setOf(Exp::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Dead::class).each { entityId ->
      LOG.debug { "Entity $entityId is dead" }
      val damageDealer = world.get(entityId, TakenDamage::class)?.damagePercentages()
        ?: return@each

      val bestiaVisual = world.get(entityId, BestiaVisual::class)
        ?: return@each

      assignExp(world, bestiaVisual.id, damageDealer)

      val position = world.get(entityId, Position::class)?.toVec3L()
        ?: return@each
      val lootBestiaId = world.get(entityId, BestiaVisual::class)?.id?.toLong()

      // TODO SPAWN LOOT       spawnLoot(world, position, lootBestiaId)

      world.destroy(entityId)
    }
  }

  private fun assignExp(
    world: World,
    bestiaId: Long,
    damageDealer: Map<EntityId, Float>,
  ) {
    // Check which of those are an actual player. Every player bestia has an Account component.
    val attackingPlayerCount = damageDealer.keys
      .mapNotNull { world.get(it, Account::class)?.accountId }
      .distinct()
      .size

    // the experience calculator requires a DB lookup so we defer the call.
    world.defer {
      val earnedExp = experienceCalculator.calculate(bestiaId, damageDealer, attackingPlayerCount)

      earnedExp.forEach { (entityId, receivedExp) ->
        LOG.debug { "Entity $entityId received $receivedExp EXP" }
        world.update(entityId, { Exp() }) { exp -> exp.value += receivedExp }
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
