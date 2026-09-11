package net.bestia.zone.ai.rumour

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong

/**
 * What each town has heard lately, and the only place that answer is kept.
 *
 * ### Tick-thread only, the convention `ScorchRegistry` documents for its own map
 *
 * Written by whatever posts news and read by conversation, both on the tick thread; [loadAll] runs once
 * at boot before the loop starts. So a plain `HashMap` is correct rather than merely convenient.
 *
 * ### Durable writes without blocking the tick
 *
 * The in-memory list is updated synchronously so a conversation inside the same tick sees the news; the
 * row goes to [AsyncJobExecutor] keyed on the settlement, whose per-key ordering means a town that hears
 * and then forgets something cannot have those two writes land the wrong way round.
 */
@Service
class RumourRegistry(
  private val repository: RumourRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldService: WorldService,
) {

  private val bySettlement = HashMap<Int, MutableList<Rumour>>()

  private val nextId = AtomicLong(1)

  val size: Int
    get() {
      return bySettlement.values.sumOf { it.size }
    }

  /** What this town has heard, newest first. Empty for almost every town almost always. */
  fun heardBy(settlement: Int): List<Rumour> {
    return bySettlement[settlement].orEmpty()
  }

  fun settlementsWithNews(): List<Int> {
    return bySettlement.keys.toList()
  }

  /**
   * Records that one town heard something, and returns the row so a caller can log it.
   *
   * The id is allocated here rather than by the database because it is also the conversation topic id -
   * see [Rumour]. A generated key would not be known until the async write had landed, and by then the
   * conversation that wanted to offer the news has already been built.
   */
  fun add(
    settlement: Int,
    kind: RumourKind,
    slots: String,
    postedOnDay: Double,
    expiresOnDay: Double,
    strength: Double,
  ): Rumour {
    val rumour = Rumour(
      id = nextId.getAndIncrement(),
      settlement = settlement,
      kind = kind,
      slots = slots,
      postedOnDay = postedOnDay,
      expiresOnDay = expiresOnDay,
      strength = strength,
      worldShapeVersion = worldService.record.shapeVersion,
      pipelineVersion = worldService.record.pipelineVersion,
    )

    bySettlement.getOrPut(settlement) { ArrayList() }.add(0, rumour)

    asyncJobExecutor.submit(settlement) { repository.save(rumour) }

    return rumour
  }

  /** Drops one town's expired news. Called by the sweep, which is the only thing that forgets. */
  fun forget(settlement: Int, expired: List<Rumour>) {
    if (expired.isEmpty()) {
      return
    }

    val remaining = bySettlement[settlement] ?: return
    remaining.removeAll(expired.toSet())

    if (remaining.isEmpty()) {
      bySettlement.remove(settlement)
    }

    val ids = expired.map { it.id }
    asyncJobExecutor.submit(settlement) { repository.deleteAllById(ids) }
  }

  /**
   * Boot-time only. Discards - not merely skips - any row belonging to a different world.
   *
   * `ScorchRegistry.loadAll`'s reasoning exactly: settlement indices are dense and re-used, so a
   * surviving row would be attached to a different town rather than to none.
   */
  fun loadAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion

    val (valid, orphaned) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion
    }

    valid.forEach { bySettlement.getOrPut(it.settlement) { ArrayList() }.add(it) }
    bySettlement.values.forEach { it.sortByDescending { row -> row.postedOnDay } }

    nextId.set((valid.maxOfOrNull { it.id } ?: 0L) + 1)

    if (orphaned.isNotEmpty()) {
      repository.deleteAll(orphaned)
      LOG.warn { "${orphaned.size} rumour row(s) do not belong to this world; discarded" }
    }

    LOG.info { "Loaded $size rumour(s) across ${bySettlement.size} settlement(s)" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
