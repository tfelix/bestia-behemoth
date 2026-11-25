package net.bestia.zone.worldevent.contract

import net.bestia.zone.worldevent.persistence.EventContribution
import net.bestia.zone.worldevent.persistence.EventContributionRepository
import org.springframework.stereotype.Service

/** Keeps the contribution ledger across a restart: the director writes it out each run, the boot reads it back. */
@Service
class ContributionJournal(
  private val ledger: ContributionLedger,
  private val repository: EventContributionRepository,
) {

  /** Replaces the stored ledger with the one in memory. In the director's transaction. */
  fun save() {
    val rows = ledger.snapshot().flatMap { (key, contributions) ->
      contributions.flatMap { who ->
        who.points.map { (axis, points) ->
          EventContribution(key.event, key.subjectId, who.masterId, who.masterName, axis.name, points)
        }
      }
    }
    repository.deleteAllInBatch()
    repository.saveAll(rows)
  }

  /** Boot only. */
  fun loadAll() {
    repository.findAll().forEach { row ->
      val axis = runCatching { ContributionLedger.Axis.valueOf(row.axis) }.getOrNull() ?: return@forEach
      ledger.add(ContributionLedger.Key(row.event, row.subjectId), row.masterId, row.masterName, axis, row.points)
    }
  }
}
