package net.bestia.zone.companion.query

import net.bestia.bus.contract.ZoneAnswer
import net.bestia.zone.compendium.CompendiumStore
import org.springframework.stereotype.Service

/** A master's Bestia Compendium, for the companion app: only what the master has seen itself. */
@Service
class CompendiumQueries(
  private val owned: OwnedMasters,
  private val store: CompendiumStore,
) {

  /** Null when the master is not the asker's. */
  fun compendium(accountId: Long, masterId: Long): ZoneAnswer.CompendiumData? {
    owned.find(accountId, masterId) ?: return null

    return ZoneAnswer.CompendiumData(
      store.entries(masterId).map {
        ZoneAnswer.CompendiumData.Entry(it.speciesId, it.caughtAt != null, it.firstSeenAt.toEpochMilli())
      }
    )
  }
}
