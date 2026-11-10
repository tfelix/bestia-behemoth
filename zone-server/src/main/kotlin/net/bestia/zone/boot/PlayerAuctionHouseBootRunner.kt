package net.bestia.zone.boot

import net.bestia.zone.auction.PlayerHouses
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Loads who owns each player auction house, so a click on one needs no database read. With the player towns. */
@Component
@Order(114)
class PlayerAuctionHouseBootRunner(
  private val houses: PlayerHouses,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    houses.loadAll()
  }
}
