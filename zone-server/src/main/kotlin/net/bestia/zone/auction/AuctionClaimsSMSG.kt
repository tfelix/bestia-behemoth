package net.bestia.zone.auction

import net.bestia.bnet.proto.AuctionClaimsSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.auction.persistence.AuctionClaim
import net.bestia.zone.message.SMSG

data class AuctionClaimsSMSG(
  val claims: List<Row>,
) : SMSG {

  data class Row(val claimId: Long, val reason: AuctionClaim.Reason, val coins: Long, val itemId: Long, val amount: Int)

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val claims = AuctionClaimsSmsgProto.AuctionClaimsSMSG.newBuilder()
      .addAllClaims(claims.map(::toProto))
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setAuctionClaims(claims)
      .build()
  }

  private fun toProto(row: Row): AuctionClaimsSmsgProto.AuctionClaim {
    return AuctionClaimsSmsgProto.AuctionClaim.newBuilder()
      .setClaimId(row.claimId)
      .setReason(
        when (row.reason) {
          AuctionClaim.Reason.SOLD -> AuctionClaimsSmsgProto.AuctionClaim.Reason.SOLD
          AuctionClaim.Reason.RETURNED -> AuctionClaimsSmsgProto.AuctionClaim.Reason.RETURNED
        }
      )
      .setCoins(row.coins)
      .setItemId(row.itemId)
      .setAmount(row.amount)
      .build()
  }
}
