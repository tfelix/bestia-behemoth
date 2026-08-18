package net.bestia.zone.cartography.render

import net.bestia.worldgen.render.Viewport
import java.awt.image.BufferedImage

/**
 * One way of drawing the world onto a map.
 *
 * Two implementations, chosen by zoom rather than by preference: [AtlasStyle] draws country - relief,
 * woods, coasts, the roads between towns - and [PlanStyle] draws the inside of a settlement. They are
 * separate types rather than one renderer with a flag because almost nothing is shared: an atlas tile is
 * symbolic (a mountain is a glyph, not a shaded slope) and a plan tile is literal (a building is its
 * actual footprint). Trying to interpolate between the two produces a map that is neither.
 *
 * A style must be a **pure function of the viewport and the world**. Two calls with equal arguments have
 * to produce identical pixels, and a tile must not be able to tell which tile it is: everything that
 * varies across the map varies with *world position* or with *paper position* (see [Parchment]), never
 * with a tile index. That is what keeps a coastline from stepping and a forest from shuffling when the
 * viewer pans, and it is the property the whole tile cache rests on.
 */
interface MapStyle {

  /** Bumped when the drawing changes, so cached tiles from an older look are not served. */
  val version: Int

  fun render(view: Viewport, inputs: TileInputs): BufferedImage
}
