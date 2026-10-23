package net.bestia.zone.ecs.core

import java.util.concurrent.atomic.LongAdder

/**
 * What [WorldOwnership] has spent since start, for the metrics export to read from another thread.
 *
 * Posted work is counted in two lanes because they cost different things: work at the start of a tick is part of
 * that tick's duration, work between two ticks is not and eats the sleep the loop would otherwise have.
 * Everything the tick thread writes has that one writer, so a volatile is enough; borrowers write from any
 * thread and use adders.
 */
class OwnershipStats {

  @Volatile
  var tickStartTasks = 0L
    private set

  @Volatile
  var tickStartNanos = 0L
    private set

  @Volatile
  var betweenStepsTasks = 0L
    private set

  @Volatile
  var betweenStepsNanos = 0L
    private set

  /** Posted tasks that threw. */
  @Volatile
  var postedFailures = 0L
    private set

  /** Leases the tick thread lent out, and how long it stood still while each was out. */
  @Volatile
  var leasesLent = 0L
    private set

  @Volatile
  var leaseLentNanos = 0L
    private set

  private val borrowed = LongAdder()
  private val borrowWait = LongAdder()
  private val timeouts = LongAdder()

  /** Leases granted to a borrowing thread. */
  val leasesBorrowed: Long
    get() {
      return borrowed.sum()
    }

  /** How long borrowers waited for the tick thread to grant them the world, summed. */
  val leaseWaitNanos: Long
    get() {
      return borrowWait.sum()
    }

  /** Borrowers that gave up after five seconds. */
  val leaseTimeouts: Long
    get() {
      return timeouts.sum()
    }

  internal fun posted(betweenSteps: Boolean, nanos: Long) {
    if (betweenSteps) {
      betweenStepsTasks++
      betweenStepsNanos += nanos
    } else {
      tickStartTasks++
      tickStartNanos += nanos
    }
  }

  internal fun postedFailed() {
    postedFailures++
  }

  internal fun lent(nanos: Long) {
    leasesLent++
    leaseLentNanos += nanos
  }

  internal fun borrowed(waitNanos: Long) {
    borrowed.increment()
    borrowWait.add(waitNanos)
  }

  internal fun timedOut() {
    timeouts.increment()
  }
}
