package net.bestia.worldgen.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GenRngTest {

  private val stage = StageId("tectonics")

  @Test
  fun `the same key always yields the same stream`() {
    val a = GenRng.derive(42L, stage, 1, 17L, 99L)
    val b = GenRng.derive(42L, stage, 1, 17L, 99L)

    repeat(1000) {
      assertEquals(a.nextLong(), b.nextLong())
    }
  }

  @Test
  fun `changing any component of the key changes the stream`() {
    val base = GenRng.derive(42L, stage, 1, 17L, 99L).nextLong()

    assertNotEquals(base, GenRng.derive(43L, stage, 1, 17L, 99L).nextLong())
    assertNotEquals(base, GenRng.derive(42L, StageId("climate"), 1, 17L, 99L).nextLong())
    assertNotEquals(base, GenRng.derive(42L, stage, 2, 17L, 99L).nextLong())
    assertNotEquals(base, GenRng.derive(42L, stage, 1, 18L, 99L).nextLong())
    assertNotEquals(base, GenRng.derive(42L, stage, 1, 17L, 100L).nextLong())
  }

  @Test
  fun `a version bump reseeds the stage completely`() {
    // Not merely a different first value - the whole stream has to move, or bumping a version to
    // invalidate a cache would leave most of the output identical and hide the change.
    val old = GenRng.derive(1L, stage, 1, 0L)
    val new = GenRng.derive(1L, stage, 2, 0L)

    val shared = (0 until 100).count { old.nextLong() == new.nextLong() }
    assertTrue(shared <= 1, "$shared of 100 draws survived a version bump")
  }

  @Test
  fun `neighbouring coordinates give uncorrelated streams`() {
    // Chunk streams are derived from chunk coordinates, so adjacent chunks must not produce visibly
    // related noise.
    val values = (0 until 256).map { GenRng.derive(7L, stage, 1, it.toLong(), 0L).nextDouble() }

    assertEquals(256, values.toSet().size)
    assertTrue(values.all { it in 0.0..1.0 })
    assertTrue(abs(values.average() - 0.5) < 0.1, "mean was ${values.average()}")
  }

  @Test
  fun `nextInt stays inside its bound and covers it`() {
    val rng = GenRng(12345L)
    val counts = IntArray(6)

    repeat(60_000) {
      val roll = rng.nextInt(6)
      assertTrue(roll in 0..5, "rolled $roll")
      counts[roll]++
    }

    // A die that never shows a face, or shows one far too often, is broken.
    assertTrue(counts.all { it > 8_000 }, "distribution was ${counts.toList()}")
  }

  @Test
  fun `nextDouble is uniform enough for content generation`() {
    val rng = GenRng(999L)
    val buckets = IntArray(10)

    repeat(100_000) {
      buckets[(rng.nextDouble() * 10).toInt().coerceAtMost(9)]++
    }

    assertTrue(buckets.all { it > 8_500 }, "distribution was ${buckets.toList()}")
  }

  @Test
  fun `hash is order dependent so structured keys do not collide`() {
    assertNotEquals(GenRng.hash(1L, 2L), GenRng.hash(2L, 1L))
    assertNotEquals(GenRng.hash(1L, 2L), GenRng.hash(1L, 2L, 0L))
    assertEquals(GenRng.hash(1L, 2L, 3L), GenRng.hash(1L, 2L, 3L))
  }

  @Test
  fun `hashUnit lands in the unit interval`() {
    for (i in 0 until 10_000) {
      val v = GenRng.hashUnit(i.toLong())
      assertTrue(v >= 0.0 && v < 1.0, "hashUnit($i) was $v")
    }
  }

  @Test
  fun `forked streams differ from their parent and from each other`() {
    val a = GenRng(5L).fork(1L).nextLong()
    val b = GenRng(5L).fork(2L).nextLong()

    assertNotEquals(a, b)
    assertNotEquals(a, GenRng(5L).nextLong())
  }
}
