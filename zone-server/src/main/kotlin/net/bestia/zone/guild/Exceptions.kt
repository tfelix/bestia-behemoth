package net.bestia.zone.guild

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.BestiaException
import net.bestia.zone.message.OperationErrorSMSG

/**
 * A guild request refused, told to the player as [opError] with its [args]. The guild window only offers what a
 * rank allows, so breaking a rule of the hierarchy is [GuildRequestRefused], not a code of its own.
 */
abstract class GuildException(
  code: String,
  message: String,
  val opError: OpError,
  val args: List<String> = emptyList(),
) : BestiaException(code, message) {

  fun toOperationError(): OperationErrorSMSG = OperationErrorSMSG(opError, args)
}

/** Something no honest client sends. The reason is for the server log only. */
class GuildRequestRefused(reason: String) : GuildException(
  code = "GUILD_REQUEST_REFUSED",
  message = reason,
  opError = OpError.REQUEST_REFUSED,
)

class GuildNameTakenException(name: String) : GuildException(
  code = "GUILD_NAME_TAKEN",
  message = "Guild name $name is taken",
  opError = OpError.GUILD_NAME_TAKEN,
)

class GuildInvalidNameException : GuildException(
  code = "GUILD_INVALID_NAME",
  message = "Guild or rank name is blank, too long or not printable ASCII",
  opError = OpError.GUILD_INVALID_NAME,
)

class NotInGuildException : GuildException(
  code = "GUILD_NOT_A_MEMBER",
  message = "The master is in no guild",
  opError = OpError.GUILD_NOT_A_MEMBER,
)

class GuildTargetUnavailableException(masterName: String) : GuildException(
  code = "GUILD_TARGET_UNAVAILABLE",
  message = "No master $masterName is being played",
  opError = OpError.GUILD_TARGET_UNAVAILABLE,
  args = listOf(masterName),
)

class GuildTargetInGuildException(masterName: String) : GuildException(
  code = "GUILD_TARGET_IN_GUILD",
  message = "$masterName is already in a guild",
  opError = OpError.GUILD_TARGET_IN_GUILD,
  args = listOf(masterName),
)

class GuildTargetExpelledException(masterName: String) : GuildException(
  code = "GUILD_TARGET_EXPELLED",
  message = "$masterName was expelled from the guild",
  opError = OpError.GUILD_TARGET_EXPELLED,
  args = listOf(masterName),
)

class GuildFullException : GuildException(
  code = "GUILD_FULL",
  message = "Every seat of the guild is taken",
  opError = OpError.GUILD_FULL,
)

class GuildInvitationExpiredException : GuildException(
  code = "GUILD_INVITE_EXPIRED",
  message = "The guild invitation expired",
  opError = OpError.GUILD_INVITE_EXPIRED,
)
