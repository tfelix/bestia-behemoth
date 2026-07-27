package net.bestia.worldgen.vector

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Continuity of the stamped surface.
 *
 * These are the tests that matter most in the whole module. A river channel that is not continuous
 * where it leaves its corridor is a visible rim in the world; a profile whose value depends on
 * anything other than its arguments is a seam waiting to happen once chunks are generated
 * independently.
 */
class ProfileContinuityTest {

  private val flatTerrain = 100.0

  private val river = LinearFeatures.river(
    id = FeatureId(1),
    centerline = Polyline(listOf(Vec2d(0.0, 0.0), Vec2d(1000.0, 0.0))),
    stationSpacing = 50.0,
    bedElevation = { flatTerrain },
    width = { 20.0 },
    depth = { 4.0 },
    shoulder = { 30.0 }
  )

  private fun heightAcross(
    feature: VectorFeature,
    x: Double,
    y: Double,
    base: Double = flatTerrain
  ): Double {
    return FeatureEvaluator(listOf(feature)).heightAt(x, y, base)
  }

  @Test
  fun `a river channel is continuous across its whole corridor`() {
    var previous = heightAcross(river, 500.0, -200.0)
    var y = -200.0
    while (y <= 200.0) {
      val current = heightAcross(river, 500.0, y)
      assertTrue(
        abs(current - previous) < 0.05,
        "channel height jumped by ${abs(current - previous)} at y=$y"
      )
      previous = current
      y += 0.02
    }
  }

  @Test
  fun `a river channel reaches its full depth at the thalweg and terrain level outside`() {
    assertEquals(flatTerrain - 4.0, heightAcross(river, 500.0, 0.0), 1e-9)
    assertEquals(flatTerrain, heightAcross(river, 500.0, 300.0), 1e-9)
  }

  @Test
  fun `a river channel is continuous along its length including past the mouth`() {
    var previous = heightAcross(river, -100.0, 0.0)
    var x = -100.0
    while (x <= 1100.0) {
      val current = heightAcross(river, x, 0.0)
      assertTrue(
        abs(current - previous) < 0.05,
        "channel height jumped by ${abs(current - previous)} at x=$x"
      )
      previous = current
      x += 0.02
    }
  }

  @Test
  fun `a glacial trough has a flat floor and continuous walls`() {
    // A trough only shows up where it cuts *below* the terrain, so this one runs through a plateau.
    val plateau = 1200.0
    val trough = LinearFeatures.glacialTrough(
      id = FeatureId(2),
      centerline = Polyline(listOf(Vec2d(0.0, 0.0), Vec2d(2000.0, 0.0))),
      floorElevation = { 400.0 },
      halfWidthFloor = { 300.0 },
      halfWidth = { 900.0 },
      wallHeight = { 700.0 }
    )

    // Flat floor: the diagnostic trait a kilometre raster cannot represent.
    assertEquals(400.0, heightAcross(trough, 1000.0, 0.0, plateau), 1e-9)
    assertEquals(400.0, heightAcross(trough, 1000.0, 250.0, plateau), 1e-9)

    // ...and untouched plateau outside the corridor.
    assertEquals(plateau, heightAcross(trough, 1000.0, 950.0, plateau), 1e-9)

    var previous = heightAcross(trough, 1000.0, -1200.0, plateau)
    var y = -1200.0
    while (y <= 1200.0) {
      val current = heightAcross(trough, 1000.0, y, plateau)
      assertTrue(
        abs(current - previous) < 2.0,
        "trough height jumped by ${abs(current - previous)} at y=$y"
      )
      previous = current
      y += 0.1
    }
  }

  @Test
  fun `a road ends without a step when it tapers`() {
    val road = LinearFeatures.road(
      id = FeatureId(3),
      centerline = Polyline(listOf(Vec2d(0.0, 0.0), Vec2d(500.0, 0.0))),
      surfaceElevation = { 90.0 },
      halfWidth = { 4.0 },
      shoulder = { 12.0 },
      endTaper = 30.0
    )

    var previous = heightAcross(road, 450.0, 0.0)
    var x = 450.0
    while (x <= 600.0) {
      val current = heightAcross(road, x, 0.0)
      assertTrue(
        abs(current - previous) < 0.5,
        "road surface jumped by ${abs(current - previous)} at x=$x"
      )
      previous = current
      x += 0.05
    }

    // Well past the taper the road is gone entirely.
    assertEquals(flatTerrain, heightAcross(road, 560.0, 0.0), 1e-9)
  }

  @Test
  fun `evaluating the same column twice gives the same answer`() {
    // Profiles must be pure. If a profile ever picks up chunk-local state this fails, and it fails
    // here rather than as an unreproducible seam report from a player.
    val first = FeatureEvaluator(listOf(river))
    val second = FeatureEvaluator(listOf(river))

    for (step in 0..500) {
      val x = step * 2.3
      val y = (step % 37) - 18.0
      assertEquals(first.heightAt(x, y, flatTerrain), second.heightAt(x, y, flatTerrain), 0.0)
    }
  }

  @Test
  fun `the same column evaluates identically whichever order the columns are visited in`() {
    val evaluator = FeatureEvaluator(listOf(river))

    val forward = (0..200).map { evaluator.heightAt(it * 5.0, 3.0, flatTerrain) }
    val backward = (200 downTo 0).map { evaluator.heightAt(it * 5.0, 3.0, flatTerrain) }.reversed()

    assertEquals(forward, backward)
  }
}
