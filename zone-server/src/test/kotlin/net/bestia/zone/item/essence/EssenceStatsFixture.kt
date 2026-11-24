package net.bestia.zone.item.essence

import net.bestia.zone.bestia.persistence.Bestia

/** Essence stats that know only [species]; none for a test that wears no essence. */
fun essenceStatsOf(vararg species: Bestia): EssenceStats {
  val byId = species.associateBy { it.id }

  return EssenceStats { speciesId -> byId[speciesId]?.essence }
}
