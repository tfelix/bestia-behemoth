package net.bestia.zone.world.stream

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.ecs.visibility.EntityVisibility
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChunkEntityVisibilityTest {

  /** 32 voxels across and 256 tall, the defaults, so the boundaries below are the real ones. */
  private val config = WorldConfig(seed = 1L, widthCells = 128, heightCells = 128)

  private val chunkService = mockk<ChunkService> {
    every { config } returns this@ChunkEntityVisibilityTest.config
    // Identity: wrapping is ChunkService's business and is asserted where it lives.
    every { normalise(any()) } answers { firstArg() }
  }

  private val observer = 42L

  private val subscriptions = ChunkSubscriptionService()
  private val visibility = ChunkEntityVisibility(chunkService, subscriptions)

  /** Puts [accountId] in possession of [chunk]'s terrain, the way a served manifest would. */
  private fun hold(accountId: Long, chunk: ChunkPos) = subscriptions.markSent(accountId, chunk)

  @Test
  fun `a first sighting reads as a move out of nowhere`() {
    val move = visibility.reindex(1L, Vec3L(5, 5, 0))

    assertEquals(ChunkEntityVisibility.Transition(from = null, to = ChunkPos(0, 0, 0)), move)
    assertEquals(listOf(1L), visibility.residentsOf(ChunkPos(0, 0, 0)))
  }

  @Test
  fun `moving inside one chunk is not a transition`() {
    visibility.reindex(1L, Vec3L(5, 5, 0))

    assertNull(visibility.reindex(1L, Vec3L(6, 7, 0)), "still the same chunk, so nobody's view changed")
  }

  @Test
  fun `crossing a horizontal edge moves the entity between residency lists`() {
    visibility.reindex(1L, Vec3L(31, 5, 0))

    val move = visibility.reindex(1L, Vec3L(32, 5, 0))

    assertEquals(ChunkEntityVisibility.Transition(ChunkPos(0, 0, 0), ChunkPos(1, 0, 0)), move)
    assertEquals(emptyList(), visibility.residentsOf(ChunkPos(0, 0, 0)), "and the old chunk is emptied")
    assertEquals(listOf(1L), visibility.residentsOf(ChunkPos(1, 0, 0)))
  }

  @Test
  fun `climbing past a slab boundary is a transition too`() {
    // The reason visibility is keyed on the chunk rather than the column: a player 256 m below holds a
    // different slab, and must not be fed what is standing on the surface above them.
    visibility.reindex(1L, Vec3L(5, 5, 255))

    val move = visibility.reindex(1L, Vec3L(5, 5, 256))

    assertEquals(ChunkEntityVisibility.Transition(ChunkPos(0, 0, 0), ChunkPos(0, 0, 1)), move)
  }

  @Test
  fun `a negative coordinate floors rather than truncates`() {
    // Truncation would put x = -1 and x = 0 in the same chunk, so an entity stepping over the origin would
    // never be seen to change chunk.
    assertEquals(ChunkPos(-1, -1, -1), visibility.chunkAt(Vec3L(-1, -1, -1)))
  }

  @Test
  fun `forgetting an entity empties the chunk it was in`() {
    visibility.reindex(1L, Vec3L(5, 5, 0))
    visibility.reindex(2L, Vec3L(6, 6, 0))

    visibility.forgot(1L)

    assertEquals(listOf(2L), visibility.residentsOf(ChunkPos(0, 0, 0)))
    assertNull(visibility.chunkHolding(1L))
  }

  @Test
  fun `forgetting an entity that was never indexed is a no-op`() {
    visibility.forgot(99L)

    assertNull(visibility.chunkHolding(99L))
  }

  @Test
  fun `a chunk arriving hands its residents to the account that got it`() {
    visibility.moved(1L, Vec3L(5, 5, 0))
    visibility.moved(2L, Vec3L(6, 6, 0))
    visibility.drain()

    hold(observer, ChunkPos(0, 0, 0))

    assertEquals(
      listOf(EntityVisibility.Delivery(observer, appeared = listOf(1L, 2L), vanished = emptyList())),
      visibility.drain()
    )
  }

  @Test
  fun `walking into a held chunk appears to whoever holds it`() {
    hold(observer, ChunkPos(1, 0, 0))
    visibility.moved(1L, Vec3L(5, 5, 0))
    visibility.drain()

    visibility.moved(1L, Vec3L(32, 5, 0))

    assertEquals(
      listOf(EntityVisibility.Delivery(observer, appeared = listOf(1L), vanished = emptyList())),
      visibility.drain()
    )
  }

  @Test
  fun `walking out of a held chunk vanishes for whoever holds it`() {
    hold(observer, ChunkPos(0, 0, 0))
    visibility.moved(1L, Vec3L(5, 5, 0))
    visibility.drain()

    visibility.moved(1L, Vec3L(32, 5, 0))

    assertEquals(
      listOf(EntityVisibility.Delivery(observer, appeared = emptyList(), vanished = listOf(1L))),
      visibility.drain()
    )
  }

  @Test
  fun `crossing between two chunks the same account holds is nobody's news`() {
    hold(observer, ChunkPos(0, 0, 0))
    hold(observer, ChunkPos(1, 0, 0))
    visibility.moved(1L, Vec3L(5, 5, 0))
    visibility.drain()

    visibility.moved(1L, Vec3L(32, 5, 0))

    assertEquals(emptyList(), visibility.drain(), "the view is eleven chunks across, so this is most crossings")
  }

  @Test
  fun `a destroyed entity is withdrawn from what was queued about it`() {
    hold(observer, ChunkPos(0, 0, 0))
    visibility.moved(1L, Vec3L(5, 5, 0))

    visibility.forgot(1L)

    assertEquals(emptyList(), visibility.drain(), "a destroy sends its own vanish; this must not build a snapshot")
  }

  @Test
  fun `a reannounce hands back everything the account already holds`() {
    // The login race this exists for: the master and whatever stands beside it were announced while the
    // client was still building its game scene, and nothing ever says so again.
    hold(observer, ChunkPos(0, 0, 0))
    hold(observer, ChunkPos(1, 0, 0))
    visibility.moved(1L, Vec3L(5, 5, 0))
    visibility.moved(2L, Vec3L(35, 5, 0))
    visibility.drain()

    visibility.reannounce(observer)

    val delivery = visibility.drain().single()
    assertEquals(observer, delivery.accountId)
    // A set: the two stand in different chunks, and which of those the held-chunk scan reaches first is
    // nobody's business.
    assertEquals(setOf(1L, 2L), delivery.appeared.toSet())
    assertEquals(emptyList(), delivery.vanished)
  }

  @Test
  fun `a reannounce says nothing about a chunk the account does not hold`() {
    hold(observer, ChunkPos(0, 0, 0))
    visibility.moved(1L, Vec3L(5, 5, 0))
    visibility.moved(2L, Vec3L(35, 5, 0))
    visibility.drain()

    visibility.reannounce(observer)

    assertEquals(
      listOf(EntityVisibility.Delivery(observer, appeared = listOf(1L), vanished = emptyList())),
      visibility.drain(),
      "entity 2 stands in a chunk this account was never sent"
    )
  }

  @Test
  fun `a reannounce is served once`() {
    hold(observer, ChunkPos(0, 0, 0))
    visibility.moved(1L, Vec3L(5, 5, 0))
    visibility.drain()

    visibility.reannounce(observer)
    visibility.drain()

    assertEquals(emptyList(), visibility.drain())
  }

  @Test
  fun `a reannounce by an account holding nothing is not a delivery`() {
    visibility.moved(1L, Vec3L(5, 5, 0))

    visibility.reannounce(observer)

    assertEquals(emptyList(), visibility.drain())
  }

  @Test
  fun `drain clears what it handed out`() {
    hold(observer, ChunkPos(0, 0, 0))
    visibility.moved(1L, Vec3L(5, 5, 0))

    assertEquals(1, visibility.drain().size)
    assertEquals(emptyList(), visibility.drain())
  }
}
