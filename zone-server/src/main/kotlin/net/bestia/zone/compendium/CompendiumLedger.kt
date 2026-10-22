package net.bestia.zone.compendium

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Which species each master being played has seen, so the tick can tell a first sighting without SQL.
 *
 * Holds masters only while they are played. A sighting for a master not loaded yet is ignored: the creature
 * is still there on the next look.
 */
@Component
class CompendiumLedger {

  private val seenByMaster = ConcurrentHashMap<Long, MutableSet<Long>>()

  fun remember(masterId: Long, seen: Set<Long>) {
    seenByMaster[masterId] = ConcurrentHashMap.newKeySet<Long>().apply { addAll(seen) }
  }

  fun forget(masterId: Long) {
    seenByMaster.remove(masterId)
  }

  /** Marks [speciesId] as seen; true only the first time, and only for a loaded master. */
  fun markSeen(masterId: Long, speciesId: Long): Boolean {
    return seenByMaster[masterId]?.add(speciesId) ?: false
  }
}
