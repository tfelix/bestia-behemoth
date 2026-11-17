package net.bestia.zone.quest

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.entity.observe.KillObserver
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.place.ecs.Place
import net.bestia.zone.quest.ecs.ActiveQuests
import net.bestia.zone.quest.ecs.TrackedKill
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Counts kills towards personal kill objectives, on the tick. Cheapest test first: the species lookup, then the
 * deadline, then the area. A counted kill is saved off the tick, and the save sends the log update.
 */
@Component
class QuestKillObserver(
  private val questService: QuestService,
  private val jobs: AsyncJobExecutor,
  private val clock: Clock = Clock.systemUTC(),
) : KillObserver {

  override val reads: ComponentClassSet = setOf(ActiveQuests::class, Place::class)
  override val writes: ComponentClassSet = setOf(ActiveQuests::class)

  override fun onKill(world: World, kill: KillFact) {
    for (masterEntityId in kill.creditedMasters) {
      val active = world.get(masterEntityId, ActiveQuests::class) ?: continue
      val candidates = active.killsOf(kill.speciesId)
      if (candidates.isEmpty()) {
        continue
      }

      val killerPlace = world.get(masterEntityId, Place::class)?.place
      val now = clock.instant()
      candidates
        .filter { it.counts(now, killerPlace, kill.position) }
        .forEach { countKill(active.masterId, it) }
    }
  }

  private fun countKill(masterId: Long, tracked: TrackedKill) {
    tracked.count += 1
    val count = tracked.count
    jobs.submit(masterId) { questService.recordPersonal(masterId, tracked.questId, tracked.objectiveIndex, count) }
  }
}
