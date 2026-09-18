package net.bestia.zone.world.spoor

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the world remembers about things that were walking.
 *
 * The design property is that this **outlives the entity**: following something that has walked out of sight
 * is the point, and by then it may have despawned, died or logged out.
 */
class ActorSignaturesTest {

  private val config = SpoorConfig(maxSignatures = 2, refreshSeconds = 100, signatureTtlSeconds = 1_000)

  private val sut = ActorSignatures(config)

  private fun wolf(level: Int = 5) = ActorSignature(
    kind = ActorKind.BESTIA,
    speciesId = 3,
    level = level,
    identifier = "blob",
    masterName = null,
  )

  @Test
  fun `an actor nobody has described is unknown rather than an error`() {
    assertNull(sut.of(99))
  }

  @Test
  fun `a described actor can be asked about`() {
    sut.remember(1, wolf(), nowSecond = 0)

    assertEquals(5, assertNotNull(sut.of(1)).level)
  }

  /** The cheap test that keeps the per-tick pass to one lookup per walking creature. */
  @Test
  fun `a fresh description is not rebuilt and a stale one is`() {
    assertTrue(sut.needsRefresh(1, nowSecond = 0), "an unknown actor must be described")

    sut.remember(1, wolf(), nowSecond = 0)

    assertFalse(sut.needsRefresh(1, nowSecond = 50))
    assertTrue(sut.needsRefresh(1, nowSecond = config.refreshSeconds))
  }

  /**
   * **The reason this exists at all.** An entity that died an hour ago is exactly the one a tracker is asking
   * about, so nothing here is keyed on the entity still being alive.
   */
  @Test
  fun `a description outlives whatever it described`() {
    sut.remember(1, wolf(), nowSecond = 0)

    assertNotNull(sut.of(1), "an actor is forgotten only by the clock, never by dying")
  }

  @Test
  fun `descriptions nobody has refreshed are forgotten`() {
    sut.remember(1, wolf(), nowSecond = 0)
    sut.remember(2, wolf(), nowSecond = 500)

    assertEquals(1, sut.sweep(nowSecond = config.signatureTtlSeconds))

    assertNull(sut.of(1))
    assertNotNull(sut.of(2))
  }

  @Test
  fun `the index is bounded`() {
    sut.remember(1, wolf(), nowSecond = 0)
    sut.remember(2, wolf(), nowSecond = 0)
    sut.remember(3, wolf(), nowSecond = 0)

    assertEquals(config.maxSignatures, sut.size)
    assertNull(sut.of(3))
  }

  /** At the cap, a walker already described must still be kept up to date. */
  @Test
  fun `a full index still refreshes what it already holds`() {
    sut.remember(1, wolf(level = 1), nowSecond = 0)
    sut.remember(2, wolf(), nowSecond = 0)

    sut.remember(1, wolf(level = 9), nowSecond = 200)

    assertEquals(9, assertNotNull(sut.of(1)).level)
  }

  @Test
  fun `a species names itself with a key the client already has a row for`() {
    assertEquals("BESTIA_BLOB", wolf().nameToken)
  }

  @Test
  fun `a master has no species key, because a name is not a key`() {
    val master = ActorSignature(
      kind = ActorKind.MASTER,
      speciesId = 12,
      level = 40,
      identifier = "",
      masterName = "Rhea",
    )

    assertNull(master.nameToken)
  }
}
