package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * The behaviour that was missing entirely: for a long time `CarryCapacity` was written once at spawn and
 * never again, so a limit ignored every level-up and `current` was frozen at the login-time weight.
 */
class CarryCapacitySystemTest {

  private val world: World = testWorld(systems = listOf(CarryCapacitySystem(WeightLimitCalculator())))

  /**
   * Effective attributes, not base ones - otherwise a strength buff or a piece of gear that grants strength
   * lets its wearer hit harder but not carry more.
   */
  @Test
  fun `max follows the effective status values`() {
    val entity = createCarrier(strength = 30)

    world.tick(0.1f)

    assertEquals(1800 + 30 * 50 + 10 * 15 + 25, capacity(entity).max)
  }

  @Test
  fun `max follows a level up`() {
    val entity = createCarrier()
    world.tick(0.1f)
    val before = capacity(entity).max

    world.get(entity, Level::class)!!.inc()
    world.tick(0.1f)

    assertEquals(25, capacity(entity).max - before)
  }

  @Test
  fun `current follows the inventory both up and down`() {
    val entity = createCarrier()
    val inventory = world.get(entity, Inventory::class)!!

    inventory.addItem(Inventory.Item(itemId = IRON_ORE, amount = 2, weight = 300))
    world.tick(0.1f)
    assertEquals(600, capacity(entity).current)

    inventory.removeItem(IRON_ORE)
    world.tick(0.1f)
    assertEquals(0, capacity(entity).current)
  }

  /**
   * Attributes are fetched rather than joined into the query. Joined, an entity that was never given
   * [StatusValues] would drop out of the loop and stop tracking its inventory as well.
   */
  @Test
  fun `an entity without status values keeps the max it was spawned with and still tracks its load`() {
    val entity = world.createEntity { id ->
      add(id, Inventory(mutableListOf()))
      add(id, CarryCapacity(current = 0, max = 2475))
    }
    world.get(entity, Inventory::class)!!.addItem(Inventory.Item(itemId = IRON_ORE, amount = 1, weight = 300))

    world.tick(0.1f)

    assertEquals(2475, capacity(entity).max)
    assertEquals(300, capacity(entity).current)
  }

  /**
   * The `lastKnown*` cache is the whole reason those fields exist. Without the short-circuit every entity
   * would recompute and resend its limit on every tick, for a number that changes a few times a session.
   */
  @Test
  fun `an unchanged entity is neither recomputed nor resent`() {
    val entity = createCarrier()
    world.tick(0.1f)
    capacity(entity).clearDirty()

    capacity(entity).max = 9999
    capacity(entity).clearDirty()
    world.tick(0.1f)

    assertEquals(9999, capacity(entity).max)
    assertFalse(capacity(entity).isDirty())
  }

  private fun createCarrier(strength: Int = 10, vitality: Int = 10, level: Int = 1): EntityId {
    return world.createEntity { id ->
      add(id, Inventory(mutableListOf()))
      add(id, CarryCapacity(current = 0, max = 0))
      add(
        id,
        StatusValues(
          strength = strength,
          intelligence = 10,
          vitality = vitality,
          dexterity = 10,
          willpower = 10,
          agility = 10
        )
      )
      add(id, Level(level))
    }
  }

  private fun capacity(entity: EntityId): CarryCapacity {
    return world.get(entity, CarryCapacity::class)!!
  }

  companion object {
    private const val IRON_ORE = 8L
  }
}
