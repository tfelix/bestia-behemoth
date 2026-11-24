package net.bestia.zone.item.essence

/** The essence each species leaves, looked up by the Bestias slice, which sits above items. */
fun interface SpeciesEssences {

  /** Null for a species without an essence, such as a boss for now. */
  fun of(speciesId: Long): EssenceDefinition?
}
