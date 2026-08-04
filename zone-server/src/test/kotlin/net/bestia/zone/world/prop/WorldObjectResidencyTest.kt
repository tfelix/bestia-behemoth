package net.bestia.zone.world.prop

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.ecs.AoiLayer
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.prop.PropPose
import net.bestia.zone.ecs.prop.StaticVisual
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.stream.ChunkSubscriptionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Residency: which static entities exist, driven by which chunks a client holds.
 *
 * Against a stub source rather than a generated world, because every property here is about the *bookkeeping* -
 * refcounting, the slab-to-column collapse, the budget, what happens on a teleport - and a real world would
 * make the answers depend on where the trees happen to be. `GeneratedPropSource` is the thing that reads the
 * generator, and it is a separate concern from when the reading happens.
 */
class WorldObjectResidencyTest {

  private lateinit var subscriptions: ChunkSubscriptionService
  private lateinit var aoi: EntityAOIService
  private lateinit var source: StubSource
  private lateinit var residency: WorldObjectResidencyService

  /** Three trees per column, at positions derived from the column so they are distinguishable. */
  private class StubSource : WorldObjectSource {
    var asked = 0

    override val kinds = setOf(StaticEntityKind.TREE)

    override fun sitesIn(chunk: ChunkPos): List<WorldObjectSite> {
      asked++
      return (0 until 3).map { i ->
        WorldObjectSite(
          kind = StaticEntityKind.TREE,
          propId = (chunk.x.toLong() shl 40) or (chunk.y.toLong() shl 8) or i.toLong(),
          position = Vec3L(chunk.x * 32L + i, chunk.y * 32L, 64),
          variant = i,
          heightDm = 80,
          yaw = 0f
        )
      }
    }
  }

  @BeforeEach
  fun setup() {
    subscriptions = ChunkSubscriptionService()
    aoi = EntityAOIService()
    source = StubSource()
    residency = WorldObjectResidencyService(listOf(source), kindRegistry(), aoi, subscriptions)
  }

  @Test
  fun `a column materialises on its first subscriber and releases on its last`() {
    val world = testWorld()

    subscriptions.markSent(1L, ChunkPos(4, 5, 0))
    assertEquals(1, residency.pending, "holding a chunk should queue exactly one column")

    residency.drain(world, budget = 8)
    assertEquals(3, residency.residentEntities)
    assertEquals(1, residency.residentColumns)

    subscriptions.unsend(1L, ChunkPos(4, 5, 0))
    residency.drain(world, budget = 8)

    assertEquals(0, residency.residentEntities, "the last holder left and the column is still resident")
    assertEquals(0, residency.residentColumns)
  }

  /**
   * The slab-to-column collapse, and the bug it prevents is specific: a subscription addresses one *slab*, so
   * a player standing in a cave holds the column's surface slab and the one below it. Without the refcount,
   * leaving the cave would fire a last-subscriber callback for the lower slab and delete the trees overhead.
   */
  @Test
  fun `three subscribed slabs of one column are one set of entities`() {
    val world = testWorld()

    subscriptions.markSent(1L, ChunkPos(4, 5, 0))
    subscriptions.markSent(1L, ChunkPos(4, 5, 1))
    subscriptions.markSent(1L, ChunkPos(4, 5, 2))
    residency.drain(world, budget = 8)

    assertEquals(1, residency.residentColumns, "three slabs of one column are one column")
    assertEquals(3, residency.residentEntities)
    assertEquals(1, source.asked, "the source was asked once per column, not once per slab")

    // Two of the three released: the column stays.
    subscriptions.unsend(1L, ChunkPos(4, 5, 2))
    subscriptions.unsend(1L, ChunkPos(4, 5, 1))
    residency.drain(world, budget = 8)
    assertEquals(3, residency.residentEntities, "a column with a slab still held must keep its entities")

    subscriptions.unsend(1L, ChunkPos(4, 5, 0))
    residency.drain(world, budget = 8)
    assertEquals(0, residency.residentEntities)
  }

  /** Two players over the same ground get one set of trees, and it survives either of them leaving. */
  @Test
  fun `overlapping players do not double-materialise a column`() {
    val world = testWorld()

    subscriptions.markSent(1L, ChunkPos(0, 0, 0))
    subscriptions.markSent(2L, ChunkPos(0, 0, 0))
    residency.drain(world, budget = 8)

    assertEquals(3, residency.residentEntities)
    assertEquals(1, source.asked)

    subscriptions.unsend(1L, ChunkPos(0, 0, 0))
    residency.drain(world, budget = 8)
    assertEquals(3, residency.residentEntities, "one player left and the other's trees went with them")
  }

  /**
   * A teleport withdraws everything and re-announces, which happens inside one tick.
   *
   * The entities must be the *same* ones. Releasing and reloading would hand every client a new set of ids for
   * the same trees, and since a prop's id is what a client points at to interact with one, that is a stale
   * reference for everybody who was already looking.
   */
  @Test
  fun `a release and re-hold inside one tick keeps the same entities`() {
    val world = testWorld()

    subscriptions.markSent(1L, ChunkPos(7, 7, 0))
    residency.drain(world, budget = 8)
    val before = residency.entitiesIn(7, 7).toList()

    // The `reset` manifest's shape: unsend everything, then mark the survivors sent again.
    subscriptions.unsend(1L, ChunkPos(7, 7, 0))
    subscriptions.markSent(1L, ChunkPos(7, 7, 0))
    residency.drain(world, budget = 8)

    assertEquals(before, residency.entitiesIn(7, 7).toList(), "the column was churned inside one tick")
    assertEquals(1, source.asked, "the column was rebuilt when nothing about it had changed")
  }

  /** `forget` is the disconnect path, and it has to release everything the account was holding. */
  @Test
  fun `forgetting an account releases the columns it held alone`() {
    val world = testWorld()

    subscriptions.markSent(1L, ChunkPos(1, 1, 0))
    subscriptions.markSent(1L, ChunkPos(2, 1, 0))
    residency.drain(world, budget = 8)
    assertEquals(2, residency.residentColumns)

    subscriptions.forget(1L)
    residency.drain(world, budget = 8)

    assertEquals(0, residency.residentColumns)
    assertEquals(0, residency.residentEntities)
  }

  @Test
  fun `the drain budget bounds how much work one tick does`() {
    val world = testWorld()

    for (x in 0 until 10) subscriptions.markSent(1L, ChunkPos(x, 0, 0))
    assertEquals(10, residency.pending)

    val (loaded, _) = residency.drain(world, budget = 4)

    assertEquals(4, loaded)
    assertEquals(6, residency.pending, "the rest must wait for the next tick")
    assertEquals(12, residency.residentEntities)
  }

  /**
   * **The load-bearing property of the whole design.**
   *
   * `ZoneEngine.syncDirtyComponents` walks every `Dirtyable` store on every tick, and a `Dirtyable` cannot opt
   * out - it sets its own flag from inside its own setters with no reference to the world or its id, so there
   * is no dirty list to stay off. The only way out of the scan is not to be in the store, which is why props
   * carry `PropPose` rather than `Position` and `PropVitality` rather than `Health`.
   *
   * Being out of the `Position` store also keeps them out of `ChunkStreamSystem.groundNewcomers`, which scans
   * that whole store every tick, and out of `HpRegenSystem`, which queries `Health` directly.
   */
  @Test
  fun `resident props are in no Dirtyable store`() {
    val world = testWorld()

    for (x in 0 until 20) subscriptions.markSent(1L, ChunkPos(x, 0, 0))
    residency.drain(world, budget = 64)

    assertEquals(60, residency.residentEntities)

    world.read {
      assertEquals(0, store(Position::class).size, "props are in the Position store and will be synced per tick")
      assertEquals(0, store(Health::class).size, "props are in the Health store and will be regenerated")
      assertEquals(60, store(PropPose::class).size)
      assertEquals(60, store(StaticVisual::class).size)
    }
  }

  /**
   * A prop is findable by an area query even though it is synced by chunk.
   *
   * Those two things looked coupled and are not: the octree decides what an area-of-effect spell hits, and
   * `Dirtyable` decides what generates per-tick traffic. `ZoneEngine` only ever inserts into the octree from
   * its dirty-`Position` loop, so residency has to insert directly - and if it did not, a fireball would spare
   * every tree in the world.
   */
  @Test
  fun `props are findable by an area query and tagged STATIC`() {
    val world = testWorld()

    subscriptions.markSent(1L, ChunkPos(0, 0, 0))
    residency.drain(world, budget = 8)

    val centre = Vec3L(1, 0, 64)
    assertEquals(3, aoi.queryEntitiesInCube(centre, 8).size, "an area query cannot see the trees")
    assertEquals(3, aoi.queryEntitiesInCube(centre, 8, AoiLayer.STATIC_ONLY).size)
    assertTrue(
      aoi.queryEntitiesInCube(centre, 8, AoiLayer.DYNAMIC_ONLY).isEmpty(),
      "a tree answered a dynamic-only query, so perception will see a wood full of neighbours"
    )
  }

  @Test
  fun `releasing a column takes its props out of the interest index`() {
    val world = testWorld()

    subscriptions.markSent(1L, ChunkPos(0, 0, 0))
    residency.drain(world, budget = 8)
    assertNotEquals(0, aoi.getTotalEntityCount())

    subscriptions.unsend(1L, ChunkPos(0, 0, 0))
    residency.drain(world, budget = 8)

    assertEquals(0, aoi.getTotalEntityCount(), "a released prop is still in the octree and can still be hit")
  }

  /** A column with nothing on it must not be re-asked every tick it stays held. */
  @Test
  fun `an empty column is remembered as empty`() {
    val empty = object : WorldObjectSource {
      var asked = 0
      override val kinds = setOf(StaticEntityKind.TREE)
      override fun sitesIn(chunk: ChunkPos): List<WorldObjectSite> {
        asked++
        return emptyList()
      }
    }
    val service = WorldObjectResidencyService(listOf(empty), kindRegistry(), EntityAOIService(), subscriptions)
    val world = testWorld()

    subscriptions.markSent(1L, ChunkPos(3, 3, 0))
    service.drain(world, budget = 8)
    subscriptions.markSent(2L, ChunkPos(3, 3, 0))
    service.drain(world, budget = 8)

    assertEquals(1, empty.asked, "an empty column was rebuilt on a second subscriber")
    assertEquals(1, service.residentColumns)
    assertEquals(0, service.residentEntities)
    assertFalse(service.entitiesIn(3, 3).isNotEmpty())
  }

  private fun kindRegistry() = PropKindRegistry().also { it.load() }
}
