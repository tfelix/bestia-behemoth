package net.bestia.worldgen.voxel

import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointFeature
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.sqrt

/**
 * What civilisation leaves growing: no tree in a yard, and a thinner wood inside a town than outside it.
 *
 * Two rules, and they are one function because a veto is a thinning of zero.
 *
 * **A yard.** A tree does not grow in the gap between two houses, against a back wall or on the verge in
 * front of a door. None of that ground is described by any feature - nothing is built on it - so it can only
 * be found by asking how close the nearest building is. This is the rule that answers the complaint; the
 * refusals already in `ChunkMaterializer.trunkSite` cover the carriageway and the roof and nothing between
 * them.
 *
 * **A town.** Even the open ground inside a settlement is not countryside: it is grazed, gathered from,
 * walked over and built on next year. [townRetention] thins it rather than clearing it, so a market square
 * keeps the few trees that make it a square rather than becoming a bald patch.
 *
 * ### Why the town edge is a ramp
 *
 * A hard rim at the grading radius would print the disc into the forest around every town - a crop circle,
 * readable from the air and from nothing else. The ramp is the same `smoothstep` the vegetation's own patch
 * field uses, for the same reason.
 *
 * ### Why the minimum and not the product
 *
 * Two settlements whose graded discs overlap describe one crowded place, not a place twice as crowded.
 * Multiplying would clear the ground between neighbouring villages, which is where the woods actually are.
 */
class SettlementCover(
  features: List<VectorFeature>,
  private val structures: TownStructures,
  private val buildingYard: Double,
  private val townRetention: Double,
  private val edgeShare: Double
) : TreeRetention {

  private val towns: List<PointFeature> = features
    .asSequence()
    .filter { it.kind == FeatureKind.SETTLEMENT_GRADING }
    .filterIsInstance<PointFeature>()
    .toList()

  /**
   * True when this chunk has no settlement near it at all, so the caller can skip the whole question.
   *
   * Almost all of the world. Worth a branch because the alternative is a call per lattice cell over ocean,
   * ice and forest that could never have had a town in it.
   */
  val isEmpty: Boolean get() = towns.isEmpty() && !structures.hasBuildings

  override fun retentionAt(worldX: Double, worldY: Double): Double {
    if (structures.nearBuilding(worldX, worldY, buildingYard)) return 0.0

    var kept = 1.0
    for (town in towns) {
      val dx = worldX - town.center.x
      val dy = worldY - town.center.y
      val distanceSq = dx * dx + dy * dy
      if (distanceSq >= town.radius * town.radius) continue

      // 1 at the centre, easing to 0 at the rim over the outermost `edgeShare` of the radius.
      val inside = 1.0 - sqrt(distanceSq) / town.radius
      val depth = PolylineFeature.smoothstep(inside / edgeShare)
      val here = 1.0 + (townRetention - 1.0) * depth
      if (here < kept) kept = here
    }

    return kept
  }
}
