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

  /**
   * The test above claims to cover subdivision and does not: twenty entities never reach the
   * forty-entity threshold, so the tree it exercises is a single leaf. This is the same property
   * against a tree deep enough to have grandchildren, which is where `merge` used to discard them.
   *
   * The spread is wide enough that the first split separates them and tight enough that several
   * children split again, so the collapse has more than one level to pull up.
   */
  @Test
  fun `a merge collapsing a deep subtree keeps entities more than one level down`() {
    val ids = (0 until 400).map { "deep-$it" }

    ids.forEachIndexed { i, id ->
      service.setEntityPosition(id, Vec3L((i % 20) * 3L, (i / 20) * 3L, 0))
    }

    assertEquals(400, service.getTotalEntityCount())

    // Down to three, which is under the merge threshold at every level, so the whole tree collapses.
    ids.dropLast(3).forEach { service.removeEntityPosition(it) }

    val survivors = service.queryEntitiesInCube(Vec3L(30, 30, 0), 400)

    assertEquals(3, service.getTotalEntityCount(), "the index disagrees with what it holds")
    assertEquals(ids.takeLast(3).toSet(), survivors)
  }

  /**
   * Subdivision used to recurse while a node was over the threshold, and coincident entries never
   * separate however far you split - so it split until the cube reached zero extent and then failed
   * every insert, silently. Splitting now stops when a cube can no longer be halved.
   */
  @Test
  fun `entities sharing one position are all kept`() {
    val ids = (0 until 100).map { "stacked-$it" }
    val pos = Vec3L(64, 64, 64)

    ids.forEach { service.setEntityPosition(it, pos) }

    assertEquals(100, service.getTotalEntityCount())
    assertEquals(ids.toSet(), service.queryEntitiesInCube(pos, 2))
  }

  @Test
  fun `a query can ask for one layer`() {
    service.setEntityPosition("mob", Vec3L(10, 10, 10), AoiLayer.DYNAMIC)
    service.setEntityPosition("tree", Vec3L(11, 11, 11), AoiLayer.STATIC)

    val centre = Vec3L(10, 10, 10)

    assertEquals(setOf("mob", "tree"), service.queryEntitiesInCube(centre, 8))
    assertEquals(setOf("mob"), service.queryEntitiesInCube(centre, 8, AoiLayer.DYNAMIC_ONLY))
    assertEquals(setOf("tree"), service.queryEntitiesInCube(centre, 8, AoiLayer.STATIC_ONLY))
  }

  /** A layer is a property of the entry, so re-placing an entity must be able to change it. */
  @Test
  fun `re-placing an entity replaces its layer too`() {
    service.setEntityPosition("x", Vec3L(5, 5, 5), AoiLayer.STATIC)
    service.setEntityPosition("x", Vec3L(6, 6, 6), AoiLayer.DYNAMIC)

    assertTrue(service.queryEntitiesInCube(Vec3L(6, 6, 6), 4, AoiLayer.STATIC_ONLY).isEmpty())
    assertEquals(setOf("x"), service.queryEntitiesInCube(Vec3L(6, 6, 6), 4, AoiLayer.DYNAMIC_ONLY))
  }

  /**
   * The root starts at 1024 centred on the origin, so anything on a 128 km world is outside it and
   * arrives via `growRootToContain` - which rebuilds the tree and has to carry every existing entry,
   * and its leaf bookkeeping, across.
   */
  @Test
  fun `growing the root past a distant position keeps everything already indexed`() {
    val near = (0 until 60).map { "near-$it" }
    near.forEachIndexed { i, id -> service.setEntityPosition(id, Vec3L(i * 5L, 0, 0)) }

    service.setEntityPosition("far", Vec3L(128_000, 96_000, 40))

    assertEquals(61, service.getTotalEntityCount())
    assertEquals(setOf("far"), service.queryEntitiesInCube(Vec3L(128_000, 96_000, 40), 4))
    assertEquals(near.toSet(), service.queryEntitiesInCube(Vec3L(150, 0, 0), 600))

    // And the rebuilt tree still removes cleanly, which is what a stale leaf map would break.
    near.forEach { service.removeEntityPosition(it) }
    assertEquals(1, service.getTotalEntityCount())
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
