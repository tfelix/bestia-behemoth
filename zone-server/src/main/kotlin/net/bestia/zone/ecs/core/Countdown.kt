package net.bestia.zone.ecs.core

/**
 * A countdown the client draws as a filling bar - a cast, a craft, anything that runs for a fixed
 * time and then either resolves or is interrupted.
 *
 * ### Why the countdown and the sync run at different rates
 *
 * [countdown] is driven every tick so a bar resolves on the tick it actually elapses, but it only
 * dirties every [syncIntervalSeconds]. Dirtying per tick meant 20 broadcasts a second per casting
 * entity to everyone in range, for a number the client already counts down itself - the sync is a
 * drift correction, not the source of the animation.
 *
 * Throttling the *system* instead (the way [net.bestia.zone.ecs.logout.LogoutSystem] does) would tie
 * resolution granularity to the send rate, and a 1.5s cast resolving at 2.0s is not acceptable in
 * combat. Hence the accumulator here, for the same reason
 * [net.bestia.zone.ecs.battle.effects.AreaEffect] carries its own.
 */
abstract class Countdown(
  remainingSeconds: Float
) : DirtyableComponent() {

  var remainingSeconds: Float = remainingSeconds
    private set

  private var sinceLastSync = 0f

  /**
   * Seconds between two drift corrections. A bar the player is watching in combat wants the default;
   * one that only reports "still busy" can beat slower, which is why a subclass may raise it.
   */
  protected open val syncIntervalSeconds: Float get() = SYNC_INTERVAL_SECONDS

  open fun countdown(deltaTime: Float) {
    remainingSeconds -= deltaTime
    sinceLastSync += deltaTime

    // Deliberately not once it has elapsed. The removal that follows on this same tick is what ends
    // the bar, and a heartbeat carrying a remaining of 0 makes the client hide it a beat early.
    if (sinceLastSync >= syncIntervalSeconds && remainingSeconds > 0f) {
      // Carried rather than reset, for the reason SystemScheduler.isDue documents: a system scheduled
      // at exactly this interval (LogoutSystem) is handed deltas that alternate just over and just
      // under it, and discarding the remainder would halve its sync rate. The modulo also drops -
      // rather than queues - the periods a lag spike swallowed. markDirty clears the accumulator, so
      // the remainder is put back after it.
      val carried = sinceLastSync % syncIntervalSeconds
      markDirty()
      sinceLastSync = carried
    }
  }

  /**
   * Also restarts the interval: a forced resync is a sync, so the next heartbeat is a full interval
   * away rather than however much of one happened to be left.
   */
  override fun markDirty() {
    super.markDirty()
    sinceLastSync = 0f
  }

  fun hasElapsed(): Boolean = remainingSeconds <= 0f

  companion object {
    /**
     * Doubles as the worst case before a bystander who walks into range mid-cast sees the bar:
     * [net.bestia.zone.entity.GetAllEntitiesHandler] does not carry countdown state, so the next
     * heartbeat is the only thing that tells them.
     */
    const val SYNC_INTERVAL_SECONDS = 1f
  }
}
