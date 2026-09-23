package net.bestia.zone.cartography.render

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.render.Colors
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import kotlin.math.cos
import kotlin.math.sin

/**
 * The symbols themselves: what a mountain, a wood or a marsh actually looks like on the paper.
 *
 * Every glyph is drawn in a local frame - origin at the symbol's *foot*, x to the right, y up the page -
 * and the caller's transform places it. Anchoring at the foot rather than the centre is what makes a row of
 * peaks of different heights stand on the same ground instead of floating at a common midline.
 *
 * ### Two marks and nothing else
 *
 * A stroked outline and a hatched flank. No gradients, no soft shadows, no alpha ramps beyond what a fading
 * line needs, because the whole point of the atlas style is that every mark on it is one a nib could make.
 * It is also why the shapes are built from a handful of line segments: a glyph that needs a bezier mesh to
 * read has stopped being a symbol.
 *
 * Since the hillshade was removed these symbols are the *only* thing carrying the shape of the land, which is
 * why they are drawn at the weight they are - see [AtlasStyle].
 */
object Glyphs {

  fun draw(g: Graphics2D, glyph: Glyph, palette: AtlasPalette) {
    val saved = g.transform

    g.transform(AffineTransform.getTranslateInstance(glyph.x, glyph.y))
    if (glyph.lean != 0.0) g.rotate(glyph.lean)

    when (glyph.kind) {
      GlyphKind.MOUNTAIN -> peak(g, glyph, palette, MOUNTAIN_ASPECT)
      GlyphKind.HILL -> hill(g, glyph, palette)
      GlyphKind.CONIFER -> conifer(g, glyph, palette)
      GlyphKind.BROADLEAF -> broadleaf(g, glyph, palette)
      GlyphKind.PALM -> palm(g, glyph, palette)
      GlyphKind.MARSH -> marsh(g, glyph, palette)
      GlyphKind.DUNE -> dune(g, glyph, palette)
      GlyphKind.ICE -> ice(g, glyph, palette)
      GlyphKind.GRASS -> grass(g, glyph, palette)
      GlyphKind.SCRUB -> scrub(g, glyph, palette)
      GlyphKind.ROCK -> rock(g, glyph, palette)
      GlyphKind.HUMMOCK -> hummock(g, glyph, palette)
    }

    g.transform = saved
  }

  /**
   * A peak: two flanks meeting at a summit, the right one hatched.
   *
   * The summit is pushed off centre by the glyph's own variant so a range does not read as a row of identical
   * triangles, and the flanks are broken by a shoulder partway down - a straight-sided triangle reads as a
   * pyramid, and one kink is enough to read as rock.
   */
  private fun peak(g: Graphics2D, glyph: Glyph, palette: AtlasPalette, aspect: Double) {
    val w = glyph.size
    val h = w * aspect
    val skew = (unit(glyph.variant, 1) - 0.5) * SUMMIT_SKEW * w
    val shoulder = SHOULDER_MIN + (SHOULDER_MAX - SHOULDER_MIN) * unit(glyph.variant, 2)

    val left = Path2D.Double()
    left.moveTo(-w, 0.0)
    left.lineTo(-w * shoulder + skew * 0.4, -h * shoulder)
    left.lineTo(skew, -h)

    val right = Path2D.Double()
    right.moveTo(skew, -h)
    right.lineTo(w * shoulder + skew * 0.4, -h * shoulder * SHOULDER_DROP)
    right.lineTo(w, 0.0)

    // The lit face first, so the peak sits on the paper as a shape rather than as two strokes over the ground
    // behind it, and the shaded face over it. Filled before either outline, so a stroke covers the seam
    // between them instead of running beside it.
    val lit = Path2D.Double(left)
    lit.lineTo(skew, 0.0)
    lit.closePath()
    g.color = Color(Colors.mix(palette.paper, palette.land, LIT_FACE_TINT))
    g.fill(lit)

    val flank = Path2D.Double(right)
    flank.lineTo(skew, 0.0)
    flank.closePath()
    g.color = Color(Colors.mix(palette.ink, palette.land, SHADED_FACE_LIFT))
    g.fill(flank)

    g.color = color(palette.ink, OUTLINE_ALPHA)
    g.stroke = pen(w * OUTLINE_WEIGHT)
    g.draw(left)
    g.draw(right)

    hatchFlank(g, palette, skew, h, w, shoulder)
  }

  /** Strokes down the shaded flank, following its fall rather than the page. */
  private fun hatchFlank(
    g: Graphics2D,
    palette: AtlasPalette,
    skew: Double,
    h: Double,
    w: Double,
    shoulder: Double
  ) {
    g.color = color(palette.ink, HATCH_ALPHA)
    g.stroke = pen(w * HATCH_WEIGHT)

    for (i in 1..FLANK_HATCHES) {
      val t = i.toDouble() / (FLANK_HATCHES + 1)
      val fromX = skew + (w * shoulder + skew * 0.4 - skew) * t
      val fromY = -h + (-h * shoulder * SHOULDER_DROP + h) * t
      g.draw(Line2D.Double(fromX, fromY, fromX + w * HATCH_RUN * (1.0 - t), 0.0))
    }
  }

  /** A rounded rise, drawn as a single arc with one hatch under its right shoulder. */
  private fun hill(g: Graphics2D, glyph: Glyph, palette: AtlasPalette) {
    val w = glyph.size
    val h = w * HILL_ASPECT

    val arc = Path2D.Double()
    arc.moveTo(-w, 0.0)
    arc.curveTo(-w * 0.55, -h * 1.15, w * 0.55, -h * 1.15, w, 0.0)

    g.color = color(palette.ink, OUTLINE_ALPHA * 0.9)
    g.stroke = pen(w * OUTLINE_WEIGHT)
    g.draw(arc)

    g.color = color(palette.ink, HATCH_ALPHA * 0.8)
    g.stroke = pen(w * HATCH_WEIGHT)
    g.draw(Line2D.Double(w * 0.42, -h * 0.52, w * 0.72, 0.0))
  }

  /**
   * A fir: a filled, slightly notched triangle on a trunk.
   *
   * Filled rather than drawn as branch strokes, and that is a size decision rather than a taste one. A tree at
   * world zoom is three pixels of half-width, and six antialiased hairlines inside three pixels resolve to a
   * grey scribble - the shape is carried entirely by the silhouette at that size. The notches give it back the
   * fir profile as soon as there are enough pixels to show them, so the same glyph serves both ends of the
   * ladder.
   */
  private fun conifer(g: Graphics2D, glyph: Glyph, palette: AtlasPalette) {
    val w = glyph.size
    val h = w * CONIFER_ASPECT

    g.color = color(palette.ink, TRUNK_ALPHA)
    g.stroke = pen(w * TREE_WEIGHT)
    g.draw(Line2D.Double(0.0, 0.0, 0.0, -h * 0.32))

    val crown = Path2D.Double()
    crown.moveTo(0.0, -h)
    for (i in CONIFER_TIERS downTo 1) {
      val t = i.toDouble() / CONIFER_TIERS
      val y = -h * t * 0.86
      val reach = w * (1.0 - t * 0.72)
      crown.lineTo(reach, y)
      crown.lineTo(reach * CONIFER_NOTCH, y + h * 0.10)
    }
    crown.lineTo(w * 0.20, -h * 0.22)
    crown.lineTo(-w * 0.20, -h * 0.22)
    for (i in 1..CONIFER_TIERS) {
      val t = i.toDouble() / CONIFER_TIERS
      val y = -h * t * 0.86
      val reach = w * (1.0 - t * 0.72)
      crown.lineTo(-reach * CONIFER_NOTCH, y + h * 0.10)
      crown.lineTo(-reach, y)
    }
    crown.closePath()

    g.color = color(palette.ink, TREE_FILL_ALPHA)
    g.fill(crown)
  }

  /**
   * A round wood: a lobed crown on a short trunk.
   *
   * Outlined and filled pale once there are pixels for it, solid below that. The outlined form is what makes a
   * wood read as a wood on the reference plates: packed tightly, a field of ringed crowns reads as canopy seen
   * from above, where the same shapes filled solid read as a dark stain with no texture in it. Below
   * [BUBBLE_MIN_PIXELS] the ring and its interior are the same pixel, so the silhouette has to carry it - the
   * argument [conifer] gives at length.
   */
  private fun broadleaf(g: Graphics2D, glyph: Glyph, palette: AtlasPalette) {
    val w = glyph.size
    val crown = w * BROADLEAF_CROWN
    val trunk = w * BROADLEAF_TRUNK

    g.color = color(palette.ink, TRUNK_ALPHA)
    g.stroke = pen(w * TREE_WEIGHT)
    g.draw(Line2D.Double(0.0, 0.0, 0.0, -trunk))

    val phase = unit(glyph.variant, 3) * Math.PI * 2.0
    val lobes = Path2D.Double()
    for (i in 0 until BROADLEAF_LOBES) {
      val a = phase + i.toDouble() / BROADLEAF_LOBES * Math.PI * 2.0
      val r = crown * (0.80 + 0.30 * unit(glyph.variant, 4 + i))
      val x = cos(a) * r
      val y = -trunk - crown * 0.72 + sin(a) * r * 0.82
      if (i == 0) lobes.moveTo(x, y) else lobes.lineTo(x, y)
    }
    lobes.closePath()

    if (w < BUBBLE_MIN_PIXELS) {
      g.color = color(palette.ink, TREE_FILL_ALPHA)
      g.fill(lobes)
      return
    }

    // Opaque, for the reason [LIT_FACE_TINT] gives: packed this tightly, crowns overlap constantly, and a
    // translucent crown shows every ring behind it as a mesh instead of a canopy.
    g.color = Color(palette.crown)
    g.fill(lobes)
    g.color = color(palette.ink, OUTLINE_ALPHA)
    g.stroke = pen(w * TREE_WEIGHT * BUBBLE_OUTLINE_GAIN)
    g.draw(lobes)
  }

  private fun palm(g: Graphics2D, glyph: Glyph, palette: AtlasPalette) {
    val w = glyph.size
    val h = w * PALM_ASPECT
    val bend = (unit(glyph.variant, 5) - 0.5) * w * PALM_BEND

    g.color = color(palette.ink, OUTLINE_ALPHA)
    g.stroke = pen(w * TREE_WEIGHT)

    val trunk = Path2D.Double()
    trunk.moveTo(0.0, 0.0)
    trunk.quadTo(bend * 0.5, -h * 0.55, bend, -h)
    g.draw(trunk)

    for (i in 0 until PALM_FRONDS) {
      val spread = (i.toDouble() / (PALM_FRONDS - 1) - 0.5) * 2.0
      val frond = Path2D.Double()
      frond.moveTo(bend, -h)
      frond.quadTo(
        bend + spread * w * 0.8, -h - w * 0.34,
        bend + spread * w * 1.25, -h + w * 0.22
      )
      g.draw(frond)
    }
  }

  private fun marsh(g: Graphics2D, glyph: Glyph, palette: AtlasPalette) {
    val w = glyph.size

    g.color = color(palette.waterInk, MARSH_ALPHA)
    g.stroke = pen(w * TREE_WEIGHT)

    // The water line first, then tufts standing in it.
    g.draw(Line2D.Double(-w, 0.0, w, 0.0))
    for (i in 0 until MARSH_TUFTS) {
      val x = (i.toDouble() / (MARSH_TUFTS - 1) - 0.5) * 1.5 * w
      val tall = w * (0.5 + 0.5 * unit(glyph.variant, 7 + i))
      g.draw(Line2D.Double(x, 0.0, x - w * 0.12, -tall))
      g.draw(Line2D.Double(x, 0.0, x + w * 0.18, -tall * 0.7))
    }
  }

  private fun dune(g: Graphics2D, glyph: Glyph, palette: AtlasPalette) {
    val w = glyph.size

    val crest = Path2D.Double()
    crest.moveTo(-w, 0.0)
    crest.quadTo(-w * 0.2, -w * DUNE_ASPECT * 2.0, w * 0.55, -w * DUNE_ASPECT * 0.35)
    crest.quadTo(w * 0.8, 0.0, w, w * DUNE_ASPECT * 0.2)

    g.color = color(palette.ink, DUNE_ALPHA)
    g.stroke = pen(w * HATCH_WEIGHT * 1.4)
    g.draw(crest)
  }

  private fun ice(g: Graphics2D, glyph: Glyph, palette: AtlasPalette) {
    val w = glyph.size

    g.color = color(palette.waterInk, ICE_ALPHA)
    g.stroke = pen(w * HATCH_WEIGHT)
    g.draw(Line2D.Double(-w, 0.0, -w * 0.15, 0.0))
    g.draw(Line2D.Double(w * 0.2, -w * 0.3, w, -w * 0.3))
  }

  /** A tussock: three blades springing from one root, the shortest mark that reads as grass. */
  private fun grass(g: Graphics2D, glyph: Glyph, palette: AtlasPalette) {
    val w = glyph.size
    val h = w * GlyphKind.GRASS.aspect

    g.color = color(palette.ink, OPEN_GROUND_ALPHA)
    g.stroke = pen(w * TREE_WEIGHT)

    for (i in 0 until GRASS_BLADES) {
      val lean = (i.toDouble() / (GRASS_BLADES - 1) - 0.5) * 2.0
      val tall = h * (0.7 + 0.3 * unit(glyph.variant, 10 + i))

      val blade = Path2D.Double()
      blade.moveTo(0.0, 0.0)
      blade.quadTo(lean * w * 0.3, -tall * 0.6, lean * w * 0.9, -tall)
      g.draw(blade)
    }
  }

  /** A low bush: a lobed clump with no trunk, so it is not read as a small tree. */
  private fun scrub(g: Graphics2D, glyph: Glyph, palette: AtlasPalette) {
    val w = glyph.size
    val h = w * GlyphKind.SCRUB.aspect

    val clump = Path2D.Double()
    clump.moveTo(-w, 0.0)
    clump.quadTo(-w * 0.8, -h * 1.3, -w * 0.25, -h * 0.9)
    clump.quadTo(0.0, -h * 1.6, w * 0.3, -h * 0.9)
    clump.quadTo(w * 0.85, -h * 1.25, w, 0.0)
    clump.closePath()

    g.color = color(palette.crown, SCRUB_FILL_ALPHA)
    g.fill(clump)
    g.color = color(palette.ink, OPEN_GROUND_ALPHA)
    g.stroke = pen(w * TREE_WEIGHT)
    g.draw(clump)
  }

  /** A boulder: an angular silhouette, which is what tells scree from a bush at this size. */
  private fun rock(g: Graphics2D, glyph: Glyph, palette: AtlasPalette) {
    val w = glyph.size
    val h = w * GlyphKind.ROCK.aspect

    val stone = Path2D.Double()
    stone.moveTo(-w, 0.0)
    stone.lineTo(-w * 0.62, -h * 1.25)
    stone.lineTo(w * 0.1 + (unit(glyph.variant, 13) - 0.5) * w * 0.4, -h * 1.6)
    stone.lineTo(w * 0.75, -h * 0.9)
    stone.lineTo(w, 0.0)
    stone.closePath()

    g.color = Color(Colors.mix(palette.paper, palette.land, ROCK_FACE_TINT))
    g.fill(stone)
    g.color = color(palette.ink, OPEN_GROUND_ALPHA)
    g.stroke = pen(w * TREE_WEIGHT)
    g.draw(stone)
  }

  /** Two low mounds: the ground itself, where nothing grows tall enough to draw. */
  private fun hummock(g: Graphics2D, glyph: Glyph, palette: AtlasPalette) {
    val w = glyph.size
    val h = w * GlyphKind.HUMMOCK.aspect

    g.color = color(palette.ink, OPEN_GROUND_ALPHA * 0.85)
    g.stroke = pen(w * HATCH_WEIGHT * 1.3)

    val left = Path2D.Double()
    left.moveTo(-w, 0.0)
    left.quadTo(-w * 0.5, -h * 2.0, 0.0, 0.0)
    g.draw(left)

    val right = Path2D.Double()
    right.moveTo(w * 0.15, 0.0)
    right.quadTo(w * 0.6, -h * 1.5, w, 0.0)
    g.draw(right)
  }

  private fun pen(width: Double) =
    BasicStroke(width.toFloat().coerceAtLeast(MIN_PEN), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

  private fun color(rgb: Int, alpha: Double) = Color(
    (rgb ushr 16) and 0xFF,
    (rgb ushr 8) and 0xFF,
    rgb and 0xFF,
    (alpha * 255).toInt().coerceIn(0, 255)
  )

  /** A stable `[0,1)` draw from a glyph's variant, so shape choices are reproducible per position. */
  private fun unit(variant: Long, index: Int): Double = GenRng.hashUnit(variant, index.toLong())

  private const val MIN_PEN = 0.55f

  private const val MOUNTAIN_ASPECT = 1.15

  /**
   * How far the summit may sit off centre, and where the flanks kink.
   *
   * The skew was nearly half the half-width, which gave one flank a long shallow tail and made a peak read as
   * a tick rather than as a mountain. Kept small, and with the kink higher up, the silhouette stays a triangle
   * while still differing from its neighbour.
   */
  private const val SUMMIT_SKEW = 0.26
  private const val SHOULDER_MIN = 0.38
  private const val SHOULDER_MAX = 0.58

  /** The lit flank's shoulder sits lower than the shaded one's, which is what gives a peak a facing. */
  private const val SHOULDER_DROP = 0.72

  private const val OUTLINE_WEIGHT = 0.20
  private const val OUTLINE_ALPHA = 0.90
  private const val HATCH_WEIGHT = 0.11
  private const val HATCH_ALPHA = 0.50

  /**
   * The two faces of a peak, and why they are **opaque**.
   *
   * The faces began as tints over the ground, which left a peak as an open caret reading more like a bird mark
   * than a mountain, and left overlapping peaks drawn *through* each other: two transparent triangles show
   * both sets of edges, so a close-packed range came out as a tangle of crossing lines rather than as a row of
   * summits. Opaque fills plus the north-to-south draw order give the range its depth instead - the nearer
   * peak simply covers the shoulder of the one behind it, which is what the reference plates do and what no
   * amount of alpha can imitate.
   *
   * Neither face is pure paper or pure ink. A little of the land tone in each keeps the symbol part of the
   * sheet rather than a sticker on it.
   */
  private const val LIT_FACE_TINT = 0.35
  private const val SHADED_FACE_LIFT = 0.12
  private const val FLANK_HATCHES = 4
  private const val HATCH_RUN = 0.55

  private const val HILL_ASPECT = 0.52

  private const val TREE_WEIGHT = 0.13
  private const val CONIFER_ASPECT = 1.55
  private const val CONIFER_TIERS = 3

  /** How far a tier steps back in before the next one flares out. Under one, or the profile is convex. */
  private const val CONIFER_NOTCH = 0.62
  private const val BROADLEAF_CROWN = 0.82
  private const val BROADLEAF_TRUNK = 0.34
  private const val BROADLEAF_LOBES = 8

  /** Half-width below which a crown is drawn solid, because its outline would be its interior. */
  private const val BUBBLE_MIN_PIXELS = 2.6

  /** Crowns are packed close, so their rings have to be heavier than a trunk tick to stay separate. */
  private const val BUBBLE_OUTLINE_GAIN = 1.5
  /** Trees are silhouettes, so the fill carries the shape; the trunk tick is fainter than the crown. */
  private const val TREE_FILL_ALPHA = 0.55
  private const val TRUNK_ALPHA = 0.5

  private const val PALM_ASPECT = 1.45
  private const val PALM_BEND = 0.55
  private const val PALM_FRONDS = 4

  private const val MARSH_ALPHA = 0.62
  private const val MARSH_TUFTS = 3

  private const val DUNE_ASPECT = 0.34
  private const val DUNE_ALPHA = 0.45

  private const val ICE_ALPHA = 0.5

  /**
   * How dark the open-ground marks are drawn.
   *
   * Lighter than a tree, and deliberately. There are far more of these - grassland is the commonest cover a
   * temperate world has - so at a tree's weight they would out-ink the woods and the ranges together, and the
   * map would say "grass" louder than it says anything else.
   */
  private const val OPEN_GROUND_ALPHA = 0.55

  private const val GRASS_BLADES = 3
  private const val SCRUB_FILL_ALPHA = 0.7

  /** A stone face is paper pulled towards the land tone: lit, but not as white as a peak's lit flank. */
  private const val ROCK_FACE_TINT = 0.45
}
