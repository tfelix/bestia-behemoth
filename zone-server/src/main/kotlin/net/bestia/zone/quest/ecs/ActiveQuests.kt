package net.bestia.zone.quest.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.entity.observe.WorldTarget

/**
 * The personal objectives a played master counts on the tick, indexed by what triggers them. A kill asks one map
 * lookup by species, a deed one by target; a master with nothing to count pays one miss. Never synced: the log
 * goes out as messages.
 */
class ActiveQuests(
  val masterId: Long,
  tracked: TrackedObjectives = TrackedObjectives(),
) : Component {

  private val killsBySpecies = HashMap<Long, MutableList<TrackedKill>>()
  private val deedsByTarget = HashMap<WorldTarget, MutableList<TrackedDeed>>()

  init {
    track(tracked)
  }

  fun killsOf(speciesId: Long): List<TrackedKill> {
    return killsBySpecies[speciesId] ?: emptyList()
  }

  fun deedsTo(target: WorldTarget): List<TrackedDeed> {
    return deedsByTarget[target] ?: emptyList()
  }

  fun track(tracked: TrackedObjectives) {
    tracked.kills.forEach { killsBySpecies.getOrPut(it.speciesId) { ArrayList() }.add(it) }
    tracked.deeds.forEach { deedsByTarget.getOrPut(it.target) { ArrayList() }.add(it) }
  }

  fun untrack(questId: Long) {
    killsBySpecies.values.forEach { tracked -> tracked.removeAll { it.questId == questId } }
    killsBySpecies.values.removeAll { it.isEmpty() }
    deedsByTarget.values.forEach { tracked -> tracked.removeAll { it.questId == questId } }
    deedsByTarget.values.removeAll { it.isEmpty() }
  }
}
