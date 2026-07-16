package net.bestia.zone.party

import net.bestia.zone.message.SMSG
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PartyInvitationSmsgProto

data class PartyInvitationSMSG(
  val invitedByMaster: String, // not sure maybe later introduce some sort of generic "player info data",
  val partyId: Long,
  val partyName: String,
  val invitationId: Long
) : SMSG {
  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = PartyInvitationSmsgProto.PartyInvitationSMSG.newBuilder()
      .setInvitedByMaster(invitedByMaster)
      .setPartyId(partyId)
      .setPartyName(partyName)
      .setInvitationId(invitationId)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setPartyInvitation(proto)
      .build()
  }
}