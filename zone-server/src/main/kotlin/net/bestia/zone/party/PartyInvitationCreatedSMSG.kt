package net.bestia.zone.party

import net.bestia.zone.message.SMSG
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PartyInvitationCreatedSmsgProto

data class PartyInvitationCreatedSMSG(
  val invitationId: Long,
  val invitedAccountId: Long,
  val status: InvitationStatus
) : SMSG {

  enum class InvitationStatus {
    CREATED,
    ALREADY_IN_PARTY,
    IGNORED
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val protoStatus = when (status) {
      InvitationStatus.CREATED -> PartyInvitationCreatedSmsgProto.InvitationStatus.INVITATION_CREATED
      InvitationStatus.ALREADY_IN_PARTY -> PartyInvitationCreatedSmsgProto.InvitationStatus.INVITATION_ALREADY_IN_PARTY
      InvitationStatus.IGNORED -> PartyInvitationCreatedSmsgProto.InvitationStatus.INVITATION_IGNORED
    }

    val proto = PartyInvitationCreatedSmsgProto.PartyInvitationCreatedSMSG.newBuilder()
      .setInvitationId(invitationId)
      .setInvitedAccountId(invitedAccountId)
      .setStatus(protoStatus)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setPartyInvitationCreated(proto)
      .build()
  }
}