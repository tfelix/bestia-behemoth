package net.bestia.worldgen.hydro

import kotlin.math.max
import kotlin.math.pow

/**
 * How wide and how deep a channel is for a given discharge, floored so the voxel grid can hold it.
 *
 * ### Why a floor exists at all
 *
 * The hydraulic geometry is right. `width = 4.2 Q^0.5` and `depth = 0.36 Q^0.4` are the standard downstream
 * relations, and the channels they produce are the correct size for the water in them. The trouble is that they
 * are the correct size for *rivers*, and most of a drainage network is not a river - it is headwater creeks a
 * hand's breadth deep. `depth = 0.36 Q^0.4` needs `Q` of about 13 cubic metres a second before it reaches one
 * metre, and nothing in a world of a few hundred kilometres carries that.
 *
 * Measured, before this floor existed: on a 512 km world - the reference size, detail scale exactly one, nothing
 * scaled - *every* channel station in the world came out shallower than one voxel. Median depth 32 cm, and the
 * single deepest river anywhere was 91 cm. At 128 km it was a median of 15 cm.
 *
 * ### What that looked like
 *
 * Not "shallow rivers". Rivers that flicker. `ChunkMaterializer` writes a water voxel only where the water
 * surface actually crosses a voxel boundary, so a channel 15 cm deep gets water in the columns where its bed
 * happens to sit just below a boundary and no water at all in the columns where it does not - and since the bed
 * descends continuously along the reach, that alternates. The client draws a dashed line of water instead of a
 * river, and a dashed line of water on sand is what "strange green strokes on the ground" was.
 *
 * ### The floor
 *
 * So a channel is given at least the gauge a voxel grid can represent. This is unphysical in the same deliberate
 * way [net.bestia.worldgen.core.WorldConfig.detailScale] is: a creek carrying two litres a second does not have
 * a two-metre bed. It is the same trade as drawing a one-lane road two voxels wide - below the grid's resolution
 * the choice is not between accurate and inaccurate, it is between visible and absent.
 *
 * The depth floor is set from what the *water* needs rather than from what the channel needs, because
 * [net.bestia.worldgen.voxel.RiverWaterSampler] fills a channel to `(1 - FREEBOARD)` of its depth: at a floor of
 * two voxels the water is one and a half, which spans a whole voxel wherever the bed happens to fall.
 *
 * ### Width carries river size; depth no longer does
 *
 * Measured after flooring: the depth floor binds at *every* station in every world tested, because the physical
 * depth never reaches two metres anywhere. So depth is now effectively a constant and it is **width** that
 * expresses how big a river is - 3 m to 9 m on a 128 km world, 3 m to 13 m on a 512 km one.
 *
 * That is the honest outcome rather than a shortfall. Real channels run 0.1 m to 0.9 m deep across the entire
 * size range this pipeline produces, which is *less than one voxel of spread* - the grid has no room to express
 * river size vertically, whatever formula is used. Manufacturing a gradient by adding an invented term above the
 * floor would produce variation the hydraulics do not support, and 13 voxels of real spread in width is already
 * the axis a player reads a river's size from.
 *
 * Two consequences worth stating rather than leaving to be discovered:
 *
 * - At a metre per voxel there is no such thing as a shallow brook. Every channel is deep enough to swim in, and
 *   `AgentProfile.maxWadeDepth` of one metre means agents will wade none of them. A creek you have to swim is
 *   strange; a creek that flickers in and out of existence along its own length is worse.
 * - The smallest channels are 3 m wide and 2 m deep, which is a ditch rather than a creek. Widening the floor
 *   would fix the cross-section and cost the drainage network its fine tributaries, which are the reason the
 *   threshold is scale-free in the first place.
 */
class ChannelGauge(
  private val params: HydrologyParams,
  private val voxelSize: Double
) {

  /** Narrowest channel worth cutting, in metres. Three voxels, so the wetted width covers three columns. */
  private val minWidth = voxelSize * params.minChannelWidthVoxels

  /** Shallowest channel worth cutting, in metres. See the class note on why this is set from the water. */
  private val minDepth = voxelSize * params.minChannelDepthVoxels

  /** Wetted width in metres from discharge in cubic metres per second. */
  fun widthOf(discharge: Double): Double =
    max(minWidth, params.widthCoefficient * discharge.coerceAtLeast(0.0).pow(0.5))

  fun depthOf(discharge: Double): Double =
    max(minDepth, params.depthCoefficient * discharge.coerceAtLeast(0.0).pow(0.4))

  /**
   * Floodplain half-width in metres.
   *
   * Already the larger of the two terms, so it inherits the width floor for free - a channel widened to the
   * minimum gauge gets a shoulder at least as wide, rather than a bank that ends inside its own bed.
   */
  fun shoulderOf(discharge: Double): Double =
    max(widthOf(discharge), params.shoulderCoefficient * discharge.coerceAtLeast(0.0).pow(0.35))
}
