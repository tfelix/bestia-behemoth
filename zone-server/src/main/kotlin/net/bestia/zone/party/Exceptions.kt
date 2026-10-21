package net.bestia.zone.party

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.BestiaException
import net.bestia.zone.message.OperationErrorSMSG

/**
 * A party request refused for a reason the player is told, as [opError] with its [args]. A refusal an honest
 * client cannot produce uses [OpError.REQUEST_REFUSED].
 */
abstract class PartyException(
  code: String,
  message: String,
  val opError: OpError,
  val args: List<String> = emptyList(),
) : BestiaException(code, message) {

  fun toOperationError(): OperationErrorSMSG = OperationErrorSMSG(opError, args)
}

class NotPartyOwnerException : PartyException(
  code = "NOT_PARTY_OWNER",
  message = "You are not the owner of the party",
  opError = OpError.PARTY_NO_PERMISSION,
)

class AlreadyInPartyException : PartyException(
  code = "ALREADY_IN_PARTY",
  message = "Player is already in a party",
  opError = OpError.PARTY_ALREADY_IN_PARTY,
)

class NotPartyException : PartyException(
  code = "NO_PARTY",
  message = "You are not member of a party",
  opError = OpError.PARTY_NOT_IN_PARTY,
)

class PartyFullException : PartyException(
  code = "PARTY_FULL",
  message = "The party is already full",
  opError = OpError.PARTY_FULL,
)

/** An answer to an invitation addressed to somebody else. The ids are guessable; an honest client never sends one. */
class PartyInviteForbiddenException(
  playerId: Long,
  invitationId: Long
) : PartyException(
  code = "FORBIDDEN_PARTY_INVITE",
  message = "Player $playerId is not allowed to access party invitation $invitationId",
  opError = OpError.REQUEST_REFUSED,
)

/** The party went away under the request: disbanded between the click and its arrival. */
class PartyNotFoundException(partyId: Long) : PartyException(
  code = "PARTY_NOT_FOUND",
  message = "Party $partyId was not found",
  opError = OpError.PARTY_NOT_IN_PARTY,
)

class PartyInvitationExpired : PartyException(
  code = "PARTY_INVITATION_EXPIRED",
  message = "The invitation did expire",
  opError = OpError.PARTY_INVITE_EXPIRED,
)

class InvalidPartyNameException : PartyException(
  code = "INVALID_PARTY_NAME",
  message = "Party name is blank, too long or contains non-ASCII characters",
  opError = OpError.PARTY_INVALID_NAME,
)

class PartyNameInUseException(name: String) : PartyException(
  code = "PARTY_NAME_IN_USE",
  message = "Party name $name is already in use",
  opError = OpError.PARTY_NAME_IN_USE,
)

class NotPartyMemberException(masterName: String) : PartyException(
  code = "NOT_PARTY_MEMBER",
  message = "$masterName is not a member of the party",
  opError = OpError.PARTY_NOT_A_MEMBER,
  args = listOf(masterName),
)

/** No master of that name, or nobody is playing it - one answer for both, as for whispers. */
class PartyPlayerNotFoundException(masterName: String) : PartyException(
  code = "PARTY_PLAYER_NOT_FOUND",
  message = "No master $masterName is being played",
  opError = OpError.PARTY_PLAYER_NOT_FOUND,
  args = listOf(masterName),
)

class PartyTargetInPartyException(masterName: String) : PartyException(
  code = "PARTY_TARGET_IN_PARTY",
  message = "$masterName is already in a party",
  opError = OpError.PARTY_TARGET_IN_PARTY,
  args = listOf(masterName),
)
