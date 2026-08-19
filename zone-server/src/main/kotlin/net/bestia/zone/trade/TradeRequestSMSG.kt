package net.bestia.zone.trade

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.TradeRequestSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

/**
 * The prompt raised on the asked player's client. Answered with [AnswerTradeRequestCMSG].
 *
 * Not an [net.bestia.zone.message.EntitySMSG] despite naming an entity: it is addressed to an account, and
 * the client routes entity messages through its entity system, where a prompt has no business being.
 */
data class TradeRequestSMSG(
  val tradeId: Long,
  val fromEntityId: EntityId,
  val fromMasterName: String,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val request = TradeRequestSmsgProto.TradeRequestSMSG.newBuilder()
      .setTradeId(tradeId)
      .setFromEntityId(fromEntityId)
      .setFromMasterName(fromMasterName)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setTradeRequest(request)
      .build()
  }
}
