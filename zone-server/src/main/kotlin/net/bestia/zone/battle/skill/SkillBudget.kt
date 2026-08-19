package net.bestia.zone.battle.skill

/**
 * One cast's allowance of world work. Created per cast by [SkillContextFactory] and spent by
 * [SkillWorld]; never shared between casts.
 *
 * The op count is the behavioural bound - it is deterministic, so a test can assert exactly what a
 * script is allowed to do. The clock is a runaway guard for a script that loops without spending ops
 * fast enough to be caught by the count.
 *
 * Not thread-safe, and does not need to be: a cast is resolved by one worker from start to finish.
 */
class SkillBudget(
  private val maxOps: Int,
  private val maxQueryResults: Int,
  private val maxMillis: Long,
  private val clock: () -> Long = System::nanoTime,
) {

  private val deadlineNanos = clock() + maxMillis * NANOS_PER_MILLI

  var spentOps: Int = 0
    private set

  val remainingOps: Int get() = (maxOps - spentOps).coerceAtLeast(0)

  /** Spends [ops] world operations, or refuses the whole cast. */
  fun charge(ops: Int = 1) {
    require(ops > 0) { "A charge has to cost something, was $ops" }

    if (spentOps + ops > maxOps) {
      throw SkillBudgetExceededException(
        "spent $spentOps of $maxOps world ops and asked for $ops more"
      )
    }

    if (clock() > deadlineNanos) {
      throw SkillBudgetExceededException("ran past its ${maxMillis}ms deadline")
    }

    spentOps += ops
  }

  /**
   * Refuses a query whose answer is larger than one cast may look at, rather than handing back a
   * truncated set - a script that silently saw half the entities in a cube would be a bug nobody could
   * see.
   */
  fun chargeQueryResults(count: Int) {
    if (count > maxQueryResults) {
      throw SkillBudgetExceededException("a query answered with $count entities, over the $maxQueryResults cap")
    }

    if (count > 0) {
      charge(count)
    }
  }

  companion object {
    private const val NANOS_PER_MILLI = 1_000_000L
  }
}
