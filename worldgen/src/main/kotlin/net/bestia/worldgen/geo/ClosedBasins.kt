package net.bestia.worldgen.geo

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.fields.PoissonDisk
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.RadialProfiles
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.ceil
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Tuning for [ClosedBasins]. */
data class ClosedBasinParams(

  /**
   * Target spacing between basin candidates in metres, before the world's scale is taken into account.
   *
   * A length rather than a count, so the *density* is what is fixed and the number follows from how big the
   * world is - which is the right way round: a continent twice as wide has room for twice as many interior
   * sags, not for the same number spread twice as thin.
   *
   * Candidates are cheap and most of them are rejected: about half land in the sea outright and most of the
   * rest have a ring that dips too near it, so 75 km of separation yields around thirty-five candidates and
   * two to five basins on a 512 km world. Which is roughly Iberia's worth of continent and about the number of
   * closed basins that size of land carries.
   */
  val spacing: Double = 75_000.0,

  /** Basin radius in metres at the reference world size. Scaled by [WorldConfig.scaleByLength]. */
  val radius: Double = 9_000.0,

  /**
   * Smallest basin radius in coarse cells, whatever the scaling says.
   *
   * A depression has to be several cells across before Priority-Flood can find it and before the lake in it is
   * more than a puddle, and the coarse grid is a kilometre whatever size the world is - so this floor is in
   * cells rather than in metres deliberately. It is what stops a 128 km world, where [radius] scales down by
   * four, from getting basins one cell wide.
   *
   * Four rather than three because [ringCells] has to fit inside it with room to spare; see the arithmetic
   * there for what "room to spare" has to mean.
   *
   * Raising it to five to pay for [warpAmplitude] was tried and reverted, and the measurement is why: a larger
   * disc has a larger sill ring, so more candidates have a ring that dips too near the sea, and the 128 km world
   * went from **seven basins to three**. The warp shortens the reach by an average of half its amplitude, which
   * at four cells still leaves a carved core two cells in radius - enough for the lake in it. Paying for a
   * cosmetic warp with a third of the world's lakes is the wrong trade.
   */
  val minRadiusCells: Double = 4.0,

  /**
   * How much of its radius the bowl's outline may be pulled *inward*, as a fraction, to stop it being a circle.
   *
   * Found by looking at a map, which is how every geometry defect in this module has been found. Without this
   * the reference world carried six flawless blue discs and the 128 km world seven, and against terrain where
   * nothing else is straight or round they read as impact craters rather than lakes - the same failure as the
   * rectangular coastline and the ruled hotspot chains, in a third form.
   *
   * **Inward only, and that is what keeps it safe.** The sill ring is measured on the *unwarped* radius, so
   * shrinking the profile's own radius only ever makes the normalised distance at a ring cell larger, which
   * only ever raises the profile there. So the guarantee in the class note survives untouched, with the same `q`
   * computed from the same radius - exactly as `OceanBorder`'s coastline wobble is safe because it can only ever
   * move the drowning further inland.
   */
  val warpAmplitude: Double = 0.35,

  /**
   * Thickness of the sill ring, in coarse cells.
   *
   * The ring is what the basin is measured against and what the carve is guaranteed never to touch, so it has
   * to be thick enough that no 8-connected path can step across it: one and a half cells, since a diagonal step
   * covers 1.41. In cells for the same reason [minRadiusCells] is - it is a statement about the grid.
   */
  val ringCells: Double = 1.5,

  /**
   * Metres of subsidence at the deepest point, for a basin with every reason to be there.
   *
   * **Not** scaled by the world size, and the exception is worth the note: the 0.5 m gate in
   * `Lakes.identify` is absolute, the evaporation balance is in metres of water per year, and a lake fifty
   * metres deep is a lake on any map. What scales here is how *wide* a basin is, not how deep.
   */
  val maxDepth: Double = 90.0,

  /** Metres of subsidence for a basin that is merely the least unsuitable place on the continent. */
  val minDepth: Double = 25.0,

  /**
   * Shallowest basin worth carving, in metres.
   *
   * The floor is clamped so it stays above sea level (see [freeboard]), and on flat ground that clamp can
   * take a basin down to nothing. Below this it is a damp patch rather than a lake, and carving it would
   * spend a landform on something no player would see.
   *
   * Also the margin on the 0.5 m gate in `Lakes.identify`: the fill depth of a finished basin is at least this,
   * so eight is a factor of sixteen rather than a hope.
   */
  val minUsefulDepth: Double = 8.0,

  /**
   * Metres the finished floor is kept above sea level.
   *
   * A closed basin *below* sea level is a real landform - the Dead Sea and the Qattara Depression are both
   * hundreds of metres down - but it is not one this pipeline can express, because `FlowRouting` calls every
   * cell under the waterline ocean and `Lakes` skips ocean cells. The result would be a pocket of sea in the
   * middle of a continent with no coast to it, which reads as a hole in the map. So the basin stays dry-land
   * and the depression is genuine; a below-sea-level basin wants sea level to become per-basin, and that is a
   * subsystem rather than a pass.
   */
  val freeboard: Double = 15.0,

  /**
   * Metres from the sea at which a candidate is as interior as the score can reward, at the reference size.
   *
   * A ramp from the coast rather than a threshold, and the distinction matters more than it looks. Interiority
   * is a real preference - a depression within reach of the coast has a short steep path to the sea for a river
   * to take, and the forty-five timesteps upstream of this pass will have taken it - but as a *gate* it is
   * brutal: the first version excluded anything within 35 km of salt water and left the 512 km reference world
   * with a single candidate, because a lobed continent has very little of itself further inland than that.
   */
  val interiorDistance: Double = 45_000.0,

  /** Metres from a divergent plate boundary within which a candidate counts as a graben. */
  val riftRange: Double = 25_000.0,

  /**
   * Uplift rate above which the crust is too active to sag, in metres per erosion timestep.
   *
   * Measured rather than reasoned into place, and the measurement moved it. The first value was twice
   * [Orogeny.INTERIOR_UPLIFT] - 2.4 - on the argument that a quiet interior sits between 0.46 and 1.2 and
   * anything much above that is an orogen. What the candidates on nine real worlds actually look like is a big
   * cluster at 2.0 to 2.5, a gap, and then the crests at 7 to 9.5. Cutting at 2.4 scored most of that middle
   * cluster at zero and left every world with exactly one basin, chosen by the fallback.
   *
   * The cluster is not orogen. It is orogen *flank*, and a basin there is a foreland basin - the trough a
   * mountain belt's own weight presses into the crust in front of it, which is the Po Valley, the Ganges plain
   * and the Alberta basin, and which holds a great deal of water. So 4.0 keeps the flanks in play and still
   * scores the crests at nothing, which is right: an orogen crest is where basins are *filled*.
   */
  val quietUplift: Double = 4.0,

  /** Crust age above which an interior counts as old enough to have sagged. */
  val cratonAge: Double = 0.45,

  /**
   * How much of each of the three preferences a candidate keeps for free.
   *
   * The score is a product of interiority, quietness and structure, and a bare product of three numbers in
   * `[0,1]` is small almost everywhere: on real worlds a perfectly reasonable site scored 0.07, because being
   * merely good at three things multiplies to being poor at one. These are *preferences* rather than
   * requirements - what a basin actually requires is asserted by the gates above and guaranteed by the
   * geometry - so each factor keeps a floor and the product stays a ranking instead of a filter.
   */
  val preferenceFloor: Double = 0.32,

  /** A sag on old crust is a weaker case for a basin than a graben, but it is still a case. */
  val sagWeight: Double = 0.8,

  /** Score below which a candidate is not worth a basin. */
  val minScore: Double = 0.20,

  /**
   * Power of the bowl. 2 is a paraboloid; higher keeps more of the floor flat.
   *
   * Four, and the reason is the coarse grid rather than geology - though geology agrees, because a sedimentary
   * basin does have a flat floor and a playa is dead level. The subsidence is defined at the basin's *centre*,
   * which is an arbitrary world position, and the nearest cell centre to it can be half a diagonal - 707 m -
   * away. Under a paraboloid the profile has already climbed by `(707/r)^2` of the rim height by then, and in
   * rough country the rim height is dominated by the sill ring's own relief, so a nominally 63 m basin came out
   * 55 m deep at cell resolution and the shortfall grew with the terrain.
   *
   * At the fourth power `(707/4000)^4` is one part in a thousand, so the shortfall is a millimetre per metre of
   * ring relief and the ring would have to stand seven kilometres above its own sill before the depression
   * failed to clear the 0.5 m gate. Which is why there is no relief cap here: the exponent removes the need for
   * one. The carved footprint is unchanged at about five eighths of the radius, and the inner third is flat.
   */
  val exponent: Double = 4.0
) : Params {
  init {
    require(spacing > 0.0) { "spacing must be positive, was $spacing" }
    require(radius > 0.0) { "radius must be positive, was $radius" }
    require(minRadiusCells > 0.0) { "minRadiusCells must be positive, was $minRadiusCells" }
    require(warpAmplitude >= 0.0) { "warpAmplitude must not be negative, was $warpAmplitude" }
    require(ringCells >= 0.0) { "ringCells must not be negative, was $ringCells" }
    require(minDepth >= 0.0) { "minDepth must not be negative, was $minDepth" }
    require(minDepth <= maxDepth) { "minDepth $minDepth is deeper than maxDepth $maxDepth" }
    require(minUsefulDepth >= 0.0) { "minUsefulDepth must not be negative, was $minUsefulDepth" }
    // Deeper than the deepest basin allowed and no basin is ever worth carving, so the whole subsystem goes
    // quiet with nothing to say - the shape of failure this class's own KDoc calls a landform spent on
    // nothing.
    require(minUsefulDepth <= maxDepth) {
      "minUsefulDepth $minUsefulDepth exceeds maxDepth $maxDepth, so no basin could ever be worth carving"
    }
    require(freeboard >= 0.0) { "freeboard must not be negative, was $freeboard" }
    require(interiorDistance > 0.0) { "interiorDistance must be positive, was $interiorDistance" }
    require(riftRange >= 0.0) { "riftRange must not be negative, was $riftRange" }
    require(quietUplift > 0.0) { "quietUplift must be positive, was $quietUplift" }
    require(cratonAge in 0.0..1.0) { "cratonAge must be in [0,1], was $cratonAge" }
    require(preferenceFloor in 0.0..1.0) { "preferenceFloor must be in [0,1], was $preferenceFloor" }
    require(sagWeight in 0.0..1.0) { "sagWeight must be in [0,1], was $sagWeight" }
    require(minScore >= 0.0) { "minScore must not be negative, was $minScore" }
    require(exponent > 0.0) { "exponent must be positive, was $exponent" }
  }

  /** See `TectonicsParams.overriddenBy`. Reached as `erosion.basins.*`, since erosion owns this pass. */
  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    spacing = source.double("spacing", spacing),
    radius = source.double("radius", radius),
    minRadiusCells = source.double("minRadiusCells", minRadiusCells),
    warpAmplitude = source.double("warpAmplitude", warpAmplitude),
    ringCells = source.double("ringCells", ringCells),
    maxDepth = source.double("maxDepth", maxDepth),
    minDepth = source.double("minDepth", minDepth),
    minUsefulDepth = source.double("minUsefulDepth", minUsefulDepth),
    freeboard = source.double("freeboard", freeboard),
    interiorDistance = source.double("interiorDistance", interiorDistance),
    riftRange = source.double("riftRange", riftRange),
    quietUplift = source.double("quietUplift", quietUplift),
    cratonAge = source.double("cratonAge", cratonAge),
    preferenceFloor = source.double("preferenceFloor", preferenceFloor),
    sagWeight = source.double("sagWeight", sagWeight),
    minScore = source.double("minScore", minScore),
    exponent = source.double("exponent", exponent)
  )

  override fun digest() = ParamsDigest()
    .put("spacing", spacing)
    .put("radius", radius)
    .put("minRadiusCells", minRadiusCells)
    .put("warpAmplitude", warpAmplitude)
    .put("ringCells", ringCells)
    .put("maxDepth", maxDepth)
    .put("minDepth", minDepth)
    .put("minUsefulDepth", minUsefulDepth)
    .put("freeboard", freeboard)
    .put("interiorDistance", interiorDistance)
    .put("riftRange", riftRange)
    .put("quietUplift", quietUplift)
    .put("cratonAge", cratonAge)
    .put("preferenceFloor", preferenceFloor)
    .put("sagWeight", sagWeight)
    .put("minScore", minScore)
    .put("exponent", exponent)
}

/**
 * One closed basin: where it is, how big, how deep, and why it is there.
 *
 * [floor] and [rim] are absolute elevations rather than depths, because that is what the bowl profile needs
 * and because deriving them once here is what makes [ClosedBasins.carve] a pure function of this record - so
 * the invariants and the viewer read the same numbers the carve used.
 */
data class ClosedBasin(
  val centre: Vec2d,
  val radius: Double,
  /** Metres of subsidence below the sill. */
  val depth: Double,
  /** Absolute elevation of the deepest point. */
  val floor: Double,
  /** Absolute elevation of the lowest cell of the sill ring: where a full basin spills. */
  val sill: Double,
  /** Absolute elevation the bowl profile reaches at [radius]. See the note in [ClosedBasins]. */
  val rim: Double,
  val score: Double,
  /** True for a graben at a divergent plate boundary, false for an interior sag on old crust. */
  val isRift: Boolean,
  /** Seed for this basin's outline warp. See [ClosedBasinParams.warpAmplitude]. */
  val warpSeed: Long
)

/**
 * Tectonic closed basins: the lake source that does not need ice.
 *
 * ### Why the pipeline had no lakes, and why this is where the second half of the fix goes
 *
 * `ErosionStage.incise` clamps every cell to at or above its receiver as it walks the drainage stack, so its
 * output surface is **depression-free by construction**. That is correct and has to stay - the comment there
 * names the bug it prevents, a cell below its own receiver being a pit the next fill has to undo - but it
 * means a lake basin can never come *out of* the fluvial process. It has to be put back by something that
 * outruns fluvial incision.
 *
 * Glacial overdeepening is one such thing, and `GlacialStage` now delivers it: an overdeepened trough floor is
 * a closed basin, and on the 512 km reference world that alone turned zero lakes into a hundred and fifteen.
 * It delivers nothing on a warm world. Measured, the 128 km world `zone-server` boots has thirty-six glacial
 * features and **no lake at all**, and five of a hundred and twenty seeds at 192 km were equally dry.
 *
 * Tectonic subsidence is the other source, and it is the one that works without a glacier: a graben dropped
 * between the shoulders of a divergent boundary, or an old continental interior that has sagged under its own
 * sediment. Between them they hold the Caspian, Baikal, the Dead Sea, Lake Eyre and every playa in the Great
 * Basin - which is to say, most of the standing water on Earth that ice did not put there.
 *
 * ### Applied after the erosion loop, for the reason the margin is
 *
 * The precedent is a dozen lines away in `ErosionStage`, where `OceanBorder` is reapplied because forty-five
 * timesteps of uplift legitimately undo it. The same argument applies with the sign flipped: a basin carved
 * before the loop is a basin the loop fills in, because filling depressions is exactly what the loop does.
 *
 * ### The depression is guaranteed rather than tuned
 *
 * Each basin is measured against a **sill ring**: the annulus of cells just inside its radius, at least one and
 * a half cells thick so that no eight-connected path can step across it. Two numbers come off that ring - its
 * lowest cell, which is the sill the basin would spill over, and its highest, which is what the profile has to
 * clear. The floor goes a subsidence *below the sill*, and the rim height is then set to whichever is larger of
 * "reaches the ring's highest cell at the radius" and "is still above the sill at the ring's inner edge".
 *
 * That second term is the whole argument, and it is arithmetic rather than tuning. With the profile
 * `floor + (d/r)^n * rimHeight`, holding it at or above the sill across the entire ring needs
 * `rimHeight >= depth / q` where `q = ((r - ringThickness) / r)^n`. Setting it so gives:
 *
 * - **no cell of the ring ends up below the sill**, because the profile across the whole band is at or above
 *   it and every ring cell starts at or above it too. Note the exact claim: a ring cell standing well above the
 *   sill *can* be shaved, and that is fine - what matters is the floor of the band, not that it is untouched;
 * - the centre sits at the sill less the subsidence, and so **below every cell of the ring**.
 *
 * The second bullet is about the centre, which is a world position rather than a cell, so the *cell* nearest it
 * can be half a diagonal away and slightly up the profile. [ClosedBasinParams.exponent] is what makes that
 * shortfall a millimetre per metre of ring relief instead of a fifth of the basin.
 *
 * A point strictly lower than a closed band around it is a closed depression, so Priority-Flood must raise it,
 * its fill depth is at least [ClosedBasinParams.minUsefulDepth], and the 0.5 m gate in `Lakes.identify` is
 * cleared by a factor of sixteen in the worst case.
 *
 * That holds *at the moment the basin is carved*, which is the honest way to put it: `GlacialStage` writes the
 * same surface afterwards, and a trough crossing a basin's ring drains it. Which is a landform rather than a
 * bug - a glacier really does breach a rim - and the two sources cover each other, because a world where ice
 * breached every basin is a world with ice-carved basins of its own. So `Invariants.checkTheWorldHasStandingWater`
 * is stated for the world and `ClosedBasinsTest` states the per-basin case as a disjunction: a basin holds water
 * or its rim is demonstrably cut.
 *
 * Measuring the **ring** rather than the whole disc is also what makes the pass usable. Judged on the disc, a
 * candidate was disqualified by any valley floor anywhere inside it, and on the 512 km reference world exactly
 * one site in the world survived. Judged on the ring, a valley that genuinely breaches the basin still shows up
 * - it crosses the ring, so it *is* the sill - while one that merely passes nearby does not.
 *
 * The shape self-scales too: in flat country the carve fills the inner two thirds of the disc, and in rough
 * country the profile climbs past the surrounding ground almost at once and the basin is a small pocket. Which
 * is the right answer both times. [ClosedBasinParams.warpAmplitude] then pulls the outline in by up to a third
 * at a varying angle so it is not a circle - inward only, which is what leaves the argument above untouched.
 *
 * ### Raster, not vector
 *
 * A basin is five to twenty kilometres across - an order of magnitude wider than the three coarse cells that
 * are the threshold for pushing a feature into the vector tier, and broad enough that a bicubic sample of the
 * kilometre grid reproduces it at chunk scale with nothing to stitch. `TectonicsStage.addHotspotChains` makes
 * the same call in the same package for the same reason.
 *
 * A [net.bestia.worldgen.vector.PointMarker] records each one anyway, carrying no terrain effect. Not for the
 * chunks - for the invariants and the viewer. The bug this phase exists to finish off survived for as long as
 * it did because nothing counted lakes and nothing drew basins, and a landform that no tool can see is a
 * landform that goes wrong quietly.
 */
object ClosedBasins {

  /** Station channels on a [net.bestia.worldgen.vector.FeatureKind.TECTONIC_BASIN] marker. */
  const val CHANNEL_RADIUS = "radius"
  const val CHANNEL_DEPTH = "depth"
  const val CHANNEL_FLOOR = "floor"
  const val CHANNEL_RIFT = "rift"

  /**
   * Chooses where the world's closed basins are.
   *
   * Every grid must already be sampled to [region]: [oceanDistance] in particular comes from the climate
   * stage at four kilometres and has to be resampled by the caller, because a stage that quietly indexed a
   * coarser grid with fine coordinates would read the top-left corner of the world for everything.
   *
   * @param rifts centrelines of the divergent plate boundaries, read from the fault markers rather than
   *   re-derived from `plate_id` - see `TectonicsStage.boundaryTypeOf`.
   */
  fun place(
    config: WorldConfig,
    rng: GenRng,
    region: CellRegion,
    elevation: Grid,
    uplift: Grid,
    crustAge: Grid,
    oceanDistance: Grid,
    rifts: List<Polyline>,
    params: ClosedBasinParams = ClosedBasinParams()
  ): List<ClosedBasin> {
    val metres = region.resolution.metresPerCell
    val seaLevel = config.seaLevel
    val bounds = region.toWorld()

    val baseRadius = max(config.scaleByLength(params.radius), metres * params.minRadiusCells)
    // Far enough apart that two basins never share a disc, whatever the scaling did to either number.
    val spacing = max(config.scaleByLength(params.spacing), baseRadius * SEPARATION_FACTOR)
    val interiorRange = config.scaleByLength(params.interiorDistance)
    val riftRange = config.scaleByLength(params.riftRange)

    val candidates = PoissonDisk.sample(bounds, spacing, rng)
    val scored = ArrayList<ClosedBasin>(candidates.size)

    for (candidate in candidates) {
      // Drawn for every candidate in index order, before anything is filtered, so the size a basin gets does
      // not depend on which candidates happened to pass the gates.
      //
      // One-sided, which is unusual for a jitter and deliberate: the floor under `baseRadius` is a hard
      // requirement of the ring arithmetic, and a two-sided jitter would undercut it - or, if the floor were
      // reapplied afterwards, would pin every basin on a small world to exactly the same size.
      val radius = baseRadius * (1.0 + rng.nextDouble() * RADIUS_JITTER)
      val warpSeed = rng.nextLong()

      // The whole disc has to be inside the region, not merely its centre. A disc the grid edge clips has no
      // *ring* - the band is an arc rather than a closed curve - and the depression inside it could drain out
      // through the side that was cut off, which is the one way the guarantee below can be void. Costs nothing
      // in practice: a candidate that close to the edge is inside the forced ocean margin and its ring is four
      // hundred metres under water, so the headroom gate would have taken it anyway.
      if (candidate.x - radius < bounds.minX || candidate.x + radius > bounds.maxX) continue
      if (candidate.y - radius < bounds.minY || candidate.y + radius > bounds.maxY) continue

      val cellX = floor((candidate.x - bounds.minX) / metres).toInt()
      val cellY = floor((candidate.y - bounds.minY) / metres).toInt()
      if (cellX !in 0 until region.width || cellY !in 0 until region.height) continue
      val i = elevation.index(cellX, cellY)

      // The sill ring's own extremes, which is what the bowl is built from - see the class note. Sampled
      // before any of the scoring, because a candidate whose ring reaches the sea is out however good its
      // position is otherwise.
      val ringInner = radius - metres * params.ringCells
      var sill = Double.MAX_VALUE
      var highest = -Double.MAX_VALUE
      forEachCellInDisc(region, metres, bounds, candidate, radius) { cell, _, _, distance ->
        if (distance < ringInner) return@forEachCellInDisc
        val z = elevation.data[cell]
        if (z < sill) sill = z
        if (z > highest) highest = z
      }
      // Unreachable given the two checks above - a disc wholly inside the region and at least four cells across
      // always has ring cells in it - and kept because the alternative is a sentinel sill that sails through the
      // headroom gate and lands in the layer as a floor near `Double.MAX_VALUE`.
      if (sill == Double.MAX_VALUE) continue

      val headroom = sill - seaLevel - params.freeboard
      if (headroom < params.minUsefulDepth) continue

      val interior = PolylineFeature.smoothstep(oceanDistance.data[i] / interiorRange)
      val quiet = PolylineFeature.smoothstep(
        (params.quietUplift - uplift.data[i]) / params.quietUplift
      )
      val craton = PolylineFeature.smoothstep(
        (crustAge.data[i] - params.cratonAge) / (1.0 - params.cratonAge)
      )

      val riftDistance = nearestRift(rifts, candidate, riftRange)
      val rift = if (riftDistance >= riftRange) 0.0 else 1.0 - riftDistance / riftRange
      val sag = craton * params.sagWeight

      val structure = max(rift, sag)
      val score = preference(params, interior) * preference(params, quiet) * preference(params, structure)

      val depth = (params.minDepth + (params.maxDepth - params.minDepth) * score)
        .coerceAtMost(headroom)
      if (depth < params.minUsefulDepth) continue

      val floor = sill - depth

      // The rim, and the one line of this pass that is load bearing rather than tuned. Whichever is higher of
      // "reach the ring's tallest cell at the radius" and "still be above the sill at the ring's inner edge" -
      // the second is what makes the ring provably uncuttable *below its own sill*, which is the property the
      // depression needs. See the class note for the derivation and for what it does not claim.
      val q = ((radius - metres * params.ringCells) / radius).pow(params.exponent)
      val rimHeight = max(highest - floor, depth / q)

      scored.add(
        ClosedBasin(
          centre = candidate,
          radius = radius,
          depth = depth,
          floor = floor,
          sill = sill,
          rim = floor + rimHeight,
          score = score,
          isRift = rift >= sag && rift > 0.0,
          warpSeed = warpSeed
        )
      )
    }

    // Best first, with position breaking ties, so the order is a function of the world rather than of how the
    // Poisson front happened to be consumed.
    val ordered = scored.sortedWith(
      compareByDescending<ClosedBasin> { it.score }
        .thenBy { it.centre.y }
        .thenBy { it.centre.x }
    )

    val kept = ordered.filter { it.score >= params.minScore }

    // A continent with no good site still gets its best one. Not a fudge to make an assertion pass: every
    // continent on Earth has at least one closed basin on it, and a world with land but nowhere in it that
    // holds standing water is the less truthful of the two answers.
    return kept.ifEmpty { ordered.take(1) }
  }

  /**
   * Cuts [basins] into [elevation] in place, lowest-elevation-wins.
   *
   * `min` rather than a subtraction, so the bowl is an absolute floor. That is what makes the guarantee in the
   * class note hold, and it is the same property that lets every carving feature in the vector tier be applied
   * twice without carving twice.
   */
  fun carve(
    elevation: Grid,
    basins: List<ClosedBasin>,
    region: CellRegion,
    params: ClosedBasinParams = ClosedBasinParams()
  ) {
    if (basins.isEmpty()) return

    val metres = region.resolution.metresPerCell
    val bounds = region.toWorld()

    // Sorted by position, so two basins whose discs somehow overlap compose in an order that depends on the
    // world rather than on the score comparison above. `min` is commutative, so this only pins the arithmetic.
    for (basin in basins.sortedWith(compareBy({ it.centre.y }, { it.centre.x }))) {
      // Built with a radius of one and fed a *normalised* distance, which is the one liberty taken here: the
      // outline is warped per angle, so there is no single radius to give it, and reproducing `bowl`'s two
      // lines locally would be a copy of the shape library rather than a use of it.
      val profile = RadialProfiles.bowl(
        floorElevation = basin.floor,
        rimHeight = basin.rim - basin.floor,
        radius = 1.0,
        exponent = params.exponent
      )

      forEachCellInDisc(region, metres, bounds, basin.centre, basin.radius) { cell, dx, dy, distance ->
        val reach = reachAt(basin, params, dx, dy)
        if (distance >= reach) return@forEachCellInDisc

        val here = elevation.data[cell]
        val target = profile.heightAt(distance / reach, here)
        if (target < here) elevation.data[cell] = target
      }
    }
  }

  /**
   * How far this basin's bowl reaches at the angle of one cell: its radius, less an inward-only warp.
   *
   * Periodic in the angle from the *sample path* rather than from the noise, exactly as
   * `OceanBorder.wobbleAlong` is: `fields/Noise` has no tileable variant, but anything evaluated around a closed
   * circle is periodic in the angle and every octave of an fbm closes with it. Without that the outline would
   * have a radial crease at whatever angle `atan2` wraps.
   */
  private fun reachAt(basin: ClosedBasin, params: ClosedBasinParams, dx: Double, dy: Double): Double {
    if (params.warpAmplitude <= 0.0) return basin.radius

    val angle = atan2(dy, dx)
    val noise = Noise.fbm(
      seed = basin.warpSeed,
      x = cos(angle) * WARP_CIRCLE_RADIUS,
      y = sin(angle) * WARP_CIRCLE_RADIUS,
      octaves = WARP_OCTAVES
    )

    // fbm is [-1, 1]; mapped to [0, 1] so the warp only ever shortens the reach. See warpAmplitude.
    return basin.radius * (1.0 - params.warpAmplitude * (0.5 + 0.5 * noise))
  }

  /** One of the three preferences, floored so that a product of them stays a ranking. */
  private fun preference(params: ClosedBasinParams, term: Double) =
    params.preferenceFloor + (1.0 - params.preferenceFloor) * term

  /** Metres from [point] to the nearest of [rifts], giving up once [limit] is exceeded. */
  private fun nearestRift(rifts: List<Polyline>, point: Vec2d, limit: Double): Double {
    var best = limit
    for (rift in rifts) {
      // The bounding box first: a fault spans hundreds of kilometres and projecting onto every segment of
      // every one of them for every candidate is the shape of the query that made the glacial carve slow.
      if (!rift.bbox.expanded(limit).contains(point.x, point.y)) continue
      val distance = rift.project(point).distance
      if (distance < best) best = distance
    }
    return best
  }

  /**
   * Visits every cell whose centre lies within [radius] of [centre], with its offset and distance.
   *
   * One traversal shared by the measuring pass and the carving pass, because the guarantee in the class note
   * is a statement about *the same set of cells* being measured and then cut - and two loops with two
   * rounding conventions would be two different sets.
   */
  private inline fun forEachCellInDisc(
    region: CellRegion,
    metres: Double,
    bounds: Aabb,
    centre: Vec2d,
    radius: Double,
    action: (cell: Int, dx: Double, dy: Double, distance: Double) -> Unit
  ) {
    val minCellX = max(0, floor((centre.x - radius - bounds.minX) / metres).toInt())
    val maxCellX = min(region.width - 1, ceil((centre.x + radius - bounds.minX) / metres).toInt())
    val minCellY = max(0, floor((centre.y - radius - bounds.minY) / metres).toInt())
    val maxCellY = min(region.height - 1, ceil((centre.y + radius - bounds.minY) / metres).toInt())

    for (cellY in minCellY..maxCellY) {
      val worldY = bounds.minY + (cellY + 0.5) * metres
      for (cellX in minCellX..maxCellX) {
        val worldX = bounds.minX + (cellX + 0.5) * metres
        val dx = worldX - centre.x
        val dy = worldY - centre.y
        val distanceSq = dx * dx + dy * dy
        if (distanceSq > radius * radius) continue
        action(cellY * region.width + cellX, dx, dy, sqrt(distanceSq))
      }
    }
  }

  /** Basin separation as a multiple of the base radius, so no two discs can overlap. */
  private const val SEPARATION_FACTOR = 4.0

  /** Fraction of the base radius a basin may exceed it by, so they are not all the same size. */
  private const val RADIUS_JITTER = 0.45

  /**
   * Radius of the circle walked through noise space to warp a basin's outline.
   *
   * Sets how many lobes the outline has: a bigger circle covers more noise per turn, so the features come out
   * smaller relative to the perimeter. Two and a half gives three or four bays, which is a lake rather than
   * either a circle or a starfish.
   */
  private const val WARP_CIRCLE_RADIUS = 2.5

  /** Enough octaves for the bays to have headlands, not enough for the shore to look eroded by noise. */
  private const val WARP_OCTAVES = 3
}
