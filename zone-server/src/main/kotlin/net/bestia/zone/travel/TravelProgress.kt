package net.bestia.zone.travel

import net.bestia.zone.geometry.Vec3L

/**
 * How well a traveller is getting on towards what it is walking at: the closest it came, and for how long it
 * has not come closer. A new aim - the next node or waypoint, or a new plan - starts the distance afresh but
 * not the time, so walking in circles between plans still runs out.
 */
class TravelProgress {

  var idleSeconds = 0f
    private set

  /** Whether the stall at the current aim was already blamed on the way ahead. */
  var stallBlamed = false
    private set

  private var aim: Vec3L? = null
  private var closest = Long.MAX_VALUE

  /** [distance] in any measure that shrinks as the traveller gets nearer. */
  fun record(aim: Vec3L, distance: Long, deltaTime: Float) {
    if (aim != this.aim) {
      this.aim = aim
      closest = distance
      stallBlamed = false
      return
    }

    if (distance < closest) {
      closest = distance
      idleSeconds = 0f
      stallBlamed = false
    } else {
      idleSeconds += deltaTime
    }
  }

  /** Reaching a node or waypoint is headway whatever the distances said. */
  fun reachedAim() {
    idleSeconds = 0f
    stallBlamed = false
  }

  fun blameStall() {
    stallBlamed = true
  }
}
