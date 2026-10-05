package net.bestia.zone.geometry

import net.bestia.bnet.proto.DeltaPathProto
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeltaPathsTest {

  @Test
  fun `a path survives the round trip, uphill and down`() {
    val path = listOf(Vec3L(100, -4, 12), Vec3L(101, -5, 13), Vec3L(101, -6, 11), Vec3L(100, -6, 11))

    assertEquals(path, DeltaPaths.decode(DeltaPaths.encode(path)))
  }

  @Test
  fun `each step after the first costs three numbers`() {
    val encoded = DeltaPaths.encode(listOf(Vec3L(0, 0, 0), Vec3L(1, 1, 0), Vec3L(2, 2, 1)))

    assertEquals(6, encoded.deltasCount)
  }

  @Test
  fun `an empty path is sent as no path`() {
    val encoded = DeltaPaths.encode(emptyList())

    assertTrue(!encoded.hasFirst())
    assertEquals(emptyList(), DeltaPaths.decode(encoded))
  }

  @Test
  fun `an incomplete trailing step is dropped`() {
    val encoded = DeltaPaths.encode(listOf(Vec3L(5, 5, 5))).toBuilder().addDeltas(1).addDeltas(0).build()

    assertEquals(listOf(Vec3L(5, 5, 5)), DeltaPaths.decode(encoded))
  }

  @Test
  fun `a 24-step leg is a small fraction of absolute waypoints`() {
    val leg = (1..24).map { Vec3L(64_000L + it, 27_000L + it, 515) }
    val absolute = leg.sumOf {
      DeltaPathProto.DeltaPath.newBuilder().setFirst(DeltaPaths.encode(listOf(it)).first).build().serializedSize
    }

    assertTrue(DeltaPaths.encode(leg).serializedSize * 3 < absolute)
  }
}
