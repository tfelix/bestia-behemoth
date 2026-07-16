package net.bestia.zone.party

import net.bestia.zone.message.SMSG
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PartyErrorSmsgProto

data class PartyErrorSMSG(
  val error: PartyErrorCode
) : SMSG {

  enum class PartyErrorCode {
    NO_PARTY,
    PLAYER_NOT_FOUND,
    INVITE_EXPIRED,
    NO_PERMISSION,
    ALREADY_IN_PARTY,
    PARTY_NAME_IN_USE,
    INVALID_PARTY_NAME,
    PARTY_FULL,
    NOT_PARTY_MEMBER
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val protoError = when (error) {
      PartyErrorCode.NO_PARTY -> PartyErrorSmsgProto.PartyErrorCode.NO_PARTY
      PartyErrorCode.PLAYER_NOT_FOUND -> PartyErrorSmsgProto.PartyErrorCode.PLAYER_NOT_FOUND
      PartyErrorCode.INVITE_EXPIRED -> PartyErrorSmsgProto.PartyErrorCode.INVITE_EXPIRED
      PartyErrorCode.NO_PERMISSION -> PartyErrorSmsgProto.PartyErrorCode.NO_PERMISSION
      PartyErrorCode.ALREADY_IN_PARTY -> PartyErrorSmsgProto.PartyErrorCode.ALREADY_IN_PARTY
      PartyErrorCode.PARTY_NAME_IN_USE -> PartyErrorSmsgProto.PartyErrorCode.PARTY_NAME_IN_USE
      PartyErrorCode.INVALID_PARTY_NAME -> PartyErrorSmsgProto.PartyErrorCode.INVALID_PARTY_NAME
      PartyErrorCode.PARTY_FULL -> PartyErrorSmsgProto.PartyErrorCode.PARTY_FULL
      PartyErrorCode.NOT_PARTY_MEMBER -> PartyErrorSmsgProto.PartyErrorCode.NOT_PARTY_MEMBER
    }

    val proto = PartyErrorSmsgProto.PartyErrorSMSG.newBuilder()
      .setError(protoError)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setPartyError(proto)
      .build()
  }
}