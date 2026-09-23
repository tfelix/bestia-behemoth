package net.bestia.zone.cartography.render

/**
 * The symbols the atlas draws instead of colouring the ground.
 *
 * Split into two families that are scattered independently rather than one list with priorities, because
 * they are not alternatives: a wooded hill is drawn as a hill *with trees on it*, which is what the reference
 * plates do and what a single scatter cannot express - it would have to pick one and the map would lose
 * either its forests or its relief wherever they coincide.
 */
enum class GlyphKind(
  val family: Family,

  /**
   * How tall the symbol stands as a multiple of its own half-width.
   *
   * Here rather than in [Glyphs] because placement needs it too: deciding whether one peak buries another
   * means knowing the shape each of them will occupy, and a scatter that guessed at it would drop the wrong
   * one. [Glyphs] remains the only thing that knows what goes *inside* that shape.
   */
  val aspect: Double
) {

  /** A peak: caret outline, lit flank left, hatched flank right. Drawn largest and first. */
  MOUNTAIN(Family.RELIEF, aspect = 1.15),

  /** A rounded rise. What most land that is not flat gets. */
  HILL(Family.RELIEF, aspect = 0.6),

  /** Fir, for taiga and anywhere cold enough that the canopy is needles. */
  CONIFER(Family.COVER, aspect = 1.55),

  /** A round crown on a short trunk: the default wood. */
  BROADLEAF(Family.COVER, aspect = 1.0),

  /** Fronds on a leaning trunk, for tropical coasts. */
  PALM(Family.COVER, aspect = 1.45),

  /** Tufts over a water line: bog, swamp, and the fringe of a delta. */
  MARSH(Family.COVER, aspect = 0.8),

  /** A low crescent, drawn in loose rows. Sand, and only where the ground is flat enough to hold it. */
  DUNE(Family.COVER, aspect = 0.34),

  /** Short broken horizontals, the convention for permanent ice. */
  ICE(Family.COVER, aspect = 0.3),

  /** A tussock: what open grassland gets, where a tree would be a lie and bare paper says nothing. */
  GRASS(Family.COVER, aspect = 0.9),

  /** A low bush, for dryland and steppe: too dry for wood, too grown for sand. */
  SCRUB(Family.COVER, aspect = 0.6),

  /** A boulder, for badlands, lava fields and ground above the treeline. */
  ROCK(Family.COVER, aspect = 0.55),

  /** A low mound, for tundra and cold desert, where the ground itself is the only texture. */
  HUMMOCK(Family.COVER, aspect = 0.35);

  enum class Family {
    RELIEF,
    COVER
  }
}
