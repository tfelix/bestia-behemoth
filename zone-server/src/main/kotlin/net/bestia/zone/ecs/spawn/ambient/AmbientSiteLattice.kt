package net.bestia.zone.ecs.spawn.ambient

import net.bestia.worldgen.core.GenRng

/**
 * Where the wilderness could hold a creature, as a function of the world seed alone.
 *
 * A jittered grid: the world is cut into cells of [spacingTiles], and each cell offers one site at a
 * pseudo-random point inside itself. That buys even coverage - which a Poisson process does not, and which
 * is the whole complaint about the den layer - while still looking scattered rather than planted in rows.
 *
 * ### Everything here is derived, nothing is stored
 *
 * A site is a pure function of `(worldSeed, cellX, cellY)`, so every observer computes the same creature in
 * the same tile, a reboot changes nothing, and walking away and back does not disturb the country behind
 * you. That is what makes a population this dense affordable at all: sixteen thousand square kilometres at
 * this spacing would be eighteen million creatures, and none of them has to exist until somebody is near it.
 *
 * ### Two things that look like details and are not
 *
 * **[Math.floorDiv], never `/`.** The world is centred on the origin, so half of it has negative
 * coordinates, and integer division truncates towards zero - which makes the cell at zero twice as wide as
 * every other and mirrors the lattice across both axes. `SpawnerCellIndex` documents the same trap, and it
 * has a regression test for it because it shipped once.
 *
 * **A salt per question.** The jitter on each axis and the thinning roll come off separate hashes rather
 * than one stream. Sharing one would correlate where a site sits with whether it survives thinning, so the
 * survivors would drift towards one corner of every cell. `WildSpawnerService.DEN_SHARE_SALT` exists for
 * exactly this reason.
 */
class AmbientSiteLattice(
  private val worldSeed: Long,
  private val spacingTiles: Long
) {

  /** Tile x of the site this cell offers. */
  fun siteX(cellX: Long, cellY: Long): Long {
    return cellX * spacingTiles + jitter(cellX, cellY, JITTER_X_SALT)
  }

  /** Tile y of the site this cell offers. */
  fun siteY(cellX: Long, cellY: Long): Long {
    return cellY * spacingTiles + jitter(cellX, cellY, JITTER_Y_SALT)
  }

  /**
   * The stream a site draws its species from.
   *
   * Stands in for a marker's feature id, so `WildSpawnerService.pick` needs no change to serve both layers.
   */
  fun siteId(cellX: Long, cellY: Long): Long {
    return GenRng.hash(worldSeed, cellX, cellY, SITE_SALT)
  }

  /** This site's thinning draw, 0 to 1. Below the cell's keep-share means the site exists. */
  fun thinningRoll(cellX: Long, cellY: Long): Double {
    return GenRng.hashUnit(worldSeed, cellX, cellY, THIN_SALT)
  }

  fun cellXOf(x: Long): Long {
    return Math.floorDiv(x, spacingTiles)
  }

  fun cellYOf(y: Long): Long {
    return Math.floorDiv(y, spacingTiles)
  }

  /**
   * Runs [action] for every cell whose site could lie within [radius] of ([x], [y]).
   *
   * Widened by one cell on each side: a cell's site sits anywhere inside it, so a cell whose *corner* is out
   * of range may still offer a site that is in it.
   */
  inline fun forEachCellNear(x: Long, y: Long, radius: Long, action: (Long, Long) -> Unit) {
    val firstX = cellXOf(x - radius) - 1
    val lastX = cellXOf(x + radius) + 1
    val firstY = cellYOf(y - radius) - 1
    val lastY = cellYOf(y + radius) + 1

    for (cy in firstY..lastY) {
      for (cx in firstX..lastX) {
        action(cx, cy)
      }
    }
  }

  private fun jitter(cellX: Long, cellY: Long, salt: Long): Long {
    return (GenRng.hashUnit(worldSeed, cellX, cellY, salt) * spacingTiles).toLong()
      .coerceIn(0, spacingTiles - 1)
  }

  companion object {
    /** Packs a cell into one key, as `SpawnerCellIndex` and `AreaNameRegistry` both do. */
    fun pack(cellX: Long, cellY: Long): Long {
      return (cellX shl 32) or (cellY and 0xFFFFFFFFL)
    }

    fun unpackX(cell: Long): Long {
      return cell shr 32
    }

    /** Sign-extends the low half; `and 0xFFFFFFFF` alone would read every negative y as a huge positive. */
    fun unpackY(cell: Long): Long {
      return (cell and 0xFFFFFFFFL).toInt().toLong()
    }

    private const val JITTER_X_SALT = 0xA11BL
    private const val JITTER_Y_SALT = 0xB22CL
    private const val SITE_SALT = 0xC33DL
    private const val THIN_SALT = 0xD44EL
  }
}
