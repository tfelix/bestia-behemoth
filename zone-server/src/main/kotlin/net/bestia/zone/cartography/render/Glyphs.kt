package net.bestia.zone.cartography.render

import net.bestia.worldgen.core.GenRng
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
 * line needs, because the whole point of the atlas style is that every mark on it is one a nib could make -
 * see [InkRelief] for the same argument applied to relief. It is also why the shapes are built from a handful
 * of line segments: a glyph that needs a bezier mesh to read has stopped being a symbol.
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

    // The shaded flank is filled first so the outline is drawn over its edge, not beside it.
    val flank = Path2D.Double(right)
    flank.lineTo(skew, 0.0)
    flank.closePath()
    g.color = color(palette.ink, FLANK_FILL_ALPHA)
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

  /** A round wood: a lobed silhouette on a short trunk. Filled, for the reason [conifer] gives. */
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

    g.color = color(palette.ink, TREE_FILL_ALPHA)
    g.fill(lobes)
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
  private const val SUMMIT_SKEW = 0.45
  private const val SHOULDER_MIN = 0.30
  private const val SHOULDER_MAX = 0.52

  /** The lit flank's shoulder sits lower than the shaded one's, which is what gives a peak a facing. */
  private const val SHOULDER_DROP = 0.72

  private const val OUTLINE_WEIGHT = 0.16
  private const val OUTLINE_ALPHA = 0.88
  private const val HATCH_WEIGHT = 0.10
  private const val HATCH_ALPHA = 0.42
  private const val FLANK_FILL_ALPHA = 0.10
  private const val FLANK_HATCHES = 3
  private const val HATCH_RUN = 0.55

  private const val HILL_ASPECT = 0.52

  private const val TREE_WEIGHT = 0.13
  private const val CONIFER_ASPECT = 1.55
  private const val CONIFER_TIERS = 3

  /** How far a tier steps back in before the next one flares out. Under one, or the profile is convex. */
  private const val CONIFER_NOTCH = 0.62
  private const val BROADLEAF_CROWN = 0.72
  private const val BROADLEAF_TRUNK = 0.55
  private const val BROADLEAF_LOBES = 8
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
}
