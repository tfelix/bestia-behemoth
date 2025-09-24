package net.bestia.zone.account.master

import net.bestia.zone.BestiaException

/**
 * Base exception for master-related operations
 */
abstract class MasterException(
  code: String,
  message: String,
  cause: Throwable? = null
) : BestiaException(code, message, cause)

abstract class MasterCreateException(
  val errorCode: MasterErrorSMSG.MasterErrorCode,
  message: String
) : MasterException(code = errorCode.name, message)

/**
 * Thrown when a master name is invalid (blank or too long)
 */
class InvalidMasterNameException
  : MasterCreateException(MasterErrorSMSG.MasterErrorCode.INVALID_NAME, "Master name is invalid")

/**
 * Thrown when a master name is already taken
 */
class MasterNameAlreadyTakenException
  : MasterCreateException(MasterErrorSMSG.MasterErrorCode.NAME_ALREADY_TAKEN, "Master name is already taken")

/**
 * Thrown when maximum number of masters is reached
 */
class MaxMastersReachedException
  : MasterCreateException(MasterErrorSMSG.MasterErrorCode.MAX_MASTERS_REACHED, "Maximum number of masters reached")

/**
 * General master exception for other errors
 */
class GeneralMasterException(message: String = "An error occurred while creating master") :
  MasterCreateException(MasterErrorSMSG.MasterErrorCode.GENERAL_ERROR, message)

class MasterNotFoundException(cause: Throwable? = null) : MasterException(
  code = "NO_MASTER",
  message = "The specified master was not found",
  cause = cause
)