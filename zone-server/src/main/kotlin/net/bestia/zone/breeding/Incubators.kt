package net.bestia.zone.breeding

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.breeding.persistence.BestiaEggRepository
import net.bestia.zone.breeding.persistence.IncubatorRow
import net.bestia.zone.breeding.persistence.IncubatorRowRepository
import net.bestia.zone.persistence.AsyncJobExecutor
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

/** Every Incubator's state for the tick, the same convention as [BreedingFarms]. */
@Service
class Incubators(
  private val rows: IncubatorRowRepository,
  private val eggs: BestiaEggRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  private val byStructure = HashMap<Long, Incubator>()

  fun of(structureId: Long): Incubator {
    return byStructure[structureId] ?: Incubator(structureId)
  }

  fun all(): Collection<Incubator> {
    return byStructure.values
  }

  fun put(incubator: Incubator) {
    byStructure[incubator.structureId] = incubator

    val row = IncubatorRow.of(incubator)
    asyncJobExecutor.submit(incubator.structureId) { rows.save(row) }
  }

  fun remove(structureId: Long) {
    byStructure.remove(structureId) ?: return
    asyncJobExecutor.submit(structureId) { rows.deleteById(structureId) }
  }

  /** Boot-time only, before the tick starts. Rows of incubators that are no longer standing are dropped. */
  fun loadAll(isStanding: (Long) -> Boolean) {
    for (row in rows.findAll()) {
      if (!isStanding(row.structureId)) {
        rows.delete(row)
        continue
      }

      val egg = row.eggInstanceId?.let { eggs.findByIdOrNull(it) }
      byStructure[row.structureId] = row.toIncubator(egg)
    }

    LOG.info { "Loaded ${byStructure.size} incubator(s)" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
