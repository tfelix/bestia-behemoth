package net.bestia.worldgen.resource

/**
 * What can be dug, felled or panned out of the ground.
 *
 * Ordinals are not persisted - deposits are stored as features with a named attribute - so this list may be
 * reordered freely, unlike [net.bestia.worldgen.bio.Biome] or
 * [net.bestia.worldgen.voxel.BlockType].
 */
enum class ResourceType(val label: String) {

  // Metals. Each has a genuine geological setting, which is the whole point of placing them causally.
  COPPER("copper"),
  TIN("tin"),
  IRON("iron"),
  GOLD_LODE("gold lode"),
  GOLD_PLACER("placer gold"),
  SILVER("silver"),

  // Bulk minerals.
  COAL("coal"),
  SALT("salt"),
  STONE("building stone"),
  CLAY("clay"),
  MARBLE("marble"),

  // Surface resources, biome derived.
  TIMBER("timber"),
  FURS("furs"),
  FISH("fish");

  /** How much a unit of this is worth, roughly. Feeds settlement scoring and later the economy. */
  val value: Double
    get() = when (this) {
      GOLD_LODE, GOLD_PLACER -> 1.0
      SILVER -> 0.75
      COPPER, TIN -> 0.5
      IRON -> 0.55
      MARBLE -> 0.4
      COAL -> 0.35
      SALT -> 0.45
      FURS -> 0.3
      TIMBER -> 0.25
      STONE, CLAY -> 0.15
      FISH -> 0.2
    }
}

/** Station channel names on an [net.bestia.worldgen.vector.FeatureKind.ORE_DEPOSIT] marker. */
object DepositChannels {

  /** [ResourceType] ordinal. A category, so it is read back with `toInt()` and never interpolated. */
  const val TYPE = "resource_type"

  /** Extractable quantity in arbitrary units; scales with how long the deposit lasts. */
  const val QUANTITY = "quantity"

  /** Concentration in `[0,1]`. Decides yield per unit of work, and whether it is worth a mine at all. */
  const val RICHNESS = "richness"

  /** Metres below the surface. Zero for a surface outcrop, which is what gets discovered first. */
  const val DEPTH = "depth"

  /**
   * Horizontal extent of the orebody in metres.
   *
   * Needed by chunk generation and by nothing else: a deposit is a point as far as the world tier is
   * concerned, but a voxel has to know whether it is inside the body or outside it.
   */
  const val RADIUS = "radius"
}
