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
  GHOST;

  companion object {

    private val COLD = setOf(Biome.TUNDRA, Biome.TAIGA, Biome.COLD_DESERT, Biome.ALPINE)
    private val HOT_AND_DRY = setOf(Biome.DESERT, Biome.BADLANDS)

    /**
     * The herb on one cell. [roll] is the cell's own draw in `0..1`, so a place keeps its herbs. The checks run
     * from the rarest herb down, so blighted ground in strong mana grows ghost herbs, not mana herbs.
     */
    fun of(blighted: Boolean, mana: Double, biome: Biome, roll: Double, params: GroundCoverParams): HerbKind {
      return when {
        blighted -> if (roll < params.ghostHerbShare) GHOST else WILD
        mana >= params.manaHerbMinMana && roll < params.manaHerbShare -> MANA
        biome in COLD && roll < params.frostHerbShare -> FROST
        biome in HOT_AND_DRY && roll < params.sunHerbShare -> SUN
        biome.canopy >= params.medicinalHerbMinCanopy && roll < params.medicinalHerbShare -> MEDICINAL
        else -> WILD
      }
    }
  }
}
