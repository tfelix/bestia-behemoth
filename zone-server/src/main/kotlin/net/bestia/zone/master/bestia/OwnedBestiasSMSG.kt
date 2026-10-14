package net.bestia.zone.master.bestia

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.OwnedBestiasSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.message.SelfSMSG

data class OwnedBestiasSMSG(
  val bestias: List<SelfSMSG.BestiaInfo>,
  val bestiaSlotCount: Int
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val owned = OwnedBestiasSmsgProto.OwnedBestiasSMSG.newBuilder()
      .setBestiaSlotCount(bestiaSlotCount)
    bestias.forEach { owned.addBestias(it.toBnet()) }

    return EnvelopeProto.Envelope.newBuilder()
      .setOwnedBestias(owned)
      .build()
  }
}
