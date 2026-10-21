package net.bestia.zone.party

import net.bestia.zone.battle.ecs.level.LevelUpListener
import net.bestia.zone.session.MasterPresenceChangedEvent
import net.bestia.zone.util.AccountId
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * The roster changes that do not go through [PartyService]: a member coming online or going offline, and a
 * member or the bestia they play gaining a level. Both arrive on threads that must not wait, so each only queues
 * a roster.
 */
@Component
class PartyRosterTriggers(
  private val publisher: PartyRosterPublisher,
) : LevelUpListener {

  @EventListener
  fun onPresenceChanged(event: MasterPresenceChangedEvent) {
    publisher.requestForMaster(event.masterId)
  }

  override fun leveledUp(accountId: AccountId, masterId: Long, isMaster: Boolean) {
    publisher.requestForMaster(masterId)
  }
}
