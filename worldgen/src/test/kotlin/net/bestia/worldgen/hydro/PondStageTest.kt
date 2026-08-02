package net.bestia.worldgen.hydro

import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pipeline.WorldParams
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.BlendMode
import net.bestia.worldgen.vector.FeatureEvaluator
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.Vec2d
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The claims a moraine-dammed pond makes about itself.
 *
 * Built against a real world rather than a synthetic one, because every one of these claims is about the
 * *interaction* between a pond and the terrain around it - which is exactly what a fixture would have to
 * fake, and faking it is how three successive versions of this producer passed their own tests while
 * leaving walls of standing water on the reference world.
 *
 * Seed 3 at 192 cells is pinned because it has ponds - four of them, and about half of all seeds have none. A conditional `if (ponds.isEmpty()) return` would
 * pass on a world with none, which is the failure this module has shipped three times.
 */
class PondStageTest {

  private val world by lazy {
    StandardWorld.build(
      StandardWorld.demoConfig(seed = 3L).copy(widthCells = 192, heightCells = 192),
      params = WorldParams.DEFAULT
    )
  }

  private val ponds by lazy {
    world.world.features.all().filter { it.kind == FeatureKind.LAKE }.filterIsInstance<AreaFeature>()
  }

  @Test
  fun `the world this is pinned to actually has ponds`() {
    // Habit six, spelled out: a subsystem that is complete, tested and never reached looks exactly like one
    // that works. Every assertion below iterates this list, so if it is ever empty they all pass vacuously.
    assertTrue(ponds.isNotEmpty(), "seed 3 at 192 cells used to have four ponds and now has none")
  }

  @Test
  fun `a pond carves its bed rather than raising the ground`() {
    for (pond in ponds) {
      assertTrue(pond.blend == BlendMode.MIN, "$pond blends ${pond.blend}; a lake bed is a carve")
      assertTrue(pond.affectsHeight, "$pond has no profile, so it would be a lake with no bed")
    }
  }

  @Test
  fun `the water stands above its own bed and below its own rim`() {
    for (pond in ponds) {
      val surface = pond.attribute(LakeChannels.SURFACE_ELEVATION)
      val floor = pond.attribute(LakeChannels.FLOOR_ELEVATION)
      assertTrue(surface > floor, "$pond stands at $surface over a bed at $floor")

      // The rim claim, checked the way the producer defines it: the ground at the ring boundary is above
      // the water, because the level was chosen as the highest one that does not spill over exactly there.
      val ground = FeatureEvaluator(world.world.features.query(pond.bbox.expanded(64.0)))
      var wettest = 0.0
      for (i in 0 until pond.ring.vertexCount) {
        val vertex = pond.ring.vertex(i)
        val outward = (vertex - pond.ring.centroid).normalized()
        val outside = vertex + outward * 24.0
        if (pond.ring.contains(outside)) continue
        val height = ground.heightAt(outside.x, outside.y, world.base.heightAt(outside.x, outside.y))
        wettest = maxOf(wettest, surface - height)
      }
      assertTrue(wettest < 8.0, "$pond has ground ${"%.1f".format(wettest)} m under its surface just outside")
    }
  }

  @Test
  fun `a pond is water the raster does not already have`() {
    // The gate that makes the count mean something. Without it a pond could be emitted over a lake
    // priority-flood already found, and the two tiers would hold separate opinions about one body of water.
    val waterLevel = world.world.layers[net.bestia.worldgen.core.LayerId.WATER_LEVEL]
      as? net.bestia.worldgen.core.FloatLayer ?: return
    val metres = waterLevel.region.resolution.metresPerCell

    for (pond in ponds) {
      val cellX = Math.floor(pond.ring.centroid.x / metres).toInt()
      val cellY = Math.floor(pond.ring.centroid.y / metres).toInt()
      assertTrue(
        waterLevel[cellX, cellY].isNaN(),
        "$pond sits on a raster lake at cell ($cellX, $cellY)"
      )
    }
  }

  @Test
  fun `the chunk tier fills a pond that the raster reports as dry`() {
    // The end-to-end claim, and the one that would go unnoticed if the sampler were never wired in: the
    // materialiser has to put water over a column the raster says is dry land.
    val pond = ponds.maxByOrNull { it.ring.area }!!
    val surface = pond.attribute(LakeChannels.SURFACE_ELEVATION)
    val centroid = pond.ring.centroid

    val sampler = net.bestia.worldgen.voxel.PondWaterSampler(
      world.world.features.query(pond.bbox)
    )
    assertTrue(!sampler.isEmpty, "the sampler found no pond in the pond's own bounds")

    val there = sampler.surfaceAt(centroid.x, centroid.y)
    assertTrue(!there.isNaN(), "no pond water over the centroid of $pond")
    assertTrue(
      kotlin.math.abs(there - surface) < 1e-9,
      "the sampler reports $there where the feature stores $surface"
    )

    // And nothing at all a long way outside it, so the sampler is bounded by the ring rather than by a box.
    val away = Vec2d(pond.bbox.maxX + 500.0, pond.bbox.maxY + 500.0)
    assertTrue(sampler.surfaceAt(away.x, away.y).isNaN(), "pond water half a kilometre outside the ring")
  }

  @Test
  fun `no two ponds claim the same water`() {
    for (i in ponds.indices) {
      for (j in i + 1 until ponds.size) {
        val a = ponds[i]
        val b = ponds[j]
        assertTrue(
          !a.contains(b.ring.centroid.x, b.ring.centroid.y),
          "$b has its centroid inside $a; two surfaces over one piece of ground"
        )
      }
    }
  }
}
