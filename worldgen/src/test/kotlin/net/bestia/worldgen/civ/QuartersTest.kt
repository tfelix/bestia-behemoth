package net.bestia.worldgen.civ

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What each patch of a settlement's core is for.
 *
 * The case worth pinning is the smallest one. `TownPatches.countFor` floors at `MIN_PATCHES`, so every
 * settlement below about three hundred buildings is laid out on exactly six patches - and every kind here
 * except housing is either unconditional or floored at one. Six patches and four roads arriving is therefore
 * a settlement that can spend every patch it has on its own market, entrances and workshops.
 */
class QuartersTest {

  private val centre = Vec2d(5_000.0, 5_000.0)

  private fun roll(seed: Long): (Long, Long) -> Double {
    return townRoll(GenRng.hashString("quarters-$seed"), 0)
  }

  /**
   * [count] square patches on a ring about the centre, none bordering another.
   *
   * Every edge unneighboured, so every patch is `onOutline` - which is the arrangement that makes the most
   * of them eligible to be gates, and so the one that starves housing hardest.
   */
  private fun ringOf(count: Int, radius: Double = 120.0, half: Double = 26.0): List<TownPatch> {
    return (0 until count).map { i ->
      val angle = i * 2.0 * PI / count
      val site = centre + Vec2d(cos(angle), sin(angle)) * radius
      val polygon = listOf(
        site + Vec2d(-half, -half), site + Vec2d(half, -half),
        site + Vec2d(half, half), site + Vec2d(-half, half)
      )
      TownPatch(polygon, site, IntArray(polygon.size) { -1 })
    }
  }

  private fun frameWith(approaches: List<Vec2d>, seed: Long = 1L): TownFrame {
    val radius = 300.0
    return TownFrame(
      centre = centre,
      builtRadius = radius,
      boundary = TownBoundary.of(
        centre = centre,
        builtRadius = radius,
        axis = Vec2d(1.0, 0.0),
        aspect = TownBoundary.aspectOf(roll(seed), StreetParams()),
        seed = seed,
        params = StreetParams()
      ),
      groundAt = { 100.0 },
      buildable = { true },
      approaches = approaches
    )
  }

  private fun compass(count: Int): List<Vec2d> {
    return (0 until count).map { i ->
      val angle = i * 2.0 * PI / count
      Vec2d(cos(angle), sin(angle))
    }
  }

  @Test
  fun `the smallest settlement still has somewhere to live`() {
    for (seed in 1L..20L) {
      val patches = ringOf(6)
      val quarters = Quarters.assign(
        patches = patches,
        frame = frameWith(compass(4), seed),
        tier = SettlementTier.TOWN,
        walled = true,
        downwind = Vec2d(1.0, 0.0),
        downstream = Vec2d(0.0, 1.0),
        roll = roll(seed)
      )

      assertTrue(
        quarters.any { it == DistrictKind.RESIDENTIAL },
        "seed $seed spent all six patches on $quarters"
      )
    }
  }

  @Test
  fun `a settlement does not spend itself on its own entrances`() {
    val patches = ringOf(6)
    val quarters = Quarters.assign(
      patches = patches,
      frame = frameWith(compass(4)),
      tier = SettlementTier.TOWN,
      walled = false,
      downwind = Vec2d(1.0, 0.0),
      downstream = Vec2d(0.0, 1.0),
      roll = roll(3L)
    )

    // Four roads arrive, but six patches cannot carry four gates and still be a settlement.
    assertTrue(
      quarters.count { it == DistrictKind.GATE } <= 2,
      "six patches took ${quarters.count { it == DistrictKind.GATE }} gates: $quarters"
    )
  }

  @Test
  fun `a settlement large enough keeps the quarters it always had`() {
    // The reserve is a floor, not a cap: a town with room for every kind must still get them.
    val quarters = Quarters.assign(
      patches = ringOf(24, radius = 400.0),
      frame = frameWith(compass(4)),
      tier = SettlementTier.TOWN,
      walled = true,
      downwind = Vec2d(1.0, 0.0),
      downstream = Vec2d(0.0, 1.0),
      roll = roll(7L)
    )

    for (kind in listOf(DistrictKind.MARKET, DistrictKind.GATE, DistrictKind.CRAFT, DistrictKind.SLUM)) {
      assertTrue(quarters.contains(kind), "a settlement of 24 patches has no $kind: $quarters")
    }
    assertTrue(quarters.count { it == DistrictKind.RESIDENTIAL } >= 8, "too little housing: $quarters")
  }
}
