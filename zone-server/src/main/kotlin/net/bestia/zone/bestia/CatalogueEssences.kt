package net.bestia.zone.bestia

import net.bestia.zone.item.essence.EssenceDefinition
import net.bestia.zone.item.essence.SpeciesEssences
import org.springframework.stereotype.Component

@Component
class CatalogueEssences(private val catalogue: BestiaCatalogue) : SpeciesEssences {

  override fun of(speciesId: Long): EssenceDefinition? = catalogue.find(speciesId)?.essence
}
