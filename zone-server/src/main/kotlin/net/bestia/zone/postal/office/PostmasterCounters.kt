package net.bestia.zone.postal.office

import net.bestia.zone.townsfolk.conversation.Speaker
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Component

/** A generated town's post office is open while its postmaster is at work. */
@Component
class PostmasterCounters(
  private val offices: PostOffices,
  private val clock: BestiaClock,
) {

  /** Null unless [speaker] is a postmaster. */
  fun counterOf(speaker: Speaker): PostCounter? {
    if (speaker.business != PostOffices.POST_OFFICE) return null

    val atWork = speaker.merchant.hours.isOpenAt(clock.now().minuteOfDay)

    return PostCounter(speaker.entityId, if (atWork) offices.officeOf(speaker.settlement) else null)
  }
}
