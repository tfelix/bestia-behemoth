package net.bestia.zone.boot

import net.bestia.zone.breeding.BreedingFarms
import net.bestia.zone.prop.PlayerStructureRegistry
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Loads the Breeding Farms after the structures they stand in ([PlayerStructureBootRunner] is `@Order(4)`). */
@Component
@Order(5)
class BreedingFarmBootRunner(
  private val farms: BreedingFarms,
  private val structures: PlayerStructureRegistry,
) : CommandLineRunner {

  @Transactional
  override fun run(vararg args: String?) {
    farms.loadAll { structureId -> structures.of(structureId) != null }
  }
}
