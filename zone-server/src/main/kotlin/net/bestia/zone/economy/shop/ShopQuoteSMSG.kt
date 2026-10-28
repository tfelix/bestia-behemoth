package net.bestia.zone.economy.shop

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.ShopQuoteSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

/** What a basket comes to; see `shop_quote_smsg.proto`. */
data class ShopQuoteSMSG(
  val merchantEntityId: EntityId,
  val lineCoins: List<Long>,
  val balance: Long,
  val refusal: OpError? = null,
  val refusedLine: Int? = null,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val quote = ShopQuoteSmsgProto.ShopQuoteSMSG.newBuilder()
      .setMerchantEntityId(merchantEntityId)
      .addAllLineCoins(lineCoins)
      .setBalance(balance)
      .setRefusedLine(refusedLine ?: NO_LINE)
    refusal?.let { quote.setRefusal(it) }

    return EnvelopeProto.Envelope.newBuilder()
      .setShopQuoteResult(quote.build())
      .build()
  }

  private companion object {
    const val NO_LINE = -1
  }
}
