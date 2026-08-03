package net.bestia.worldgen.mana

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The mana field, measured rather than eyeballed.
 *
 * Both properties here were wrong in the first implementation and neither would have failed a test that only
 * checked the layer existed and was in range: the field's whole dynamic span was 0.25 to 0.85, so every
 * downstream threshold meant something other than what it said, and the provinces came out thirty-odd patches
 * rather than around ten because the wavelength was being divided by the detail scale.
 */
class ManaStageTest {

  private fun world(seed: Long, cells: Int = 128) =
    StandardWorld.build(WorldConfig(seed = seed, widthCells = cells, heightCells = cells))

  private fun mana(world: GeneratedWorld) = world.world.layers.require<FloatLayer>(LayerId.MANA_DENSITY)

  @Test
  fun `the field uses the range it claims`() {
    // A stretched field, not a raw fbm: the point is that "mana above 0.75" is a place a world actually has.
    // Before the smoothstep the 95th percentile was 0.69 and nothing in the world ever reached 0.8.
    for (seed in listOf(1L, 7L, 42L)) {
      val values = mana(world(seed)).data.map { it.toDouble() }.sorted()
      fun q(p: Double) = values[(p * (values.size - 1)).toInt()]

      assertTrue(q(0.05) < 0.20, "seed $seed: quiet ground should be genuinely quiet, p05 was ${q(0.05)}")
      assertTrue(q(0.95) > 0.85, "seed $seed: a world needs somewhere strong, p95 was ${q(0.95)}")
      assertTrue(q(0.50) in 0.30..0.70, "seed $seed: the field should not be skewed, p50 was ${q(0.50)}")
    }
  }

  @Test
  fun `a world has a handful of mana provinces, not thirty and not one`() {
    // Connected components of "above 0.75", counted with a flood fill and filtered to those big enough to be
    // a place rather than a pixel. This is the count the wavelength is tuned against, and it is the number
    // that says whether a corrupted region will be an endgame zone or a scattering of blotches.
    for (seed in listOf(1L, 7L, 42L)) {
      val provinces = provinceCount(mana(world(seed)), threshold = 0.75, minCells = 12)

      assertTrue(
        provinces in 4..20,
        "seed $seed had $provinces mana provinces on a 128 km world; expected roughly ten"
      )
    }
  }

  @Test
  fun `provinces are not a picture of the fault network`() {
    // The first version weighted the faults at 0.30 against a raw fbm and the exported map was ribbons along
    // every plate boundary. Rather than assert a correlation coefficient, assert the thing that was visibly
    // wrong: the strong ground must not all be within one fault range of a boundary.
    val world = world(7L)
    val values = mana(world)
    val config = world.config

    var strong = 0
    var strongNearFault = 0
    var all = 0
    var allNearFault = 0
    val near = world.params.mana.faultRange * 0.5

    for (y in 0 until config.heightCells) {
      for (x in 0 until config.widthCells) {
        val here = net.bestia.worldgen.vector.Vec2d((x + 0.5) * 1000.0, (y + 0.5) * 1000.0)
        val isNear = nearestFaultDistance(world, here) < near
        all++
        if (isNear) allNearFault++
        if (values[x, y] < 0.75f) continue
        strong++
        if (isNear) strongNearFault++
      }
    }

    assertTrue(strong > 0, "no strong mana anywhere")

    // Against a control, because the bare share means nothing: this world's plate boundaries are dense enough
    // that most of it is near one whatever the mana does, and the first version of this test asserted 97.9%
    // was too high when the background rate was almost as high. What is being asserted is *lift* - how much
    // more likely strong mana is to sit on a boundary than a cell picked at random.
    val strongShare = strongNearFault.toDouble() / strong
    val backgroundShare = allNearFault.toDouble() / all
    val lift = strongShare / backgroundShare

    assertTrue(
      lift < 1.35,
      "strong mana is ${"%.2f".format(lift)}x as likely as background to sit on a plate boundary " +
          "($strongShare against $backgroundShare); the field is a fault map"
    )
  }

  private fun nearestFaultDistance(world: GeneratedWorld, at: net.bestia.worldgen.vector.Vec2d): Double {
    var best = Double.MAX_VALUE
    for (feature in world.world.features.all()) {
      if (feature.kind != net.bestia.worldgen.vector.FeatureKind.FAULT) continue
      val marker = feature as? net.bestia.worldgen.vector.MarkerFeature ?: continue
      var s = 0.0
      while (s <= marker.centerline.length) {
        val point = marker.centerline.pointAt(s)
        val dx = point.x - at.x
        val dy = point.y - at.y
        val d = dx * dx + dy * dy
        if (d < best) best = d
        s += 2000.0
      }
    }
    return if (best == Double.MAX_VALUE) Double.MAX_VALUE else kotlin.math.sqrt(best)
  }

  /** Connected components above [threshold], keeping those of at least [minCells] cells. */
  private fun provinceCount(layer: FloatLayer, threshold: Double, minCells: Int): Int {
    val width = layer.region.width
    val height = layer.region.height
    val seen = BooleanArray(width * height)
    var provinces = 0

    for (start in 0 until width * height) {
      if (seen[start] || layer.data[start] < threshold) continue

      var size = 0
      val stack = ArrayDeque<Int>()
      stack.addLast(start)
      seen[start] = true

      while (stack.isNotEmpty()) {
        val index = stack.removeLast()
        size++
        val x = index % width
        val y = index / width
        for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
          val nx = x + dx
          val ny = y + dy
          if (nx !in 0 until width || ny !in 0 until height) continue
          val next = ny * width + nx
          if (seen[next] || layer.data[next] < threshold) continue
          seen[next] = true
          stack.addLast(next)
        }
      }

      if (size >= minCells) provinces++
    }

    return provinces
  }
}
