package net.bestia.worldgen.spawn

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.ParamsDigest

/**
 * How dangerous a biome is, on its own, before anything else about a place is considered.
 *
 * ### Not a scalar on [Biome]
 *
 * `Biome.litter` and `Biome.canopy` are ecological facts - how much leaf fall a biome produces, how much of
 * it is under a canopy - and they belong on the enum because half the pipeline reads them. Hostility is a
 * **gameplay weight**: it says how hard the game should be here, which is a designer's opinion and not a
 * property of the ground. It belongs to the subsystem that reads it, which is this one.
 *
 * The practical difference: retuning this must not move `BiomeStage.paramsVersion` and regenerate every
 * world's biome raster. Folding it into [SpawnerStage] alone is what keeps that true.
 *
 * ### Exhaustive, with no `else`
 *
 * A default here is a difficulty assigned on behalf of a biome nobody has thought about, and the failure it
 * produces is the plausible kind - the next biome added gets middling monsters, looks fine, and is wrong in
 * the one place nothing checks. Same argument as `voxel/SurfaceCover`.
 */
object SpawnHostility {

  /**
   * `0` for gentle country, `1` for the harshest ground a player can walk on.
   *
   * The deserts and the swamps sit at the top on purpose: `REMINDER.md` asks for both to be worth the trip,
   * and strong bestia are the first half of that answer. The water biomes are here for exhaustiveness only -
   * nothing spawns on them.
   *
   * There was a `CLIFF` arm at 0.7, and it went with the biome. `SpawnerStage.dangerAt` recovers most of what
   * it said through its `relief` term, but not all: `relief` ramps on *absolute elevation*, so a mountain crag
   * still reads as dangerous and a sea cliff at fifty metres no longer does. That is a real loss and a small
   * one - a sea cliff is a place a player walks past rather than into - and the fix if it ever matters is a
   * gradient term in `dangerAt`, which already has the elevation grid, rather than a biome for slope.
   */
  fun of(biome: Biome): Double = when (biome) {
    // Nothing spawns on water; there are no aquatic bestia yet.
    Biome.OCEAN, Biome.LAKE -> 0.0

    // Ice: nothing lives here that is not built for it.
    Biome.ICE_SHEET -> 1.0
    Biome.ALPINE -> 0.85
    Biome.COLD_DESERT -> 0.8
    Biome.TUNDRA -> 0.55

    // Hot and dry, and the reason a player would ever cross one.
    Biome.DESERT -> 0.95
    Biome.BADLANDS -> 0.85

    // Wet and poisonous. The other half of REMINDER.md's ask: swamps carry the ingredients, and they carry
    // the things that make collecting them a decision. Above the bog beside it, and that gap is the split's
    // whole gameplay content - a bog is cold, sucking ground that will drown you, and a swamp is cold, sucking
    // ground that is also full of things that want to. See `Biome.SWAMP`.
    Biome.SWAMP -> 0.8
    Biome.BOG -> 0.7

    Biome.TAIGA -> 0.45
    Biome.TROPICAL_RAINFOREST -> 0.5
    Biome.TROPICAL_SEASONAL_FOREST -> 0.4
    Biome.TEMPERATE_RAINFOREST -> 0.35
    Biome.TEMPERATE_FOREST -> 0.3

    // The three merged: steppe was 0.4, shrubland 0.35 and savanna 0.45, which is the spread that made the
    // case for one biome rather than three. The step up from grassland's 0.15 is the point - dryland is the
    // ground a player crosses on the way to the desert, and it is where difficulty should first be felt.
    Biome.DRYLAND -> 0.4

    // The two volcanic biomes, and the reason they are worth having as a *pair*: the field is the hardest
    // ground in the world and the basin beside it is not. A player walks into a geothermal basin, works, and
    // leaves; the field above it is the place the equipment is for. `LocalTemperature`'s geothermal term is the
    // other half of the same statement, and this table would be the wrong place to make it twice.
    Biome.VOLCANIC_FIELD -> 1.0
    Biome.GEOTHERMAL_BASIN -> 0.8

    // The gentle country a new master starts in.
    Biome.GRASSLAND -> 0.15
    Biome.RIPARIAN -> 0.2
    Biome.BEACH -> 0.1
  }

  /**
   * Fingerprint of the whole table, folded into [SpawnerStage.paramsVersion].
   *
   * Without it a designer could retune every monster level in the world and move no version number at all -
   * the same hole `Biomes.catalogueDigest` and `ResourceType.catalogueDigest` exist to close.
   */
  fun catalogueDigest(): Long {
    var digest = GenRng.hashString("SpawnHostility")
    for (biome in Biome.entries) {
      digest = GenRng.hash(
        digest,
        GenRng.hashString(biome.name),
        ParamsDigest().put("hostility", of(biome)).value
      )
    }
    return digest
  }
}
