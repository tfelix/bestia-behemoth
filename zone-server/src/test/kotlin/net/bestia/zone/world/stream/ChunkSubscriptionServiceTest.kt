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
    assertEquals(null, subscriptions.anchorOf(1L))
  }
}
