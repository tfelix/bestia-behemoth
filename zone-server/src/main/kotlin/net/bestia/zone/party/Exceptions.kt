package net.bestia.zone.party

import net.bestia.zone.BestiaException

abstract class PartyException(
  code: String,
  message: String,
  cause: Throwable? = null
) : BestiaException(code, message, cause)

class NotPartyOwnerException : PartyException(
  code = "NOT_PARTY_OWNER",
  message = "You are not the owner of the party"
)

class AlreadyInPartyException() : PartyException(
  code = "ALREADY_IN_PARTY",
  message = "Player is already in a party"
)

class NotPartyException : PartyException(
  code = "NO_PARTY",
  message = "You are not member of a party"
)

class PartyFullException : PartyException(
  code = "PARTY_FULL",
  message = "The party is already full"
)

class PartyInviteForbiddenException(
  playerId: Long,
  invitationId: Long
) : PartyException(
  code = "FORBIDDEN_PARTY_INVITE",
  message = "Player $playerId is not allowed to access party invitation $invitationId"
)

class TooManyPartyInvitationsInFlightException : PartyException(
  code = "TOO_MANY_PARTY_INVITES",
  message = "The server limit of in-flight invites was reached"
)

class PartyNotFoundException(partyId: Long) : PartyException(
  code = "PARTY_NOT_FOUND",
  message = "Party $partyId was not found"
)

class PartyInvitationExpired() : PartyException(
  code = "PARTY_INVITATION_EXPIRED",
  message = "The invitation did expire"
)