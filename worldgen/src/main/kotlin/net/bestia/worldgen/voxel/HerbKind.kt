package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome

/**
 * Which herb a [PropKind.HERB] is, carried on `PropInstances.subKindAt`. A herb kind rather than a [PropKind] each,
 * for [PropKind.BUILDING]'s reason: [PropId] has room for sixteen kinds. The order is the ordinal on the wire, so
 * append only.
 *
 * Each rare herb grows where the land is special: the forest floor, strong mana, the cold, the desert, or blighted
 * ground. Elsewhere, and on most cells there too, a herb is a wild herb.
 */
enum class HerbKind {
  WILD,
  MEDICINAL,
  MANA,
  FROST,
  SUN,
  GHOST,

  /**
   * A forage patch: not one herb but a bit of everything edible the land around it grows - wild garlic in a forest,
   * kelp on a beach, chilies on a lava field. What it yields is the zone's to decide from the biome; worldgen only
   * says where one grows.
   */
  FORAGE;

  companion object {

    private val COLD = setOf(Biome.TUNDRA, Biome.TAIGA, Biome.COLD_DESERT, Biome.ALPINE)
    private val HOT_AND_DRY = setOf(Biome.DESERT, Biome.BADLANDS)

    /**
     * The herb on one cell. [roll] is the cell's own draw in `0..1`, so a place keeps its herbs. The checks run
     * from the rarest herb down, so blighted ground in strong mana grows ghost herbs, not mana herbs.
     *
     * A forage patch takes the top of the roll and the rare herbs its bottom, so the two never compete for a cell.
     * Blighted ground grows none: nothing there is fit to eat.
     */
    fun of(blighted: Boolean, mana: Double, biome: Biome, roll: Double, params: GroundCoverParams): HerbKind {
      return when {
        blighted -> if (roll < params.ghostHerbShare) GHOST else WILD
        roll >= 1.0 - params.forageShare -> FORAGE
        mana >= params.manaHerbMinMana && roll < params.manaHerbShare -> MANA
        biome in COLD && roll < params.frostHerbShare -> FROST
        biome in HOT_AND_DRY && roll < params.sunHerbShare -> SUN
        biome.canopy >= params.medicinalHerbMinCanopy && roll < params.medicinalHerbShare -> MEDICINAL
        else -> WILD
      }
    }
  }
}
