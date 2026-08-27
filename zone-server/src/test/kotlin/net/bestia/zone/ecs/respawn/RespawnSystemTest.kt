package net.bestia.zone.ecs.respawn

import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.battle.damage.TakenDamage
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.InCombat
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.GroundHeight
import net.bestia.zone.ecs.movement.Grounded
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Getting back up: where, at what height, at what health, and what the body stops carrying. */
class RespawnSystemTest {

  private val surfaceAt77 = GroundHeight { 77L }
  private val offTheGrid = GroundHeight { null }

  private fun World.deadBody(savePoint: Vec3L) = createEntity { eid ->
    add(eid, Position(90, 91, 92))
    add(eid, Grounded)
    add(eid, Health(current = 0, max = 200))
    add(eid, TakenDamage())
    add(eid, InCombat())
    add(eid, Dead())
    add(eid, Respawn(savePoint))
  }

  @Test
  fun `the body is moved to its save point with a single hit point`() {
    val world = testWorld()
    val id = world.deadBody(Vec3L(10, 20, 30))

    RespawnSystem(surfaceAt77).update(world, 0f)

    val position = world.get(id, Position::class)
    assertEquals(10L, position?.x)
    assertEquals(20L, position?.y)
    assertEquals(1, world.get(id, Health::class)?.current)
  }

  /**
   * The stored save point's own z is from whenever it was written; the terrain decides. Getting this
   * wrong buries the body, which renders as a black screen rather than failing loudly.
   */
  @Test
  fun `the height comes from the terrain, not from the stored save point`() {
    val world = testWorld()
    val id = world.deadBody(Vec3L(10, 20, 30))

    RespawnSystem(surfaceAt77).update(world, 0f)

    assertEquals(77L, world.get(id, Position::class)?.z)
    assertTrue(world.has(id, Grounded::class), "nothing left for the grounding sweep to do")
  }

  @Test
  fun `a save point the terrain cannot answer for is handed to the grounding sweep`() {
    val world = testWorld()
    val id = world.deadBody(Vec3L(10, 20, 30))

    RespawnSystem(offTheGrid).update(world, 0f)
    world.tick(0f)

    assertEquals(30L, world.get(id, Position::class)?.z)
    assertFalse(world.has(id, Grounded::class))
  }

  @Test
  fun `death, combat and the damage ledger are all cleared`() {
    val world = testWorld()
    val id = world.deadBody(Vec3L(10, 20, 30))

    RespawnSystem(surfaceAt77).update(world, 0f)
    world.tick(0f)

    assertFalse(world.has(id, Dead::class))
    assertFalse(world.has(id, InCombat::class))
    assertFalse(world.has(id, TakenDamage::class))
    assertFalse(world.has(id, Respawn::class))
  }

  @Test
  fun `whatever it was walking towards is dropped`() {
    val world = testWorld()
    val id = world.createEntity { eid ->
      add(eid, Position(90, 91, 92))
      add(eid, Health(current = 0, max = 200))
      add(eid, Path(mutableListOf(Vec3L(91, 91, 92))))
      add(eid, Dead())
      add(eid, Respawn(Vec3L(10, 20, 30)))
    }

    RespawnSystem(surfaceAt77).update(world, 0f)
    world.tick(0f)

    assertFalse(world.has(id, Path::class))
  }
}
