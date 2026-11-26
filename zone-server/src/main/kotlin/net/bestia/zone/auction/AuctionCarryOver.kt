package net.bestia.zone.auction

import net.bestia.zone.auction.persistence.AuctionListingRepository
import net.bestia.zone.auction.persistence.AuctionPickupRepository
import net.bestia.zone.auction.persistence.PlayerAuctionHouseRepository
import net.bestia.zone.auction.persistence.TradeLinkRepository
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** The market of an ended world closes for good: listings, unclaimed goods, player auction houses and their links. */
@Component
class AuctionCarryOver(
  private val listings: AuctionListingRepository,
  private val pickups: AuctionPickupRepository,
  private val houses: PlayerAuctionHouseRepository,
  private val links: TradeLinkRepository,
) {

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.CARRY_OVER)
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    if (!event.isRebirth) {
      return
    }
    listings.deleteAll()
    pickups.deleteAll()
    links.deleteAll()
    houses.deleteAll()
  }
}
