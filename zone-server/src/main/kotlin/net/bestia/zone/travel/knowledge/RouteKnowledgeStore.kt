package net.bestia.zone.travel.knowledge

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.travel.persistence.RouteBlockage
import net.bestia.zone.travel.persistence.RouteBlockageRepository
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Keeps each master's [RouteKnowledge] across restarts: it is meant to last weeks. A creature's is not kept.
 *
 * Writes go through [AsyncJobExecutor] keyed on the master, so a blockage seen and then seen cleared cannot
 * land in the wrong order.
 */
@Service
class RouteKnowledgeStore(
  private val knowledge: RouteKnowledge,
  private val repository: RouteBlockageRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldService: WorldService,
) {

  init {
    knowledge.onLearned { knower, edge, lapsesAt ->
      if (knower is Knower.Player) write(knower.masterId, edge, lapsesAt)
    }
  }

  /** Boot-time only. Rows of another world, or lapsed ones, are deleted rather than skipped. */
  fun loadAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion

    val (valid, stale) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion &&
        !knowledge.isLapsed(it.lapsesAt)
    }

    valid.forEach { knowledge.restore(Knower.Player(it.id.masterId), it.id.edgeIndex, it.lapsesAt) }

    if (stale.isNotEmpty()) repository.deleteAll(stale)

    LOG.info { "Loaded ${valid.size} known route blockage(s), dropped ${stale.size}" }
  }

  private fun write(masterId: Long, edge: Int, lapsesAt: Instant?) {
    val key = RouteBlockage.Key(masterId, edge)

    if (lapsesAt == null) {
      asyncJobExecutor.submit(masterId) { repository.deleteById(key) }
      return
    }

    val row = RouteBlockage(
      id = key,
      lapsesAt = lapsesAt,
      worldShapeVersion = worldService.record.shapeVersion,
      pipelineVersion = worldService.record.pipelineVersion,
    )
    asyncJobExecutor.submit(masterId) { repository.save(row) }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
