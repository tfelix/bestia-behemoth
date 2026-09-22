package net.bestia.worldgen.hydro

import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.BaseHeightField
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.IntGrid
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.fields.Tables
import net.bestia.worldgen.geo.DetailParams
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.geo.GlacialStage
import net.bestia.worldgen.geo.TectonicsStage
import net.bestia.worldgen.geo.WorldHeightField
import net.bestia.worldgen.vector.FeatureEvaluator
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.LinearFeatures
import net.bestia.worldgen.vector.PointFeature
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.vector.RadialProfiles
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Tuning for [HydrologyStage]. */
data class HydrologyParams(

  /** Fraction of precipitation that becomes runoff rather than evaporating or recharging aquifers. */
  val runoffCoefficient: Double = 0.34,

  /**
   * Catchment area in square metres at which a cell starts carrying a channel, at mean rainfall.
   *
   * A *catchment area* rather than a discharge, and that is the fix for small worlds rather than a refactor of
   * one. Channel initiation is scale-free in nature - drainage density is roughly constant, and real channels
   * begin after well under a square kilometre - so expressing the threshold as an area makes it mean the same
   * thing on a world of any size. The equivalent discharge is derived from this and the world's own mean
   * rainfall, which is what keeps the arid-versus-humid distinction that [aridityExponent] exists for.
   *
   * Written as an absolute discharge, as it was, the threshold quietly encoded "and the world is four thousand
   * kilometres across": a 128 km world has catchments a fiftieth of the size, so nothing reached it and the
   * world came out with two rivers. Scaled by [WorldConfig.scaleByArea], so a small world's detail scale brings
   * it down further still.
   *
   * Raised from 93 million when the world became land-dominated. This is the knob for **how many** rivers, and
   * with half a world of land rather than a quarter the same threshold produced a great many of them - and,
   * because a bigger threshold takes longer to reach, produced them short: the network only lit up in the last
   * stretch before the sea, which reads on the map as a comb of little coastal streams rather than as rivers.
   * Fewer, larger catchments is what leaves room for trunks.
   *
   * It is *only* the count. Where a river starts is [channelSlopeExponent]'s question and how far it runs is
   * [aridityExponent]'s, and confusing the three costs a tuning cycle each time: the network keeps its shape
   * across the whole usable range of this number and only thins.
   *
   * **It is not absolute, and it has to be re-measured whenever the climate moves.** The threshold is a
   * *discharge* derived from this area and the world's mean rainfall, so a uniform change in rainfall cancels -
   * but a change in how rainfall is *distributed* does not. Flattening the model's absurd rain shadows took
   * this world from mostly-bone-dry to broadly damp, which raised the [aridityExponent] multiplier on hardly
   * any cells any more, and the same 110 million that had given 106 channels gave 531. Hence 420 million: the
   * count is back to 140 and there are now 52 confluences rather than 14, which is the network being genuinely
   * dendritic rather than a set of separate coastal streams.
   */
  val channelCatchmentArea: Double = 420_000_000.0,

  /**
   * How strongly the channel threshold rises in dry regions.
   *
   * Scaling the threshold by local rainfall rather than using one figure everywhere is what gives an
   * arid region sparse drainage. With a fixed threshold a desert gets the same dense dendritic network
   * as a rainforest, only with less water in it, and the map stops distinguishing them.
   *
   * This is the knob for **how long** a river is, which is not obvious and is why it was the one to move. It is
   * an exponent on a ratio, so at 1.0 an interior receiving a third of the mean rainfall needs three times the
   * catchment before it carries a channel - and on a continental world, where the interior *is* dry, that
   * pushes every channel head down towards the coast and leaves the uplands with no drainage drawn at all.
   * At 0.55 a dry interior still gets a sparser network than a wet coast, which is the whole point of the
   * term, but a river that rises in the mountains is still drawn as rising in the mountains.
   */
  val aridityExponent: Double = 0.55,

  /**
   * How strongly the channel threshold falls on steep ground. Zero restores the area-only threshold.
   *
   * This is the slope-area law for channel initiation - Montgomery and Dietrich's `A * S^n > constant` - and
   * without it the threshold is an area alone, which is wrong in the one way that shows. A hillside sheds its
   * water into a defined channel after a few hectares; a floodplain of the same catchment carries no channel
   * at all, because there is no gradient to cut one. Ignoring that put every channel head on the coastal plain
   * - the only place a purely area-based threshold is ever reached first - and the map came out as combs of
   * short parallel streams running straight off the shore, with the uplands they should have risen in blank.
   *
   * The ratio it is applied to is against **this world's own mean land slope**, not against a constant, for the
   * same reason [channelCatchmentArea] is converted using this world's own mean rainfall: it makes the term a
   * redistribution rather than a discount. A fixed reference slope has to be either above or below a given
   * world's typical ground, and whichever it is, it moves every threshold on the map in that direction and
   * silently becomes a second control on how many rivers there are. Measured: a reference of 0.03 on a world
   * whose land averages nearly three times that took the river count from 93 to 270 while barely moving where
   * the heads sat, which is the wrong axis entirely.
   *
   * The literature puts the exponent near 2 for debris-flow-dominated heads. That is measured at metres, not at
   * kilometre cells where a slope is already an average over a thousand metres of ground, so the spread here
   * would be enormous - hence 1.0 and a hard clamp rather than the textbook figure.
   */
  val channelSlopeExponent: Double = 1.0,

  /**
   * Largest factor the slope term may move the threshold, either way.
   *
   * A clamp rather than a taper because the tails are where this misbehaves: a flat lake bed approaches zero
   * slope and would demand an infinite catchment, and a cliff face would carry a channel from its first cell.
   */
  val channelSlopeRange: Double = 4.0,

  /**
   * Metres of channel that must lie upstream of a cell before it is drawn at all, before world scaling.
   *
   * **The anti-comb knob.** See [RiverNetwork.trimHeadwaters] for the mechanism and the measurement. In
   * short: the herringbone is not a routing artefact and cannot be removed by perturbing the routing or by
   * raising the discharge threshold - it is genuine drainage on genuine ground, drawn at a scale that should
   * not be showing it, and the fix is to stop drawing the fingertips.
   *
   * This is the knob for **how fine** the drawn network is, which is a third axis alongside
   * [channelCatchmentArea]'s how-many and [aridityExponent]'s how-long. Raising it shortens every river a
   * little and deletes the short ones entirely; it never thins a trunk, because a trunk has kilometres of
   * channel above it before the trim reaches anything that matters.
   */
  val minHeadwaterLength: Double = 12_000.0,

  /** Metres of water evaporated from a lake surface per year. Decides which basins are salt lakes. */
  val evaporationDepth: Double = 1.1,

  /** Hydraulic geometry: `width = a * Q^0.5`. */
  val widthCoefficient: Double = 4.2,

  /** Hydraulic geometry: `depth = b * Q^0.4`. */
  val depthCoefficient: Double = 0.36,

  /** Floodplain half-width coefficient: `shoulder = c * Q^0.35`. */
  val shoulderCoefficient: Double = 24.0,

  /**
   * Narrowest and shallowest channel worth cutting, in voxels. See [ChannelGauge] for why these exist.
   *
   * In voxels rather than metres because what they defend against is the grid's resolution, not anything about
   * water: the hydraulic geometry above is correct and still produces channels this pipeline cannot draw.
   */
  val minChannelWidthVoxels: Double = 3.0,
  val minChannelDepthVoxels: Double = 2.0,

  /**
   * Station and vertex spacing along a river centerline, in metres.
   *
   * Also the finest meander the geometry can hold, which is what really sets it: at 1 km cells the
   * *path* carries no information below about 500 m, but the meander is added after smoothing and wants
   * a wavelength of a few hundred metres to look like a river rather than a bent pipe.
   *
   * Sixty, not the hundred and twenty it was. `Polyline.offsetLaterally` moves *vertices*, so a
   * wavelength below about three vertex spacings is a zigzag rather than a curve - which is what
   * [MIN_WAVELENGTH_STATIONS] guards, and halving this is what lets the geometry hold the meander
   * [meanderBeltFactor] now asks for. It costs one more station per sixty metres of channel: about
   * 400 kB of station table on a 128 km world, against a world tier measured in hundreds of megabytes.
   */
  val stationSpacing: Double = 60.0,

  /**
   * Meander amplitude as a fraction of the meander wavelength, before slope confinement.
   *
   * A fraction of the wavelength rather than a multiple of the channel width, and that swap is the whole
   * of the fix for rivers that came out straight. Sinuosity is dimensionless, so the knob that sets it
   * has to be too - and width is the one quantity that cannot set it here, because [ChannelGauge]'s
   * floor pins most of the network to exactly three voxels wide. Amplitude tied to width therefore gave
   * every river in the world the same gentle wiggle whatever its size.
   *
   * **Above one, and that is not a geometry error.** For `y = A sin(2 pi x / L)` sinuosity is about
   * `1 + (pi A / L)^2`, so a ratio near 0.3 ought to give 1.5 and this ought to be a number well under
   * one. It is not, because [Meander.offset] blends two octaves of *gradient* noise and gradient noise
   * spends almost none of its time near its own bound: measured on the reference world, the realised
   * swing is about **0.17** of the stated amplitude, not the half a first estimate assumed. So this is a
   * scale factor on a noise field rather than a literal amplitude-to-wavelength ratio, and the only honest
   * way to set it is to move it and read `ProbeMain --channels` back.
   *
   * The measurements it was set from, on the genesis world at 1 km cells:
   *
   * | ratio | median sinuosity | reaches under 1.05 |
   * |---|---|---|
   * | 0.30 | 1.023 | 62.5% |
   * | 0.65 | 1.056 | 43.9% |
   * | 1.10 | 1.093 | 29.3% |
   *
   * A real lowland river runs 1.2 to 1.5 and this is still under that, deliberately: the reaches here are
   * kilometres long and a player sees a hundred metres of one, where a median of 1.09 is already some
   * forty metres of lateral swing over a six-hundred-metre bend. Pushing to a textbook figure would be
   * tuning a statistic rather than a view.
   *
   * ### What actually bounds it, and what does not
   *
   * There is **no amplitude-to-wavelength bound**, and it is worth writing that down because it is an
   * easy and plausible mistake: `y = A sin(2 pi x / L)` is a simple curve for every `A`, and offsetting a
   * *straight* line by it can never make the result cross itself however large `A` grows. A first pass at
   * this file asserted `A <= 0.21 L` on a parallel-curve cusp argument and was wrong twice over - the
   * argument is about offsetting a curve by a *constant*, which is not what happens here, and the bound
   * it produced rejected this field's own default, so `WorldParams.DEFAULT` did not survive its own
   * `require`.
   *
   * What does bound it is the **base line's own curvature**. `Polyline.offsetLaterally` displaces each
   * vertex along the local normal of the line it is given, so pushing inward past that line's centre of
   * curvature folds the result. The limit is therefore `amplitude < radius of curvature of the smoothed
   * D8 path`, which is a property of that path rather than a ratio of the meander, and cannot be checked
   * from this value alone. The smoothed path carries radii in the hundreds of metres at 1 km cells;
   * [meanderAmplitudeCap] and the slope confinement are what hold the amplitude under them.
   *
   * A fold is not a crash if one ever happens: `Polyline.project` is a nearest-segment search and stays a
   * pure function of position, so the seam guarantee holds. It would show as a step in the bed and the
   * water surface where the station parameter jumps across the crossing.
   */
  val meanderAmplitudeRatio: Double = 1.10,

  /**
   * How strongly longitudinal slope suppresses wandering. At a slope of `1/this` the amplitude is halved.
   *
   * Promoted out of a private constant in [Meander], where it reached no `ParamsDigest` at all and could
   * therefore be retuned without moving a single version number.
   *
   * Sixty, against the fifty it was, and **measured rather than guessed**. The first attempt put it at 110
   * on the reasoning that the amplitude is now several times larger so the same confinement would leave too
   * much swing. That overshot: `ProbeMain --channels` puts the median bed gradient at 8.5 m/km, so at 110
   * the *median* river in the world sat at a confinement of 0.52 - every ordinary reach half-suppressed,
   * when the term is meant to single out the steep ones. Sixty leaves the median at 0.66 and still takes a
   * genuinely steep headwater at 0.05 down to 0.25, which is the shape that was wanted: straight in the
   * valleys they have cut, wandering on the flats.
   */
  val meanderConfinement: Double = 60.0,

  /**
   * Bend tightness - `curvature * width` - at which half of [thalwegOffset] is reached.
   *
   * Promoted out of [Profiles.ChannelShape]'s own default for [meanderConfinement]'s reason: the
   * construction site let it default, so it reached no digest.
   *
   * **Deliberately not the 0.3 that `ChannelShape`'s KDoc suggests.** That advice assumed tightening the
   * meanders would lift bend tightness into the 0.3-0.5 band a real meander apex sits in. It does not,
   * because tightness is `curvature * width` and the *width* is unchanged: peak curvature of an
   * amplitude-`A`, wavelength-`L` sinusoid is `A (2 pi / L)^2`, which takes the reference 14 m channel
   * from 0.085 to about 0.10-0.12 - not to 0.3. Setting 0.3 here would put every bend in the world back
   * down in the flat foot of the response and *weaken* the thalweg offset, which is the opposite of what
   * that paragraph was reaching for.
   */
  val meanderBendScale: Double = 0.12,

  /** Ceiling on meander amplitude in metres, so a trunk river cannot wander off its own floodplain. */
  val meanderAmplitudeCap: Double = 700.0,

  /**
   * Meander wavelength as a multiple of the *floodplain half-width*, not of the channel width.
   *
   * The textbook law is `L = 11 w`, which is what this was and what its old name said. That law is right
   * and unusable here: [ChannelGauge] produces 3-13 m channels, so it asks for a 33-143 m wavelength -
   * below what a centreline derived from 1 km cells can carry, and below what reads as a meander at a
   * metre per voxel. Worse, it never once beat the `stationSpacing * 3` floor beside it, so **every
   * river in the world came out with an identical 360 m wavelength**.
   *
   * [ChannelGauge.shoulderOf] is the floodplain half-width, which is the belt a river actually wanders
   * in, and it spans 3-41 m across the network where the width spans 3-13. Anchoring here gives a real
   * per-reach spread: roughly 300 m on a headwater creek to 1,070 m on the largest trunk.
   *
   * The trade is [ChannelGauge]'s own, stated in its KDoc and applying unchanged: below the grid's
   * resolution the choice is not between accurate and inaccurate, it is between visible and absent.
   */
  val meanderBeltFactor: Double = 26.0,

  /**
   * How far the deepest line of the channel sits off centre on a full bend, as a fraction of the half-width.
   *
   * The cure for the extruded look. Written on `abs(lateral)`, as it was, every cross-section of every river
   * in the world is exactly symmetric for its whole length - which no channel cut by water has ever been.
   * See [Profiles.ChannelShape] for how the parabola is stretched rather than translated, and why the point
   * bar that falls out of it can stand above the water as a gravel bank.
   *
   * Zero restores the symmetric parabola exactly, including skipping the curvature station channel.
   */
  val thalwegOffset: Double = 0.45,

  /**
   * How far the bank and bed wander in and out, as a fraction of the channel's own width.
   *
   * A fraction rather than metres because the floor in [ChannelGauge] means the narrowest channels are 3 m
   * wide: half a metre of wobble is a third of the half-width there and a rounding error on a trunk, so an
   * absolute figure cannot be right for both. Zero disables the noise.
   */
  val bankRoughness: Double = 0.12,

  /**
   * Wavelength of that wander, as a multiple of channel width.
   *
   * Below about half a width the wobble stops reading as an irregular bank and starts reading as static;
   * well above one width it reads as the channel changing size rather than as a ragged edge.
   */
  val bankRoughnessWavelength: Double = 0.8,

  /**
   * Reaches shorter than this in metres are dropped: they are single-cell stubs at drainage divides.
   *
   * Applied twice - once to the routed reach and again to what is left of one whose mouth was cut back to
   * the shoreline, which is what takes out the coastal stubs that are almost entirely seaward of it. On the
   * reference world that is six of forty-six.
   */
  val minReachLength: Double = 700.0,

  /** Confluence smoothing disc radius, as a multiple of the joined channel's width. */
  val confluenceRadiusFactor: Double = 1.7,

  /**
   * Least distance outside the wetted edge that the bed must clear, when the corridor is probed.
   *
   * A floor rather than the whole answer, because what it has to clear scales with the channel: the
   * profile's bank wobble is [bankRoughness] of the width, so on a 33 m trunk the real bank moves in and
   * out by four metres where on a 3 m creek it moves by a third of one. See `corridorReach`, which takes
   * the larger of this and the wobble.
   *
   * It was a flat four metres at first, which held on every 128 km world and failed on a 600 km one - the
   * widest channels there run past 30 m and the wobble past four, so the bed was measured over ground that
   * stopped short of its own bank. The bank invariant found it; no unit test could have.
   */
  val bankContainment: Double = 4.0,

  /**
   * Metres the bank top is set below the lowest ground sampled across the corridor.
   *
   * Covers the ground *between* the [bedProbeSamples] lateral probes: they span some twenty metres
   * against a detail wavelength of 340 m, so ground between two of them cannot be far below either. A
   * third of a voxel is also under the rounding the fill rule already applies.
   */
  val bedFreeboard: Double = 0.35,

  /**
   * Share of the floodplain half-width the bed is graded to. See `corridorReach`.
   *
   * Under one because the shoulder eases back to the terrain over its whole width, so its outer reaches
   * are the surrounding land rather than the valley floor, and grading to those would cut a trench. Well
   * over a half so the probe reaches past whatever the channel itself is standing on.
   */
  val floodplainShare: Double = 0.65,

  /** Least lateral samples per station when probing the corridor. Odd, so one lands on the centreline. */
  val bedProbeSamples: Int = 5,

  /**
   * Widest gap allowed between two lateral corridor samples, in metres.
   *
   * What actually decides the sample count on anything but the narrowest channel. Four metres is under the
   * shortest wavelength `WorldHeightField` can produce, so nothing the detail noise does can hide between
   * two samples; what used to hide there was the edge of a valley.
   */
  val bedProbeGap: Double = 4.0,

  /**
   * Fraction of the channel depth left between the water surface and the bank top.
   *
   * Moved here out of a private constant in `RiverWaterSampler`, because the water surface is now a
   * station channel this stage decides rather than something the chunk tier re-derives. A channel runs
   * nearly full rather than brim full: a river level with its banks is a river in flood, and every river
   * in the world being in flood reads as a mistake.
   */
  val channelFreeboard: Double = 0.25,

  /**
   * Share by which the channel opens out **above** its mean width, along its own length.
   *
   * The pool-and-riffle term, and the answer to a width that is a pure function of discharge: discharge
   * only changes at a confluence, so a reach between two junctions came out the same width to the metre
   * for its whole length.
   *
   * **One-sided, upward only, and that is the whole reason it is safe.** Multiplying symmetrically would
   * take a floored 3 m channel down to 2 m - under the three voxels of wetted width
   * [minChannelWidthVoxels] exists to guarantee - and would reintroduce exactly the dashed-line-of-water
   * flicker [ChannelGauge] documents at length. Upward only: 3 m becomes 3.0-4.65 m, 9 m becomes 9-14 m.
   *
   * This is *not* the invented size gradient [ChannelGauge]'s KDoc argues against. That argument is about
   * how big a river is, and it is right - depth cannot express it. Pool-riffle structure is a different
   * claim: along-channel rather than between-channel, present in every alluvial river, and saying nothing
   * about the river's size.
   */
  val channelWidthVariation: Double = 0.55,

  /** Wavelength of that opening out, as a multiple of the meander wavelength. One riffle per half bend. */
  val channelWidthWavelength: Double = 0.60,

  /**
   * Extra depth in the pools, as a share of the riffle depth.
   *
   * Driven by the *inverse* of the width term, because a pool is deep and narrow where a riffle is wide
   * and shallow. That is what makes the two read as one structure rather than two unrelated noises. The
   * water surface is unaffected: it is computed from the unvaried gauge depth, so a pool deepens the bed
   * under a level surface instead of dipping the surface itself.
   */
  val poolDepthShare: Double = 0.40,

  /**
   * The chunk tier's detail noise, because this stage now cuts its channels into the surface a chunk
   * will actually build.
   *
   * Held for the reason [PondParams] holds it, and with a sharper edge. The bed used to come from the
   * coarse depression-filled raster while the chunk tier lifts that raster *plus* up to 15-24 m of
   * analytic detail - against a channel 2 m deep. Wherever the detail put the real ground below the
   * coarse bed there was nothing holding the water in, and the river materialised as a slab standing
   * above its own banks. Forwarded by `WorldParams.resolved`, not settable from a params file.
   */
  val detail: DetailParams = DetailParams()
) : Params {
  init {
    require(runoffCoefficient in 0.0..1.0) { "runoffCoefficient must be in [0,1]" }
    require(channelCatchmentArea > 0.0) { "channelCatchmentArea must be positive" }
    require(stationSpacing > 0.0) { "stationSpacing must be positive" }
    require(minChannelWidthVoxels >= 0.0 && minChannelDepthVoxels >= 0.0) {
      "channel gauge floors must not be negative"
    }
    require(aridityExponent.isFinite()) { "aridityExponent must be finite, was $aridityExponent" }
    require(channelSlopeExponent.isFinite()) {
      "channelSlopeExponent must be finite, was $channelSlopeExponent"
    }
    // A factor applied either way, so below 1 it would invert into a *narrowing* of the threshold band and
    // the clamp its KDoc describes would stop being a clamp.
    require(channelSlopeRange >= 1.0) { "channelSlopeRange must be at least 1, was $channelSlopeRange" }
    require(minHeadwaterLength >= 0.0) {
      "minHeadwaterLength must not be negative, was $minHeadwaterLength"
    }
    require(evaporationDepth >= 0.0) { "evaporationDepth must not be negative, was $evaporationDepth" }
    require(widthCoefficient > 0.0) { "widthCoefficient must be positive, was $widthCoefficient" }
    require(depthCoefficient > 0.0) { "depthCoefficient must be positive, was $depthCoefficient" }
    require(shoulderCoefficient >= 0.0) { "shoulderCoefficient must not be negative, was $shoulderCoefficient" }
    require(meanderAmplitudeRatio >= 0.0) {
      "meanderAmplitudeRatio must not be negative, was $meanderAmplitudeRatio"
    }
    require(meanderConfinement >= 0.0) {
      "meanderConfinement must not be negative, was $meanderConfinement"
    }
    require(meanderBendScale > 0.0) { "meanderBendScale must be positive, was $meanderBendScale" }
    require(meanderAmplitudeCap >= 0.0) { "meanderAmplitudeCap must not be negative, was $meanderAmplitudeCap" }
    // A multiplier on the floodplain half-width, and a zero wavelength is an infinite-frequency sine
    // along every river in the world.
    require(meanderBeltFactor > 0.0) {
      "meanderBeltFactor must be positive, was $meanderBeltFactor"
    }
    // Above 0.9 the thalweg reaches the bank and the parabola on that side collapses to zero span; the
    // profile clamps anyway, but a value that can only mean "as far as allowed" is better refused here.
    require(thalwegOffset in 0.0..0.9) { "thalwegOffset must be in [0,0.9], was $thalwegOffset" }
    require(bankRoughness >= 0.0) { "bankRoughness must not be negative, was $bankRoughness" }
    require(bankRoughnessWavelength > 0.0) {
      "bankRoughnessWavelength must be positive, was $bankRoughnessWavelength"
    }
    require(minReachLength >= 0.0) { "minReachLength must not be negative, was $minReachLength" }
    require(confluenceRadiusFactor >= 0.0) {
      "confluenceRadiusFactor must not be negative, was $confluenceRadiusFactor"
    }
    require(bankContainment >= 0.0) { "bankContainment must not be negative, was $bankContainment" }
    require(bedFreeboard >= 0.0) { "bedFreeboard must not be negative, was $bedFreeboard" }
    // Odd and at least three: one probe on the centreline and one on each bank is the least that can
    // describe a corridor, and an even count has no centre sample at all.
    require(bedProbeSamples >= 3 && bedProbeSamples % 2 == 1) {
      "bedProbeSamples must be odd and at least three, was $bedProbeSamples"
    }
    require(bedProbeGap > 0.0) { "bedProbeGap must be positive, was $bedProbeGap" }
    require(floodplainShare > 0.0) { "floodplainShare must be positive, was $floodplainShare" }
    require(channelFreeboard in 0.0..1.0) { "channelFreeboard must be in [0,1], was $channelFreeboard" }
    require(channelWidthVariation >= 0.0) {
      "channelWidthVariation must not be negative, was $channelWidthVariation"
    }
    require(channelWidthWavelength > 0.0) {
      "channelWidthWavelength must be positive, was $channelWidthWavelength"
    }
    require(poolDepthShare >= 0.0) { "poolDepthShare must not be negative, was $poolDepthShare" }
  }



  override fun digest() = ParamsDigest()
    .put("runoffCoefficient", runoffCoefficient)
    .put("channelCatchmentArea", channelCatchmentArea)
    .put("aridityExponent", aridityExponent)
    .put("channelSlopeExponent", channelSlopeExponent)
    .put("channelSlopeRange", channelSlopeRange)
    .put("minHeadwaterLength", minHeadwaterLength)
    .put("evaporationDepth", evaporationDepth)
    .put("widthCoefficient", widthCoefficient)
    .put("depthCoefficient", depthCoefficient)
    .put("shoulderCoefficient", shoulderCoefficient)
    .put("minChannelWidthVoxels", minChannelWidthVoxels)
    .put("minChannelDepthVoxels", minChannelDepthVoxels)
    .put("stationSpacing", stationSpacing)
    .put("meanderAmplitudeRatio", meanderAmplitudeRatio)
    .put("meanderConfinement", meanderConfinement)
    .put("meanderBendScale", meanderBendScale)
    .put("meanderAmplitudeCap", meanderAmplitudeCap)
    .put("meanderBeltFactor", meanderBeltFactor)
    .put("thalwegOffset", thalwegOffset)
    .put("bankRoughness", bankRoughness)
    .put("bankRoughnessWavelength", bankRoughnessWavelength)
    .put("minReachLength", minReachLength)
    .put("confluenceRadiusFactor", confluenceRadiusFactor)
    .put("bankContainment", bankContainment)
    .put("bedFreeboard", bedFreeboard)
    .put("bedProbeSamples", bedProbeSamples)
    .put("bedProbeGap", bedProbeGap)
    .put("floodplainShare", floodplainShare)
    .put("channelFreeboard", channelFreeboard)
    .put("channelWidthVariation", channelWidthVariation)
    .put("channelWidthWavelength", channelWidthWavelength)
    .put("poolDepthShare", poolDepthShare)
    .nested("detail", detail.digest().value)
}

/**
 * Stage 4: hydrology. Depression filling, flow routing, lakes, and the river network as vector features.
 *
 * Runs on the eroded surface, so the rivers are in the valleys erosion cut rather than in the valleys
 * the pre-erosion tectonic surface happened to have. Erosion solved its own drainage network on the way
 * there; this solves it once more on the final surface, and that solution is the authoritative one.
 *
 * The output that matters most is not a raster. It is a set of [FeatureKind.RIVER_CHANNEL] features:
 * continuous, resolution-independent centerlines with per-station width, depth and bed elevation, which
 * a chunk two hundred kilometres away can sample and get a channel that lines up with its neighbour's
 * to the millimetre.
 */
class HydrologyStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: HydrologyParams = HydrologyParams()
) : Stage {

  override val id = ID

  /**
   * 2: the bed is cut into the detailed chunk-tier surface rather than into the kilometre raster, the
   * water surface became a station channel of its own, and the meander is anchored to the floodplain
   * instead of to the channel width. All three change what this stage *computes*, not merely what it is
   * tuned to - which is what this number is for, and why retuning alone must never move it.
   */
  override val version = 2

  override val paramsVersion get() = params.digest().value

  /**
   * Note **glacial**, which is what makes a post-glacial river run down the trough it should have inherited.
   *
   * Before it, glacial and hydrology were siblings that neither ordered nor could see one another - they ran
   * in the right order only because the topological sort breaks ties on stage name and `"glacial"` sorts
   * first, which is an alphabetical accident standing where a dependency belongs. Declaring it turns that
   * accident into a guarantee, and lets this stage read the surface ice actually left.
   *
   * It also feeds every stage below: dependency scoping is transitive, so habitability, settlement placement
   * and town layout all reach glacial through this one edge and stop deciding things on ground that is not
   * there.
   */
  override val dependencies =
    listOf(TectonicsStage.ID, ClimateStage.ID, ErosionStage.ID, GlacialStage.ID)
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Raster(LayerId.FLOW_DIRECTION),
    StageOutput.Raster(LayerId.FLOW_ACCUMULATION),
    StageOutput.Raster(LayerId.DISCHARGE),
    StageOutput.Raster(LayerId.WATER_LEVEL),
    StageOutput.Raster(LayerId.LAKE_ID),
    StageOutput.Vector(FeatureKind.RIVER_CHANNEL),
    StageOutput.Vector(FeatureKind.RIVER_CONFLUENCE)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val metres = region.resolution.metresPerCell
    val cellArea = metres * metres
    val seaLevel = ctx.config.seaLevel

    val surface = ctx.layers.float(LayerId.ELEVATION)
    val elevation = Grid.from(surface)
    val precipitation = Grid.resampled(ctx.layers.float(LayerId.PRECIPITATION), region)

    val network = FlowRouting.solve(elevation, seaLevel, metres)

    val drainageArea = network.accumulate { cellArea }
    val discharge = network.accumulate { runoffAt(precipitation.data[it], cellArea) }

    val lakes = Lakes.identify(
      network = network,
      elevation = elevation,
      discharge = discharge,
      seaLevel = seaLevel,
      evaporationDepth = params.evaporationDepth
    )

    val meanPrecipitation = precipitation.mean().coerceAtLeast(1.0)
    // The discharge that the threshold catchment area yields at this world's mean rainfall. Derived rather than
    // configured, so the same number means the same thing on a world of any size or wetness.
    val channelDischarge = runoffAt(
      meanPrecipitation,
      ctx.config.scaleByArea(params.channelCatchmentArea)
    ).coerceAtLeast(MIN_CHANNEL_DISCHARGE)

    // Both modifiers on the threshold are ratios against this world's own mean, so the base threshold keeps
    // meaning "the catchment a channel needs on ordinary ground under ordinary rain" and each term only says
    // how far from ordinary a cell is. Neither can move the river count on its own; that stays
    // channelCatchmentArea's job.
    val slope = landSlopes(elevation, region, metres, seaLevel)
    val meanSlope = meanOverLand(slope, elevation, seaLevel)
    val slopeFloor = 1.0 / params.channelSlopeRange

    val graph = RiverNetwork.extract(
      network = network,
      discharge = discharge,
      lakeId = lakes.lakeId,
      minHeadwaterLength = ctx.config.scaleByLength(params.minHeadwaterLength)
    ) { i ->
      // Higher threshold where it is drier, so the network thins out towards the deserts.
      val aridity =
        (meanPrecipitation / precipitation.data[i].coerceAtLeast(1.0)).pow(params.aridityExponent)

      // Lower threshold where it is steep, so channels rise in the mountains rather than on the plain.
      val steepness = (meanSlope / max(slope.data[i], MIN_SLOPE))
        .pow(params.channelSlopeExponent)
        .coerceIn(slopeFloor, params.channelSlopeRange)

      channelDischarge * aridity * steepness
    }

    // The surface a chunk will actually build, which is the only surface a channel may be cut into.
    // Built exactly as `PondStage` and `civ/TownStage` build their own, and for the same reason:
    // `assemble` has not run, so `GeneratedWorld.base` does not exist yet. Both layers are in this
    // stage's declared closure already - ELEVATION through glacial, ROCK_HARDNESS through tectonics -
    // so this costs no new dependency edge.
    val ground: BaseHeightField = WorldHeightField(
      elevation = surface,
      hardness = ctx.layers.float(LayerId.ROCK_HARDNESS),
      seed = ctx.config.seed,
      seaLevel = ctx.config.seaLevel,
      params = params.detail
    )

    val features = buildFeatures(ctx, region, surface, ground, network, discharge, graph)

    val direction = IntGrid(region.width, region.height, network.direction.copyOf())

    return StageResult(
      layers = listOf(
        direction.toLayer(LayerId.FLOW_DIRECTION, region),
        drainageArea.toLayer(LayerId.FLOW_ACCUMULATION, region),
        discharge.toLayer(LayerId.DISCHARGE, region),
        lakes.surface.toLayer(LayerId.WATER_LEVEL, region),
        lakes.lakeId.toLayer(LayerId.LAKE_ID, region)
      ),
      features = features
    )
  }

  /**
   * Ground slope everywhere, with everything below sea level flattened to it.
   *
   * [Grid.gradient] is the wrong instrument for channel initiation because at kilometre cells the steepest
   * ground in the world is the shoreline: a cell of coast beside a cell of shelf at -400 m reads as a slope of
   * 0.4, steeper than any mountain front the erosion model produces. Fed that, the slope term does the exact
   * opposite of its purpose - it makes the coast the *easiest* place in the world to start a channel, and the
   * map fills with combs of parallel streams a few cells long hanging off every shore.
   *
   * Clamping the neighbours at sea level asks the question that was meant: how steep is the land here. A
   * channel head is a subaerial feature, and what is under the water offshore has nothing to do with it.
   */
  private fun landSlopes(elevation: Grid, region: CellRegion, metres: Double, seaLevel: Double): Grid {
    fun dry(x: Int, y: Int) = max(seaLevel, elevation[x, y])

    return Grid(region.width, region.height) { x, y ->
      val dzdx = (dry(x + 1, y) - dry(x - 1, y)) / (2.0 * metres)
      val dzdy = (dry(x, y + 1) - dry(x, y - 1)) / (2.0 * metres)
      sqrt(dzdx * dzdx + dzdy * dzdy)
    }
  }

  /**
   * Mean of [slope] over the cells that are above sea level, or over everything on a world with no land.
   *
   * Land only, because the sea floor is most of a half-water world and it is nearly flat once the shoreline
   * scarp has been clamped out. Averaging it in would drag the reference far below any real hillside and turn
   * a redistribution back into a discount.
   */
  private fun meanOverLand(slope: Grid, elevation: Grid, seaLevel: Double): Double {
    var sum = 0.0
    var count = 0
    for (i in slope.data.indices) {
      if (elevation.data[i] <= seaLevel) continue
      sum += slope.data[i]
      count++
    }
    return if (count == 0) slope.mean().coerceAtLeast(MIN_SLOPE) else (sum / count).coerceAtLeast(MIN_SLOPE)
  }

  /** Runoff from one cell, in cubic metres per second. */
  private fun runoffAt(precipitationMillimetres: Double, cellArea: Double): Double =
    max(0.0, precipitationMillimetres) / 1000.0 * params.runoffCoefficient * cellArea /
        Lakes.SECONDS_PER_YEAR

  /**
   * Turns each reach of the graph into a vector feature, and each confluence into a smoothing disc.
   *
   * Three transformations take the D8 path to a river:
   *
   * 1. **Corner cutting** removes the staircase. A D8 path only ever goes in eight directions, and left
   *    as it is the channel reads as a canal cut by someone with a set square.
   * 2. **Meandering** adds sinuosity, tapered to zero at both ends so the reach still meets its
   *    neighbours exactly.
   * 3. **Station attributes** are interpolated from the per-cell tables by *normalised position along
   *    the reach*, which stays a pure function of arc length - the requirement that makes the whole
   *    thing seam-free.
   *
   * A fourth then takes the mouth back off again where the reach ran into the sea - see [shoreCrossing].
   */
  private fun buildFeatures(
    ctx: GenContext,
    region: CellRegion,
    surface: FloatLayer,
    ground: BaseHeightField,
    network: DrainageNetwork,
    discharge: Grid,
    graph: RiverGraph
  ): List<VectorFeature> {
    val metres = region.resolution.metresPerCell
    val seaLevel = ctx.config.seaLevel
    val nextId = FeatureIds.allocator(id)
    val features = ArrayList<VectorFeature>(graph.reachCount + graph.confluences.size)

    // The one place the world's voxel size reaches into hydrology: a channel the grid cannot hold is a channel
    // that renders as a dashed line rather than as a river. See ChannelGauge.
    val gauge = ChannelGauge(params, ctx.config.voxelSize)

    fun centreOf(cell: Int) = Vec2d(
      (region.minX + cell % region.width + 0.5) * metres,
      (region.minY + cell / region.width + 0.5) * metres
    )

    for (reach in graph.reaches) {
      val raw = runCatching { Polyline(reach.cells.map(::centreOf)) }.getOrNull() ?: continue
      if (raw.length < params.minReachLength) continue

      val cellCount = reach.cells.size

      // The coarse filled surface, kept for exactly one purpose now: the reach-wide longitudinal slope
      // the meander amplitude is confined by. That is a mean over kilometres and wants no sub-cell
      // fidelity, and the fill descends monotonically along every D8 path by construction, which makes
      // it the right surface to measure a gradient against.
      //
      // What it is no longer used for is the bed itself. It is a *kilometre* raster, and the chunk tier
      // lifts it plus up to 15-24 m of analytic detail - against a channel two metres deep. Wherever the
      // detail put the real ground below this surface there was nothing holding the water in, and the
      // river materialised as a slab of water standing above its own banks. See the corridor probe below.
      val coarseBed = DoubleArray(cellCount)
      var falling = Double.MAX_VALUE
      for (k in 0 until cellCount) {
        falling = min(falling, network.filled.data[reach.cells[k]])
        coarseBed[k] = falling
      }

      val flow = DoubleArray(cellCount) { discharge.data[reach.cells[it]] }

      val meanFlow = flow.average()
      val meanWidth = gauge.widthOf(meanFlow)
      val meanSlope = (coarseBed.first() - coarseBed.last()) / raw.length

      // The belt a river wanders in is its floodplain, not its own wetted width - see `meanderBeltFactor`.
      val belt = gauge.shoulderOf(meanFlow)
      val wavelength = max(
        params.stationSpacing * MIN_WAVELENGTH_STATIONS,
        belt * params.meanderBeltFactor
      )
      val amplitude = Meander.amplitudeFor(
        wavelength,
        meanSlope,
        params.meanderAmplitudeRatio,
        params.meanderConfinement,
        params.meanderAmplitudeCap
      )

      val fine = raw.chaikin(SMOOTHING_PASSES).resample(params.stationSpacing)
      val meanderSeed = GenRng.hash(ctx.seed, id.hash, reach.id.toLong())

      // One wavelength is the shortest distance a meander can fade to zero over without becoming the
      // kink `Meander` calls not optional, and the ceiling keeps a middle on a short reach. The old floor
      // of `minReachLength * 0.35` was 245 m against a 360 m wavelength - it flattened a whole meander at
      // each end of every short reach, which is most of them.
      val taper = min(
        fine.length * MAX_TAPER_FRACTION,
        max(wavelength * TAPER_WAVELENGTHS, fine.length * END_TAPER_FRACTION)
      )

      val meandered = fine.offsetLaterally { s ->
        Meander.offset(meanderSeed, s, fine.length, amplitude, wavelength, taper)
      }

      // Only a reach that ends in the sea: cutting one that ends at a confluence would pull it off the
      // reach below, and the length floor is re-applied here rather than earlier because what is left after
      // the cut is what decides whether this is still a river or a smudge on the shoreline.
      val trimmed = if (reach.endsInSea) {
        val mouth = shoreCrossing(meandered, surface, seaLevel)
        if (mouth < params.minReachLength) continue
        meandered.truncatedTo(mouth)
      } else {
        meandered
      }

      // Resampled once, here, and handed to the feature exactly as it stands. Everything below measures
      // against this line, and `resample` is not idempotent - see `LinearFeatures.river`'s `preResampled`.
      // Letting the factory resample again would cut the channel into a line a shade away from the one
      // whose corridor was probed, which is the whole of what the probe is for.
      val line = trimmed.resample(params.stationSpacing)
      if (line.vertexCount < 2) continue

      // Normalised position along the reach, which is what the per-cell tables are indexed by. Using the
      // pre-meander, pre-trim length is deliberate: it is a fixed number rather than one that shifts with
      // the offset being computed, so the mapping stays a pure function of arc length - and a trimmed
      // mouth then lands part-way through the tables instead of stretching them over a shorter channel.
      val span = fine.length.coerceAtLeast(1e-9)
      fun positionOf(s: Double) = (s / span).coerceIn(0.0, 1.0) * (cellCount - 1)
      fun flowAt(s: Double) = Tables.linear(flow, positionOf(s))

      // Pool and riffle. Discharge only changes at a confluence, so a reach between two junctions came
      // out the same width to the metre for its whole length; this is the term that gives it structure.
      // One-sided and upward only - see `channelWidthVariation` for why a symmetric one would take the
      // narrowest channels back under the floor that stops them flickering.
      val riffleSeed = GenRng.hash(ctx.seed, id.hash, reach.id.toLong(), POOL_SEED_SALT)
      val pulse = max(params.stationSpacing * 4.0, wavelength * params.channelWidthWavelength)
      fun riffle(s: Double) =
        (0.5 + 0.5 * Noise.gradient2d(riffleSeed, s / pulse, POOL_PHASE)).coerceIn(0.0, 1.0)

      // Width and shoulder share one factor, for `roadFeature`'s reason: a way that opens out opens out
      // entire, and separate fields would give a channel widest where its floodplain is narrowest. Depth
      // takes the *inverse*, because a pool is deep and narrow where a riffle is wide and shallow - which
      // is what makes the two read as one structure rather than as two unrelated noises.
      fun widthAt(s: Double) =
        gauge.widthOf(flowAt(s)) * (1.0 + params.channelWidthVariation * riffle(s))
      fun shoulderAt(s: Double) =
        gauge.shoulderOf(flowAt(s)) * (1.0 + params.channelWidthVariation * riffle(s))
      fun depthAt(s: Double) =
        gauge.depthOf(flowAt(s)) * (1.0 + params.poolDepthShare * (1.0 - riffle(s)))

      // The bed, cut into the ground a chunk will actually build rather than into a kilometre raster.
      //
      // The running minimum keeps `checkRiverBedsDescend` true over a surface that is not itself
      // monotone, and it is forced rather than chosen: under "descends **and** stays under the ground"
      // it is the largest sequence that qualifies. Where the detailed ground rises against the flow the
      // bed holds level, which is a pool - physically right rather than a workaround. Incision is bounded
      // by twice the local relief, and on a floodplain `WorldHeightField`'s roughness term collapses to a
      // fraction of its amplitude, so that is metres; in hills the river cuts a gorge, which is correct.
      // Against the ground **as already carved**, not against the bare heightfield.
      //
      // The base field is the analytic surface before any vector feature touches it, and by the time
      // hydrology runs the glacial stage has cut troughs, fjords and cirques into it that are tens of
      // metres deep. Probing the bare field put the bed above the floor of every trough a river runs
      // along - which is the same defect as reading the kilometre raster, arrived at from the other side,
      // and the bank invariant caught it on eleven of twelve sweep worlds.
      //
      // `ctx.features` is this stage's scoped view, so what it returns is exactly the features hydrology
      // is allowed to see: everything from tectonics through glacial, and nothing this stage or a later
      // one has produced. Features that arrive afterwards - oxbows, ponds, alluvium, roads, town grading -
      // are out of reach by construction, and are the invariant's business to skip rather than this one's.
      val carved = FeatureEvaluator(
        ctx.features.query(line.bbox.expanded(gauge.shoulderOf(meanFlow) + params.bankContainment + metres))
      )

      val floor = DoubleArray(line.vertexCount) { k ->
        val s = line.arcLengthAt(k)
        corridorFloor(ground, carved, line, s, corridorReach(widthAt(s), shoulderAt(s))) -
            params.bedFreeboard
      }
      val bed = descendingBed(floor, line)

      // Indexed by *normalised* arc length rather than by station count, so the chord shortening a
      // resample leaves cannot walk the last station off the end of the array.
      val bedSpan = line.length.coerceAtLeast(1e-9)
      fun bedAt(s: Double) = Tables.linear(bed, (s / bedSpan).coerceIn(0.0, 1.0) * (bed.size - 1))

      // Unit stream power, `omega = gamma Q S / w` in W/m2, which is what decides whether a reach can
      // move gravel, only sand, or nothing at all. Stored as a continuous quantity rather than as a
      // material code because the station table interpolates with Catmull-Rom, and a spline through
      // discrete class codes overshoots past the end classes and smears every boundary. It also keeps
      // the palette decision in `voxel/`, where the other cover thresholds already live.
      //
      // Measured over a window rather than per station, and that is load-bearing: the running minimum
      // above leaves exactly flat stretches wherever it holds, and a per-station slope reads zero across
      // every one of them - which would class the whole reach as silt.
      fun bedSlopeAt(s: Double): Double {
        val window = params.stationSpacing * SLOPE_WINDOW_STATIONS
        val lo = (s - window).coerceAtLeast(0.0)
        val hi = (s + window).coerceAtMost(bedSpan)
        val run = hi - lo
        return if (run <= 0.0) 0.0 else ((bedAt(lo) - bedAt(hi)) / run).coerceAtLeast(0.0)
      }

      // Both terms scale off the reach's own width, so a creek and a trunk get the same channel *character*
      // rather than the same number of metres of it. The wavelength floor is four voxels: below that the
      // wobble is finer than the grid can draw and turns into noise on the bank rather than a shape.
      val shape = Profiles.ChannelShape(
        thalwegOffset = params.thalwegOffset,
        bendScale = params.meanderBendScale,
        roughness = meanWidth * params.bankRoughness,
        roughnessWavelength = max(
          ctx.config.voxelSize * 4.0,
          meanWidth * params.bankRoughnessWavelength
        ),
        seed = GenRng.hash(ctx.seed, id.hash, reach.id.toLong(), BANK_SEED_SALT)
      )

      features.add(
        LinearFeatures.river(
          id = nextId(),
          centerline = line,
          shape = shape,
          stationSpacing = params.stationSpacing,
          preResampled = true,
          bedElevation = { s -> bedAt(s) },
          width = { s -> widthAt(s) },
          depth = { s -> depthAt(s) },
          shoulder = { s -> shoulderAt(s) },
          // The *unvaried* gauge depth, deliberately: a pool deepens the bed under a level surface
          // instead of dipping the surface itself, which is what a river does and what a varying depth
          // here would undo.
          waterElevation = { s -> bedAt(s) - gauge.depthOf(flowAt(s)) * params.channelFreeboard },
          streamPower = { s -> WATER_SPECIFIC_WEIGHT * flowAt(s) * bedSlopeAt(s) / widthAt(s) }
        )
      )
    }

    for (cell in graph.confluences) {
      val flow = discharge.data[cell]
      val width = gauge.widthOf(flow)
      val depth = gauge.depthOf(flow)
      val radius = max(params.stationSpacing, width * params.confluenceRadiusFactor)

      // Against the same detailed ground the reaches are now cut into, not against the coarse raster.
      // With the reaches typically below the filled surface, a bowl floored off `network.filled` would
      // stand as a hump in the middle of every junction - which is the crease this bowl exists to
      // remove, inverted. Probed radially rather than across a corridor, because a confluence has no
      // single direction.
      val centre = centreOf(cell)
      var low = Double.MAX_VALUE
      for (j in 0 until CONFLUENCE_PROBES) {
        val angle = 2.0 * PI * j / CONFLUENCE_PROBES
        val at = centre + Vec2d(cos(angle), sin(angle)) * corridorReach(width, gauge.shoulderOf(flow))
        low = min(low, ground.heightAt(at.x, at.y))
      }
      val floor = low - params.bedFreeboard - depth

      features.add(
        PointFeature(
          id = nextId(),
          kind = FeatureKind.RIVER_CONFLUENCE,
          center = centre,
          radius = radius,
          // A shallow bowl whose rim reaches back up to the bank top. Stamped above both reaches, so it
          // replaces the crease that `min` of two parabolic channels leaves along the bisector of the Y.
          profile = RadialProfiles.bowl(floor, depth, radius, exponent = 2.0)
        )
      )
    }

    return features
  }

  /**
   * The corridor floor made to descend, without the flat stretches a running minimum leaves.
   *
   * A running minimum is the obvious way to satisfy `checkRiverBedsDescend` against a surface that is not
   * itself monotone, and it was the first thing here. It is also wrong in a way that shows: it **holds**
   * its last value across every rise, so on a detailed surface the bed comes out as a staircase of long
   * level pounds. Measured on the reference world, 38% of all channel length had a gradient under
   * 0.01 m/km - which `ProbeMain` reports, in those words, as *reads as a canal*. The old code did not
   * have the problem only because it ran over the depression-filled raster, which descends by construction,
   * so the minimum never actually latched.
   *
   * So the envelope is kept and then **interpolated across**: between two stations where it genuinely
   * touches the corridor floor, the bed runs straight from one to the other instead of sitting flat and
   * then stepping. That is still monotonically descending, because the touch points are, and it is still
   * under the ground the whole way - between two touches the floor is at or above the earlier touch's
   * value, and a line falling from that value to a lower one stays under it. The cost is a slightly deeper
   * cut mid-span, which is a pool between two riffles and reads as a river rather than as a lock.
   *
   * The run after the last touch has nothing to aim at and stays level, which is the mouth.
   */
  private fun descendingBed(floor: DoubleArray, line: Polyline): DoubleArray {
    val bed = DoubleArray(floor.size)
    if (floor.isEmpty()) return bed

    // The lower envelope, and the stations where it is the floor itself rather than a memory of one.
    val touches = ArrayList<Int>()
    var running = Double.MAX_VALUE
    for (k in floor.indices) {
      if (floor[k] <= running) {
        running = floor[k]
        touches.add(k)
      }
      bed[k] = running
    }

    for (t in 0 until touches.size - 1) {
      val from = touches[t]
      val to = touches[t + 1]
      if (to - from < 2) continue

      val startS = line.arcLengthAt(from)
      val span = line.arcLengthAt(to) - startS
      if (span <= 0.0) continue

      for (k in from + 1 until to) {
        val f = (line.arcLengthAt(k) - startS) / span
        bed[k] = bed[from] + (bed[to] - bed[from]) * f
      }
    }

    return bed
  }

  /**
   * How far either side of the centreline the corridor is probed, for a channel of a given width.
   *
   * Half the width, plus the largest of three things it has to clear: [HydrologyParams.bankContainment],
   * the profile's own bank wobble with a margin, and a share of the floodplain.
   *
   * The wobble is what the probe must get outside of - inside it a sample lands on the carved bed and
   * reports it as bank, which is the bed measuring itself. The floodplain term is what stops a river being
   * *perched*: measured over a few metres either side, a channel crossing a moraine finds only the ridge
   * it is standing on and grades its bed to the crest, leaving the water twenty metres above ground a
   * stone's throw away. A river grades to the land it drains, not to the ridge it happens to cross, and
   * `shoulderOf` is already that land - the floodplain half-width.
   */
  private fun corridorReach(width: Double, shoulder: Double): Double =
    width * 0.5 + max(
      max(params.bankContainment, width * params.bankRoughness * BANK_WOBBLE_CLEARANCE),
      shoulder * params.floodplainShare
    )

  /**
   * Lowest ground across the channel corridor at one station.
   *
   * A **lower envelope** rather than a single centreline sample, and that is what makes the running
   * minimum above behave. Sampling only the centre would let a bank sitting in a detail-noise dip stand
   * below the bed that was derived beside it - which is precisely the failure this whole probe exists to
   * remove, merely moved from the kilometre scale to the metre one. Taking the minimum across the
   * corridor also varies less from station to station than a centre sample would, so the running minimum
   * has less to latch onto and incises less.
   *
   * [reach] is the half-width probed either side: the wetted half-width plus [HydrologyParams.bankContainment],
   * so the outermost samples land outside the bank rather than inside it.
   */
  private fun corridorFloor(
    ground: BaseHeightField,
    carved: FeatureEvaluator,
    line: Polyline,
    s: Double,
    reach: Double
  ): Double {
    val at = line.pointAt(s)
    val normal = line.tangentAt(s).perpendicular()

    // Enough samples that no two are further apart than `bedProbeGap`, and never fewer than
    // `bedProbeSamples`. A fixed count is wrong for a corridor whose width spans an order of magnitude:
    // five across a 3 m creek is dense, and five across a 44 m trunk leaves eleven-metre holes through
    // which a valley edge falls unseen - which is exactly how a 600 km world came back with a river
    // carrying its water twenty metres over ground the probe had stepped straight past.
    val span = reach * 2.0
    val needed = ceil(span / params.bedProbeGap).toInt() + 1
    val samples = max(params.bedProbeSamples, if (needed % 2 == 0) needed + 1 else needed)

    var low = Double.MAX_VALUE
    for (j in 0 until samples) {
      // -1 at one bank, +1 at the other, and exactly 0 on the centreline because the count is odd.
      val t = -1.0 + 2.0 * j / (samples - 1)
      val p = at + normal * (t * reach)
      low = min(low, carved.heightAt(p.x, p.y, ground.heightAt(p.x, p.y)))
    }

    return low
  }

  /**
   * Arc length at which [line] last leaves dry land, or its whole length if it never does.
   *
   * ### Why a reach has to be cut back at all
   *
   * A reach ends at the cell it flows into, and [Reach.cells] includes that cell - which is what makes two
   * reaches meet exactly at their confluence. At the coast there is nothing on the other side to meet, so
   * that last cell is simply open water, and the channel is drawn, carved and filled up to a whole cell
   * beyond the shore. On a kilometre grid that was measured at a median of 600 m and up to 1.3 km of river
   * lying over the sea.
   *
   * ### Why the cut is against the interpolated surface and not the cell mask
   *
   * Routing classifies a *cell* as ocean or not, but nothing downstream draws that classification: the map
   * inks the zero crossing of the bicubically sampled elevation and chunk generation lifts the same field,
   * precisely so the shoreline resolves finer than a kilometre. Cutting at the cell boundary would still
   * leave the mouth on the wrong side of the line everyone else sees - by up to half a cell in either
   * direction, since the crossing sits wherever the two cells' heights say it does. So this asks the same
   * question the renderer asks, at the same field.
   *
   * Walking back from the end rather than forward from the start finds the *last* crossing, so a channel
   * running out along a spit is cut where it finally enters the sea and not where it first touched it.
   */
  private fun shoreCrossing(line: Polyline, surface: FloatLayer, seaLevel: Double): Double {
    fun heightAbove(point: Vec2d): Double {
      return surface.sampleBicubic(point.x, point.y) - seaLevel
    }

    var last = line.vertexCount - 1
    while (last >= 0 && heightAbove(line.points[last]) <= 0.0) {
      last--
    }

    if (last < 0) return 0.0
    if (last == line.vertexCount - 1) return line.length

    // Linear between the two straddling vertices. They are one station apart against a field whose finest
    // feature is a kilometre cell, so the field is straight over that span to well under a metre.
    val dry = heightAbove(line.points[last])
    val wet = heightAbove(line.points[last + 1])
    val t = dry / (dry - wet)

    return line.arcLengthAt(last) + t * (line.arcLengthAt(last + 1) - line.arcLengthAt(last))
  }

  companion object {
    val ID = StageId("hydrology")

    /**
     * Floor on the derived channel threshold, in cubic metres per second.
     *
     * A world that is both tiny and arid can derive a threshold so low that every cell qualifies, and a raster
     * where every cell is a river is not a drainage network - it is a flooded plain with a graph over it, and
     * the feature count that comes out of it will exhaust memory before anybody looks at it.
     */
    const val MIN_CHANNEL_DISCHARGE = 0.02

    /** Slope floor for the channel-initiation term, so a dead-flat cell cannot divide by zero. */
    private const val MIN_SLOPE = 1e-4

    /** Keeps a reach's bank roughness independent of its meander, which is seeded from the same reach id. */
    private const val BANK_SEED_SALT = 0x42414e4bL

    private const val SMOOTHING_PASSES = 2

    /**
     * Stations per meander wavelength, below which the offset geometry stops being a curve.
     *
     * `Polyline.offsetLaterally` moves vertices, so a wavelength spanning fewer than about this many of
     * them comes out as a zigzag. Five rather than the three it was, because the amplitude is now large
     * enough that an under-resolved bend reads as a kink rather than as a slightly coarse curve.
     */
    private const val MIN_WAVELENGTH_STATIONS = 5.0

    /** Ceiling on the end taper as a share of reach length, so a short reach keeps a middle. */
    private const val MAX_TAPER_FRACTION = 0.45

    /**
     * End taper as a multiple of the meander wavelength.
     *
     * Half a wavelength, and it was a whole one until the sinuosity was actually measured. The taper runs
     * from *both* ends, so a full wavelength against a reach of two or three costs most of the meander in
     * the very reaches short enough to need it - median sinuosity came back at 1.05 where the amplitude
     * asked for far more. Half a bend is enough to fade the offset to zero without the kink `Meander`
     * calls not optional, and it leaves a middle for the bend to happen in.
     */
    private const val TAPER_WAVELENGTHS = 0.5

    /** Its own salt, so the pool-riffle pulse does not land on the bank wobble or the meander. */
    private const val POOL_SEED_SALT = 0x504f4f4cL

    /** Arbitrary but fixed second coordinate, so the 2D noise field reads as one dimensional. */
    private const val POOL_PHASE = 0.61

    /** Radial probes around a confluence. Eight is enough to find the bank whichever way the Y points. */
    private const val CONFLUENCE_PROBES = 8

    /**
     * Margin on the bank wobble that the corridor probe must clear. See `corridorReach`.
     *
     * Above one so the probe lands outside the wobble rather than on its crest, and above the share the
     * bank invariant probes at, so the ground it checks is ground this stage actually measured.
     */
    private const val BANK_WOBBLE_CLEARANCE = 1.5

    /** Stations either side of a station over which the bed slope is measured. See `bedSlopeAt`. */
    private const val SLOPE_WINDOW_STATIONS = 5.0

    /** Specific weight of water in N/m3, for unit stream power. */
    private const val WATER_SPECIFIC_WEIGHT = 9810.0


    /** Fraction of a reach's length at each end over which the meander fades out. */
    private const val END_TAPER_FRACTION = 0.16
  }
}

