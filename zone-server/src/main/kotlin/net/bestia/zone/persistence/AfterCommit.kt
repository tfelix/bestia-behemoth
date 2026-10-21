package net.bestia.zone.persistence

import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Runs [block] once the current transaction has committed, or at once outside a transaction.
 *
 * For whatever mirrors a database change into the live world or onto the wire: a change that is rolled back must
 * not leave the world or a client ahead of the rows.
 */
fun afterCommit(block: () -> Unit) {
  if (!TransactionSynchronizationManager.isSynchronizationActive()) {
    block()
    return
  }

  TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
    override fun afterCommit() {
      block()
    }
  })
}
