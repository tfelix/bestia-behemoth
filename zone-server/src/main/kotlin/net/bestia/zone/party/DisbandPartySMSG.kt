package net.bestia.zone.party

import net.bestia.zone.message.SMSG
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.DisbandPartySmsgProto

data class DisbandPartySMSG(
  val partyId: Long
) : SMSG {
  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = DisbandPartySmsgProto.DisbandPartySMSG.newBuilder()
      .setPartyId(partyId)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setDisbandParty(proto)
      .build()
  }
}