package net.bestia.worldgen.geo

import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.hydro.DrainageNetwork
import net.bestia.worldgen.hydro.FlowRouting
import kotlin.math.pow

/** Tuning for [ErosionStage]. */
data class ErosionParams(

  /**
   * Depth the forced ocean margin is pushed to, in metres below sea level.
   *
   * Must match [TectonicsParams.oceanBorderDepth], and defaults from it for that reason: this stage has to put
   * the margin back after uplift, and two different depths would leave a step at the margin's inner edge that
   * the next erosion pass would turn into an escarpment.
   */
  val oceanBorderDepth: Double = TectonicsParams().oceanBorderDepth,

  /**
   * Must match [TectonicsParams.oceanBorderWobble], and defaults from it for the same reason the depth does.
   *
   * More sharply than the depth, in fact: the two applications of the margin must agree on *where the
   * coastline is*, and a different wobble here would carve a step at the difference between the two - which is
   * exactly the escarpment the second application exists to prevent.
   */
  val oceanBorderWobble: Double = TectonicsParams().oceanBorderWobble,

  /**
   * Timesteps of geological time.
   *
   * The implicit stream power solver is stable at any timestep, so this is a quality knob rather than a
   * stability one: more steps let the drainage network reorganise itself, which is what turns an
   * initially noisy surface into a properly dendritic one.
   */
  val timesteps: Int = 45,

  val timestep: Double = 1.0,

  /**
   * Erodibility scale: the `K` of the stream power law, before rock hardness modulates it.
   *
   * Two independent things depend on this and they have to be reasoned about separately, because getting
   * one right while ignoring the other produces a landscape that is *either* the wrong shape *or* barely
   * eroded at all.
   *
   * **Equilibrium relief** is set by the ratio `U / K`, not by either alone: at steady state
   * `U = K A^m S`, so `S = U / (K A^m)`. Scaling `K` and the uplift field together therefore leaves the
   * shape of the finished landscape untouched.
   *
   * **How fast that equilibrium is reached** is set by `K` alone. A cell keeps `(1 + f)^-timesteps` of
   * its initial drop to its receiver, where `f = K dt A^m / L` is close to `K dt` for a headwater at
   * kilometre cells. At `K = 0.035` that leaves headwaters holding 57% of the *initial noise* after 45
   * steps - so the terrain is still mostly the fractal surface it started as, with no drainage structure
   * in it, and the rivers sit in valleys that do not exist. Reaching equilibrium at that rate needs about
   * 180 steps.
   *
   * So this is deliberately large, and the uplift field is scaled to match. Headwaters keep about 8% of
   * their initial disequilibrium after 45 steps, which is enough for the network to have re-graded them,
   * and the relief is the same as it would have been with a fifth of the erodibility and four times the
   * timesteps. See [net.bestia.worldgen.geo.Orogeny.INTERIOR_UPLIFT] for the other half of the ratio.
   */
  val erodibility: Double = 0.115,

  /** The `m` of `K A^m S^n`. Around 0.5 for real drainage networks. */
  val areaExponent: Double = 0.5,

  /** How much less readily rock erodes under water, where there is no rain and no river. */
  val marineErodibility: Double = 0.06,

  /** Relaxation passes per timestep for mass wasting. */
  val thermalIterations: Int = 2,

  /**
   * Maximum stable slope of the softest rock, in metres per metre.
   *
   * These two are **resolution dependent**, which is easy to get wrong. A talus angle is a property of
   * a scree slope a few tens of metres across; averaged over a kilometre cell, terrain as dramatic as
   * the Alps rarely exceeds 0.3. Values calibrated for voxels - around 0.6 to 1.4 - would simply never
   * trigger here, and the stage would silently do nothing at all. These are for kilometre cells; a
   * chunk-scale detail pass wants the steeper set.
   *
   * Set high enough that relaxation only trims genuinely implausible coarse slopes. Too low and mass
   * wasting smooths away the valleys stream power just cut, which is a slow way of turning off erosion.
   */
  val talusSoft: Double = 0.30,

  /** Maximum stable slope of the hardest rock. This is what lets hard rock hold a cliff. */
  val talusHard: Double = 0.65,

  /** Fraction of the excess slope removed per relaxation pass. Above 0.5 it oscillates. */
  val thermalRate: Double = 0.35,

  /**
   * Deposition efficiency, the `G` of the erosion-deposition form
   * `dz/dt = -K A^m S + G Qs/A`.
   *
   * Dividing the sediment flux by the drainage area is what makes this stable. The naive alternative -
   * a transport capacity that the load is compared against, with the excess dropped - concentrates an
   * entire basin's worth of eroded material into whichever single cell first fails the test, and
   * produces a spike hundreds of metres tall at the mountain front rather than a fan.
   */
  val depositionG: Double = 0.9,

  /** Deposition multiplier for cells under water: a river entering the sea drops its load. Deltas. */
  val marineDeposition: Double = 3.0
) {
  init {
    require(timesteps >= 1) { "timesteps must be at least 1" }
    require(thermalRate in 0.0..0.5) { "thermalRate above 0.5 oscillates, was $thermalRate" }
    require(depositionG >= 0.0) { "depositionG must not be negative" }
  }
}

/**
 * Stage 3: erosion at world scale - stream power incision, mass wasting, and sediment deposition.
 *
 * Consumes [LayerId.BEDROCK_ELEVATION] and produces [LayerId.ERODED_ELEVATION]: the two are separate
 * layers because they are separate things, and because the layer store will not let one stage overwrite
 * another's output.
 *
 * What comes out the far side is **not** the surface the rest of the pipeline means when it says "the land".
 * Ice still has to cut into it, so [LayerId.ELEVATION] belongs to `GlacialStage` and this stage hands it the
 * fluvial surface to carve. The distinction is not bookkeeping: when erosion owned `ELEVATION`, nothing
 * downstream declared glacial, so troughs existed only at chunk-materialisation time and every stage that
 * decides where something sits had already committed to ground the finished chunks then cut away.
 *
 * The three processes each contribute something no amount of noise can fake:
 *
 * - **Stream power** cuts valleys where water flows, in proportion to how much water flows there. This
 *   is what makes drainage networks dendritic and what makes a valley get wider downstream.
 * - **Mass wasting** caps slopes at the talus angle of the local rock. This is what makes hard rock
 *   hold cliffs while soft rock slumps into rounded hills - and it is the reason rock hardness is
 *   tracked at all.
 * - **Deposition** fills what incision excavates, so mountain fronts get alluvial fans and coasts get
 *   deltas rather than the terrain simply going down everywhere.
 *
 * Hydrology and erosion are iterated together, which the architecture document calls for: rivers cut
 * their valleys and the valleys redirect the rivers. Here that loop lives *inside* the stage rather
 * than as an edge in the stage DAG, because a DAG cannot express a fixpoint. Downstream, the hydrology
 * stage solves the network once more on the final surface to get the authoritative river geometry.
 */
class ErosionStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: ErosionParams = ErosionParams()
) : Stage {

  override val id = ID

  // 2: the ocean margin is reapplied after uplift, which was lifting it back above sea level.
  // 3: emits ERODED_ELEVATION; ELEVATION is now the glacial stage's, so ice reaches downstream stages.
  override val version = 3
  override val dependencies = listOf(TectonicsStage.ID, ClimateStage.ID)
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Raster(LayerId.ERODED_ELEVATION),
    StageOutput.Raster(LayerId.SEDIMENT)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val metres = region.resolution.metresPerCell
    val seaLevel = ctx.config.seaLevel

    val elevation = Grid.from(ctx.layers.float(LayerId.BEDROCK_ELEVATION))
    val hardness = Grid.from(ctx.layers.float(LayerId.ROCK_HARDNESS))
    val uplift = Grid.from(ctx.layers.float(LayerId.UPLIFT))
    val precipitation = Grid.resampled(ctx.layers.float(LayerId.PRECIPITATION), region)

    val sediment = Grid(region.width, region.height)
    val eroded = DoubleArray(elevation.size)
    val load = DoubleArray(elevation.size)

    // Source term for flow accumulation: cell area weighted by how wet the cell is, so a river in a
    // monsoon belt carries genuinely more water than one of the same catchment size in a desert.
    val meanPrecipitation = precipitation.mean().coerceAtLeast(1e-6)
    val cellArea = metres * metres
    val source = DoubleArray(elevation.size) {
      cellArea * (precipitation.data[it] / meanPrecipitation).coerceIn(0.05, 6.0)
    }

    repeat(params.timesteps) {
      val network = FlowRouting.solve(elevation, seaLevel, metres)
      val drainage = network.accumulate { source[it] }

      incise(network, drainage, elevation, hardness, uplift, seaLevel, eroded)
      deposit(network, drainage, elevation, sediment, eroded, load, cellArea, seaLevel)
      relax(elevation, hardness, metres)
    }

    /*
     * The ocean margin, put back.
     *
     * Tectonics forces it below sea level, and then this stage adds uplift for every timestep - to *every*
     * cell, including the margin, which has nowhere to drain to and so keeps all of it. Over a couple of
     * hundred timesteps that lifts a strip of the margin back above the waterline, and a world that wraps
     * east to west then has land at the seam a player can stand on and see the world change.
     *
     * Found by registering `Invariants.checkOceanBorderIsOcean`, which had been written and never added to
     * the check list - so the property the architecture document lists as asserted was failing on every seed
     * and nothing was looking. It is a pre-existing bug rather than one belonging to any of the stages added
     * around it, and this is its fix: the same continuous blend, applied to the eroded surface, before
     * hydrology and everything after it sees the layer.
     */
    OceanBorder.of(
      ctx.config, params.oceanBorderDepth, region, metres, region.width, params.oceanBorderWobble
    ).applyTo(elevation, seaLevel)

    return StageResult.of(
      elevation.toLayer(LayerId.ERODED_ELEVATION, region),
      sediment.toLayer(LayerId.SEDIMENT, region)
    )
  }

  /**
   * One implicit stream power step: `dz/dt = U - K A^m S`.
   *
   * Solved for `n = 1` by rearranging into
   * `z_i' = (z_i + dt*U_i + f * z_r') / (1 + f)`, with `f = K dt A^m / L`,
   * and walking the stack downstream-first so the receiver's new elevation `z_r'` is already known.
   *
   * Unconditionally stable, one pass, no sub-stepping. The explicit form would need a timestep small
   * enough for the steepest cell in the world and would take thousands of iterations to do what this
   * does in forty.
   */
  private fun incise(
    network: DrainageNetwork,
    drainage: Grid,
    elevation: Grid,
    hardness: Grid,
    uplift: Grid,
    seaLevel: Double,
    eroded: DoubleArray
  ) {
    val dt = params.timestep

    for (k in network.stack.indices) {
      val i = network.stack[k]
      val before = elevation.data[i]

      if (network.isOutlet(i)) {
        eroded[i] = 0.0
        continue
      }

      val rise = if (network.ocean[i]) 0.0 else uplift.data[i] * dt
      // Ramped rather than switched at sea level, for the same reason as the deposition efficiency.
      val submergence = ((seaLevel - elevation.data[i]) / MARINE_RAMP).coerceIn(0.0, 1.0)
      val erodibility = erodibilityAt(hardness.data[i]) *
          (1.0 - (1.0 - params.marineErodibility) * submergence)

      val length = network.flowLength(i)
      val factor = if (length <= 0.0) {
        0.0
      } else {
        erodibility * dt * drainage.data[i].pow(params.areaExponent) / length
      }

      val receiverElevation = elevation.data[network.receiver[i]]
      val target = (before + rise + factor * receiverElevation) / (1.0 + factor)

      // The implicit form cannot overshoot below the receiver, but it can leave a cell marginally under
      // it through rounding, and a cell below its own receiver is a pit the next fill has to undo.
      elevation.data[i] = if (target < receiverElevation) receiverElevation else target
      eroded[i] = (before + rise - elevation.data[i]).coerceAtLeast(0.0)
    }
  }

  /**
   * Sediment routing: carry what incision loosened downstream and settle it out along the way.
   *
   * Deposition per cell is `G Qs / A` - the sediment flux passing through, divided by the drainage area
   * that flux is spread over. Since flux grows with basin size and so does area, the *rate* stays
   * bounded however large the catchment gets, which is what makes this stable. Where the gradient breaks
   * at a mountain front, incision falls away while flux does not, so deposition wins and builds an
   * alluvial fan; where a river meets the sea it drops everything, and that is a delta.
   *
   * Without any deposition the landscape only ever goes down, and a world of pure incision has no
   * plains in it - just an increasingly deep dissection of the original tectonic surface.
   *
   * The architecture document asks for fans and deltas as *vector polygons* with internal lobe
   * structure. They are raster deposition here: the vector tier has no polygon type yet, and adding one
   * for this would be a subsystem rather than a stage. The consequence is visible - a fan boundary is
   * only as sharp as a kilometre cell - and it is recorded as a known deviation rather than papered over.
   */
  private fun deposit(
    network: DrainageNetwork,
    drainage: Grid,
    elevation: Grid,
    sediment: Grid,
    eroded: DoubleArray,
    load: DoubleArray,
    cellArea: Double,
    seaLevel: Double
  ) {
    java.util.Arrays.fill(load, 0.0)

    for (k in network.stack.indices.reversed()) {
      val i = network.stack[k]

      // Deposition is driven by the load *arriving from upstream*, and this cell's own erosion is added
      // only afterwards. The order is the whole physics of it: material eroded from a cell leaves that
      // cell. Adding it to the load first means a headwater immediately deposits ninety per cent of
      // what it just eroded back onto itself, incision cancels out everywhere the drainage area is
      // small, and the landscape comes out as smooth as it started - with no valley network at all.
      if (load[i] > 0.0) {
        // `load` is volume already divided by cell area, so dividing by the area *ratio* rather than by
        // the area itself keeps the units in metres.
        val areaRatio = (drainage.data[i] / cellArea).coerceAtLeast(1.0)
        val dropped = (efficiencyAt(elevation.data[i], seaLevel) * load[i] / areaRatio)
          .coerceAtMost(load[i])

        elevation.data[i] += dropped
        sediment.data[i] += dropped
        load[i] -= dropped
      }

      load[i] += eroded[i]

      val receiver = network.receiver[i]
      if (receiver != i) load[receiver] += load[i]
    }
  }

  /**
   * Deposition efficiency, raised under water so that a river entering the sea drops its load and builds
   * a delta.
   *
   * A ramp over the first few tens of metres of depth rather than a test against sea level. A hard switch
   * puts a discontinuity in the erosion *rate* along the entire shoreline, and forty timesteps of that
   * carve a ring of trench and berm around every coast in the world - a landform made by a comparison
   * operator, which is worse than no landform.
   */
  private fun efficiencyAt(elevation: Double, seaLevel: Double): Double {
    val submergence = ((seaLevel - elevation) / MARINE_RAMP).coerceIn(0.0, 1.0)
    return params.depositionG * (1.0 + (params.marineDeposition - 1.0) * submergence)
  }

  /**
   * Thermal erosion: wherever a slope exceeds what the local rock can stand, move material down it.
   *
   * Double buffered, so the result does not depend on the order cells are visited in - which for a
   * relaxation is the difference between a deterministic stage and one whose output depends on how the
   * loop was written.
   */
  private fun relax(elevation: Grid, hardness: Grid, metresPerCell: Double) {
    if (params.thermalIterations <= 0) return

    val delta = DoubleArray(elevation.size)

    repeat(params.thermalIterations) {
      java.util.Arrays.fill(delta, 0.0)

      for (y in 1 until elevation.height - 1) {
        for (x in 1 until elevation.width - 1) {
          val i = elevation.index(x, y)
          val here = elevation.data[i]
          val talus = params.talusSoft + (params.talusHard - params.talusSoft) * hardness.data[i]

          for (d in 0 until 8) {
            val j = elevation.index(x + D8.DX[d], y + D8.DY[d])
            val run = D8.LENGTH[d] * metresPerCell
            val slope = (here - elevation.data[j]) / run
            if (slope <= talus) continue

            // Enough material to bring this pair back to the talus angle, damped and shared out over
            // the eight directions so a cliff top does not empty into one neighbour.
            val excess = (slope - talus) * run
            val move = excess * params.thermalRate / 8.0
            delta[i] -= move
            delta[j] += move
          }
        }
      }

      for (i in delta.indices) elevation.data[i] += delta[i]
    }
  }

  /**
   * Erodibility from hardness.
   *
   * Superlinear on purpose: the interesting landscapes come from the *contrast* between adjacent rock
   * types, and a linear mapping makes soft rock only twice as erodible as hard rock, which is not enough
   * for a resistant bed to hold a waterfall or a mesa to keep its cap.
   *
   * But only so superlinear. The offset and exponent give a spread of about sixteen between the softest
   * mudstone and the hardest granite, which lets hard rock hold roughly four times the slope of soft rock
   * at the same uplift. A steeper curve - `(1.05 - h)^1.7`, which spreads by a hundred - makes the hardest
   * rock effectively immune, and an immune cell under active uplift simply rises until mass wasting stops
   * it, so orogens turn into plateaus at the talus angle.
   */
  private fun erodibilityAt(hardness: Double): Double =
    params.erodibility * (1.15 - hardness).coerceAtLeast(0.05).pow(1.5)

  companion object {
    val ID = StageId("erosion")

    /** Depth in metres over which marine conditions take over from terrestrial ones. */
    private const val MARINE_RAMP = 80.0
  }
}
