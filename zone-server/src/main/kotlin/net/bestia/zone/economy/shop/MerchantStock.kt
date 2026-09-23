package net.bestia.zone.economy.shop

import net.bestia.zone.dialog.conversation.SpeakerResolver
import net.bestia.zone.economy.EconomyCatalogue
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * What one shopkeeper will deal in.
 *
 * The prices stay the settlement's: a town has one market and one price for a loaf whoever is holding
 * it. What a merchant decides is the counter. A miller takes grain and hands back flour and has never
 * had a loaf to sell, and that is most of what makes a town feel like somewhere people work rather than
 * one shop with several faces.
 */
@Service
class MerchantStock(
  private val catalogue: EconomyCatalogue,
  private val speakers: SpeakerResolver,
) {

  /** Commodity ids this entity trades in, or null when they keep no shop at all. */
  fun of(entityId: EntityId): Set<String>? {
    val business = speakers.of(entityId)?.business ?: return null

    // A general store holds stock without producing any of it, which is what makes it the one counter a
    // player can rely on finding the ordinary things behind.
    if (business in catalogue.retailTrades()) {
      return catalogue.commodities().mapTo(HashSet()) { it.id }
    }

    val trade = catalogue.tradeOfBusiness(business) ?: return null

    // Inputs as well as the output, because that is what a trade is. A miller who would not buy grain is
    // a miller nobody can sell grain to, and hauling - the whole merchant profession - stops existing.
    return (trade.consumes.map { it.commodity } + trade.produces).toSet()
  }
}
