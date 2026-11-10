package net.bestia.zone.auction

import net.bestia.bnet.proto.AuctionHouseOpenSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId
import java.time.Instant

data class AuctionHouseOpenSMSG(
  val houseEntityId: EntityId,
  val townName: String,
  val linkedTownNames: List<String>,
  val ownerName: String = "",
  val ownerFeePercent: Int = 0,
  val owner: OwnerView? = null,
) : SMSG {

  /** What only the owner of a player house sees. */
  data class OwnerView(
    val maxFeePercent: Int,
    val till: Long,
    val feeChangeableAt: Instant?,
    val canTearDown: Boolean,
  )

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val open = AuctionHouseOpenSmsgProto.AuctionHouseOpenSMSG.newBuilder()
      .setHouseEntityId(houseEntityId)
      .setTownName(townName)
      .addAllLinkedTownNames(linkedTownNames)
      .setOwnerName(ownerName)
      .setOwnerFeePercent(ownerFeePercent)

    owner?.let {
      open.setIsOwner(true)
        .setMaxOwnerFeePercent(it.maxFeePercent)
        .setTill(it.till)
        .setFeeChangeableAtEpochSeconds(it.feeChangeableAt?.epochSecond ?: 0)
        .setCanTearDown(it.canTearDown)
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setAuctionHouseOpen(open.build())
      .build()
  }
}
