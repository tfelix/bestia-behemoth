package net.bestia.worldgen.viewer

/** Packed 24-bit RGB helpers. The viewer works in ints throughout; `java.awt.Color` allocates. */
object Colors {

  fun rgb(r: Int, g: Int, b: Int): Int =
    (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

  fun red(c: Int) = (c ushr 16) and 0xFF
  fun green(c: Int) = (c ushr 8) and 0xFF
  fun blue(c: Int) = c and 0xFF

  fun mix(a: Int, b: Int, t: Double): Int {
    val f = t.coerceIn(0.0, 1.0)
    return rgb(
      (red(a) + (red(b) - red(a)) * f).toInt(),
      (green(a) + (green(b) - green(a)) * f).toInt(),
      (blue(a) + (blue(b) - blue(a)) * f).toInt()
    )
  }

  /** Multiplies brightness, for hillshading an already-coloured pixel. */
  fun scale(c: Int, factor: Double): Int = rgb(
    (red(c) * factor).toInt(),
    (green(c) * factor).toInt(),
    (blue(c) * factor).toInt()
  )
}

/** Maps a normalised position along a colour scale to a packed RGB value. */
fun interface ColorRamp {

  /** @param t clamped to `[0,1]` by the implementation, so callers need not pre-clamp */
  fun rgb(t: Double): Int
}

/** A piecewise-linear ramp through colour stops. */
class GradientRamp(stops: List<Stop>) : ColorRamp {

  data class Stop(val at: Double, val rgb: Int)

  private val stops = stops.sortedBy { it.at }

  init {
    require(stops.size >= 2) { "A gradient needs at least two stops, got ${stops.size}" }
  }

  override fun rgb(t: Double): Int {
    val c = t.coerceIn(0.0, 1.0)
    if (c <= stops.first().at) return stops.first().rgb
    if (c >= stops.last().at) return stops.last().rgb

    var i = 0
    while (i < stops.size - 2 && c > stops[i + 1].at) i++

    val a = stops[i]
    val b = stops[i + 1]
    val span = b.at - a.at

    return if (span <= 0.0) b.rgb else Colors.mix(a.rgb, b.rgb, (c - a.at) / span)
  }

  companion object {
    fun of(vararg stops: Pair<Double, Int>) =
      GradientRamp(stops.map { Stop(it.first, it.second) })
  }
}

/**
 * The standard ramps.
 *
 * Colour choice is not decoration here. A debug view exists to make a wrong value obvious, so the
 * ramps are chosen for discrimination - [HYPSOMETRIC_LAND] deliberately has a hard tone change
 * around the treeline, and [DIVERGING] has a light midpoint so the sign of a residual reads at a
 * glance.
 */
object Ramps {

  val GRAYSCALE = GradientRamp.of(
    0.0 to Colors.rgb(16, 16, 18),
    1.0 to Colors.rgb(245, 245, 245)
  )

  /** Sea floor: deep trench to shelf. */
  val BATHYMETRY = GradientRamp.of(
    0.0 to Colors.rgb(6, 18, 54),
    0.55 to Colors.rgb(18, 62, 122),
    0.85 to Colors.rgb(46, 116, 176),
    1.0 to Colors.rgb(120, 176, 214)
  )

  /** Land: coastal green through olive, rock brown, bare grey, snow. */
  val HYPSOMETRIC_LAND = GradientRamp.of(
    0.0 to Colors.rgb(76, 128, 74),
    0.12 to Colors.rgb(112, 152, 82),
    0.30 to Colors.rgb(166, 168, 96),
    0.50 to Colors.rgb(160, 128, 82),
    0.68 to Colors.rgb(128, 100, 78),
    0.82 to Colors.rgb(140, 136, 132),
    0.93 to Colors.rgb(196, 196, 198),
    1.0 to Colors.rgb(252, 252, 255)
  )

  /** Perceptually even; the safe default for a field nobody has a colour convention for yet. */
  val VIRIDIS = GradientRamp.of(
    0.0 to Colors.rgb(68, 1, 84),
    0.25 to Colors.rgb(59, 82, 139),
    0.5 to Colors.rgb(33, 145, 140),
    0.75 to Colors.rgb(94, 201, 98),
    1.0 to Colors.rgb(253, 231, 37)
  )

  val TEMPERATURE = GradientRamp.of(
    0.0 to Colors.rgb(49, 54, 149),
    0.35 to Colors.rgb(116, 173, 209),
    0.5 to Colors.rgb(255, 255, 191),
    0.7 to Colors.rgb(244, 143, 78),
    1.0 to Colors.rgb(165, 0, 38)
  )

  val PRECIPITATION = GradientRamp.of(
    0.0 to Colors.rgb(214, 190, 140),
    0.3 to Colors.rgb(176, 200, 132),
    0.6 to Colors.rgb(72, 158, 140),
    1.0 to Colors.rgb(24, 62, 132)
  )

  /** Light in the middle, so zero is obvious. For residuals and differences. */
  val DIVERGING = GradientRamp.of(
    0.0 to Colors.rgb(33, 102, 172),
    0.5 to Colors.rgb(247, 247, 247),
    1.0 to Colors.rgb(178, 24, 43)
  )
}
