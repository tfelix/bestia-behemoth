package net.bestia.zone.ecs.battle.damage

import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.exp.Exp
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** What dying costs a player, and that it costs it exactly once. */
class PlayerDeathSystemTest {

  private val sut = PlayerDeathSystem(ZoneConfig(tickRate = 10))

  @Test
  fun `a dead player forfeits one percent of its current exp`() {
    val world = testWorld()
    val id = world.createEntity { eid ->
      add(eid, Account(1L))
      add(eid, Exp(1000, 5000))
      add(eid, Dead())
    }

    sut.update(world, 0f)

    assertEquals(990, world.get(id, Exp::class)?.value)
  }

  @Test
  fun `the penalty is charged once however long the body lies there`() {
    val world = testWorld()
    val id = world.createEntity { eid ->
      add(eid, Account(1L))
      add(eid, Exp(1000, 5000))
      add(eid, Dead())
    }

    repeat(5) { sut.update(world, 0f) }

    assertEquals(990, world.get(id, Exp::class)?.value)
  }

  /** Floored rather than rounded, so an almost-empty bar cannot be driven negative. */
  @Test
  fun `an almost empty exp bar forfeits nothing`() {
    val world = testWorld()
    val id = world.createEntity { eid ->
      add(eid, Account(1L))
      add(eid, Exp(50, 5000))
      add(eid, Dead())
    }

    sut.update(world, 0f)

    assertEquals(50, world.get(id, Exp::class)?.value)
  }

  @Test
  fun `the body stops walking where it fell`() {
    val world = testWorld()
    val id = world.createEntity { eid ->
      add(eid, Account(1L))
      add(eid, Exp(100, 5000))
      add(eid, Path(mutableListOf(Vec3L(1, 0, 0))))
      add(eid, Dead())
    }

    sut.update(world, 0f)

    assertFalse(world.has(id, Path::class))
  }

  /** A wild mob is DeathSystem's business; this stage must not touch anything without an owner. */
  @Test
  fun `an unowned corpse is left alone`() {
    val world = testWorld()
    val id = world.createEntity { eid ->
      add(eid, Exp(1000, 5000))
      add(eid, Dead())
    }

    sut.update(world, 0f)

    assertEquals(1000, world.get(id, Exp::class)?.value)
  }
}
