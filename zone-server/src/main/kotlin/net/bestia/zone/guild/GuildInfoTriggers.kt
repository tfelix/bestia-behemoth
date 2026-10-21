package net.bestia.zone.guild

import net.bestia.zone.battle.ecs.level.LevelUpListener
import net.bestia.zone.session.MasterPresenceChangedEvent
import net.bestia.zone.util.AccountId
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * The guild changes that do not go through [GuildService]: a member coming online or going offline, and a
 * member's master gaining a level. Both arrive on threads that must not wait, so each only queues a push.
 */
@Component
class GuildInfoTriggers(
  private val publisher: GuildInfoPublisher,
) : LevelUpListener {

  @EventListener
  fun onPresenceChanged(event: MasterPresenceChangedEvent) {
    publisher.requestForMaster(event.masterId)
  }

  /** The guild window lists masters only, so a bestia's level is none of its business. */
  override fun leveledUp(accountId: AccountId, masterId: Long, isMaster: Boolean) {
    if (isMaster) {
      publisher.requestForMaster(masterId)
    }
  }
}
