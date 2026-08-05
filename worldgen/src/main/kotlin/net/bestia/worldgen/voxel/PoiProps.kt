package net.bestia.worldgen.voxel

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.poi.PoiChannels
import net.bestia.worldgen.poi.PoiKind
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Quantize
import net.bestia.worldgen.vector.VectorFeature

/**
 * The hand-authored landmarks, as props: one `FeatureKind.POI` marker in, one prop out.
 *
 * ### The one prop source that is not a scatter
 *
 * [VegetationScatter], [CrystalScatter], [AetheriteScatter] and `TownStructures.spireProps` all hash a lattice
 * and ask a field whether each cell fires. This asks nothing. The world tier has already decided that this
 * world holds a lost grave and that it stands *here*; all that is left is to resolve the ground under it and to
 * refuse the one situation the world tier could not see.
 *
 * It is closest in structure to [AetheriteScatter] - built per chunk from the markers the materialiser already
 * queried, rather than held as a field like the layer-driven scatters - and shares nothing else with it.
 *
 * ### No second opinion
 *
 * Deliberately **no biome re-check, no ICE/SNOW cap veto and no water test**, all three of which every scatter
 * here applies. A scatter is a statement about a field, so re-sampling the field at the prop is the same
 * question asked more precisely; a landmark is a decision, and re-deciding it is a different thing entirely.
 * `SurfaceSampler.biomeAt` dithers at metre scale by construction, so a biome test here would delete a
 * world-unique landmark on the strength of a dither the world tier never saw - and a POI is not a member of a
 * population that can absorb the loss. `PoiParams`' clearances are where the ground is judged, and
 * `Invariants.checkPoisBecomeProps` is what says whether that judgement held.
 *
 * The single exception is `NaN` from [PropSite], and it is not a second opinion but a physical impossibility:
 * there is no ground here to stand a landmark on, so there is no elevation to give it. That path is counted by
 * the invariant rather than tolerated silently, because a POI that vanishes is a world missing a landmark it
 * rolled for.
 */
class PoiProps(
  private val config: WorldConfig,
  features: List<VectorFeature>
) {

  /** One landmark: where it is and which entry in the catalogue it is. */
  private class Poi(val kind: PoiKind, val x: Double, val y: Double)

  private val cellUnits = Quantize.toFixed(POI_CELL_SIZE)
  private val voxelUnits = Quantize.toFixed(config.voxelSize)

  private val pois: List<Poi> = features
    .asSequence()
    .filter { it.kind == FeatureKind.POI }
    .filterIsInstance<PointMarker>()
    .mapNotNull { marker ->
      val ordinal = marker.attribute(PoiChannels.KIND).toInt()
      PoiKind.entries.getOrNull(ordinal)?.let { Poi(it, marker.position.x, marker.position.y) }
    }
    .toList()

  val isEmpty get() = pois.isEmpty()

  /**
   * The landmarks whose own position falls inside one chunk, as props.
   *
   * Ownership is the landmark's voxel column in integers, for [VegetationScatter.propsIn]'s reason: a bounds
   * test on a closed interval hands an object exactly on a chunk boundary to both of the chunks that share it.
   */
  fun propsIn(chunk: ChunkPos, site: PropSite, into: PropInstances) {
    if (pois.isEmpty()) return

    val chunkSize = config.chunkSize.toLong()

    for (poi in pois) {
      if (Math.floorDiv(columnOf(poi.x), chunkSize).toInt() != chunk.x) continue
      if (Math.floorDiv(columnOf(poi.y), chunkSize).toInt() != chunk.y) continue

      val ground = site.groundAt(poi.x, poi.y)
      if (ground.isNaN()) continue

      into.add(
        kind = PropKind.POI,
        identity = PropId.of(PropKind.POI, cellOf(poi.x), cellOf(poi.y)),
        x = poi.x,
        y = poi.y,
        ground = ground,
        heightM = poi.kind.heightM,
        // No spread: a landmark is a point, and `radiusAt` is the crown of a tree. Nothing downstream reads it.
        radiusM = 0.0,
        // Not blighted even where the ground is. The flags say what a *scatter* sampled about its surroundings;
        // this object is one specific thing with one specific mesh, and there is no blighted waystone.
        flags = 0,
        subKind = poi.kind.ordinal
      )
    }
  }

  /**
   * The lattice cell that names this landmark, for [PropId].
   *
   * A metre, which is the finest the fixed-point grid offers, and the choice matters for a reason [PropId]'s own
   * "what breaks it" section spells out: a cell index is the durable half of a prop's name, so a lattice whose
   * spacing later moves renames every prop on it. Every other prop source picks a spacing to *space its objects
   * out* and is therefore stuck with it forever; this one is not spacing anything, so it can take the finest
   * quantisation there is and never need to change it.
   *
   * Injective given that landmarks are kilometres apart - `PoiParams.candidateStride` is four thousand of these
   * cells - so two POIs cannot land in one. Thirty signed bits at a metre reaches five hundred thousand
   * kilometres against a design world of four thousand.
   */
  private fun cellOf(world: Double): Long = Math.floorDiv(Quantize.toFixed(world), cellUnits)

  private fun columnOf(world: Double): Long = Math.floorDiv(Quantize.toFixed(world), voxelUnits)

  companion object {

    /** See [cellOf]. Not a spacing: nothing is laid out on this lattice, it only names things. */
    const val POI_CELL_SIZE = 1.0
  }
}
