package net.bestia.zone.inn

import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.zone.townsfolk.conversation.Speaker
import net.bestia.zone.cartography.SettlementNames
import net.bestia.zone.world.settlement.SettlementSiteIndex
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Component

/** A generated town's inn is open while its innkeeper is at work. Tick thread only, like [SettlementSiteIndex]. */
@Component
class InnKeepers(
  private val siteIndex: SettlementSiteIndex,
  private val names: SettlementNames,
  private val clock: BestiaClock,
) {

  /** Null unless [speaker] is an innkeeper at work. A town with several inns saves every guest at its first. */
  fun counterOf(speaker: Speaker): InnCounter? {
    if (speaker.business != INN) return null
    if (!speaker.merchant.hours.isOpenAt(clock.now().minuteOfDay)) return null

    val building = siteIndex.siteOf(speaker.settlement)?.buildingsFor(INN_TYPE)?.firstOrNull() ?: return null
    val name = names.nameOf(speaker.settlement) ?: return null

    return InnCounter(speaker.entityId, siteIndex.doorstepOf(building), name)
  }

  private companion object {
    /** The trade in `BusinessCatalogue`. */
    const val INN = "inn"
    val INN_TYPE = BusinessCatalogue.ALL.indexOfFirst { it.id == INN }
  }
}
