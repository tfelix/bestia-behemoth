package net.bestia.worldgen.poi

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.ParamsDigest

/**
 * One named landmark, and how many of it a world holds.
 *
 * Not a scatter (no notion of a world-wide count) and not a history site (nothing earned it). A hand-authored
 * list: each entry rolls [count] times per world with [chance], and every passing roll places one copy. With
 * `count = 1, chance = 1.0` the landmark is unique and guaranteed, and [PoiStage] fails the world if it finds no
 * place for it.
 *
 * ### Append only
 *
 * The ordinal is the zone's `StaticEntityKind.POI_*` join and the salt of every roll. Appending an entry moves no
 * existing landmark; renumbering moves all of them.
 */
enum class PoiKind(

  /** What to call it in tooling and in a log line. */
  val label: String,

  /** How many times the world rolls for this landmark. Each passing roll places one copy. */
  val count: Int,

  /** Chance each roll passes, in `(0,1]`. At 1.0 every copy is required and a missing place fails the world. */
  val chance: Double,

  /**
   * Biomes it may stand in. **Empty means any land biome**, not "none".
   *
   * A water biome is refused at class load: nothing could ever stand on it.
   */
  val biomes: Set<Biome>,

  /** Metres between two copies of this landmark. Irrelevant when [count] is 1. */
  val minSpacing: Double = 0.0
) {

  /** A grave out in the waste, marked by whoever found the body. */
  LOST_GRAVE("lost grave", 1, 0.45, setOf(Biome.DESERT, Biome.COLD_DESERT, Biome.BADLANDS, Biome.TUNDRA)),

  /** A ring somebody raised on open ground, where a ring can be seen from a distance. */
  STANDING_STONES("standing stones", 1, 0.60, setOf(Biome.GRASSLAND, Biome.DRYLAND, Biome.TUNDRA, Biome.TAIGA)),

  /** A broken obelisk from before anything in the chronicle. Unique, guaranteed, and allowed on any land. */
  BROKEN_OBELISK("broken obelisk", 1, 1.0, emptySet()),

  /** Waystones on the kind of ground people walk across. */
  WAYSTONE(
    "waystone",
    4,
    0.70,
    setOf(Biome.GRASSLAND, Biome.TEMPERATE_FOREST, Biome.DRYLAND, Biome.TAIGA),
    minSpacing = 16_000.0
  ),

  /** A tree turned to stone, in the ground that does that to a tree. */
  PETRIFIED_TREE("petrified tree", 1, 0.30, setOf(Biome.DESERT, Biome.BADLANDS, Biome.VOLCANIC_FIELD, Biome.DRYLAND)),

  /** An idol in the wet ground that swallowed whatever stood around it. */
  SUNKEN_IDOL("sunken idol", 1, 0.25, setOf(Biome.SWAMP, Biome.BOG, Biome.TROPICAL_RAINFOREST));

  init {
    require(count >= 1) { "$name count must be at least 1, was $count" }
    require(chance > 0.0 && chance <= 1.0) { "$name chance must be in (0,1], was $chance" }
    require(minSpacing >= 0.0) { "$name minSpacing must not be negative, was $minSpacing" }
    require(biomes.none { it.isWater }) {
      "$name lists the water biome ${biomes.first { it.isWater }}; nothing stands on water"
    }
  }

  /** Whether a world without this landmark is a broken world. */
  val isRequired: Boolean
    get() {
      return chance >= 1.0
    }

  /** Whether this may stand on [biome]. Water is never ground, whatever the filter says. */
  fun allows(biome: Biome): Boolean {
    return !biome.isWater && (biomes.isEmpty() || biome in biomes)
  }

  companion object {

    /**
     * Fingerprint of the whole list, folded into [PoiStage.paramsVersion], so retuning a landmark regenerates the
     * world like a params change does. Biomes are folded sorted, so their declaration order is not a change.
     */
    fun catalogueDigest(): Long {
      val digest = ParamsDigest()
      for (kind in entries) {
        digest.nested(
          "${kind.ordinal}:${kind.name}",
          ParamsDigest()
            .put("label", kind.label)
            .put("count", kind.count)
            .put("chance", kind.chance)
            .put("biomes", kind.biomes.map { it.name }.sorted().joinToString(","))
            .put("minSpacing", kind.minSpacing)
            .value
        )
      }
      return digest.value
    }
  }
}

/** Station channels on a [net.bestia.worldgen.vector.FeatureKind.POI] marker. The position is the marker's own. */
object PoiChannels {

  /** [PoiKind] ordinal. Read with `PoiKind.entries[v.toInt()]`; never interpolated. */
  const val KIND = "kind"

  /**
   * Index of the roll that placed this copy, in `[0, count)`. A failed roll leaves a gap, so (kind, copy) names
   * one landmark stably however the other rolls of the world went.
   */
  const val COPY = "copy"
}
