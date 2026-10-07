package net.bestia.zone.battle.ecs.damage

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.attack.AttackExecutionService
import net.bestia.zone.battle.attack.AttackStrategyFactory
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.battle.ecs.attack.AttackSystem
import net.bestia.zone.battle.ecs.attack.AttackTarget
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.StatusValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PropPromotionService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.bestia.zone.entity.ecs.Dead

/** Death tagging, that a body already on the ground is not killed a second time, and that no reported hit is lost. */
class ReceivedDamageSystemTest {

  private val sut = ReceivedDamageSystem()

  /**
   * The second swing is staged on the tick that drains the first, so it reaches a component that is still
   * there but already drained. The client is told about both, so both must come off [Health].
   */
  @Test
  fun `a hit staged on the tick the previous one is drained still lands`() {
    val messages = mockk<OutMessageProcessor>(relaxed = true)
    val attacks = AttackExecutionService(
      BattleContextFactory(PropPromotionService(mockk(relaxed = true))),
      AttackStrategyFactory(LineOfSightService(), FixedRandom(0f)),
      messages
    )
    val world = testWorld(systems = listOf(AttackSystem(attacks), sut))
    val target = world.spawnFighter(at = Vec3L(1, 0, 0))
    val first = world.spawnFighter(at = Vec3L(0, 0, 0))
    val second = world.spawnFighter(at = Vec3L(2, 0, 0))

    world.add(first, AttackTarget(target))
    world.tick(TICK)
    world.add(second, AttackTarget(target))
    // Well inside the attack delay, so each attacker swings exactly once.
    repeat(5) { world.tick(TICK) }

    val reported = mutableListOf<DamageEntitySMSG>()
    verify { messages.sendToObserversOf(any(), target, capture(reported)) }
    assertEquals(2, reported.size)
    assertTrue(reported.all { it.damage > 0 })
    assertEquals(FULL_HEALTH - reported.sumOf { it.damage }, world.getOrThrow(target, Health::class).current)
  }

  @Test
  fun `dropping to zero hit points tags the entity dead`() {
    val world = testWorld()
    val id = world.createEntity { eid ->
      add(eid, Health(current = 10, max = 100))
      add(eid, IncomingDamage().also { it.add(10, sourceEntity = 99L) })
    }

    sut.update(world, 0f)
    world.tick(0f)

    assertTrue(world.has(id, Dead::class))
  }

  /**
   * A player body lies at 0 HP with its Dead component until it respawns. `World.add` overwrites, so
   * a second drain replacing that component would wipe the flag saying this death has already cost
   * its owner EXP - and PlayerDeathSystem would charge them again.
   */
  @Test
  fun `damage landing on a body already down leaves its death untouched`() {
    val world = testWorld()
    val id = world.createEntity { eid ->
      add(eid, Health(current = 10, max = 100))
      add(eid, IncomingDamage().also { it.add(10, sourceEntity = 99L) })
    }

    sut.update(world, 0f)
    world.tick(0f)

    val firstDeath = world.get(id, Dead::class)
    firstDeath?.resolved = true

    world.add(id, IncomingDamage().also { it.add(5, sourceEntity = 99L) })
    sut.update(world, 0f)
    world.tick(0f)

    assertSame(firstDeath, world.get(id, Dead::class))
    assertTrue(world.get(id, Dead::class)?.resolved == true, "the death was re-charged")
  }

  @Test
  fun `a survivor is not tagged dead`() {
    val world = testWorld()
    val id = world.createEntity { eid ->
      add(eid, Health(current = 10, max = 100))
      add(eid, IncomingDamage().also { it.add(3, sourceEntity = 99L) })
    }

    sut.update(world, 0f)
    world.tick(0f)

    assertFalse(world.has(id, Dead::class))
  }

  private fun World.spawnFighter(at: Vec3L): EntityId {
    return createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, Health(FULL_HEALTH, FULL_HEALTH))
      add(id, StatusValues(strength = 10, intelligence = 10, vitality = 10, dexterity = 10, willpower = 10, agility = 10))
    }
  }

  private companion object {
    const val FULL_HEALTH = 1000

    /** The production tick rate, `world.tick-rate: 20`. */
    const val TICK = 0.05f
  }
}
