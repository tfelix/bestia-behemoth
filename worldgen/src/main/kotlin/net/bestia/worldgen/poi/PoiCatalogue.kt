package net.bestia.worldgen.poi

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.ParamsDigest

/**
 * One named landmark, and how likely a world is to hold it.
 *
 * ### What this is, and what it is not
 *
 * The third way something gets onto the ground in this world, beside the two that already existed and
 * distinct from both.
 *
 * A **scatter** - `VegetationScatter`, `CrystalScatter` - hashes a lattice against a field, so it can say
 * "a tree every four metres where the canopy is high" and cannot say "one of these, somewhere". A lattice has
 * no notion of a world-wide count.
 *
 * A **built site** - `SpecialSites` feeding `HistorySim` - can say that, and everything it places is *earned*:
 * a mine exists because a civilisation reached the technology and rolled for it in a particular year, and it
 * carries a founder, a name and a chronicle entry. A standing stone nobody built has no history to earn it and
 * would need a fake one.
 *
 * So a POI is neither. It is a hand-authored list, one roll per entry per world, and the roll is the whole
 * decision: pass and this world holds exactly one, fail and this world does not have it at all. Which world
 * you are in is part of what makes a world worth exploring, and that is the mechanism.
 *
 * ### Append only
 *
 * The ordinal is `PropInstances.subKindAt` on the emitted prop, and it reaches a client through
 * `StaticEntityKind`. Renumbering these renames every POI prop in the world and points every mesh at the wrong
 * landmark.
 *
 * The ordinal is also what [PoiStage] salts its rolls with, rather than a position in a stream, so appending an
 * entry here does not move any existing entry's roll or the place it stands. That is the property that makes
 * this list extensible: adding a landmark leaves every world's existing ones exactly where they were.
 */
enum class PoiKind(

  /** What to call it in tooling and in a log line. */
  val label: String,

  /**
   * Chance a world holds this at all, in `[0,1]`. Rolled **once per world**.
   *
   * 0.45 means forty-five worlds in a hundred have one and the rest have none. It is not a density and it is
   * not per candidate site: a world that passes gets exactly one, however large it is.
   *
   * The observed frequency comes out at or a little *below* this, and legitimately so - a world with no desert
   * anywhere passes the roll for a desert-only POI and then has nowhere to put it. `PoiStageTest` measures the
   * gap rather than assuming it away, because a gap that is not small is a filter nobody can satisfy.
   */
  val chance: Double,

  /**
   * Biomes it may stand in. **Empty means any land biome**, not "none".
   *
   * Deliberately the opposite convention from `SpecialSites.WASTE_HARSHNESS`, where a biome's absence from the
   * map *is* the filter. There the answer is a number every biome could plausibly have, so absence is the only
   * way to say "not here"; here the answer is a membership and an empty set is the natural way to say "anywhere
   * on land", which is what a broken obelisk wants. A landmark restricted to nothing would be an entry nobody
   * could ever see, so that reading is refused in [PoiStage] rather than being silently possible.
   */
  val biomes: Set<Biome>,

  /**
   * How tall the thing stands, in metres.
   *
   * Fixed per kind rather than rolled, unlike every scatter in the chunk tier. A tree's height is a sample of
   * how well a tree grew here; a landmark is one specific object with one specific mesh, and a waystone that is
   * three times its own height on one world is a bug rather than variety.
   */
  val heightM: Double
) {

  /**
   * A grave out in the waste, marked by whoever found the body.
   *
   * The hard country on purpose: the same reading `SpecialSites.wastes` takes of a desert, which is that people
   * vanish crossing wide ground rather than dangerous ground.
   */
  LOST_GRAVE("lost grave", 0.45, setOf(Biome.DESERT, Biome.COLD_DESERT, Biome.BADLANDS, Biome.TUNDRA), 1.2),

  /** A ring somebody raised on open ground, where a ring can be seen from a distance. */
  STANDING_STONES("standing stones", 0.60, setOf(Biome.GRASSLAND, Biome.DRYLAND, Biome.TUNDRA, Biome.TAIGA), 2.6),

  /**
   * A broken obelisk, from before anything in the chronicle.
   *
   * The one entry with no biome filter, and it is here to keep that path alive: an empty set is the common case
   * for a landmark that predates the land's present state, and a code path with no entry using it is a code
   * path nobody has checked.
   */
  BROKEN_OBELISK("broken obelisk", 0.35, emptySet(), 6.0),

  /** A waystone on the kind of ground people walk across. */
  WAYSTONE("waystone", 0.70, setOf(Biome.GRASSLAND, Biome.TEMPERATE_FOREST, Biome.DRYLAND, Biome.TAIGA), 1.6),

  /** A tree turned to stone, in the ground that does that to a tree. */
  PETRIFIED_TREE(
    "petrified tree",
    0.30,
    setOf(Biome.DESERT, Biome.BADLANDS, Biome.VOLCANIC_FIELD, Biome.DRYLAND),
    5.5
  ),

  /** An idol in the wet ground that swallowed whatever stood around it. */
  SUNKEN_IDOL("sunken idol", 0.25, setOf(Biome.SWAMP, Biome.BOG, Biome.TROPICAL_RAINFOREST), 2.2);

  init {
    require(chance in 0.0..1.0) { "$name chance must be in [0,1], was $chance" }
    require(heightM > 0.0) { "$name heightM must be positive, was $heightM" }
    // The one way an entry can be written so that nothing can ever satisfy it, and it looks like a filter
    // rather than a mistake. Refused here so it is a class-load failure rather than a landmark that never
    // appears on any seed and is found by `PoiStageTest` months later.
    require(biomes.none { it.isWater }) {
      "$name lists the water biome ${biomes.first { it.isWater }}; nothing stands on water"
    }
  }

  /** Whether this may stand on [biome]. Water is never ground, whatever the filter says. */
  fun allows(biome: Biome): Boolean =
    !biome.isWater && (biomes.isEmpty() || biome in biomes)

  companion object {

    /**
     * Fingerprint of the whole list, folded into [PoiStage.paramsVersion].
     *
     * The piece that makes a catalogue in Kotlin rather than in a file safe to have. Every number in here
     * decides which landmarks a world holds and where they stand, so editing one has to invalidate the cached
     * world exactly as editing a params file does - and without this it would move no version number at all,
     * which is the hole `Biomes.catalogueDigest` and `SpawnHostility.catalogueDigest` exist to close.
     *
     * Folded with the ordinal alongside the name because the ordinal is stored - it is the prop's `subKind` and
     * the client's mesh index - so reordering this list is a change to what an existing world means, not a
     * presentation detail.
     *
     * The biome set is folded **sorted by name**, so declaring the same biomes in a different order is not a
     * change. `ParamsDigest` sorts its own fields for the same reason.
     */
    fun catalogueDigest(): Long {
      val digest = ParamsDigest()
      for (kind in entries) {
        digest.nested(
          "${kind.ordinal}:${kind.name}",
          ParamsDigest()
            .put("label", kind.label)
            .put("chance", kind.chance)
            .put("biomes", kind.biomes.map { it.name }.sorted().joinToString(","))
            .put("heightM", kind.heightM)
            .value
        )
      }
      return digest.value
    }
  }
}

/**
 * Station channels on a [net.bestia.worldgen.vector.FeatureKind.POI] marker.
 *
 * One channel, which is the whole record: a POI is a kind standing at a position, and the position is the
 * marker's own. There is deliberately no radius - the prop has no extent, so nothing has to find this marker by
 * `ChunkMaterializer.MARKER_MARGIN` and it is not in
 * `Invariants.checkStructuralMarkersFitTheQueryMargin`'s list - and no name seed, because nothing names a POI
 * yet and a channel written by nobody and read by nobody is worse than its absence.
 */
object PoiChannels {

  /** [PoiKind] ordinal. Read with `PoiKind.entries[v.toInt()]`; never interpolated. */
  const val KIND = "kind"
}
