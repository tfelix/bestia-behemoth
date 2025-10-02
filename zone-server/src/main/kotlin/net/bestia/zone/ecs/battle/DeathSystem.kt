package net.bestia.zone.ecs.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.visual.BestiaVisual
import net.bestia.zone.ecs.Entity
import net.bestia.zone.ecs.IteratingSystem
import net.bestia.zone.ecs.network.IsDirty
import net.bestia.zone.ecs.status.Exp
import net.bestia.zone.ecs.status.GivenExp
import net.bestia.zone.ecs2.ZoneServer
import net.bestia.zone.item.LootEntityFactory
import net.bestia.zone.message.entity.VanishEntitySMSG
import net.bestia.zone.message.processor.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import kotlin.math.floor

@Component
class DeathSystem(
  private val outMessageProcessor: OutMessageProcessor,
  private val itemEntityFactory: LootEntityFactory
) : IteratingSystem() {
  override val requiredComponents = setOf(
    Dead::class
  )

  override fun update(
    deltaTime: Float,
    entity: Entity,
    zone: ZoneServer
  ) {
    LOG.debug { "Entity $entity is dead" }
    val givenExp = entity.getOrThrow(GivenExp::class).value
    val damageDealer = entity.getOrThrow(TakenDamage::class).damagePercentages()

    assignExp(givenExp, damageDealer, zone)
    spawnLoot(entity, zone)
    sendDeathAnimation(entity, zone)

    zone.removeEntity(entity.id)
  }

  private fun assignExp(
    givenExp: Int,
    damageDealer: Map<EntityId, Float>,
    zone: ZoneServer
  ) {
    LOG.debug { "Distribute $givenExp EXP to $damageDealer" }

    damageDealer.forEach { (entityId, percent) ->
      val receivedExp = floor(givenExp * percent).toInt()

      zone.withEntityWriteLock(entityId) { entity ->
        val addExp = entity.getOrDefault(Exp::class) { Exp() }

        addExp.value += receivedExp
        entity.add(IsDirty)
      }
    }
  }

  private fun spawnLoot(entity: Entity, zone: ZoneServer) {
    val bestiaId = entity.get(BestiaVisual::class)?.id?.toLong()
      ?: return

    val pos = entity.get(Position::class)?.toVec3L()
      ?: return

    zone.queueExternalJob {
      itemEntityFactory.createLootEntities(bestiaId, pos)
    }
  }

  private fun sendDeathAnimation(entity: Entity, zone: ZoneServer) {
    val position = entity.getOrThrow(Position::class).toVec3L()
    val entityId = entity.id
    val vanishMsg = VanishEntitySMSG(
      entityId = entityId,
      kind = VanishEntitySMSG.VanishKind.DEATH
    )

    zone.queueExternalJob {
      outMessageProcessor.sendToAllPlayersInRange(position, vanishMsg)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}