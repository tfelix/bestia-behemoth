package net.bestia.zone.ecs.battle.damage

import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Death tagging, and that a body already on the ground is not killed a second time. */
class ReceivedDamageSystemTest {

  private val sut = ReceivedDamageSystem()

  @Test
  fun `dropping to zero hit points tags the entity dead`() {
    val world = testWorld()
    val id = world.createEntity { eid ->
      add(eid, Health(current = 10, max = 100))
      add(eid, Damage().also { it.add(10, sourceEntity = 99L) })
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
      add(eid, Damage().also { it.add(10, sourceEntity = 99L) })
    }

    sut.update(world, 0f)
    world.tick(0f)

    val firstDeath = world.get(id, Dead::class)
    firstDeath?.resolved = true

    world.add(id, Damage().also { it.add(5, sourceEntity = 99L) })
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
      add(eid, Damage().also { it.add(3, sourceEntity = 99L) })
    }

    sut.update(world, 0f)
    world.tick(0f)

    assertFalse(world.has(id, Dead::class))
  }
}
