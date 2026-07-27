package net.bestia.worldgen.core

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Makes a finite world behave as though it had no edge.
 *
 * A player walking east off the eastern edge arrives from the west, so every coordinate the server holds has
 * more than one spelling: `x` and `x + worldWidth` are the same place. Everything that compares two positions
 * has to agree on which spelling to use, and on how far apart two places are when the short way round crosses
 * the seam.
 *
 * Both are one-liners and both are wrong by default. `x % width` returns a negative for a negative `x`, which
 * puts a player who steps west off zero somewhere outside the world instead of at its far edge. And a plain
 * `b - a` for two points either side of the seam gives nearly the world's width when the real distance is a few
 * metres - which in an interest-management query means two players standing next to each other cannot see one
 * another, and in a pathfinder means the route goes the long way round the planet.
 *
 * Terrain is *not* continuous across the seam: the world is generated on a finite grid, and the east edge knows
 * nothing about the west. What makes the seam invisible is [WorldConfig.oceanBorderMetres] forcing both sides
 * below sea level, so the wrap happens between two stretches of open water. This class is the coordinate half
 * of that arrangement; the ocean is the visual half, and neither works without the other.
 */
class WorldWrap(private val config: WorldConfig) {

  val width: Double get() = config.widthMetres
  val height: Double get() = config.heightMetres

  val wrapsX: Boolean get() = config.wrapX
  val wrapsY: Boolean get() = config.wrapY

  val chunksAcross: Int get() = Math.ceil(width / config.chunkExtent).toInt()
  val chunksDown: Int get() = Math.ceil(height / config.chunkExtent).toInt()

  /** The canonical spelling of a world position: inside the world on any wrapped axis. */
  fun normalise(x: Double, y: Double): Vec2 = Vec2(normaliseX(x), normaliseY(y))

  fun normaliseX(x: Double): Double = if (config.wrapX) floorMod(x, width) else x

  fun normaliseY(y: Double): Double = if (config.wrapY) floorMod(y, height) else y

  /**
   * The canonical chunk for a possibly out-of-range chunk coordinate.
   *
   * What a chunk request from a client goes through before it reaches the generator, so that asking for the
   * chunk one past the eastern edge returns the westernmost one rather than a column of empty air off the side
   * of the raster. The vertical coordinate is never wrapped: up is not a loop.
   */
  fun normalise(chunk: ChunkPos): ChunkPos = ChunkPos(
    if (config.wrapX) Math.floorMod(chunk.x, chunksAcross) else chunk.x,
    if (config.wrapY) Math.floorMod(chunk.y, chunksDown) else chunk.y,
    chunk.z
  )

  /**
   * Signed displacement from [fromX] to [toX], taking whichever way round is shorter.
   *
   * The value to use for anything directional - a facing, a velocity, a step towards a target - because it
   * says *which way* as well as how far.
   */
  fun deltaX(fromX: Double, toX: Double): Double = shortest(toX - fromX, config.wrapX, width)

  fun deltaY(fromY: Double, toY: Double): Double = shortest(toY - fromY, config.wrapY, height)

  /** Straight-line distance, going round the seam if that is shorter. */
  fun distance(fromX: Double, fromY: Double, toX: Double, toY: Double): Double =
    hypot(deltaX(fromX, toX), deltaY(fromY, toY))

  /**
   * Whether two points are within [range] of each other, seam included.
   *
   * Squared internally, so an interest-management query does not pay for a square root per candidate.
   */
  fun isWithin(range: Double, fromX: Double, fromY: Double, toX: Double, toY: Double): Boolean {
    val dx = deltaX(fromX, toX)
    val dy = deltaY(fromY, toY)
    return dx * dx + dy * dy <= range * range
  }

  /**
   * How far a position is from the nearest world edge, in metres.
   *
   * Not wrapped, deliberately: this asks about the edge of the *grid*, which is where the ocean margin is and
   * where the raster runs out. A wrapped axis has no edge to be near as far as a player is concerned, but the
   * generator still has one.
   */
  fun distanceToEdge(x: Double, y: Double): Double =
    minOf(x, width - x, y, height - y)

  /** Whether a position is inside the forced ocean margin. */
  fun isInOceanBorder(x: Double, y: Double): Boolean =
    config.oceanBorderMetres > 0.0 && distanceToEdge(x, y) < config.oceanBorderMetres

  data class Vec2(val x: Double, val y: Double)

  private fun shortest(delta: Double, wraps: Boolean, extent: Double): Double {
    if (!wraps) return delta

    // Fold into (-extent/2, extent/2]. Two points 90% of the world apart the long way are 10% apart the short
    // way, and the short way is the one a player would walk.
    val folded = floorMod(delta + extent * 0.5, extent) - extent * 0.5
    // Exactly antipodal is a tie; return the positive one so the answer is at least deterministic rather than
    // depending on which way the floating point happened to fall.
    return if (abs(folded) == extent * 0.5) extent * 0.5 else folded
  }

  private fun floorMod(value: Double, extent: Double): Double {
    val remainder = value % extent
    return if (remainder < 0.0) remainder + extent else remainder
  }
}
