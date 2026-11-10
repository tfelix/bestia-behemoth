package net.bestia.zone.auction

import net.bestia.zone.auction.persistence.TradeLink
import net.bestia.zone.auction.persistence.TradeLinkRepository
import net.bestia.zone.persistence.AsyncJobExecutor
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/** Who has a Trade Agreement with whom, in memory for searches on any thread; rows are written in a job. */
@Service
class TradeLinks(
  private val repository: TradeLinkRepository,
  private val jobs: AsyncJobExecutor,
) {

  data class Partner(val masterId: Long, val name: String)

  private val partners = ConcurrentHashMap<Long, Set<Partner>>()

  fun partnersOf(masterId: Long): Set<Partner> {
    return partners[masterId].orEmpty()
  }

  fun isLinked(masterId: Long, otherMasterId: Long): Boolean {
    return partnersOf(masterId).any { it.masterId == otherMasterId }
  }

  fun link(one: Partner, other: Partner) {
    add(one, other)
    val (first, second) = ordered(one, other)
    jobs.submit(KEY) { repository.save(TradeLink(first.masterId, first.name, second.masterId, second.name)) }
  }

  fun unlink(masterId: Long, otherMasterId: Long) {
    partners.computeIfPresent(masterId) { _, set -> set.filterNot { it.masterId == otherMasterId }.toSet() }
    partners.computeIfPresent(otherMasterId) { _, set -> set.filterNot { it.masterId == masterId }.toSet() }
    val first = minOf(masterId, otherMasterId)
    val second = maxOf(masterId, otherMasterId)
    jobs.submit(KEY) { repository.findByFirstMasterIdAndSecondMasterId(first, second)?.let(repository::delete) }
  }

  /** Boot only. */
  fun loadAll() {
    repository.findAll().forEach { add(Partner(it.firstMasterId, it.firstName), Partner(it.secondMasterId, it.secondName)) }
  }

  private fun add(one: Partner, other: Partner) {
    partners.merge(one.masterId, setOf(other)) { old, new -> old + new }
    partners.merge(other.masterId, setOf(one)) { old, new -> old + new }
  }

  private fun ordered(one: Partner, other: Partner): Pair<Partner, Partner> {
    return if (one.masterId < other.masterId) one to other else other to one
  }

  private companion object {
    /** One key, so a link and its cut are written in the order they happened. */
    const val KEY = "trade-links"
  }
}
