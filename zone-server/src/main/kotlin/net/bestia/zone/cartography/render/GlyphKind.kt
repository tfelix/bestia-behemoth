package net.bestia.zone.cartography.render

/**
 * The symbols the atlas draws instead of colouring the ground.
 *
 * Split into two families that are scattered independently rather than one list with priorities, because
 * they are not alternatives: a wooded hill is drawn as a hill *with trees on it*, which is what the reference
 * plates do and what a single scatter cannot express - it would have to pick one and the map would lose
 * either its forests or its relief wherever they coincide.
 */
enum class GlyphKind(val family: Family) {

  /** A peak: caret outline, lit flank left, hatched flank right. Drawn largest and first. */
  MOUNTAIN(Family.RELIEF),

  /** A rounded rise. What most land that is not flat gets. */
  HILL(Family.RELIEF),

  /** Fir, for taiga and anywhere cold enough that the canopy is needles. */
  CONIFER(Family.COVER),

  /** A round crown on a short trunk: the default wood. */
  BROADLEAF(Family.COVER),

  /** Fronds on a leaning trunk, for tropical coasts. */
  PALM(Family.COVER),

  /** Tufts over a water line: bog, swamp, and the fringe of a delta. */
  MARSH(Family.COVER),

  /** A low crescent, drawn in loose rows. Sand, and only where the ground is flat enough to hold it. */
  DUNE(Family.COVER),

  /** Short broken horizontals, the convention for permanent ice. */
  ICE(Family.COVER);

  enum class Family {
    RELIEF,
    COVER
  }
}
