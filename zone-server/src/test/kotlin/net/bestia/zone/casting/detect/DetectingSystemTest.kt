package net.bestia.zone.casting.detect

import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DetectingSystemTest {

  private val aoi = EntityAOIService()
  private val world: EcsWorld = testWorld(systems = listOf(DetectingSystem(aoi)))

  @Test
  fun `the bearer senses masters near it, but not itself or a mob`() {
    val bearer = body(Vec3L(0, 0, 0), master = true).also { world.add(it, Detecting(remainingSeconds = 30f)) }
    val near = body(Vec3L(5, 5, 0), master = true)
    body(Vec3L(4, 0, 0), master = false)
    body(Vec3L(100, 0, 0), master = true)

    world.tick(1f)

    assertEquals(
      listOf(Detecting.Detected(near, Vec3L(5, 5, 0), isMaster = true)),
      world.get(bearer, Detecting::class)!!.found
    )
  }

  @Test
  fun `it ends when its time runs out`() {
    val bearer = body(Vec3L(0, 0, 0), master = true).also { world.add(it, Detecting(remainingSeconds = 1f)) }

    world.tick(1f)

    assertFalse(world.has(bearer, Detecting::class))
  }

  private fun body(at: Vec3L, master: Boolean): EntityId {
    val id = world.createEntity { id ->
      add(id, Position(at.x, at.y, at.z))
      if (master) add(id, Master(masterId = id))
    }
    aoi.setEntityPosition(id, at)

    return id
  }
}
