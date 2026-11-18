package net.bestia.zone.fact

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.fact.ecs.KnownFacts
import net.bestia.zone.fact.persistence.MasterFact
import net.bestia.zone.fact.persistence.MasterFactRepository
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** What each master has learned about the world. Runs off the tick: every call reads or writes the database. */
@Service
class FactService(
  private val facts: MasterFactRepository,
  private val world: WorldView,
  private val sessions: ConnectionInfoService,
  private val masters: MasterEntityResolver = MasterEntityResolver(sessions),
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
    tellPlayedMaster(masterId, fact)

    return true
  }

  /** The fact a played master just learned must count on the tick at once; others load theirs at spawn. */
  private fun tellPlayedMaster(masterId: Long, fact: FactRef) {
    val accountId = sessions.activeMasters().entries.firstOrNull { it.value == masterId }?.key ?: return
    val masterEntityId = masters.selectedMasterEntityOf(accountId) ?: return

    world.post { get(masterEntityId, KnownFacts::class)?.add(fact) }
  }

  @Transactional
  fun deleteAllOf(masterId: Long) {
    facts.deleteAllByIdMasterId(masterId)
  }
}
