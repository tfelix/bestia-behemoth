package net.bestia.zone.ecs.core

/**
 * A countdown the client draws as a filling bar - a cast, a craft, anything that runs for a fixed
 * time and then either resolves or is interrupted.
 *
 * ### Why the countdown and the sync run at different rates
 *
 * [countdown] is driven every tick so a bar resolves on the tick it actually elapses, but it only
 * dirties every [SYNC_INTERVAL_SECONDS]. Dirtying per tick meant 20 broadcasts a second per casting
 * entity to everyone in range, for a number the client already counts down itself - the sync is a
 * drift correction, not the source of the animation.
 *
 * Throttling the *system* instead (the way [net.bestia.zone.ecs.logout.LogoutSystem] does) would tie
 * resolution granularity to the send rate, and a 1.5s cast resolving at 2.0s is not acceptable in
 * combat. Hence the accumulator here, for the same reason
 * [net.bestia.zone.ecs.battle.effects.AreaEffect] carries its own.
 */
abstract class CountdownBar(
  remainingSeconds: Float
) : Dirtyable {

  var remainingSeconds: Float = remainingSeconds
    private set

  private var sinceLastSync = 0f

  // Starts dirty so the opening message goes out on the tick the component is added: that message is
  // the client's only cue that a bar should appear at all.
  private var dirty = true

  fun countdown(deltaTime: Float) {
    remainingSeconds -= deltaTime
    sinceLastSync += deltaTime

    // Deliberately not once it has elapsed. The removal that follows on this same tick is what ends
    // the bar, and a heartbeat carrying a remaining of 0 makes the client hide it a beat early.
    if (sinceLastSync >= SYNC_INTERVAL_SECONDS && remainingSeconds > 0f) {
      // Modulo rather than a plain reset, for the reason SystemScheduler.isDue documents: a system
      // scheduled at exactly SYNC_INTERVAL_SECONDS (LogoutSystem) is handed deltas that alternate
      // just over and just under it, and discarding the remainder would halve its sync rate. The
      // modulo also drops - rather than queues - the periods a lag spike swallowed.
      sinceLastSync %= SYNC_INTERVAL_SECONDS
      dirty = true
    }
  }

  fun hasElapsed(): Boolean = remainingSeconds <= 0f

  override fun isDirty(): Boolean = dirty

  override fun markDirty() {
    dirty = true
    sinceLastSync = 0f
  }

  override fun clearDirty() {
    dirty = false
  }

  companion object {
    /**
     * Doubles as the worst case before a bystander who walks into range mid-cast sees the bar:
     * [net.bestia.zone.entity.GetAllEntitiesHandler] does not carry countdown state, so the next
     * heartbeat is the only thing that tells them.
     */
    const val SYNC_INTERVAL_SECONDS = 1f
  }
}
