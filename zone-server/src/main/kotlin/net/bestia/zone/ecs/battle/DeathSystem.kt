package net.bestia.zone.ecs.battle

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.IteratingSystem
import com.github.quillraven.fleks.World
import com.github.quillraven.fleks.World.Companion.inject
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.item.ItemEntityFactory
import net.bestia.zone.message.entity.VanishEntitySMSG
import net.bestia.zone.message.processor.OutMessageProcessor
import net.bestia.zone.ecs.EntityRegistry
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.visual.BestiaVisual
import kotlin.math.floor

class DeathSystem(
  private val outMessageProcessor: OutMessageProcessor = inject(),
  private val entityRegistry: EntityRegistry = inject(),
  private val itemEntityFactory: ItemEntityFactory = inject()
) : IteratingSystem(
  World.family { all(Dead) }
) {

  override fun onTickEntity(entity: Entity) {
    LOG.debug { "Entity $entity is dead" }

    val givenExp = getExp(entity)
    val damageDealer = getDamageDealer(entity)
    assignExp(givenExp, damageDealer)

    // get loot table and spawn item entities in the world.
    spawnLoot(entity)

    // send death animation
    sendDeathAnimation(entity)

    // Now remove dead entity from the world
    world -= entity
  }

  private fun getExp(entity: Entity): Int {
    return entity.getOrNull(GivenExp)?.value ?: 0
  }

  private fun getDamageDealer(entity: Entity): Map<Entity, Float> {
    return entity.getOrNull(TakenDamage)?.damagePercentages() ?: emptyMap()
  }

  private fun assignExp(
    givenExp: Int,
    damageDealer: Map<Entity, Float>
  ) {
    LOG.debug { "Distribute $givenExp EXP to $damageDealer" }

    damageDealer.forEach { (entity, percent) ->
      val receivedExp = floor(givenExp * percent).toInt()

      entity.configure {
        val addExp = it.getOrAdd(AddExp) {
          AddExp()
        }
        addExp.expToAdd += receivedExp
      }
    }
  }

  private fun spawnLoot(entity: Entity) {
    val bestiaId = entity.getOrNull(BestiaVisual)?.id?.toLong()
      ?: return

    val pos = entity.getOrNull(Position)?.toVec3L()
      ?: return

    itemEntityFactory.createLootEntities(bestiaId, pos)
  }

  private fun sendDeathAnimation(entity: Entity) {
    val pos = entity.getOrNull(Position)?.toVec3L()
      ?: return

    val entityId = entityRegistry.getEntityId(entity)

    if (entityId == null) {
      LOG.warn { "No entityId found in registry for entity $entity" }
      return
    }

    // TODO this here runs inside the ECS thread... ugs. we certainly need a better model here.
    val vanishMsg = VanishEntitySMSG(
      entityId = entityId,
      kind = VanishEntitySMSG.VanishKind.GONE
    )

    outMessageProcessor.sendToAllPlayersInRange(pos, vanishMsg)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}