package net.bestia.zone.world

import net.bestia.worldgen.civ.BridgeChannels
import net.bestia.worldgen.derived.AgentProfile
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureEvaluator
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import net.bestia.worldgen.voxel.LavaSampler
import net.bestia.worldgen.voxel.PondWaterSampler
import net.bestia.worldgen.voxel.RiverWaterSampler
import net.bestia.zone.navigation.region.WaterSource
import net.bestia.zone.navigation.region.WaterSurvey
import org.springframework.stereotype.Service

/**
 * Answers [WaterSource] from the generated world, with the very tests `ChunkMaterializer.fillColumn` floods a
 * column by: the ground is the heightfield plus every feature's cut, and the water over it the highest of the
 * standing, flowing and impounded surfaces. A column the grid calls deep is one the chunk tier fills.
 *
 * Point samples, never a chunk: [net.bestia.worldgen.core.ChunkColumnSource.heights] evaluates all 1024 columns
 * of a chunk, and the grid wants nine of them.
 */
@Service
class GeneratedWaterSource(private val worlds: WorldService) : WaterSource {

  override val isReady get() = worlds.isLoaded

  override fun survey(area: Aabb, exact: Boolean): WaterSurvey = surveyOf(worlds.generated, area, exact)

  companion object {

    /**
     * Features narrower than a grid cell, left out of an inexact survey's ground.
     *
     * For time, not accuracy: measured over Trestein, a town's six hundred buildings and streets and the four
     * roads through it made a sample cost twenty microseconds rather than one, and a kilometre block's survey a
     * third of a second rather than ten milliseconds. None of them can turn a cell from water to land: a road
     * is a few metres of a 32 m cell, and the bridges they cross by come in as crossings. Every bank, channel
     * and the town's own grading stay in.
     */
    private val NARROW = setOf(
      FeatureKind.ROAD, FeatureKind.STREET, FeatureKind.BUILDING, FeatureKind.BUSINESS, FeatureKind.DISTRICT,
      FeatureKind.FIELD,
    )

    /** The same answers over a world held directly, for a test that builds one without the service. */
    fun of(generated: GeneratedWorld): WaterSource = object : WaterSource {
      override val isReady get() = true
      override fun survey(area: Aabb, exact: Boolean) = surveyOf(generated, area, exact)
    }

    private fun surveyOf(generated: GeneratedWorld, area: Aabb, exact: Boolean): WaterSurvey {
      val features = generated.world.features.query(area)

      val cuts = FeatureEvaluator(if (exact) features else features.filter { it.kind !in NARROW })
      val rivers = RiverWaterSampler(features, generated.config.voxelSize)
      val ponds = PondWaterSampler(features)
      val lava = LavaSampler(features)
      val surface = generated.materializer.surface
      val wadeable = AgentProfile().maxWadeDepth * generated.config.voxelSize

      // The grid asks for the ground and then for the water over the same column; the ground is most of the cost.
      var lastX = Double.NaN
      var lastY = Double.NaN
      var lastGround = 0.0
      val groundAt = { x: Double, y: Double ->
        if (x != lastX || y != lastY) {
          val base = generated.base.heightAt(x, y)
          lastGround = if (cuts.isEmpty) base else cuts.heightAt(x, y, base)
          lastX = x
          lastY = y
        }
        lastGround
      }

      val isDeep = { x: Double, y: Double ->
        val ground = groundAt(x, y)

        val molten = lava.surfaceAt(x, y)
        if (!molten.isNaN() && molten > ground) {
          true
        } else {
          var water = surface.waterLevelAt(x, y)
          val flowing = rivers.surfaceAt(x, y)
          val impounded = ponds.surfaceAt(x, y)
          if (!flowing.isNaN() && flowing > water) water = flowing
          if (!impounded.isNaN() && impounded > water) water = impounded

          water - ground > wadeable
        }
      }

      return WaterSurvey(isDeep, crossings(features), groundAt, roads(features))
    }

    private fun roads(features: List<VectorFeature>): List<List<Vec2d>> {
      return features.asSequence()
        .filter { it.kind == FeatureKind.ROAD || it.kind == FeatureKind.STREET }
        .filterIsInstance<PolylineFeature>()
        .map { it.centerline.points }
        .toList()
    }

    /** Each bridge deck as its centre line, from the marker `BridgeDecks` builds its decks from. */
    private fun crossings(features: List<VectorFeature>): List<Pair<Vec2d, Vec2d>> {
      return features.asSequence()
        .filter { it.kind == FeatureKind.BRIDGE }
        .filterIsInstance<PointMarker>()
        .mapNotNull { marker ->
          runCatching {
            val halfSpan = marker.attribute(BridgeChannels.SPAN) * 0.5
            val dx = marker.attribute(BridgeChannels.BEARING_X) * halfSpan
            val dy = marker.attribute(BridgeChannels.BEARING_Y) * halfSpan
            val at = marker.position
            Vec2d(at.x - dx, at.y - dy) to Vec2d(at.x + dx, at.y + dy)
          }.getOrNull()
        }
        .toList()
    }
  }
}
