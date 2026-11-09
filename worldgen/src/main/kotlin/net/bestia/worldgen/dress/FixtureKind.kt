package net.bestia.worldgen.dress

/**
 * The man-made things that dress a world: what people put up around where they live and along the ways between.
 *
 * **One prop kind for all of them, and this is its sub-kind.** `PropId` has four bits of kind and twelve are
 * already spoken for, so a pier, a fence and a lantern each taking a kind of their own would end the catalogue
 * at four. `PoiKind` and `BuildingFunction` ride on `PropInstances.subKindAt` for the same reason, and the zone
 * joins each entry to a `StaticEntityKind.FIXTURE_*` by name.
 *
 * The ordinal reaches the prop's sub-kind byte and from there the wire, so **append only**.
 *
 * @param heightM nominal height in metres: what the prop is drawn at unless its producer says otherwise.
 * @param readable whether clicking one tells the player something - a signpost's directions, a grave's name.
 */
enum class FixtureKind(val heightM: Double, val readable: Boolean = false) {

  /** A timber pier out over the water from a waterfront town. Its ground is the deck. */
  PIER(0.4),

  /** A rowing boat, moored at a pier or drawn up on a beach. */
  BOAT(0.9),

  /** One straight run of fence, wall or hedge. Which of those is the style. */
  FENCE(1.1),

  /** One tile of a field's crop, laid in rows along the furrow bearing. Which crop is the style. */
  CROP(0.8),

  HAYSTACK(2.2),

  SCARECROW(1.9),

  /** A windmill on the highest rise above a settlement's fields. */
  WINDMILL(11.0),

  /** A well on a village green or a town square. */
  WELL(2.4),

  /** A signpost where a lane meets a road, naming where each way goes. */
  SIGNPOST(2.6, readable = true),

  /** A milestone on a trunk road, naming the settlements either way and how far. */
  MILESTONE(0.9, readable = true),

  /** A lantern along a road near a settlement, burning with a pale magic flame after dark. */
  LANTERN(2.8),

  /** A pile of stones at the top of a trail's climb. */
  CAIRN(1.6),

  /** A plain headstone in a settlement's cemetery. */
  GRAVESTONE(1.0, readable = true),

  /** The headstone of somebody the chronicle remembers. */
  NAMED_GRAVE(1.3, readable = true),

  /** A rock outcrop on a hilltop. */
  OUTCROP(3.5),

  /** The house of a roadside inn, a day's walk from anywhere. */
  INN(8.0);

  companion object {
    /** Every entry's ordinal and height, for `DressingStage.paramsVersion`: retuning one moves the world. */
    fun catalogueDigest(): Long {
      var h = 0x46495854L
      for (kind in entries) {
        h = net.bestia.worldgen.core.GenRng.mix64(h xor kind.ordinal.toLong())
        h = net.bestia.worldgen.core.GenRng.mix64(h xor java.lang.Double.doubleToLongBits(kind.heightM))
      }
      return h
    }
  }
}

/** The styles a [FixtureKind.FENCE] is built in, carried in the prop's style bits. */
enum class FenceStyle { POST_AND_RAIL, DRY_STONE, HEDGE, WATTLE }

/** The crops a [FixtureKind.CROP] tile carries, in its style bits. */
enum class CropStyle { WHEAT, BARLEY, CABBAGE }

/** Channel names on a `FeatureKind.FIXTURE` marker. */
object FixtureChannels {

  /** [FixtureKind] ordinal. Read with `toInt()`. */
  const val KIND = "kind"

  /** Facing in radians, the way `BuildingProps` gives a building's: the long axis, from east, anticlockwise. */
  const val YAW = "yaw"

  /** Half-extent along the facing, in metres. Zero for a fixture with no footprint of its own. */
  const val HALF_LENGTH = "half_length"

  /** Half-extent across the facing, in metres. */
  const val HALF_WIDTH = "half_width"

  /** Height in metres. */
  const val HEIGHT = "height"

  /**
   * Elevation the fixture stands at, or NaN to stand on the ground under it.
   *
   * Set for the two things that stand on water rather than on land - a pier's deck, a moored boat - which the
   * chunk tier's ground veto would otherwise refuse.
   */
  const val ELEVATION = "elevation"

  /** Style ordinal: a [FenceStyle], a [CropStyle], or a model variant. */
  const val STYLE = "style"

  /** `SettlementChannels.INDEX` of the settlement this belongs to, or -1. */
  const val SETTLEMENT = "settlement"

  /** Position along a lantern line, from the settlement outwards, so the lamplighter walks them in order. */
  const val ORDER = "order"

  /** Index into `Chronicle.figures` for a named grave. */
  const val FIGURE = "figure"

  /**
   * A destination a signpost or milestone names, by arm: the settlement index, the distance in metres along
   * the road, and the compass bearing to it in radians. Up to [MAX_DESTINATIONS], -1 where an arm names none.
   */
  fun destination(arm: Int) = "dest_$arm"
  fun distance(arm: Int) = "dist_$arm"
  fun bearing(arm: Int) = "bearing_$arm"

  const val MAX_DESTINATIONS = 4
}

/** Channel names on a `FeatureKind.CEMETERY` area's perimeter table. Constant around the ring. */
object CemeteryChannels {
  const val SETTLEMENT = "settlement"

  /** How many of the plots, from the first, hold a named grave marker. The chunk tier fills the rest. */
  const val NAMED = "named"

  /** Share of the unnamed plots that hold a headstone, by how long the settlement has buried its dead. */
  const val FILL = "fill"

  const val YAW = "yaw"
  const val HALF_LENGTH = "half_length"
  const val HALF_WIDTH = "half_width"

  /** [FenceStyle] ordinal of the wall around it. */
  const val WALL = "wall"
}
