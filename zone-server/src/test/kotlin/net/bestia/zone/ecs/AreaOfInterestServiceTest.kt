package net.bestia.zone.ecs

import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AreaOfInterestServiceTest {
  private lateinit var service: AreaOfInterestService

  @BeforeEach
  fun setup() {
    service = AreaOfInterestService()
  }

  @Test
  fun `when two entities are in different cells both are found`() {
    service.setEntityPosition(1, Vec3L(10, 10, 0))
    service.setEntityPosition(2, Vec3L(-10, -10, 0))

    val found = service.queryEntitiesInCube(Vec3L(0, 0, 0), 20)

    assertEquals(setOf(1L, 2L), found)
  }

  @Test
  fun `add and query single entity`() {
    val pos = Vec3L(10, 10, 10)
    service.setEntityPosition(1, pos)

    assertTrue(service.queryEntitiesInCube(pos, 1).contains(1L))
  }

  @Test
  fun `remove entity and verify absence`() {
    val pos = Vec3L(20, 20, 20)
    service.setEntityPosition(2, pos)
    service.removeEntityPosition(2)

    assertFalse(service.queryEntitiesInCube(pos, 1).contains(2L))
    assertEquals(0, service.getTotalEntityCount())
  }

  @Test
  fun `move entity and verify new position`() {
    val pos1 = Vec3L(30, 30, 30)
    val pos2 = Vec3L(400, 400, 40)
    service.setEntityPosition(3, pos1)
    service.setEntityPosition(3, pos2)

    assertFalse(service.queryEntitiesInCube(pos1, 1).contains(3L))
    assertTrue(service.queryEntitiesInCube(pos2, 1).contains(3L))
  }

  @Test
  fun `query returns all entities in cube`() {
    val ids = listOf(4L, 5L, 6L, 7L)
    val positions = listOf(Vec3L(100, 100, 100), Vec3L(101, 101, 101), Vec3L(102, 102, 102), Vec3L(103, 103, 103))
    ids.zip(positions).forEach { (id, pos) -> service.setEntityPosition(id, pos) }

    assertEquals(ids.toSet(), service.queryEntitiesInCube(Vec3L(101, 101, 101), 3))
  }

  @Test
  fun `entities outside query cube are not returned`() {
    service.setEntityPosition(8, Vec3L(200, 200, 200))
    service.setEntityPosition(9, Vec3L(300, 300, 300))

    val found = service.queryEntitiesInCube(Vec3L(200, 200, 200), 5)

    assertEquals(setOf(8L), found)
  }

  @Test
  fun `removing most of a crowd keeps the rest`() {
    val ids = (0L until 400L).toList()
    ids.forEach { i -> service.setEntityPosition(i, Vec3L((i % 20) * 3L, (i / 20) * 3L, 0)) }

    ids.dropLast(3).forEach { service.removeEntityPosition(it) }

    assertEquals(3, service.getTotalEntityCount())
    assertEquals(ids.takeLast(3).toSet(), service.queryEntitiesInCube(Vec3L(30, 30, 0), 400))
  }

  @Test
  fun `entities sharing one position are all kept`() {
    val ids = (0L until 100L).toList()
    val pos = Vec3L(64, 64, 64)

    ids.forEach { service.setEntityPosition(it, pos) }

    assertEquals(100, service.getTotalEntityCount())
    assertEquals(ids.toSet(), service.queryEntitiesInCube(pos, 2))
  }

  @Test
  fun `a query can ask for one layer`() {
    service.setEntityPosition(MOB, Vec3L(10, 10, 10), AoiLayer.DYNAMIC)
    service.setEntityPosition(TREE, Vec3L(11, 11, 11), AoiLayer.STATIC)

    val centre = Vec3L(10, 10, 10)

    assertEquals(setOf(MOB, TREE), service.queryEntitiesInCube(centre, 8))
    assertEquals(setOf(MOB), service.queryEntitiesInCube(centre, 8, AoiLayer.DYNAMIC_ONLY))
    assertEquals(setOf(TREE), service.queryEntitiesInCube(centre, 8, AoiLayer.STATIC_ONLY))
  }

  @Test
  fun `re-placing an entity replaces its layer too`() {
    service.setEntityPosition(TREE, Vec3L(5, 5, 5), AoiLayer.STATIC)
    service.setEntityPosition(TREE, Vec3L(6, 6, 6), AoiLayer.DYNAMIC)

    assertTrue(service.queryEntitiesInCube(Vec3L(6, 6, 6), 4, AoiLayer.STATIC_ONLY).isEmpty())
    assertEquals(setOf(TREE), service.queryEntitiesInCube(Vec3L(6, 6, 6), 4, AoiLayer.DYNAMIC_ONLY))
    assertEquals(1, service.getTotalEntityCount())
  }

  @Test
  fun `a distant position is indexed like a near one`() {
    service.setEntityPosition(1, Vec3L(5, 0, 0))
    service.setEntityPosition(2, Vec3L(128_000, 96_000, 40))

    assertEquals(setOf(2L), service.queryEntitiesInCube(Vec3L(128_000, 96_000, 40), 4))
    assertEquals(setOf(1L), service.queryEntitiesInCube(Vec3L(0, 0, 0), 20))
  }

  @Test
  fun `negative coordinates fall into their own cells`() {
    service.setEntityPosition(1, Vec3L(-1, -1, 0))
    service.setEntityPosition(2, Vec3L(0, 0, 0))

    assertEquals(setOf(1L), service.queryEntitiesInCube(Vec3L(-1, -1, 0), 0))
    assertEquals(setOf(1L, 2L), service.queryEntitiesInCube(Vec3L(0, 0, 0), 2))
  }

  @Test
  fun `a step inside one cell does not move the entity between cells`() {
    service.setEntityPosition(1, Vec3L(1, 1, 0))
    val before = service.cellChanges

    service.setEntityPosition(1, Vec3L(2, 1, 0))
    service.setEntityPosition(1, Vec3L(AreaOfInterestService.CELL_SIZE, 1, 0))

    assertEquals(before + 1, service.cellChanges, "only the step into the next cell counts")
  }

  @Test
  fun `an entity on the upper face of the cube is included`() {
    service.setEntityPosition(1, Vec3L(10, 10, 10))

    assertEquals(setOf(1L), service.queryEntitiesInCube(Vec3L(5, 5, 5), 10))
  }

  @Test
  fun `query empty area returns empty set`() {
    assertTrue(service.queryEntitiesInCube(Vec3L(9999, 9999, 9999), 10).isEmpty())
  }

  @Test
  fun `entity on boundary is included`() {
    val pos = Vec3L(500, 500, 500)
    service.setEntityPosition(30, pos)

    assertTrue(service.queryEntitiesInCube(pos, 1).contains(30L))
  }

  @Test
  fun `anyone within a horizontal radius is found, height ignored`() {
    service.setEntityPosition(1, Vec3L(100, 100, 500))

    assertTrue(service.anyWithinHorizontal(100, 103, 3))
    assertFalse(service.anyWithinHorizontal(100, 104, 3))
  }

  private companion object {
    const val MOB = 101L
    const val TREE = 102L
  }
}
