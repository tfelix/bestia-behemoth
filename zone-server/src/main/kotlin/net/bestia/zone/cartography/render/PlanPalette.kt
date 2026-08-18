package net.bestia.zone.cartography.render

import net.bestia.worldgen.civ.DistrictKind
import net.bestia.worldgen.render.Colors

/**
 * Tones for the settlement plan: quarters, roofs, streets, walls, water.
 *
 * A separate table from [AtlasPalette] rather than an extension of it, because the two styles need opposite
 * things from a palette. An atlas keeps land and sea within a few percent of each other so the ink carries the
 * picture; a plan has almost no ink and has to distinguish a market from a slum from a park, so its whole job
 * is hue. Sharing one table would mean every change to the world map's restraint fighting every change to the
 * town map's legibility.
 *
 * The reference plate this follows uses desaturated slate blues and greys for built-up ground, a warm neutral
 * for open country, and reserves saturation for water and greenery. [districtTone] holds that line: quarters
 * differ in *value and temperature*, never in chroma, so a plan of thirty quarters does not read as a chart.
 */
class PlanPalette(
  /** Open country outside the built-up area. */
  val ground: Int,

  /** Water: the one thing on a plan allowed a real colour. */
  val water: Int,

  /** Bank and channel line. A shade of the water, never the building outline: a river is not a building. */
  val waterEdge: Int,

  /** Street surface. Drawn as a wide pale stroke, which is what makes a street read as a gap. */
  val street: Int,

  /** Building outline, and the darkest tone on the plan. */
  val outline: Int,

  /** Town wall and gates: heavier than any building. */
  val wall: Int,

  /** Woodland and parks. */
  val green: Int,

  /** Label ink. */
  val ink: Int
) {

  /**
   * The fill a quarter gets.
   *
   * Ordered by how built-up the ground is rather than by any civic meaning: farmland and park sit nearest the
   * open country tone, residential and craft in the middle, the citadel darkest. That way a plan reads as a
   * density map at a glance, before any of the quarters are identified.
   */
  fun districtTone(kind: DistrictKind): Int = when (kind) {
    DistrictKind.FARMLAND -> Colors.rgb(206, 204, 178)
    DistrictKind.PARK -> Colors.rgb(176, 190, 156)
    DistrictKind.SLUM -> Colors.rgb(166, 178, 186)
    DistrictKind.RESIDENTIAL -> Colors.rgb(150, 164, 182)
    DistrictKind.CRAFT -> Colors.rgb(142, 152, 172)
    DistrictKind.MARKET -> Colors.rgb(158, 158, 176)
    DistrictKind.PATRICIATE -> Colors.rgb(138, 140, 168)
    DistrictKind.CIVIC -> Colors.rgb(130, 134, 160)
    DistrictKind.MILITARY -> Colors.rgb(120, 126, 146)
    DistrictKind.GATE -> Colors.rgb(126, 130, 150)
    DistrictKind.CITADEL -> Colors.rgb(108, 112, 134)
  }

  companion object {

    /** Slate and stone, as in the reference plate. */
    val SLATE = PlanPalette(
      ground = Colors.rgb(206, 208, 190),
      water = Colors.rgb(150, 174, 186),
      waterEdge = Colors.rgb(112, 140, 158),
      street = Colors.rgb(238, 238, 230),
      outline = Colors.rgb(54, 58, 70),
      wall = Colors.rgb(44, 46, 54),
      green = Colors.rgb(126, 148, 112),
      ink = Colors.rgb(124, 40, 40)
    )
  }
}
