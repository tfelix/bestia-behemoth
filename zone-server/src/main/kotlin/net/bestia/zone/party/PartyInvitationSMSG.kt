package net.bestia.zone.party

import net.bestia.zone.message.SMSG
import net.bestia.bnet.proto.EnvelopeProto

data class PartyInvitationSMSG(
  val invitedByMaster: String, // not sure maybe later introduce some sort of generic "player info data",
  val partyId: Long,
  val partyName: String,
  val invitationId: Long
) : SMSG {
  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    TODO("Not yet implemented")
  }
}