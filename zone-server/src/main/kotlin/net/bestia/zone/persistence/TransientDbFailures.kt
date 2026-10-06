package net.bestia.zone.persistence

import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.dao.QueryTimeoutException
import org.springframework.transaction.CannotCreateTransactionException

/**
 * Runs a one-transaction DB write again when it lost a lock race or could not start. Each of these failures
 * rolls the transaction back, so a second try cannot apply the write twice. Inline on the calling DB worker,
 * so the owner's later writes still land after this one.
 */
fun <T> retryingTransientFailures(attempts: Int = 3, block: () -> T): T {
  var attempt = 1

  while (true) {
    try {
      return block()
    } catch (e: Exception) {
      if (!isTransient(e) || attempt >= attempts) throw e

      Thread.sleep(BACKOFF_MILLIS * attempt)
      attempt++
    }
  }
}

private fun isTransient(e: Exception): Boolean {
  return e is PessimisticLockingFailureException || e is QueryTimeoutException || e is CannotCreateTransactionException
}

private const val BACKOFF_MILLIS = 50L
