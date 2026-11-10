package net.bestia.zone.breeding

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.breeding.persistence.BestiaEggRepository
import net.bestia.zone.breeding.persistence.BreedingFarmRow
import net.bestia.zone.breeding.persistence.BreedingFarmRowRepository
import net.bestia.zone.persistence.AsyncJobExecutor
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

/**
 * Every Breeding Farm's state, for the tick to read without a database hit; the same convention as
 * [net.bestia.zone.prop.PlayerStructureRegistry]. A farm that never held anything has no entry and reads as idle.
 */
@Service
class BreedingFarms(
  private val rows: BreedingFarmRowRepository,
  private val eggs: BestiaEggRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  private val byStructure = HashMap<Long, BreedingFarm>()

  fun of(structureId: Long): BreedingFarm {
    return byStructure[structureId] ?: BreedingFarm(structureId)
  }

  fun all(): Collection<BreedingFarm> {
    return byStructure.values
  }

  /** The farm whose pair [playerBestiaId] belongs to. */
  fun pairedWith(playerBestiaId: Long): BreedingFarm? {
    return byStructure.values.firstOrNull { farm ->
      farm.pair?.let { it.motherBestiaId == playerBestiaId || it.fatherBestiaId == playerBestiaId } == true
    }
  }

  /** In memory now, in its row afterwards. */
  fun put(farm: BreedingFarm) {
    byStructure[farm.structureId] = farm

    val row = BreedingFarmRow.of(farm)
    asyncJobExecutor.submit(farm.structureId) { rows.save(row) }
  }

  fun remove(structureId: Long) {
    byStructure.remove(structureId) ?: return
    asyncJobExecutor.submit(structureId) { rows.deleteById(structureId) }
  }

  /** Boot-time only, before the tick starts. Rows of farms that are no longer standing are dropped. */
  fun loadAll(isStanding: (Long) -> Boolean) {
    for (row in rows.findAll()) {
      if (!isStanding(row.structureId)) {
        rows.delete(row)
        continue
      }

      val egg = row.eggInstanceId?.let { eggs.findByIdOrNull(it) }
      byStructure[row.structureId] = row.toFarm(egg)
    }

    LOG.info { "Loaded ${byStructure.size} breeding farm(s)" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
