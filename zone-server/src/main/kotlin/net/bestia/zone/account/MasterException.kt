package net.bestia.zone.account

import net.bestia.zone.BestiaException

/**
 * Base exception for master-related operations
 */
abstract class MasterException(
  code: String,
  message: String,
  cause: Throwable? = null
) : BestiaException(code, message, cause)

class MasterNotFoundException(cause: Throwable? = null) : MasterException(
  code = "NO_MASTER",
  message = "The specified master was not found",
  cause = cause
)