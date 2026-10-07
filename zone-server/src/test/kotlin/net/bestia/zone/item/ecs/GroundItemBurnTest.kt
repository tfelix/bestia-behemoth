package net.bestia.zone.item.ecs

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.effects.AreaEffectSystem
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.LooseInstanceDisposal
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.material.ItemMaterialRegistry
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.prop.PropPromotionService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** An Ember patch over items on the ground, with the real area effect, damage and protection systems. */
class GroundItemBurnTest {

  private val blueprint = Item(
    id = 16L, identifier = "blueprint", weight = 1, type = Item.ItemType.ETC, material = ItemMaterial.PAPER
  )
  private val sword = Item(
    id = 19L, identifier = "sword", weight = 120, type = Item.ItemType.ETC, material = ItemMaterial.METAL
  )

  private val aoi = EntityAOIService()
  private val messages = mockk<OutMessageProcessor>(relaxed = true)

  private val groundItemDamage = GroundItemDamage(
    itemTemplates = ItemTemplateRegistry(mockk<ItemRepository> { every { findAll() } returns listOf(blueprint, sword) }),
    materials = ItemMaterialRegistry().also { it.load() },
    groundStackRemoval = GroundStackRemoval(PersistedEntityDeletionQueue()),
    asyncJobExecutor = AsyncJobExecutor(workerCount = 1),
    looseInstanceDisposal = mockk<LooseInstanceDisposal>(relaxed = true),
  )

  private val world: EcsWorld = testWorld(
    systems = listOf(
      AreaEffectSystem(aoi, messages, PropPromotionService(mockk(relaxed = true)), groundItemDamage),
      LootProtectionSystem(),
    )
  )

  private val spawner = LootItemEntitySpawner(WorldRulesConfig(tickRate = 20))

  private fun onTheGround(item: Item, lootOwner: Long? = null): EntityId {
    return spawner.spawnLootItem(world, itemId = item.id, amount = 3, pos = CENTER, lootOwner = lootOwner)
      .also { aoi.setEntityPosition(it, CENTER) }
  }

  private fun emberAtCenter() {
    val patch = world.createEntity { id ->
      add(id, Position.fromVec3(CENTER))
      add(
        id,
        AreaEffect.lasting(
          casterId = 1L, skillId = 1000L, skillLevel = 1, radiusTiles = 1, damagePerTick = 7,
          tickIntervalSeconds = 1.2f, durationSeconds = 9.6f, element = Element.FIRE
        )
      )
    }
    aoi.setEntityPosition(patch, CENTER)
  }

  @Test
  fun `ember burns a blueprint on the ground`() {
    val stack = onTheGround(blueprint)
    emberAtCenter()

    world.tick(1.2f)

    assertFalse(world.isAlive(stack))
  }

  @Test
  fun `and spares an iron sword`() {
    val stack = onTheGround(sword)
    emberAtCenter()

    repeat(200) { world.tick(0.05f) }

    assertTrue(world.isAlive(stack))
    assertEquals(0, world.get(stack, GroundItemIntegrity::class)!!.lost)
  }

  @Test
  fun `an item on the ground shows no damage number`() {
    val stack = onTheGround(sword)
    emberAtCenter()

    world.tick(1.2f)

    verify(exactly = 0) { messages.sendToObserversOf(any(), stack, any()) }
  }

  /** The killer gets the protection time to pick it up, and the fire takes it after. */
  @Test
  fun `kill loot survives the fire until its protection ends`() {
    val loot = onTheGround(blueprint, lootOwner = 7L)
    emberAtCenter()

    repeat(100) { world.tick(0.05f) }
    assertTrue(world.isAlive(loot), "burnt while still protected")

    repeat(100) { world.tick(0.05f) }
    assertFalse(world.isAlive(loot), "the fire never took it")
  }

  private companion object {
    val CENTER = Vec3L(10, 10, 0)
  }
}
