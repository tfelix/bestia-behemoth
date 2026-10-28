package net.bestia.zone.townsfolk.conversation

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.dialog.DialogArg
import net.bestia.zone.economy.shop.Counter
import net.bestia.zone.economy.shop.MerchantStock
import net.bestia.zone.economy.shop.ShopCounter
import org.springframework.stereotype.Component

/**
 * The counter, as a conversation reaches it.
 *
 * A merchant at work is not talked to first: `TalkService` opens their window directly, and this option
 * is the way back to it from *Chat*. Off shift it becomes the question of when they open.
 *
 * Pinned, on the tier's own argument: an option a player cannot rely on is one they never learn to look
 * for, and a merchant is exactly the case that has to be relied upon.
 */
@Component
class ShopTopicProvider(
  private val merchants: MerchantStock,
  private val shops: ShopCounter,
  private val lines: ConversationLineCatalogue,
) : DialogTopicProvider {

  override val pinned = true

  /** After the occupation, so "what do you do" reads before "show me it". */
  override val order = 5

  override fun owns(topicId: Int): Boolean {
    return Topics.owns(Topics.SHOP, topicId)
  }

  override fun rootOptions(speaker: Speaker): List<ConversationOption> {
    val counter = counterOf(speaker) ?: return emptyList()

    val option = if (shops.isOpen(counter)) {
      ConversationOption(Topics.SHOP + TRADE, Line(ConversationKeys.SHOP_TRADE_ASK), OptionKind.ACTION)
    } else {
      ConversationOption(Topics.SHOP + HOURS, Line(ConversationKeys.SHOP_HOURS_ASK))
    }

    return listOf(option)
  }

  override fun nodeFor(asker: Asker, speaker: Speaker, topicId: Int): ConversationNode? {
    val counter = counterOf(speaker) ?: return null

    return when (Topics.localOf(Topics.SHOP, topicId)) {
      TRADE -> when (shops.open(asker.accountId, asker.entityId, counter)) {
        null -> ConversationNode(lines.lineFor(speaker, ConversationKeys.SHOP_OPENED), emptyList())
        // The shift ended between the option and the click.
        OpError.SHOP_CLOSED -> hoursNode(speaker, counter)
        else -> null
      }

      HOURS -> hoursNode(speaker, counter)
      else -> null
    }
  }

  private fun counterOf(speaker: Speaker): Counter? {
    return merchants.counterOf(speaker.entityId, speaker.merchant)
  }

  private fun hoursNode(speaker: Speaker, counter: Counter): ConversationNode? {
    val opens = counter.hours.opensAtMinute ?: return null
    val closes = counter.hours.closesAtMinute ?: return null

    val args = mapOf(
      ConversationKeys.SLOT_OPENS to DialogArg.Number(nearestHour(opens)),
      ConversationKeys.SLOT_CLOSES to DialogArg.Number(nearestHour(closes)),
    )

    return ConversationNode(lines.lineFor(speaker, ConversationKeys.SHOP_HOURS, args), emptyList())
  }

  /** Spoken, so a merchant who opens at 7:12 says seven. */
  private fun nearestHour(minuteOfDay: Int): Long {
    val hour = (minuteOfDay + HourWindow.MINUTES_PER_HOUR / 2) / HourWindow.MINUTES_PER_HOUR

    return (hour % HOURS_PER_DAY).toLong()
  }

  private companion object {
    const val TRADE = 1
    const val HOURS = 2
    const val HOURS_PER_DAY = 24
  }
}
