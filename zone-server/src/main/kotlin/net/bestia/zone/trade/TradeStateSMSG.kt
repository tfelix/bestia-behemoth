package net.bestia.zone.trade

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.TradeStateSmsgProto
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

/**
 * The whole trade as one recipient sees it - which is why "own" and "partner" mean different things in the
 * two copies sent for the same trade.
 *
 * Always a full snapshot. A refused action is answered by re-sending this, which snaps an optimistic client
 * back without either end needing a per-refusal code.
 */
data class TradeStateSMSG(
  val tradeId: Long,
  val status: Status,
  val partnerMasterName: String,
  val partnerEntityId: EntityId,
  val ownOffer: List<ReservedItem>,
  val partnerOffer: List<ReservedItem>,
  val ownLocked: Boolean,
  val partnerLocked: Boolean,
  val ownConfirmed: Boolean,
  val partnerConfirmed: Boolean,
) : SMSG {

  /** The subset of [TradeStatus] a client is ever shown; the rest are moments with no window to draw. */
  enum class Status { OPEN, LOCKED, COMPLETED, CANCELLED }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val state = TradeStateSmsgProto.TradeStateSMSG.newBuilder()
      .setTradeId(tradeId)
      .setStatus(mapStatus(status))
      .setPartnerMasterName(partnerMasterName)
      .setPartnerEntityId(partnerEntityId)
      .addAllOwnOffer(ownOffer.map { mapItem(it) })
      .addAllPartnerOffer(partnerOffer.map { mapItem(it) })
      .setOwnLocked(ownLocked)
      .setPartnerLocked(partnerLocked)
      .setOwnConfirmed(ownConfirmed)
      .setPartnerConfirmed(partnerConfirmed)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setTradeState(state)
      .build()
  }

  private fun mapStatus(status: Status): TradeStateSmsgProto.TradeStatus = when (status) {
    Status.OPEN -> TradeStateSmsgProto.TradeStatus.TRADE_STATUS_OPEN
    Status.LOCKED -> TradeStateSmsgProto.TradeStatus.TRADE_STATUS_LOCKED
    Status.COMPLETED -> TradeStateSmsgProto.TradeStatus.TRADE_STATUS_COMPLETED
    Status.CANCELLED -> TradeStateSmsgProto.TradeStatus.TRADE_STATUS_CANCELLED
  }

  private fun mapItem(item: ReservedItem): TradeStateSmsgProto.TradeOfferItem =
    TradeStateSmsgProto.TradeOfferItem.newBuilder()
      .setOfferSlotId(item.offerSlotId)
      .setItemId(item.itemId.toInt())
      .setUniqueId(item.uniqueId)
      .setAmount(item.amount)
      .setDurability(item.durability)
      .setMaxDurability(item.maxDurability)
      .setSlots(item.slots)
      .setUpgradeLevel(item.upgradeLevel)
      .build()
}
