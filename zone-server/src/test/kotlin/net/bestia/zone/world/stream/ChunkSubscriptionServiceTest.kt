package net.bestia.zone.world.stream

import net.bestia.worldgen.core.ChunkPos
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChunkSubscriptionServiceTest {

  private val subscriptions = ChunkSubscriptionService()

  private val a = ChunkPos(0, 0, 0)
  private val b = ChunkPos(1, 0, 0)

  @Test
  fun `announcing authorises a request but does not make the client a patch recipient`() {
    subscriptions.applyManifest(1L, added = listOf(a), removed = emptyList(), reset = true)

    assertTrue(subscriptions.isAnnouncedTo(1L, a), "an offered chunk may be requested")
    assertTrue(
      subscriptions.subscribersOf(a).isEmpty(),
      "a client that has not been sent the payload cannot apply a patch to it"
    )

    subscriptions.markSent(1L, a)

    assertEquals(setOf(1L), subscriptions.subscribersOf(a))
  }

  @Test
  fun `withdrawing a chunk also stops its patches`() {
    subscriptions.applyManifest(1L, listOf(a, b), emptyList(), reset = true)
    subscriptions.markSent(1L, a)
    subscriptions.markSent(1L, b)

    subscriptions.applyManifest(1L, added = emptyList(), removed = listOf(a), reset = false)

    assertFalse(subscriptions.isAnnouncedTo(1L, a))
    assertTrue(
      subscriptions.subscribersOf(a).isEmpty(),
      "a client told to forget a chunk must not go on receiving edits for it"
    )
    assertEquals(setOf(1L), subscriptions.subscribersOf(b), "the other chunk is untouched")
  }

  @Test
  fun `a reset clears what was held rather than adding to it`() {
    subscriptions.applyManifest(1L, listOf(a), emptyList(), reset = true)
    subscriptions.markSent(1L, a)

    subscriptions.applyManifest(1L, listOf(b), emptyList(), reset = true)

    assertFalse(subscriptions.isAnnouncedTo(1L, a), "a reset supersedes everything, not just what it lists")
    assertTrue(
      subscriptions.subscribersOf(a).isEmpty(),
      "the reverse index must not keep an account against a chunk that left its set - that leak would show " +
          "up as patches for terrain the client discarded"
    )
    assertTrue(subscriptions.isAnnouncedTo(1L, b))
  }

  @Test
  fun `many accounts share one chunk and are tracked independently`() {
    val accounts = (1L..30L).toList()

    accounts.forEach {
      subscriptions.applyManifest(it, listOf(a), emptyList(), reset = true)
      subscriptions.markSent(it, a)
    }

    assertEquals(accounts.toSet(), subscriptions.subscribersOf(a))

    subscriptions.forget(7L)

    assertEquals(29, subscriptions.subscribersOf(a).size)
    assertFalse(7L in subscriptions.subscribersOf(a))
  }

  @Test
  fun `forgetting a connection leaves nothing behind`() {
    subscriptions.applyManifest(1L, listOf(a, b), emptyList(), reset = true)
    subscriptions.markSent(1L, a)
    subscriptions.setAnchor(1L, a)

    subscriptions.forget(1L)

    assertEquals(0, subscriptions.trackedAccounts)
    assertTrue(subscriptions.announcedTo(1L).isEmpty())
    assertTrue(subscriptions.sentTo(1L).isEmpty())
    assertTrue(subscriptions.subscribersOf(a).isEmpty())
    assertTrue(subscriptions.subscribersOfColumn(a.x, a.y).isEmpty())
    assertEquals(null, subscriptions.anchorOf(1L))
  }

  /**
   * The column index refcounts, and that is the whole reason it is a count rather than a set: a player
   * standing in a cave holds several slabs of one column at once, and dropping them one at a time must not
   * make them stop being a holder until the last one goes.
   */
  @Test
  fun `a column holder survives losing all but one of its slabs`() {
    val surface = ChunkPos(4, 5, 0)
    val middle = ChunkPos(4, 5, 1)
    val deep = ChunkPos(4, 5, 2)

    subscriptions.markSent(1L, surface)
    subscriptions.markSent(1L, middle)
    subscriptions.markSent(1L, deep)
    subscriptions.markSent(2L, deep)

    assertEquals(setOf(1L, 2L), subscriptions.subscribersOfColumn(4, 5))

    subscriptions.unsend(1L, deep)
    subscriptions.unsend(1L, middle)
    assertEquals(setOf(1L, 2L), subscriptions.subscribersOfColumn(4, 5), "one slab held is still holding")

    subscriptions.unsend(1L, surface)
    assertEquals(setOf(2L), subscriptions.subscribersOfColumn(4, 5))

    subscriptions.unsend(2L, deep)
    assertTrue(subscriptions.subscribersOfColumn(4, 5).isEmpty())
  }

  /**
   * `unsend` is reached for chunks that were only ever *announced* - by `forget`, and by a `reset` manifest
   * after a teleport. Decrementing a count that was never incremented would drop a live holder.
   */
  @Test
  fun `unsending a chunk that was never sent does not disturb the column index`() {
    val surface = ChunkPos(4, 5, 0)
    val neverSent = ChunkPos(4, 5, 1)

    subscriptions.markSent(1L, surface)
    subscriptions.unsend(1L, neverSent)

    assertEquals(setOf(1L), subscriptions.subscribersOfColumn(4, 5))
  }

  @Test
  fun `column subscribers do not leak across columns`() {
    subscriptions.markSent(1L, ChunkPos(4, 5, 0))
    subscriptions.markSent(2L, ChunkPos(4, 6, 0))

    assertEquals(setOf(1L), subscriptions.subscribersOfColumn(4, 5))
    assertEquals(setOf(2L), subscriptions.subscribersOfColumn(4, 6))
    assertTrue(subscriptions.subscribersOfColumn(9, 9).isEmpty())
  }
}
