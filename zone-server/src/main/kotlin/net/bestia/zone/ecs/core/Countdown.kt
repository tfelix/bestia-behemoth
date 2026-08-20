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
abstract class Countdown(
  remainingSeconds: Float
) : DirtyableComponent() {

  var remainingSeconds: Float = remainingSeconds
    private set

  open fun countdown(deltaTime: Float) {
    remainingSeconds -= deltaTime
  }

  fun hasElapsed(): Boolean = remainingSeconds <= 0f
}
