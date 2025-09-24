package net.bestia.zone

import java.lang.Exception

open class BestiaException(
  code: String,
  message: String,
  cause: Throwable? = null
) : Exception(message, cause)