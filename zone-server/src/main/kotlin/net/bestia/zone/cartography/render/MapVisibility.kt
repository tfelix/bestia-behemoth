package net.bestia.zone.cartography.render

import net.bestia.worldgen.vector.FeatureKind

/**
 * What each kind of world feature is allowed to become on a map a player can see.
 *
 * ### Why this is an exhaustive `when` and not a set of allowed kinds
 *
 * A map is a disclosure channel. `world/stream/WorldInfoSMSG` already withholds the world seed on the grounds
 * that it "is precisely what would turn prospecting into arithmetic", and a map that marked every ore body
 * would do the same job more conveniently. The risk is not that someone adds `ORE_DEPOSIT` to a whitelist; it
 * is that someone adds a *new* feature kind two years from now and it is drawn because the drawing code had a
 * sensible default.
 *
 * So there is no default. [of] is a `when` over every [FeatureKind] with no `else`, which means adding a kind
 * to the generator does not compile until somebody has decided what a player may learn from it. That is the
 * same forcing function `viewer/MapRenderer.colorOf` already uses for colours, borrowed here for a question
 * that matters more.
 *
 * ### The bands
 *
 * [minMetresPerPixel] is the coarsest zoom a kind survives to, not the finest. Drawing a hamlet at 512 m per
 * pixel costs a dot the size of a county and tells the reader nothing; drawing every street at that zoom
 * costs a grey wash over the whole map. The thresholds are the same generalisation
 * `viewer/RenderOptions.HIDDEN_BY_DEFAULT` performs by hand, expressed as scale instead of as a list.
 */
enum class MapVisibility(
  /**
   * Coarsest scale, in metres per pixel, at which this may be drawn. Zero means every zoom.
   *
   * Read as "hide when `metresPerPixel` exceeds this", so a larger number means the feature survives further
   * out. [SECRET] and [OMITTED] ignore it entirely.
   */
  val minMetresPerPixel: Double
) {

  /** Rivers, lakes and the shapes water leaves. Drawn at every zoom; the map's skeleton. */
  WATER(Double.MAX_VALUE),

  /** Roads, bridges, sea lanes. What connects the places. */
  ROUTE(160.0),

  /** Settlements and the ruins of them, plus the landmarks worth a symbol. */
  PLACE(Double.MAX_VALUE),

  /** Small landmarks: a shrine, a waystone. Real places, but not ones a world map has room for. */
  LANDMARK(20.0),

  /** Streets, buildings, walls, quarters - everything only [PlanStyle] has the scale to draw. */
  TOWN_DETAIL(12.0),

  /**
   * Never drawn at any zoom, because knowing it is worth more than a map should give away.
   *
   * Ore, buried hoards, cave systems and creature dens. All of them are content a player is meant to find by
   * going there, and all of them are in the feature store because the *generator* needs them.
   */
  SECRET(0.0),

  /**
   * Nothing to draw: an attribute record pinned to another feature's coordinates, or a construction the
   * generator needed and the ground does not show.
   */
  OMITTED(0.0);

  companion object {

    fun of(kind: FeatureKind): MapVisibility = when (kind) {
      // Water, and the landforms that are only legible as water.
      FeatureKind.RIVER_CHANNEL -> WATER
      FeatureKind.LAKE -> WATER
      FeatureKind.OXBOW_LAKE -> WATER
      // Landforms carved into the heightfield rather than water drawn on top of it: the coastline contour
      // already traces a delta's mouth and a fjord's walls, because both are shapes in the ground.
      FeatureKind.DELTA -> OMITTED
      FeatureKind.FJORD -> OMITTED
      FeatureKind.COASTLINE -> WATER

      // A confluence is a smoothing patch where two channels meet, not a place. The channels are drawn.
      FeatureKind.RIVER_CONFLUENCE -> OMITTED

      FeatureKind.ROAD -> ROUTE
      FeatureKind.BRIDGE -> ROUTE
      FeatureKind.SEA_LANE -> ROUTE
      FeatureKind.ROAD_JUNCTION -> OMITTED

      FeatureKind.SETTLEMENT -> PLACE
      FeatureKind.RUIN -> PLACE
      FeatureKind.ASH_RUIN -> PLACE
      FeatureKind.FORT -> PLACE
      FeatureKind.MONASTERY -> PLACE
      FeatureKind.LIGHTHOUSE -> PLACE
      FeatureKind.MONUMENT -> PLACE
      FeatureKind.TOMB -> PLACE
      FeatureKind.BATTLEFIELD -> PLACE

      FeatureKind.SHRINE -> LANDMARK
      FeatureKind.POI -> LANDMARK

      // A working mine is a place on the map; what is *in* it is ORE_DEPOSIT, which is not.
      FeatureKind.MINE -> PLACE
      FeatureKind.ROADSIDE_INN -> LANDMARK

      FeatureKind.STREET -> TOWN_DETAIL
      FeatureKind.BUILDING -> TOWN_DETAIL
      FeatureKind.DISTRICT -> TOWN_DETAIL
      FeatureKind.TOWN_WALL -> TOWN_DETAIL
      FeatureKind.GATE -> TOWN_DETAIL

      // Volcanism is visible from a long way off and is not a secret to anyone standing near it.
      FeatureKind.VOLCANIC_VENT -> PLACE
      FeatureKind.LAVA_POOL -> LANDMARK

      // A wound is the most conspicuous thing in a landscape; hiding it would be the odd choice.
      FeatureKind.WOUND -> PLACE

      // Prospecting, and what the generator needs to do it. None of this may reach a client.
      FeatureKind.ORE_DEPOSIT -> SECRET
      FeatureKind.CAVE_HOARD -> SECRET
      FeatureKind.CAVE_SYSTEM -> SECRET
      FeatureKind.CAVE_PASSAGE -> SECRET
      FeatureKind.CAVE_ENTRANCE -> SECRET
      FeatureKind.BESTIA_SPAWN -> SECRET

      // Attribute records pinned to a settlement's own coordinates - drawing them double-marks the town.
      FeatureKind.SETTLEMENT_HISTORY -> OMITTED
      FeatureKind.SETTLEMENT_ECONOMY -> OMITTED
      FeatureKind.SETTLEMENT_GRADING -> OMITTED
      FeatureKind.BUSINESS -> OMITTED

      // Structures in the heightfield rather than things on the ground. The relief already shows them.
      FeatureKind.FAULT -> OMITTED
      FeatureKind.HOTSPOT -> OMITTED
      FeatureKind.TECTONIC_BASIN -> OMITTED
      FeatureKind.GLACIAL_TROUGH -> OMITTED
      FeatureKind.CIRQUE -> OMITTED
      FeatureKind.MORAINE -> OMITTED
      FeatureKind.ALLUVIAL_FAN -> OMITTED

      // Woods reach the map as scattered glyphs off the canopy raster, not as one dot per stand.
      FeatureKind.VEGETATION_STAND -> OMITTED
    }

    /** Whether a kind may be drawn at all at this scale. */
    fun draws(kind: FeatureKind, metresPerPixel: Double): Boolean {
      val visibility = of(kind)
      if (visibility == SECRET || visibility == OMITTED) return false

      return metresPerPixel <= visibility.minMetresPerPixel
    }
  }
}
