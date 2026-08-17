package net.bestia.zone.ecs.battle.status

import net.bestia.zone.battle.status.RegenerationCalculator
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Drives [HpRegenSystem], [ManaRegenSystem] and [StaminaRegenSystem] against a real [World], the
 * same way [net.bestia.zone.ecs.EcsConfiguration] wires them in production (minus Spring).
 *
 * A single `tick(10.1f)` fires all three at once: their schedules are 6 s / 8 s / 10 s, so anything
 * past 10 s is due for every one of them. Amounts themselves are pinned in
 * [net.bestia.zone.battle.status.RegenerationCalculatorTest]; what matters here is that the systems
 * reach the right entities, respect the combat gate, and never overshoot a pool.
 */
class RegenSystemsTest {

  private val calculator = RegenerationCalculator()

  /** Everything past the longest schedule (10 s), so one call makes all three systems due. */
  private val allDue = 10.1f

  private fun newWorld(): World = testWorld(
    systems = listOf(
      HpRegenSystem(calculator),
      ManaRegenSystem(calculator),
      StaminaRegenSystem(calculator)
    )
  )

  /**
   * A wounded entity with the attributes of a balanced level-1 master, so the expected amounts are
   * the documented 2 HP / 2 mana / 3 stamina per tick.
   */
  private fun World.woundedEntity(): EntityId {
    val id = create()
    add(id, StatusValues(strength = 9, intelligence = 9, vitality = 9, dexterity = 9, willpower = 9, agility = 9))
    add(id, Health(current = 1, max = 18))
    add(id, Mana(current = 1, max = 28))
    add(id, Stamina(current = 1, max = 27))
    return id
  }

  @Test
  fun `an out-of-combat entity regenerates every pool at the documented rate`() {
    val world = newWorld()
    val entity = world.woundedEntity()

    world.tick(allDue)

    assertEquals(3, world.get(entity, Health::class)!!.current, "1 + hpRegen(18, 9)")
    assertEquals(3, world.get(entity, Mana::class)!!.current, "1 + manaRegen(28, 9)")
    assertEquals(4, world.get(entity, Stamina::class)!!.current, "1 + staminaRegen(27, 9, 9)")
  }

  @Test
  fun `nothing regenerates before the schedule is due`() {
    val world = newWorld()
    val entity = world.woundedEntity()

    world.tick(1.0f)

    assertEquals(1, world.get(entity, Health::class)!!.current)
    assertEquals(1, world.get(entity, Mana::class)!!.current)
    assertEquals(1, world.get(entity, Stamina::class)!!.current)
  }

  @Test
  fun `being in combat blocks hp and mana regen but not stamina`() {
    val world = newWorld()
    val entity = world.woundedEntity()
    world.add(entity, InCombat())

    world.tick(allDue)

    assertEquals(1, world.get(entity, Health::class)!!.current, "HP must not regenerate in combat")
    assertEquals(1, world.get(entity, Mana::class)!!.current, "mana must not regenerate in combat")
    // Deliberate asymmetry - see StaminaRegenSystem's KDoc. Stamina is the travel/exposure
    // resource, and gating it on combat turns exposure into a death spiral.
    assertEquals(4, world.get(entity, Stamina::class)!!.current, "stamina regen ignores combat")
  }

  @Test
  fun `regen resumes once the combat timer expires`() {
    val world = testWorld(systems = listOf(InCombatSystem(), HpRegenSystem(calculator)))
    val entity = world.create()
    world.add(entity, StatusValues(9, 9, 9, 9, 9, 9))
    world.add(entity, Health(current = 1, max = 18))
    world.add(entity, InCombat())

    // Still inside the timeout: the marker is there and HP is pinned.
    world.tick(6.1f)
    assertTrue(world.has(entity, InCombat::class))
    assertEquals(1, world.get(entity, Health::class)!!.current)

    // Past InCombat.TIMEOUT_SECONDS in total, so InCombatSystem strips the marker...
    world.tick(6.1f)
    assertFalse(world.has(entity, InCombat::class))

    // ...and the next due tick regenerates again.
    world.tick(6.1f)
    assertEquals(3, world.get(entity, Health::class)!!.current)
  }

  @Test
  fun `a full pool is left alone`() {
    val world = newWorld()
    val entity = world.create()
    world.add(entity, StatusValues(9, 9, 9, 9, 9, 9))
    world.add(entity, Health(current = 18, max = 18))

    world.tick(allDue)

    assertEquals(18, world.get(entity, Health::class)!!.current)
  }

  @Test
  fun `regen never overshoots the maximum`() {
    val world = newWorld()
    val entity = world.create()
    world.add(entity, StatusValues(9, 9, 9, 9, 9, 9))
    // One point missing, two points of regen due.
    world.add(entity, Health(current = 17, max = 18))

    world.tick(allDue)

    assertEquals(18, world.get(entity, Health::class)!!.current)
  }

  @Test
  fun `an entity without status values does not regenerate`() {
    // A pool with no attributes behind it has no vitality to regenerate from. Worth pinning: the
    // formula has a floor of 1, so a defaulted attribute of 0 would silently heal it anyway.
    val world = newWorld()
    val entity = world.create()
    world.add(entity, Health(current = 1, max = 18))

    world.tick(allDue)

    assertEquals(1, world.get(entity, Health::class)!!.current)
  }
}
