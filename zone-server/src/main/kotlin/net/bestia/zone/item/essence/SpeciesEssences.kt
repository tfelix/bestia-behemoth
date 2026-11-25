package net.bestia.zone.item.essence

/** The essence each species leaves, looked up by the Bestias slice, which sits above items. */
fun interface SpeciesEssences {

  /** Null for a species without an essence. */
  fun of(speciesId: Long): EssenceDefinition?

  /** The level of a boss or world boss, null for any other species. */
  fun bossLevelOf(speciesId: Long): Int? = null
}
