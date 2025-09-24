package net.bestia.zone.party

import net.bestia.zone.message.SMSG
import net.bestia.bnet.proto.EnvelopeProto

data class PartyErrorSMSG(
  val error: PartyErrorCode
) : SMSG {

  enum class PartyErrorCode {
    NO_PARTY,
    PLAYER_NOT_FOUND,
    INVITE_EXPIRED,
    NO_PERMISSION,
    ALREADY_IN_PARTY,
    PARTY_NAME_IN_USE
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    TODO("Not yet implemented")
  }
}