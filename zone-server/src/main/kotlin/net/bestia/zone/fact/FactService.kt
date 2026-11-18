package net.bestia.zone.fact

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.fact.ecs.KnownFacts
import net.bestia.zone.fact.persistence.MasterFact
import net.bestia.zone.fact.persistence.MasterFactRepository
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** What each master has learned about the world. Only [learnAsPlayed] may be called on the tick. */
@Service
class FactService(
  private val facts: MasterFactRepository,
  private val world: WorldView,
  private val sessions: ConnectionInfoService,
  private val jobs: AsyncJobExecutor,
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

  /**
   * Learns for the master [accountId] plays now, if it plays one. Safe to call on the tick: the row is written
   * by a job.
   */
  fun learnAsPlayed(accountId: AccountId, fact: FactRef, source: FactSource) {
    val masterId = sessions.activeMasters()[accountId] ?: return

    jobs.submit(masterId) { learn(masterId, fact, source) }
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
