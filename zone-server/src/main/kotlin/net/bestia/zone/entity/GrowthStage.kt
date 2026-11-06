package net.bestia.zone.entity

/**
 * How far a removed tree has grown back: a stump first, then a sapling and a young tree, then grown again.
 *
 * Every prop that was never removed is [GROWN], which is also what every non-tree kind always is.
 *
 * The ordinal reaches the wire as the proto's `GrowthStage`, so **append only**.
 */
enum class GrowthStage {
  GROWN,
  STUMP,
  SAPLING,
  YOUNG
}
