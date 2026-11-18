package net.bestia.zone.fact

import net.bestia.zone.fact.persistence.MasterFact
import net.bestia.zone.fact.persistence.MasterFactRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** What each master has learned about the world. Runs off the tick: every call reads or writes the database. */
@Service
class FactService(
  private val facts: MasterFactRepository,
) {

  @Transactional(readOnly = true)
  fun factsOf(masterId: Long): Set<FactRef> {
    return facts.findAllByIdMasterId(masterId).mapTo(HashSet()) { it.id.fact() }
  }

  /** True when the master did not know it before. */
  @Transactional
  fun learn(masterId: Long, fact: FactRef, source: FactSource): Boolean {
    val key = MasterFact.Key(masterId, fact)
    if (facts.existsById(key)) {
      return false
    }

    facts.save(MasterFact(key, source, learnedAt = Instant.now()))

    return true
  }

  @Transactional
  fun deleteAllOf(masterId: Long) {
    facts.deleteAllByIdMasterId(masterId)
  }
}
