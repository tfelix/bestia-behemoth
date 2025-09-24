package net.bestia.zone.party

import net.bestia.zone.message.SMSG
import net.bestia.bnet.proto.EnvelopeProto

data class PartyInvitationCreatedSMSG(
  val invitationId: Long,
  val invitedPlayerEntityId: Long,
  val status: InvitationStatus
) : SMSG {

  enum class InvitationStatus {
    CREATED,
    ALREADY_IN_PARTY,
    IGNORED
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    TODO("Not yet implemented")
  }
}