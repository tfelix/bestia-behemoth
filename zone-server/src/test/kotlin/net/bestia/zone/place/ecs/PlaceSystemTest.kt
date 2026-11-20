package net.bestia.zone.place.ecs

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PlaceSystemTest {

  private var version = 1L
  private var answer = PlaceRef("Elm Vale")
  private val names = mockk<PlaceNameService> {
    every { version } answers { this@PlaceSystemTest.version }
    every { resolve(any(), any()) } answers { answer }
  }

  @Test
  fun `a town founded around a player standing still changes their place`() {
    val world = testWorld()
    val system = PlaceSystem(names)
    val player = world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(10, 10, 0)))
      add(id, Place(PlaceRef("Elm Vale")))
    }
    system.update(world, 0f)
    world.get(player, Position::class)!!.clearDirty()

    answer = PlaceRef("Oakhold", SettlementArea(0, 0, 150))
    system.update(world, 0f)
    assertEquals("Elm Vale", world.get(player, Place::class)!!.place.name, "nothing changed, nothing to resolve")

    version++
    system.update(world, 0f)
    assertEquals("Oakhold", world.get(player, Place::class)!!.place.name)
  }
}
