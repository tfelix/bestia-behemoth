package net.bestia.zone.party

import net.bestia.zone.message.SMSG
import net.bestia.bnet.proto.EnvelopeProto

data class DisbandPartySMSG(
  val partyId: Long
) : SMSG {
  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    TODO("Not yet implemented")
  }
}