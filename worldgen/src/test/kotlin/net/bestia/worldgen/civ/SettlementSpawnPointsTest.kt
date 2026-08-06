package net.bestia.worldgen.civ

import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Settlement spawn-point selection, against a real world. */
class SettlementSpawnPointsTest {

  private val generated: GeneratedWorld by lazy {
    StandardWorld.build(StandardWorld.demoConfig(seed = 909L).copy(widthCells = 160, heightCells = 160))
  }

  private fun standingByPopulationDesc(): List<Int> {
    val chronicle = generated.world.chronicle
    return chronicle.settlements.indices
      .filter { chronicle.settlementStood(it, chronicle.presentYear) }
      .sortedByDescending { chronicle.settlements[it].population }
  }

  @Test
  fun `never offers the largest settlement`() {
    val ranked = standingByPopulationDesc()
    assertTrue(ranked.size >= 4, "test world only has ${ranked.size} standing settlements")

    val candidates = SettlementSpawnPoints.choose(generated)
    assertTrue(candidates.none { it.settlementIndex == ranked.first() })
  }

  @Test
  fun `offers up to three candidates, ranked 2nd through 4th largest`() {
    val ranked = standingByPopulationDesc()
    val expected = ranked.drop(1).take(SettlementSpawnPoints.MAX_HOME_CANDIDATES)

    val candidates = SettlementSpawnPoints.choose(generated)

    assertEquals(3, SettlementSpawnPoints.MAX_HOME_CANDIDATES)
    assertEquals(expected, candidates.map { it.settlementIndex })
  }

  @Test
  fun `candidates are only ever standing settlements`() {
    val chronicle = generated.world.chronicle
    val candidates = SettlementSpawnPoints.choose(generated)

    assertTrue(candidates.isNotEmpty())
    for (candidate in candidates) {
      assertTrue(
        chronicle.settlementStood(candidate.settlementIndex, chronicle.presentYear),
        "settlement ${candidate.settlementIndex} does not stand today"
      )
    }
  }

  @Test
  fun `every candidate position is on solid ground clear of its own settlement`() {
    val sites = generated.world.features.all()
      .filter { it.kind == net.bestia.worldgen.vector.FeatureKind.SETTLEMENT }
      .filterIsInstance<net.bestia.worldgen.vector.PointMarker>()
      .associateBy { it.attribute(SettlementChannels.INDEX).toInt() }

    for (candidate in SettlementSpawnPoints.choose(generated)) {
      val height = generated.base.heightAt(candidate.position.x, candidate.position.y)
      assertTrue(
        height > generated.config.seaLevel,
        "candidate for settlement ${candidate.settlementIndex} at ${candidate.position} is underwater ($height m)"
      )

      val site = sites.getValue(candidate.settlementIndex)
      val distance = distanceBetween(candidate.position, site.position)
      assertTrue(
        distance >= candidate.tier.footprintRadius,
        "candidate for settlement ${candidate.settlementIndex} is only $distance m from its centre, " +
            "inside its own ${candidate.tier.footprintRadius} m footprint"
      )
    }
  }

  @Test
  fun `standingSettlementCount matches the standing settlement list`() {
    assertEquals(standingByPopulationDesc().size, SettlementSpawnPoints.standingSettlementCount(generated))
  }

  private fun distanceBetween(a: Vec2d, b: Vec2d): Double = hypot(a.x - b.x, a.y - b.y)
}
