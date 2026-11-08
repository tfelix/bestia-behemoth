package net.bestia.worldgen.geo

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.Invariants
import net.bestia.worldgen.pipeline.Invariants.PropScan
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.vector.FeatureEvaluator
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointFeature
import net.bestia.worldgen.vector.PolylineFeature
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The hills exist, are the size they claim, and leave the ways walkable.
 *
 * Pinned to seed 7 at 128 cells, a world with hill country on it, for the habit-6 reason: a stage that places no
 * hill on any world passes every structural check there is, so the count is asserted rather than assumed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KnollStageTest {

  private val world: GeneratedWorld by lazy {
    TestWorlds.get(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
  }

  private val hills: List<PointFeature> by lazy {
    world.world.features.all().filter { it.kind == FeatureKind.KNOLL }.filterIsInstance<PointFeature>()
  }

  @Test
  fun `the world has hill country`() {
    val radii = hills.map { it.radius }.sorted()
    println("${hills.size} hills; radius min ${radii.firstOrNull()} median ${radii.getOrNull(radii.size / 2)} max ${radii.lastOrNull()}")
    assertTrue(hills.size >= MIN_HILLS, "only ${hills.size} hills on a world that should have hill country")
    assertTrue(hills.size <= MAX_HILLS, "${hills.size} hills is an entity of the feature index, not a landscape")
  }

  /**
   * A hill raises the ground by no more than it claims, and no single flank is steeper than walking allows.
   *
   * Probed through the finished heightfield at the centre and half way out, against the ground with the hill
   * taken away: the difference is what the hill actually added, whatever else stands there.
   */
  @Test
  fun `a hill is as tall as its params allow and no steeper`() {
    val params = KnollParams()
    val all = world.world.features.all()
    val sample = hills.filterIndexed { i, _ -> i % maxOf(1, hills.size / SAMPLE) == 0 }
    assertTrue(sample.isNotEmpty(), "no hills to measure")

    for (hill in sample) {
      val with = FeatureEvaluator(all.filter { it.bbox.intersects(hill.bbox) })
      val without = FeatureEvaluator(all.filter { it.bbox.intersects(hill.bbox) && it.id != hill.id })
      val base = world.base

      fun added(x: Double, y: Double) =
        with.heightAt(x, y, base.heightAt(x, y)) - without.heightAt(x, y, base.heightAt(x, y))

      val top = added(hill.center.x, hill.center.y)
      assertTrue(top <= params.maxHeight + 0.01, "${hill.id} adds ${top} m, over the ${params.maxHeight} m cap")

      val mid = added(hill.center.x + hill.radius * 0.5, hill.center.y)
      val slope = (top - mid) / (hill.radius * 0.5)
      assertTrue(slope <= MAX_MEAN_FLANK_SLOPE, "${hill.id} falls ${"%.2f".format(slope)} per metre over half its radius")
    }
  }

  @Test
  fun `the invariants find no hill on anything built`() {
    val violations = Invariants.check(world, PropScan.SAMPLED).filter { it.check.contains("knoll") }
    assertTrue(violations.isEmpty(), violations.joinToString("\n"))
  }

  /** Some hills stand beside roads, which is the point: a road threads between them. */
  @Test
  fun `some roads run between hills`() {
    val roads = world.world.features.all().filter { it.kind == FeatureKind.ROAD }.filterIsInstance<PolylineFeature>()
    val beside = hills.count { hill ->
      roads.any { it.bbox.expanded(hill.radius * 2).contains(hill.center.x, hill.center.y) &&
          it.centerline.project(hill.center).distance < hill.radius * 2 }
    }
    println("$beside of ${hills.size} hills stand within two radii of a road")
    assertTrue(beside > 0, "no hill stands near any road, so the road rule is never exercised")
  }

  private companion object {
    const val MIN_HILLS = 100
    const val MAX_HILLS = 60_000
    const val SAMPLE = 40

    /** Mean fall over the inner half of the radius; the smoothstep cone's mean there is about 1.0 * h/r. */
    const val MAX_MEAN_FLANK_SLOPE = 0.35
  }
}
