package net.bestia.worldgen.resource

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two terms that decide how much gold a world holds, and which of them is in charge at what size.
 *
 * Worth pinning because the currency is backed by this: a change to the abundance or to the guaranteed
 * deposit count re-sizes every world that would be generated from a player count.
 */
class GoldEndowmentTest {

  @Test
  fun `the guaranteed deposits are what a small world actually gets`() {
    // 0.49 t per thousand square kilometres asks for about eight tons of lode on a 128 km world, and
    // three deposits that may not be drawn empty hold twenty-four.
    assertEquals(24.0, GoldEndowment.guaranteedTons())
    assertTrue(
      MinableOre.GOLD.worldTons(areaOf(128.0)) < GoldEndowment.guaranteedTons(),
      "the abundance already beats the floor at 128 km, so no world is floor-dominated"
    )
    assertEquals(GoldEndowment.guaranteedTons(), GoldEndowment.lodeTonsIn(areaOf(128.0)))
  }

  @Test
  fun `the abundance takes over on a large world`() {
    assertTrue(
      GoldEndowment.lodeTonsIn(areaOf(512.0)) > GoldEndowment.guaranteedTons() * 4,
      "a 512 km world is still being carried by its floor, which would make it no richer than Genesis"
    )
  }

  @Test
  fun `the crossover sits between the two, and small worlds are richer per square kilometre`() {
    val genesisDensity = GoldEndowment.lodeTonsIn(areaOf(128.0)) / (128.0 * 128.0)
    val targetDensity = GoldEndowment.lodeTonsIn(areaOf(512.0)) / (512.0 * 512.0)

    assertTrue(genesisDensity > targetDensity, "the floor is supposed to over-provision a small world")
  }

  @Test
  fun `more world is never less gold`() {
    var previous = 0.0

    for (edgeKm in 16..2048 step 16) {
      val tons = GoldEndowment.tonsIn(areaOf(edgeKm.toDouble()))
      assertTrue(tons >= previous, "a ${edgeKm} km world holds less gold than a smaller one")
      previous = tons
    }
  }

  @Test
  fun `placer is counted on top of the lode rather than inside it`() {
    val area = areaOf(512.0)

    assertEquals(
      GoldEndowment.lodeTonsIn(area) * (1.0 + GoldEndowment.PLACER_SHARE_OF_LODE),
      GoldEndowment.tonsIn(area)
    )
  }

  @Test
  fun `a ton of metal is the voxels the materialiser would place for it`() {
    val meanYieldKg = GradeMix().meanYieldKg
    val voxels = GoldEndowment.voxelsForTons(tons = 1.0, meanYieldKg = meanYieldKg)

    assertEquals(1_000.0 / meanYieldKg, voxels, 1e-9)
    assertTrue(voxels > 850.0 && voxels < 860.0, "a ton came to $voxels voxels, not the expected ~857")
  }

  private fun areaOf(edgeKm: Double): Double {
    return (edgeKm * 1_000.0) * (edgeKm * 1_000.0)
  }
}
