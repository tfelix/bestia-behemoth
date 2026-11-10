package net.bestia.zone.boot

import net.bestia.zone.auction.PlayerHouseService
import net.bestia.zone.auction.PlayerHouses
import net.bestia.zone.auction.TradeAgreementService
import net.bestia.zone.auction.TradeLinks
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Loads who owns each player auction house and who has a Trade Agreement with whom, so neither a click nor a search
 * needs a database read. With the player towns.
 */
@Component
@Order(114)
class PlayerAuctionHouseBootRunner(
  private val houses: PlayerHouses,
  private val links: TradeLinks,
  private val houseService: PlayerHouseService,
  private val agreements: TradeAgreementService,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    houses.loadAll()
    links.loadAll()
    houseService.resolveSkills()
    agreements.resolveSkills()
  }
}
