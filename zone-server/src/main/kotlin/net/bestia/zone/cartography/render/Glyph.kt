package net.bestia.zone.cartography.render

/**
 * One symbol, already placed and sized in the tile's pixel space.
 *
 * Positions are fractional on purpose. A glyph's world position is fixed, so its pixel position is whatever
 * the projection makes it; rounding here would make a glyph jump by up to a pixel between two tiles that share
 * it, which on a tiled map is a visible discontinuity along every seam.
 */
data class Glyph(
  val kind: GlyphKind,
  val x: Double,
  val y: Double,

  /**
   * Where the symbol stands in the world, which is the only frame two tiles can agree in.
   *
   * Carried alongside the pixel position so one scatter can be fed to the next as ground that is already
   * taken - a wood is not drawn over a range - without the second scatter having to invert the projection.
   */
  val worldX: Double,
  val worldY: Double,

  /** Half-width in pixels. Height follows from the kind's own proportions. */
  val size: Double,

  /**
   * Lean in radians, positive clockwise. Zero for anything that stands upright.
   *
   * Relief glyphs take it from the terrain - a peak leans away from its slope, the way a drawn range does -
   * and cover glyphs take a small random amount, which is most of what stops a wood from looking stamped.
   */
  val lean: Double,

  /** Per-glyph randomness for the drawing to vary shape with, derived from world position. */
  val variant: Long
)
