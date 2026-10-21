package net.bestia.zone.party

import net.bestia.zone.message.SMSG
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PartyInvitationSmsgProto

data class PartyInvitationSMSG(
  val invitedByMaster: String,
  val partyId: Long,
  val partyName: String,
  val invitationId: Long,
  val expiresInSeconds: Int,
) : SMSG {
  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = PartyInvitationSmsgProto.PartyInvitationSMSG.newBuilder()
      .setInvitedByMaster(invitedByMaster)
      .setPartyId(partyId)
      .setPartyName(partyName)
      .setInvitationId(invitationId)
      .setExpiresInSeconds(expiresInSeconds)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setPartyInvitation(proto)
      .build()
  }
}
