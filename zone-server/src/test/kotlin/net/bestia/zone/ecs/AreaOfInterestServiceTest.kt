package net.bestia.zone.ecs

import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AreaOfInterestServiceTest {
  private lateinit var service: AreaOfInterestService<String>

  @BeforeEach
  fun setup() {
    service = AreaOfInterestService<String>()
  }

  @Test
  fun `when two entities are in different buckets both are found`() {
    service.setEntityPosition("1", Vec3L(10, 10, 0))
    service.setEntityPosition("2", Vec3L(-10, -10, 0))

    val found = service.queryEntitiesInCube(Vec3L(0, 0, 0), 20)

    assertEquals(setOf("1", "2"), found)
  }

  @Test
  fun `add and query single entity`() {
    val id = "1"
    val pos = Vec3L(10, 10, 10)
    service.setEntityPosition(id, pos)
    val found = service.queryEntitiesInCube(pos, 1)
    assertTrue(found.contains(id))
  }

  @Test
  fun `remove entity and verify absence`() {
    val id = "2"
    val pos = Vec3L(20, 20, 20)
    service.setEntityPosition(id, pos)
    service.removeEntityPosition(id)
    val found = service.queryEntitiesInCube(pos, 1)
    assertFalse(found.contains(id))
  }

  @Test
  fun `move entity and verify new position`() {
    val id = "3"
    val pos1 = Vec3L(30, 30, 30)
    val pos2 = Vec3L(40, 40, 40)
    service.setEntityPosition(id, pos1)
    service.setEntityPosition(id, pos2)
    val foundOld = service.queryEntitiesInCube(pos1, 1)
    val foundNew = service.queryEntitiesInCube(pos2, 1)
    assertFalse(foundOld.contains(id))
    assertTrue(foundNew.contains(id))
  }

  @Test
  fun `query returns all entities in cube`() {
    val ids = listOf("4", "5", "6", "7")
    val positions = listOf(
      Vec3L(100, 100, 100),
      Vec3L(101, 101, 101),
      Vec3L(102, 102, 102),
      Vec3L(103, 103, 103)
    )
    ids.zip(positions).forEach { (id, pos) -> service.setEntityPosition(id, pos) }
    val found = service.queryEntitiesInCube(Vec3L(101, 101, 101), 3)
    assertTrue(ids.all { found.contains(it) })
  }

  @Test
  fun `entities outside query cube are not returned`() {
    val idInside = "8"
    val idOutside = "9"
    service.setEntityPosition(idInside, Vec3L(200, 200, 200))
    service.setEntityPosition(idOutside, Vec3L(300, 300, 300))
    val found = service.queryEntitiesInCube(Vec3L(200, 200, 200), 5)
    assertTrue(found.contains(idInside))
    assertFalse(found.contains(idOutside))
  }

  @Test
  fun `subdivision and merge do not lose entities`() {
    val base = 1000L
    val positions = (0 until 20).map { i -> Vec3L(base + i, base + i, base + i) }
    val ids = (10..29).map { it.toString() }
    ids.zip(positions).forEach { (id, pos) -> service.setEntityPosition(id, pos) }
    // All should be found
    val found = service.queryEntitiesInCube(Vec3L(base + 10, base + 10, base + 10), 20)
    assertTrue(ids.all { found.contains(it) })
    // Remove most entities to trigger merge
    ids.take(17).forEach { service.removeEntityPosition(it) }
    val foundAfter = service.queryEntitiesInCube(Vec3L(base + 10, base + 10, base + 10), 20)
    assertTrue(ids.takeLast(3).all { foundAfter.contains(it) })
    assertTrue(ids.take(17).none { foundAfter.contains(it) })
  }

  @Test
  fun `query empty area returns empty set`() {
    val found = service.queryEntitiesInCube(Vec3L(9999, 9999, 9999), 10)
    assertTrue(found.isEmpty())
  }

  @Test
  fun `entity on boundary is included`() {
    val id = "30"
    val pos = Vec3L(500, 500, 500)
    service.setEntityPosition(id, pos)
    val found = service.queryEntitiesInCube(Vec3L(500, 500, 500), 1)
    assertTrue(found.contains(id))
  }
}
