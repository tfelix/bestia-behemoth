package net.bestia.zone.honor

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.honor.persistence.LocalHonor
import net.bestia.zone.honor.persistence.LocalHonor.Scope
import net.bestia.zone.honor.persistence.LocalHonorRepository
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong

/**
 * Every settlement's and townsperson's honor towards every Master, kept in memory and written behind.
 *
 * Tick-thread only, like `RumourRegistry`: plain maps, loaded once at boot. Writes go to [AsyncJobExecutor] keyed
 * on the Master, so two changes to one Master's rows land in order.
 */
@Service
class LocalHonorRegistry(
  private val repository: LocalHonorRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldService: WorldService,
) {

  private data class Key(val masterId: Long, val scope: Scope, val holder: Long)

  private val rows = HashMap<Key, LocalHonor>()

  private val nextId = AtomicLong(1)

  val size: Int
    get() {
      return rows.size
    }

  fun ledgerOf(masterId: Long, scope: Scope, holder: Long): HonorLedger? {
    return rows[Key(masterId, scope, holder)]?.ledger
  }

  /** Every row of one scope, for the gossip sweep. */
  fun all(scope: Scope): List<LocalHonor> {
    return rows.values.filter { it.scope == scope }
  }

  /** @param untoldLoss added to what a witness has yet to tell their town; only meaningful for a grudge */
  fun store(masterId: Long, scope: Scope, holder: Long, ledger: HonorLedger, masterName: String, untoldLoss: Int = 0) {
    val row = rows.getOrPut(Key(masterId, scope, holder)) {
      LocalHonor(
        id = nextId.getAndIncrement(),
        masterId = masterId,
        scope = scope,
        holder = holder,
        worldShapeVersion = worldService.record.shapeVersion,
        pipelineVersion = worldService.record.pipelineVersion,
      )
    }
    row.ledger = ledger
    row.masterName = masterName
    row.untold += untoldLoss

    save(row)
  }

  /** The witness has told their town everything they saw. */
  fun told(row: LocalHonor) {
    row.untold = 0
    save(row)
  }

  private fun save(row: LocalHonor) {
    val detached = row.copy()
    asyncJobExecutor.submit(row.masterId) { repository.save(detached) }
  }

  /** Boot-time only. Discards rows of a different world, as `RumourRegistry.loadAll` does. */
  fun loadAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion

    val (valid, orphaned) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion
    }

    valid.forEach { rows[Key(it.masterId, it.scope, it.holder)] = it }
    nextId.set((valid.maxOfOrNull { it.id } ?: 0L) + 1)

    if (orphaned.isNotEmpty()) {
      repository.deleteAll(orphaned)
      LOG.warn { "${orphaned.size} local honor row(s) do not belong to this world; discarded" }
    }

    LOG.info { "Loaded $size local honor row(s)" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
