package net.bestia.zone.quest.ecs

import net.bestia.zone.ecs.core.Component

/**
 * The personal objectives a played master counts on the tick, indexed by what triggers them. A kill asks one map
 * lookup by species; a master with nothing to count pays one miss. Never synced: the log goes out as messages.
 */
class ActiveQuests(
  val masterId: Long,
  kills: List<TrackedKill> = emptyList(),
) : Component {

  private val killsBySpecies = HashMap<Long, MutableList<TrackedKill>>()

  init {
    track(kills)
  }

  fun killsOf(speciesId: Long): List<TrackedKill> {
    return killsBySpecies[speciesId] ?: emptyList()
  }

  fun track(kills: List<TrackedKill>) {
    kills.forEach { killsBySpecies.getOrPut(it.speciesId) { ArrayList() }.add(it) }
  }

  fun untrack(questId: Long) {
    killsBySpecies.values.forEach { tracked -> tracked.removeAll { it.questId == questId } }
    killsBySpecies.values.removeAll { it.isEmpty() }
  }
}
