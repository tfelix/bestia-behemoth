package net.bestia.zone.quest

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.quest.ecs.ActiveQuests
import net.bestia.zone.quest.ecs.TrackedObjectives
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/** Mirrors committed log changes into the [ActiveQuests] of masters being played. Others load theirs at spawn. */
@Component
class LiveQuests(
  private val world: WorldView,
  private val sessions: ConnectionInfoService,
  private val masters: MasterEntityResolver = MasterEntityResolver(sessions),
) {

  fun track(masterId: Long, tracked: TrackedObjectives) {
    if (tracked.isEmpty) {
      return
    }
    onPlayedMaster(masterId) { it.track(tracked) }
  }

  fun untrack(masterIds: Collection<Long>, questId: Long) {
    masterIds.forEach { masterId -> onPlayedMaster(masterId) { it.untrack(questId) } }
  }

  private fun onPlayedMaster(masterId: Long, change: (ActiveQuests) -> Unit) {
    val accountId = sessions.activeMasters().entries.firstOrNull { it.value == masterId }?.key ?: return
    val masterEntityId = masters.selectedMasterEntityOf(accountId) ?: return

    world.post { get(masterEntityId, ActiveQuests::class)?.let(change) }
  }
}
