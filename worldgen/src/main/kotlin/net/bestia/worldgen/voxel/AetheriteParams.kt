package net.bestia.worldgen.voxel

import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.ParamsText

/** Tuning for [AetheriteScatter]. */
data class AetheriteParams(

  /**
   * Edge of the lattice square that holds at most one shard, in metres.
   *
   * Distinct from `CrystalParams.cellSize` (11 m) and `TownStructures.WOUND_SPIRE_SPACING` (7 m) so that the
   * three scatters interleave rather than landing on the same points. Unlike those two the distinctness is not
   * a *correctness* requirement here - [PropId] packs the kind alongside the cell, so two kinds on one spacing
   * still get different names - but a shared lattice would put a shard and a crystal at the same jittered
   * position whenever both fired, and one object inside another is the artefact the jitter exists to avoid.
   *
   * **Not a thing to change after a world ships**: a cell index is the durable half of a prop's identity, so
   * moving this renames every shard in the world. See [PropId].
   */
  val cellSize: Double = 8.0,

  /** How far a shard may wander from its cell's centre, as a share of [cellSize]. `VegetationParams`' trade. */
  val jitterShare: Double = 0.7,

  /**
   * How far the surface expression reaches, as a multiple of the orebody's own radius.
   *
   * Above 1 on purpose. An orebody is a flattened ellipsoid 21 to 70 m across sitting some metres down, and
   * what reaches daylight is not the body's outline but the weathered halo over it - so a field the size of the
   * body would be a suspiciously neat disc, and a field slightly wider reads as ground that has been pushed
   * around. At 2.0 the measured bodies give fields 50 to 140 m across, which is a place rather than a spot.
   */
  val reachOfRadius: Double = 2.0,

  /**
   * Metres below the surface past which a body has no surface expression at all.
   *
   * The gate that decides whether this feature exists on a given world, so it is worth stating what it was set
   * from. Surveyed over six worlds (128 and 256 cells, three seeds each), the corrupted bodies numbered 3 to 13
   * and their depths ran from 0 to 144 m; at 20 m, **one to nine of them per world** are shallow enough to show,
   * typically two to four. That is the rarity a prospecting hint wants - a handful of sites per world - and it
   * is above zero on every world measured, which is the property that matters most. A deposit at 141 m is under
   * a hundred metres of rock and has no business marking the grass.
   */
  val maxDepth: Double = 20.0,

  /**
   * Chance a lattice cell over the centre of a corrupted body holds a shard.
   *
   * Falls off to zero at the edge of [reachOfRadius], so this is the peak rather than the average. Well above
   * `CrystalParams.corruptedDensity` because the whole field is only a hundred metres across and a sparse
   * scatter over it would read as nothing at all - a player has to be able to tell they have found something.
   */
  val centreDensity: Double = 0.30,

  /** Share of shards that are the large variant at the centre of a rich body. */
  val largeShare: Double = 0.18,

  /** Shortest a shard stands, in metres. */
  val minHeight: Double = 0.6,

  /** Tallest a shard stands, in metres. Lower than a mana crystal: this is ore, not a growth. */
  val maxHeight: Double = 2.2
) : Params {

  init {
    require(cellSize > 0.0) { "cellSize must be positive, was $cellSize" }
    require(jitterShare in 0.0..1.0) { "jitterShare must be in [0,1], was $jitterShare" }
    require(reachOfRadius >= 1.0) { "reachOfRadius must be at least 1, was $reachOfRadius" }
    require(maxDepth >= 0.0) { "maxDepth must not be negative, was $maxDepth" }
    require(centreDensity in 0.0..1.0) { "centreDensity must be in [0,1], was $centreDensity" }
    require(largeShare in 0.0..1.0) { "largeShare must be in [0,1], was $largeShare" }
    require(minHeight > 0.0) { "minHeight must be positive, was $minHeight" }
    require(minHeight <= maxHeight) { "minHeight $minHeight exceeds maxHeight $maxHeight" }
  }

  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    cellSize = source.double("cellSize", cellSize),
    jitterShare = source.double("jitterShare", jitterShare),
    reachOfRadius = source.double("reachOfRadius", reachOfRadius),
    maxDepth = source.double("maxDepth", maxDepth),
    centreDensity = source.double("centreDensity", centreDensity),
    largeShare = source.double("largeShare", largeShare),
    minHeight = source.double("minHeight", minHeight),
    maxHeight = source.double("maxHeight", maxHeight)
  )

  override fun digest() = ParamsDigest()
    .put("cellSize", cellSize)
    .put("jitterShare", jitterShare)
    .put("reachOfRadius", reachOfRadius)
    .put("maxDepth", maxDepth)
    .put("centreDensity", centreDensity)
    .put("largeShare", largeShare)
    .put("minHeight", minHeight)
    .put("maxHeight", maxHeight)
}