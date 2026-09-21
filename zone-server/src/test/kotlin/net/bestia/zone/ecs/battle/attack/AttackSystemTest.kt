package net.bestia.zone.ecs.battle.attack

import io.mockk.mockk
import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.skill.AttackExecutionService
import net.bestia.zone.battle.skill.AttackStrategyFactory
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.PropPromotionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.bestia.zone.ecs.battle.damage.Damage as DamageComponent

/**
 * Driven by hand at the production tick rate. Every swing lands ([FixedRandom] draws zero), so the number of
 * entries on the target's `Damage` is the number of swings - nothing drains it here, since
 * `ReceivedDamageSystem` is deliberately not registered.
 */
class AttackSystemTest {

  private val attacks = AttackExecutionService(
    BattleContextFactory(PropPromotionService(mockk(relaxed = true))),
    AttackStrategyFactory(LineOfSightService(), FixedRandom(0f)),
    mockk(relaxed = true)
  )

  private val world = testWorld(systems = listOf(AttackSystem(attacks)))

  @Test
  fun `a standing order swings once and then waits out the attack delay`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))
    world.add(attacker, AttackTarget(target))

    world.tick(TICK)
    assertEquals(1, world.swingsOn(target), "the order swings on the very first tick")

    // A second of ticks, against an attack delay of 1.33s for these attributes.
    repeat(20) { world.tick(TICK) }
    assertEquals(1, world.swingsOn(target), "and nothing more until the delay has run out")
  }

  @Test
  fun `the order keeps swinging for as long as it stands`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))
    world.add(attacker, AttackTarget(target))

    // Five seconds at a 1.33s cadence.
    repeat(100) { world.tick(TICK) }

    assertEquals(4, world.swingsOn(target))
  }

  @Test
  fun `a stalled server does not let an attacker bank its swings`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))
    world.add(attacker, AttackTarget(target))

    world.tick(TICK)
    // Four attack delays' worth of wall clock arriving in one lump, which is what a lag spike looks like.
    world.tick(5f)

    assertEquals(2, world.swingsOn(target), "a spike releases the one swing it is owed, not the four it skipped")
  }

  @Test
  fun `a dead target ends the order`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))
    world.add(attacker, AttackTarget(target))
    world.add(target, Dead())

    world.tick(TICK)

    assertFalse(world.has(attacker, AttackTarget::class), "a corpse is not something to keep hitting")
    assertFalse(world.has(target, DamageComponent::class))
  }

  @Test
  fun `a target that leaves the world ends the order`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))
    world.add(attacker, AttackTarget(target))
    world.destroy(target)

    world.tick(TICK)

    assertFalse(world.has(attacker, AttackTarget::class))
  }

  @Test
  fun `a dead attacker stops swinging`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))
    world.add(attacker, AttackTarget(target))
    world.add(attacker, Dead())

    world.tick(TICK)

    assertFalse(world.has(attacker, AttackTarget::class))
    assertFalse(world.has(target, DamageComponent::class))
  }

  @Test
  fun `a target out of reach keeps the order, and is hit again once it comes back`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    val target = world.spawnFighter(at = Vec3L(30, 0, 0))
    world.add(attacker, AttackTarget(target))

    world.tick(TICK)
    assertTrue(world.has(attacker, AttackTarget::class), "walking out of reach is the target's doing, not the player's")
    assertEquals(0, world.swingsOn(target))

    world.getOrThrow(target, Position::class).x = 1
    world.tick(TICK)

    assertEquals(1, world.swingsOn(target))
  }

  @Test
  fun `an attacker with no order still has its attack delay counted down`() {
    val attacker = world.spawnFighter(at = Vec3L(0, 0, 0))
    world.add(attacker, AttackDelay(1f))

    repeat(5) { world.tick(TICK) }

    // What a mob is: it swings through its behaviour tree and never holds an AttackTarget at all. The delta
    // is for the five separate float subtractions, not for any slack in the rule.
    assertEquals(0.75f, world.getOrThrow(attacker, AttackDelay::class).remainingSeconds, 1e-4f)
  }

  private fun World.swingsOn(target: EntityId): Int {
    return get(target, DamageComponent::class)?.amounts?.size ?: 0
  }

  private fun World.spawnFighter(at: Vec3L): EntityId = createEntity { id ->
    add(id, Position.fromVec3(at))
    add(id, StatusValues(strength = 10, intelligence = 10, vitality = 10, dexterity = 10, willpower = 10, agility = 10))
  }

  private companion object {
    /** The production tick rate, `world.tick-rate: 20`. */
    const val TICK = 0.05f
  }
}
