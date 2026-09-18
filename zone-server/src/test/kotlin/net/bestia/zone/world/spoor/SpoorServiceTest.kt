package net.bestia.zone.world.spoor

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.ground.GroundStampConfig
import net.bestia.zone.world.ground.GroundStampKind
import net.bestia.zone.world.ground.GroundStampRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading the ground.
 *
 * The interesting part is the reduction: a crossroads holds prints from several things, and the reading has to
 * come back as one account of it rather than as a list. Which one it picks is the whole behaviour.
 */
class SpoorServiceTest {

  private val chunkSize = 32
  private val spoorConfig = SpoorConfig()

  private var clockSecond = 1_000L

  private val clock = mockk<BestiaClock> {
    every { now() } answers { mockk { every { absoluteSecond } returns clockSecond } }
  }

  private val worldService = mockk<WorldService> {
    every { config } returns mockk { every { this@mockk.chunkSize } returns this@SpoorServiceTest.chunkSize }
  }

  private val stamps = GroundStampRegistry(GroundStampConfig(), worldService, clock)
  private val signatures = ActorSignatures(spoorConfig)

  private val sut = SpoorService(stamps, signatures, clock)

  private fun walk(actorId: Long, x: Long, y: Long, octant: Int = 0, atSecond: Long = 1_000) {
    stamps.stamp(x, y, GroundStampKind.FOOTPRINT, octant, seed = 0, actorId = actorId, nowSecond = atSecond)
  }

  private fun blob(id: Long = 7) = ActorSignature(
    kind = ActorKind.BESTIA,
    speciesId = id,
    level = 3,
    identifier = "blob",
    masterName = null,
  )

  @Test
  fun `ground nothing has crossed reads as nothing`() {
    assertNull(sut.read(Vec3L(0, 0, 0), radiusTiles = 10))
  }

  @Test
  fun `a search of nothing at all finds nothing`() {
    walk(actorId = 1, x = 0, y = 0)

    assertNull(sut.read(Vec3L(0, 0, 0), radiusTiles = 0))
  }

  @Test
  fun `prints inside the reach are counted and prints outside it are not`() {
    for (x in 0L..4L) walk(actorId = 1, x = x, y = 0)

    assertEquals(3, assertNotNull(sut.read(Vec3L(0, 0, 0), radiusTiles = 2)).passages)
  }

  /** A disc rather than the square of columns it is cut from, so reach is the same in every direction. */
  @Test
  fun `the reach is a circle, not the box it was read from`() {
    walk(actorId = 1, x = 3, y = 3)

    assertNull(sut.read(Vec3L(0, 0, 0), radiusTiles = 4), "a corner of the bounding box was counted as in reach")
  }

  @Test
  fun `a trail crossing a chunk boundary is read as one trail`() {
    for (x in 30L..33L) walk(actorId = 1, x = x, y = 0)

    val reading = assertNotNull(sut.read(Vec3L(31, 0, 0), radiusTiles = 4))

    assertEquals(4, reading.passages)
    assertEquals(1, reading.walkers)
  }

  @Test
  fun `negative ground is read the same as positive ground`() {
    for (x in -4L..-1L) walk(actorId = 1, x = x, y = -40)

    assertEquals(4, assertNotNull(sut.read(Vec3L(-2, -40, 0), radiusTiles = 5)).passages)
  }

  /**
   * **What makes following a trail work.** The thing that lives here and the thing that walked through are
   * told apart by how much of the ground each covers, not by which print happens to be nearest.
   */
  @Test
  fun `the heaviest traffic is what gets reported`() {
    for (x in 0L..5L) walk(actorId = 1, x = x, y = 0)
    walk(actorId = 2, x = 0, y = 1)

    val reading = assertNotNull(sut.read(Vec3L(0, 0, 0), radiusTiles = 8))

    assertEquals(6, reading.passages)
    assertEquals(2, reading.walkers)
  }

  @Test
  fun `between two equally used trails the fresher one is reported`() {
    signatures.remember(1, blob(id = 1), nowSecond = 900)
    signatures.remember(2, blob(id = 2), nowSecond = 900)

    walk(actorId = 1, x = 0, y = 0, atSecond = 100)
    walk(actorId = 2, x = 1, y = 0, atSecond = 900)

    assertEquals(2L, assertNotNull(sut.read(Vec3L(0, 0, 0), radiusTiles = 4)).signature?.speciesId)
  }

  /** Steadier than any single print: a straight walk votes eight times for one direction. */
  @Test
  fun `the heading is the one most of the prints agree on`() {
    for (x in 0L..5L) walk(actorId = 1, x = x, y = 0, octant = 2)
    walk(actorId = 1, x = 0, y = 1, octant = 5)

    assertEquals(2, assertNotNull(sut.read(Vec3L(0, 0, 0), radiusTiles = 8)).octant)
  }

  @Test
  fun `age is measured from the freshest print of that trail`() {
    walk(actorId = 1, x = 0, y = 0, atSecond = 100)
    walk(actorId = 1, x = 1, y = 0, atSecond = 800)

    clockSecond = 1_000

    assertEquals(200, assertNotNull(sut.read(Vec3L(0, 0, 0), radiusTiles = 4)).ageSeconds)
  }

  @Test
  fun `a trail whose walker has been forgotten is still a trail`() {
    walk(actorId = 42, x = 0, y = 0)

    val reading = assertNotNull(sut.read(Vec3L(0, 0, 0), radiusTiles = 4))

    assertNull(reading.signature, "nothing was ever recorded about actor 42")
    assertTrue(reading.passages > 0, "the prints are there whether or not anyone knows what left them")
  }

  @Test
  fun `a described walker is named in the reading`() {
    signatures.remember(42, blob(), nowSecond = 900)
    walk(actorId = 42, x = 0, y = 0)

    assertEquals("BESTIA_BLOB", assertNotNull(sut.read(Vec3L(0, 0, 0), radiusTiles = 4)).signature?.nameToken)
  }
}
