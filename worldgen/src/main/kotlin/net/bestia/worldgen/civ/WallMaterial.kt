package net.bestia.worldgen.civ

/**
 * What a building's walls are made of.
 *
 * These were `BlockType.MASONRY`, `TIMBER` and `PLASTER` until buildings stopped being voxels. The *choice*
 * was worth keeping and the blocks were not: a temple in stone in a town of timber is the signal a temple is
 * for, and `TownBuildings` still rolls it from culture, wealth and prestige exactly as it did. What changed is
 * that the answer is now an attribute of an entity rather than a material to fill walls with, because there
 * are no walls to fill - a building is a prop the runtime turns into something a player can enter and own.
 *
 * The ordinal rides on `BuildingChannels.WALL_MATERIAL` and reaches the client as part of a prop's variant, so
 * this is a wire contract like [net.bestia.worldgen.voxel.PropKind]'s. **Append only.**
 */
enum class WallMaterial {

  /** Sawn and framed timber. What a poor town in a forested culture builds in. */
  TIMBER,

  /** Wattle and daub, or lime render over a timber frame. What most of a poor town is walled with. */
  PLASTER,

  /** Dressed stone. Wealth, prestige, or a fortification, which is stone regardless of either. */
  STONE
}
