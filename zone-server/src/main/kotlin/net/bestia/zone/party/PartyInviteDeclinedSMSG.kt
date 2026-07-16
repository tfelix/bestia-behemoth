package net.bestia.zone.party

import net.bestia.zone.message.SMSG
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PartyInviteDeclinedSmsgProto

data class PartyInviteDeclinedSMSG(
  val invitationId: Long,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = PartyInviteDeclinedSmsgProto.PartyInviteDeclinedSMSG.newBuilder()
      .setInvitationId(invitationId)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setPartyInviteDeclined(proto)
      .build()
  }
}
