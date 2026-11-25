package net.bestia.zone.worldevent.contract

import org.springframework.stereotype.Service

/**
 * Who did what for each running world event, without anybody taking a contract: fighting its creatures and
 * holding its ground count as they happen. Kills land on the tick, holding off it, so every method locks.
 */
@Service
class ContributionLedger {

  /** One running event: which kind of event and what it is about, as its creatures are tagged. */
  data class Key(val event: String, val subjectId: Long)

  enum class Axis { FIGHTING, HOLDING }

  class Contribution(val masterId: Long, val masterName: String) {
    val points = HashMap<Axis, Double>()
    val total: Double get() = points.values.sum()
  }

  private val byEvent = HashMap<Key, MutableMap<Long, Contribution>>()

  @Synchronized
  fun add(key: Key, masterId: Long, masterName: String, axis: Axis, points: Double) {
    if (points <= 0.0) return
    val contribution = byEvent.getOrPut(key) { HashMap() }.getOrPut(masterId) { Contribution(masterId, masterName) }
    contribution.points.merge(axis, points, Double::plus)
  }

  /** Each contributor's share of all that was done for the event, highest first. */
  @Synchronized
  fun sharesOf(key: Key): List<Pair<Contribution, Double>> {
    val contributions = byEvent[key]?.values.orEmpty()
    val total = contributions.sumOf { it.total }
    if (total <= 0.0) return emptyList()
    return contributions.map { it to it.total / total }.sortedByDescending { it.second }
  }

  @Synchronized
  fun forget(key: Key) {
    byEvent.remove(key)
  }

  /** Every contribution, for saving. */
  @Synchronized
  fun snapshot(): Map<Key, List<Contribution>> = byEvent.mapValues { (_, it) -> it.values.toList() }
}
