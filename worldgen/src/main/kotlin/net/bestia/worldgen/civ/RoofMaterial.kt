package net.bestia.worldgen.civ

/**
 * What a building's roof is covered in.
 *
 * Two values rather than [WallMaterial]'s three, and that asymmetry is the original one: the roof roll was
 * always a single coin toss on wealth and prestige, tiled or not. See [WallMaterial] for why the choice
 * outlived the blocks that used to express it.
 *
 * The ordinal rides on `BuildingChannels.ROOF_MATERIAL` and reaches the client as part of a prop's variant.
 * **Append only.**
 */
enum class RoofMaterial {

  /** Reed or straw thatch. The default, and what a building gets when nobody could afford otherwise. */
  THATCH,

  /** Fired clay tile. Wealth, or a building somebody wanted to last. */
  TILE
}
