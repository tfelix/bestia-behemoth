package net.bestia.zone.dialog.conversation

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.economy.shop.MerchantStock
import net.bestia.zone.economy.shop.ShopOfferPublisher
import org.springframework.stereotype.Component

/**
 * The counter, reached by asking for it.
 *
 * An action rather than a question, and the first of them: picking it opens the window instead of
 * answering. The reply still arrives, because a conversation that simply stopped dead on a choice reads
 * as a bug however well the window behind it works.
 *
 * Pinned, on the tier's own argument: an option a player cannot rely on is one they never learn to look
 * for, and a merchant is exactly the case that has to be relied upon.
 */
@Component
class ShopTopicProvider(
  private val merchants: MerchantStock,
  private val economy: SettlementEconomyService,
  private val offers: ShopOfferPublisher,
  private val lines: ConversationLineCatalogue,
  private val world: WorldView,
) : DialogTopicProvider {

  override val pinned = true

  /** After the occupation, so "what do you do" reads before "show me it". */
  override val order = 5

  override fun owns(topicId: Int): Boolean {
    return Topics.owns(Topics.SHOP, topicId)
  }

  override fun rootOptions(speaker: Speaker): List<ConversationOption> {
    if (merchants.forBusiness(speaker.business) == null) return emptyList()

    return listOf(
      ConversationOption(Topics.SHOP + BUY, Line(ConversationKeys.SHOP_BUY_ASK), OptionKind.ACTION),
      ConversationOption(Topics.SHOP + SELL, Line(ConversationKeys.SHOP_SELL_ASK), OptionKind.ACTION),
    )
  }

  /**
   * Both options open the same window, because both directions of a trade happen at one counter. They
   * are two options rather than one so a player who wants to sell is told they can before they have
   * worked out that the shop takes things as well.
   */
  override fun nodeFor(asker: Asker, speaker: Speaker, topicId: Int): ConversationNode? {
    if (Topics.localOf(Topics.SHOP, topicId) !in setOf(BUY, SELL)) return null

    val stocked = merchants.forBusiness(speaker.business) ?: return null

    // Whose prices is still decided by where the player is standing, never by who they asked - the
    // merchant only chooses the counter. See `OpenShopHandler`, which answers the same question.
    val shop = world.read {
      get(asker.entityId, Position::class)?.toVec3L()?.let { economy.shopAt(it.x, it.y) }
    } ?: return null

    offers.publishTo(asker.accountId, shop.first, shop.second, stocked)

    return ConversationNode(lines.lineFor(speaker, ConversationKeys.SHOP_OPENED), emptyList())
  }

  private companion object {
    const val BUY = 1
    const val SELL = 2
  }
}
