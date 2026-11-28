package net.bestia.zone.item.care

import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.care.TestBagCare.BANDAGE
import net.bestia.zone.item.care.TestBagCare.BREAD
import net.bestia.zone.item.care.TestBagCare.CHEESE
import net.bestia.zone.item.care.TestBagCare.POTION
import net.bestia.zone.item.care.TestBagCare.bagWith
import net.bestia.zone.item.ecs.Inventory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BagCareTest {

  private val care = TestBagCare.create()
  private val world = testWorld()

  private fun bestiaWith(bag: Inventory) = world.createEntity { id ->
    world.add(id, bag)
    world.add(id, Stamina(10, 100))
    world.add(id, Health(10, 100))
  }

  @Test
  fun `eats what restores the most stamina`() {
    val bestia = bestiaWith(bagWith(BREAD to 1, CHEESE to 1))

    assertTrue(care.use(world, bestia, BagCare.Need.STAMINA))

    assertEquals(55, world.getOrThrow(bestia, Stamina::class).current)
    assertFalse(world.getOrThrow(bestia, Inventory::class).hasItem(CHEESE.toInt()))
    assertTrue(world.getOrThrow(bestia, Inventory::class).hasItem(BREAD.toInt()))
  }

  @Test
  fun `puts on a bandage for health`() {
    val bestia = bestiaWith(bagWith(BREAD to 1, BANDAGE to 2))

    assertTrue(care.use(world, bestia, BagCare.Need.HEALTH))

    assertEquals(30, world.getOrThrow(bestia, Health::class).current)
    assertEquals(1, world.getOrThrow(bestia, Inventory::class).getItem(BANDAGE.toInt())?.amount)
  }

  @Test
  fun `never drinks a potion, that is the master's to hand out`() {
    val bag = bagWith(POTION to 3)
    val bestia = bestiaWith(bag)

    assertFalse(care.hasFor(bag, BagCare.Need.HEALTH))
    assertFalse(care.use(world, bestia, BagCare.Need.HEALTH))
    assertEquals(3, bag.getItem(POTION.toInt())?.amount)
  }

  @Test
  fun `leaves alone what it must never use`() {
    val bestia = bestiaWith(bagWith(BREAD to 1, CHEESE to 1))

    assertTrue(care.use(world, bestia, BagCare.Need.STAMINA, CareItems(neverUse = listOf(CHEESE))))

    assertEquals(45, world.getOrThrow(bestia, Stamina::class).current)
    assertTrue(world.getOrThrow(bestia, Inventory::class).hasItem(CHEESE.toInt()))
  }

  @Test
  fun `drinks a potion it may also use`() {
    val bag = bagWith(POTION to 1)
    val bestia = bestiaWith(bag)
    val items = CareItems(alsoUse = listOf(POTION))

    assertTrue(care.hasFor(bag, BagCare.Need.HEALTH, items))
    assertTrue(care.use(world, bestia, BagCare.Need.HEALTH, items))
    assertEquals(55, world.getOrThrow(bestia, Health::class).current)
  }

  @Test
  fun `hauls what it must never use and keeps what it may also use`() {
    val bag = bagWith(BREAD to 2, POTION to 1)

    val goods = care.spareGoods(bag, CareItems(neverUse = listOf(BREAD), alsoUse = listOf(POTION)))

    assertEquals(listOf(BREAD), goods.map { it.itemId })
  }

  @Test
  fun `has nothing for a need no carried item meets`() {
    val bag = bagWith(BREAD to 1)

    assertTrue(care.hasFor(bag, BagCare.Need.STAMINA))
    assertFalse(care.hasFor(bag, BagCare.Need.HEALTH))
  }
}
