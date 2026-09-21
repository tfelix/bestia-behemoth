package net.bestia.zone.battle.skill

import io.mockk.mockk
import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.ecs.battle.attack.AttackDelay
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.battle.FixedRandom
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
   * Always draws zero, so `nextFloat()` is 0.0: below every hit and crit threshold, and no attack variance. The
   * point of these tests is the pathway, and with the fixture's identical stats a real roll misses about one
   * swing in five.
   */
  private val alwaysLands = FixedRandom(0f)

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
  fun `a basic swing takes real health off`() {
    // Both fighters have every attribute at 10 and no Level component, so BattleContextFactory reads level 1:
    // ATK = 1/4 + 10 + 10/5 + 10/3 = 15, and `alwaysLands` also wins the crit roll, so the swing bypasses the
    // target's soft defence for floor(2 * 15 * 1.4) = 42.
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))

    sut.attack(world, attacker, target, BattleAttack.getBasicMeleeAttack())

    assertEquals(42, world.get(target, DamageComponent::class)?.total())
  }

  @Test
  fun `an ordinary hit is smaller than a critical, and pays the target's defence`() {
    // 0.5 clears the hit check (about 0.84 for these two) but not the crit check (about 0.024), and spends half
    // the variance: floor(2 * 15 * 0.925) - 14 = 27 - 14 = 13.
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))

    serviceWith(FixedRandom(0.5f)).attack(world, attacker, target, BattleAttack.getBasicMeleeAttack())

    assertEquals(13, world.get(target, DamageComponent::class)?.total())
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
    world.readyToSwing(attacker)
    sut.attack(world, attacker, target, swing)

    assertEquals(
      2,
      world.get(target, DamageComponent::class)?.amounts?.size,
      "each swing is its own entry on the target's Damage component, so neither is lost"
    )
  }

  @Test
  fun `a second swing before the attack delay has elapsed is refused`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))
    val swing = BattleAttack.getBasicMeleeAttack()

    assertEquals(AttackOutcome.SWUNG, sut.attack(world, attacker, target, swing))

    assertEquals(
      AttackOutcome.NOT_READY,
      sut.attack(world, attacker, target, swing),
      "asking again cannot make an entity swing faster than its attack speed"
    )
    assertEquals(
      1,
      world.get(target, DamageComponent::class)?.amounts?.size,
      "and the refused swing must stage nothing"
    )
  }

  @Test
  fun `a swing out of reach costs no attack delay`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val far = world.spawnFighter(at = Vec3L(30, 0, 0))
    val near = world.spawnFighter(at = Vec3L(1, 0, 0))
    val swing = BattleAttack.getBasicMeleeAttack()

    assertEquals(AttackOutcome.OUT_OF_RANGE, sut.attack(world, attacker, far, swing))

    assertEquals(
      AttackOutcome.SWUNG,
      sut.attack(world, attacker, near, swing),
      "a swing that never happened must not put the attacker on its attack delay"
    )
  }

  @Test
  fun `the attack delay armed is the one the attacker's own agility and dexterity earn`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))

    sut.attack(world, attacker, target, BattleAttack.getBasicMeleeAttack())

    // Every attribute at 10: 700ms motion less (4*10 + 10)/1000 of it, doubled.
    assertEquals(1.33f, world.get(attacker, AttackDelay::class)?.remainingSeconds)
  }

  /** Clears whatever the last swing cost, so a test can take the next one without ticking a world. */
  private fun World.readyToSwing(entityId: EntityId) {
    getOrThrow(entityId, AttackDelay::class).remainingSeconds = 0f
  }

  @Test
  fun `a missed swing stages nothing`() {
    val neverLands = FixedRandom(1f)
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
