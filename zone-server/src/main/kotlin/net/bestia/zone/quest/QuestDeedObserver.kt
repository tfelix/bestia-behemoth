package net.bestia.zone.quest

import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.observe.DeedFact
import net.bestia.zone.entity.observe.DeedObserver
import net.bestia.zone.fact.ecs.KnownFacts
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.quest.ecs.ActiveQuests
import net.bestia.zone.quest.ecs.TrackedDeed
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Counts examining and talking towards personal objectives, on the tick. The target lookup comes first, so a
 * click on anything no quest points at costs one miss. A counted deed is saved off the tick.
 */
@Component
class QuestDeedObserver(
  private val questService: QuestService,
  private val jobs: AsyncJobExecutor,
  sessions: ConnectionInfoService,
  private val masters: MasterEntityResolver = MasterEntityResolver(sessions),
  private val clock: Clock = Clock.systemUTC(),
) : DeedObserver {

  override fun onDeed(world: World, deed: DeedFact) {
    val masterEntityId = masters.masterEntityOf(world, deed.actorId) ?: return
    val active = world.get(masterEntityId, ActiveQuests::class) ?: return
    val candidates = active.deedsTo(deed.target)
    if (candidates.isEmpty()) {
      return
    }

    val now = clock.instant()
    val known = world.get(masterEntityId, KnownFacts::class)
    candidates
      .filter { it.counts(now, deed.deed) }
      .filter { it.needsFact == null || known?.knows(it.needsFact) == true }
      .forEach { count(active.masterId, it) }
  }

  private fun count(masterId: Long, tracked: TrackedDeed) {
    tracked.count += 1
    val count = tracked.count
    jobs.submit(masterId) { questService.recordPersonal(masterId, tracked.questId, tracked.objectiveIndex, count) }
  }
}
