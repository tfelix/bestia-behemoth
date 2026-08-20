package net.bestia.zone.cartography.tile

import net.bestia.worldgen.render.Viewport
import net.bestia.worldgen.vector.Aabb
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/**
 * One tile of the map pyramid: a level and a position on that level's grid.
 *
 * ### The ladder is anchored at the fine end
 *
 * Level `L` is `2^L` metres per pixel, so L0 is always one metre per pixel and a tile always spans
 * `[TILE_PIXELS] * 2^L` metres. That is the opposite of the usual web-map convention, where level 0 is the
 * whole world in one tile and everything below is derived from it - and the difference matters here because
 * the world's size is a configuration value. Genesis is 128 km across and a shipped world might be 4096;
 * anchoring at the coarse end would mean the same ground had a different level number in each, so a client's
 * cached tiles, a bake on disk and a zoom setting would all be meaningless across a resize.
 *
 * Anchored at the fine end, L3 is 8 m per pixel in every world that will ever exist. What changes with world
 * size is only how many levels there are *above* the finest, which is exactly the thing that should change.
 *
 * ### Tile coordinates run with the world, not with the screen
 *
 * `ty` increases northward, like world y and unlike a screen row. Every other option was worse: matching the
 * screen means the y axis flips somewhere between the URL and the viewport, and that flip is the kind of bug
 * that survives review because an upside-down map still looks like a map.
 */
data class TileId(val level: Int, val tx: Long, val ty: Long) {

  init {
    require(level >= 0) { "level must not be negative, was $level" }
  }

  val metresPerPixel: Double get() = 2.0.pow(level)

  /** Metres along one edge. */
  val span: Double get() = TILE_PIXELS * metresPerPixel

  val bounds: Aabb
    get() = Aabb(tx * span, ty * span, (tx + 1) * span, (ty + 1) * span)

  /**
   * The viewport that renders exactly this tile.
   *
   * The centre is the tile's own centre, so `Viewport.minX` comes back to `tx * span` exactly - which is what
   * makes [net.bestia.zone.cartography.render.Parchment]'s paper lattice line up between neighbours.
   */
  fun viewport(): Viewport = Viewport(
    centerX = (tx + 0.5) * span,
    centerY = (ty + 0.5) * span,
    metresPerPixel = metresPerPixel,
    widthPx = TILE_PIXELS,
    heightPx = TILE_PIXELS
  )

  /**
   * The tile one level coarser that contains this one, or null at [MAX_LEVEL].
   *
   * Bounded by the arithmetic ceiling and not by the world's own coarsest level, because a [TileId] does not
   * know how big the world is - `fitLevel` is the world's business. A caller walking up the pyramid has to
   * stop at its own fit level; this only guarantees the span cannot overflow.
   */
  fun parent(): TileId? =
    if (level >= MAX_LEVEL) null else TileId(level + 1, floorDiv2(tx), floorDiv2(ty))

  /** The four tiles one level finer that this one covers. */
  fun children(): List<TileId> =
    if (level == 0) emptyList()
    else listOf(
      TileId(level - 1, tx * 2, ty * 2),
      TileId(level - 1, tx * 2 + 1, ty * 2),
      TileId(level - 1, tx * 2, ty * 2 + 1),
      TileId(level - 1, tx * 2 + 1, ty * 2 + 1)
    )

  /** Stable path segment, zero-padded so a directory listing sorts the way a person expects. */
  fun path(): String = "L%02d/%06d/%06d".format(level, tx, ty)

  override fun toString() = "L$level/$tx/$ty"

  companion object {

    /**
     * Tile edge in pixels.
     *
     * 256 rather than 512: the fog mask granularity, the survey cell and the tile all have to agree, and 256
     * is a whole number of `SurveyGrid.CELL_METRES` cells at every level - sixteen of them across an L0 tile,
     * one across an L4 tile, and a fraction of one above that, which is where the mask starts sampling per
     * pixel instead. A tile edge is therefore always a cell boundary, so a tile and its neighbour read the
     * same bits for the ground between them rather than each rounding it their own way.
     */
    const val TILE_PIXELS = 256

    /**
     * A ceiling on the level number, so a malformed request cannot ask for a tile whose span overflows.
     *
     * 30 is 1 073 741 824 metres per pixel, about eight times the diameter of Jupiter. No world will reach it,
     * which is the point: it exists to bound the arithmetic, not to describe a limit anyone will meet.
     */
    const val MAX_LEVEL = 30

    /** The level at which a world of this size fits in a single tile: the coarsest one worth serving. */
    fun fitLevel(worldMetres: Double): Int {
      require(worldMetres > 0.0) { "worldMetres must be positive, was $worldMetres" }
      return ceil(ln(worldMetres / TILE_PIXELS) / ln(2.0)).toInt().coerceIn(0, MAX_LEVEL)
    }

    /** How many tiles a world of this size needs along one edge at [level]. */
    fun tilesAcross(worldMetres: Double, level: Int): Long {
      val span = TILE_PIXELS * 2.0.pow(level)
      return ceil(worldMetres / span).toLong().coerceAtLeast(1L)
    }

    /** The tile containing a world position at a level. */
    fun of(level: Int, worldX: Double, worldY: Double): TileId {
      val span = TILE_PIXELS * 2.0.pow(level)
      return TileId(level, floor(worldX / span).toLong(), floor(worldY / span).toLong())
    }

    /** Every tile at [level] whose bounds meet [area], in row-major order from the south-west. */
    fun covering(level: Int, area: Aabb): List<TileId> {
      val span = TILE_PIXELS * 2.0.pow(level)
      val fromX = floor(area.minX / span).toLong()
      val toX = floor(area.maxX / span).toLong()
      val fromY = floor(area.minY / span).toLong()
      val toY = floor(area.maxY / span).toLong()

      val tiles = ArrayList<TileId>()
      for (ty in fromY..toY) {
        for (tx in fromX..toX) {
          tiles += TileId(level, tx, ty)
        }
      }
      return tiles
    }

    /** Floor division by two, correct for negative coordinates as well as positive ones. */
    private fun floorDiv2(value: Long): Long = value shr 1
  }
}
