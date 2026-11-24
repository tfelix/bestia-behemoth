package net.bestia.zone.bestiastorage

import net.bestia.zone.townsfolk.conversation.ServiceCounter
import net.bestia.zone.townsfolk.conversation.Speaker
import net.bestia.zone.util.AccountId
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Component

/** A Bestia Keeper at work opens the storage instead of a conversation, as a postmaster opens the post office. */
@Component
class BestiaKeeperDesk(
  private val storage: BestiaStorageService,
  private val clock: BestiaClock,
) : ServiceCounter {

  override fun serve(accountId: AccountId, speaker: Speaker): Boolean {
    if (speaker.business != BESTIA_CENTER) {
      return false
    }
    if (!speaker.merchant.hours.isOpenAt(clock.now().minuteOfDay)) {
      return false
    }

    storage.openAtCounter(accountId)

    return true
  }

  companion object {
    /** The trade in `BusinessCatalogue`. */
    const val BESTIA_CENTER = "bestia_center"
  }
}
