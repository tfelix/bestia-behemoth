package net.bestia.zone.battle.skill

import io.mockk.mockk
import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.PropPromotionService
import java.util.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.bestia.zone.ecs.battle.damage.Damage as DamageComponent

class AttackExecutionServiceTest {

  private val world = testWorld()

  /**
   * Always draws zero, so `nextFloat()` is 0.0: below every hit and crit threshold. The point of these tests
   * is the pathway, and with the fixture's identical stats a real roll lands a coin-flip miss half the time.
   */
  private val alwaysLands = object : Random() {
    override fun next(bits: Int) = 0
  }

  private fun serviceWith(random: Random) = AttackExecutionService(
    BattleContextFactory(PropPromotionService(mockk(relaxed = true))),
    AttackStrategyFactory(LineOfSightService(), random),
    mockk(relaxed = true)
  )

  private val sut = serviceWith(alwaysLands)

  @Test
  fun `a swing in reach stages damage on the target`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))

    sut.attack(world, attacker, target, BattleAttack.getBasicMeleeAttack())

    // What the old arrangement could not do at all: a mob cast skill id 0, a row `skills.yml` never had.
    assertTrue(world.has(target, DamageComponent::class), "a swing in reach must resolve onto the target")
  }

  @Test
  fun `a basic swing deals no damage yet, because the melee formula is still a placeholder`() {
    // Pins the honest current state rather than a balance decision: BaseDamageCalculator.calculateDamage is
    // commented out down to `return 0` ("placeholder until the real damage formula is invented"). When that
    // formula lands this test should fail, and be replaced by one asserting the real numbers.
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))

    sut.attack(world, attacker, target, BattleAttack.getBasicMeleeAttack())

    assertEquals(0, world.get(target, DamageComponent::class)?.total() ?: 0)
  }

  @Test
  fun `a target out of the weapon's reach is not touched`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(30, 0, 0))

    sut.attack(world, attacker, target, BattleAttack.getBasicMeleeAttack())

    assertFalse(world.has(target, DamageComponent::class), "a basic swing reaches one tile")
  }

  @Test
  fun `an attacker with no status values cannot fight, and is refused rather than throwing`() {
    // What a prop or a freshly created entity looks like: BattleContextFactory returns null for it.
    val attacker = world.createEntity { id -> world.add(id, Position.fromVec3(Vec3L(0, 0, 0))) }
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))

    sut.attack(world, attacker, target, BattleAttack.getBasicMeleeAttack())

    assertFalse(world.has(target, DamageComponent::class))
  }

  @Test
  fun `two swings on the same target add up rather than replacing each other`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))
    val swing = BattleAttack.getBasicMeleeAttack(Element.NORMAL)

    sut.attack(world, attacker, target, swing)
    sut.attack(world, attacker, target, swing)

    assertEquals(
      2,
      world.get(target, DamageComponent::class)?.amounts?.size,
      "each swing is its own entry on the target's Damage component, so neither is lost"
    )
  }

  @Test
  fun `a missed swing stages nothing`() {
    val neverLands = object : Random() {
      override fun next(bits: Int) = Int.MAX_VALUE
    }
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))

    serviceWith(neverLands).attack(world, attacker, target, BattleAttack.getBasicMeleeAttack())

    assertFalse(
      world.has(target, DamageComponent::class),
      "a miss is broadcast for the client to show, but must not stage a hit"
    )
  }

  private fun World.spawnFighter(at: Vec3L): EntityId = createEntity { id ->
    add(id, Position.fromVec3(at))
    add(id, StatusValues(strength = 10, intelligence = 10, vitality = 10, dexterity = 10, willpower = 10, agility = 10))
  }
}
