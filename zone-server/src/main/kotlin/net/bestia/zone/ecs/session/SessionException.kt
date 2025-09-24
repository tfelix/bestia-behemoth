package net.bestia.zone.ecs.session

import net.bestia.zone.BestiaException

open class SessionException(
  code: String,
  message: String,
  cause: Throwable? = null
) : BestiaException(code, message, cause)