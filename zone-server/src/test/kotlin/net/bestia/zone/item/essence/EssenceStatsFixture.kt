package net.bestia.zone.item.essence

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia

/** Essence stats that know only [species]; none for a test that wears no essence. */
fun essenceStatsOf(vararg species: Bestia): EssenceStats {
  val byId = species.associateBy { it.id }
  val catalogue = mockk<BestiaCatalogue> {
    every { find(any()) } answers { byId[firstArg()] }
  }

  return EssenceStats(catalogue)
}
