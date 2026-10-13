package net.bestia.zone.ground

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Chunk columns whose clients are owed a message, and the Bestia second each one falls due.
 *
 * A column is owed once however often it is marked: a second mark adds its accounts and can only move the due
 * time earlier. That is what coalesces a run of changes into one send, and what stops a snapshot from being held
 * back by a resync already waiting. Concurrent, because marking is safe from any wave.
 */
class OwedColumns {

  private class Owed(val accounts: MutableSet<Long>, @Volatile var dueSecond: Long)

  private val byColumn = ConcurrentHashMap<Long, Owed>()

  /** The lowest due second held, so a tick with nothing due costs one read rather than a walk. */
  private val earliestDue = AtomicLong(Long.MAX_VALUE)

  val size: Int
    get() {
      return byColumn.size
    }

  fun owe(columnKey: Long, accounts: Collection<Long>, dueSecond: Long) {
    byColumn.compute(columnKey) { _, owed ->
      if (owed == null) {
        Owed(ConcurrentHashMap.newKeySet<Long>().apply { addAll(accounts) }, dueSecond)
      } else {
        owed.accounts.addAll(accounts)
        owed.dueSecond = minOf(owed.dueSecond, dueSecond)
        owed
      }
    }

    earliestDue.accumulateAndGet(dueSecond) { a, b -> minOf(a, b) }
  }

  fun forget(columnKey: Long) {
    byColumn.remove(columnKey)
  }

  fun retainOnly(columnKey: Long, accounts: Collection<Long>) {
    byColumn[columnKey]?.accounts?.retainAll(accounts)
  }

  fun isDue(nowSecond: Long): Boolean {
    return nowSecond >= earliestDue.get()
  }

  /**
   * Hands every column that has fallen due to [send], and forgets it.
   *
   * @return how many columns went out. A column whose accounts have all left is dropped without being sent.
   */
  fun drainDue(nowSecond: Long, send: (columnKey: Long, accounts: Set<Long>) -> Unit): Int {
    if (!isDue(nowSecond)) return 0

    // Rebuilt from what stays, so a column removed elsewhere stops holding the gate open. A mark racing this
    // lowers it again.
    earliestDue.set(Long.MAX_VALUE)

    var sent = 0

    for ((columnKey, owed) in byColumn) {
      if (owed.dueSecond > nowSecond) {
        earliestDue.accumulateAndGet(owed.dueSecond) { a, b -> minOf(a, b) }
        continue
      }

      if (!byColumn.remove(columnKey, owed) || owed.accounts.isEmpty()) continue

      send(columnKey, owed.accounts)
      sent++
    }

    return sent
  }
}
