package net.bestia.bus

import net.bestia.bus.contract.LastWorld

/**
 * Whether a new world is being born. The zone says so from the world's end until it is ready again, so the login
 * server can tell players why they cannot get in.
 */
interface WorldRebirth {

  fun begin(lastWorld: LastWorld)

  fun end()

  fun isUnderway(): Boolean

  /**
   * How the last world ended. Kept here because the zone that knows it is down while the next world is born. Stays
   * until the next world ends, so the app can still name the world before.
   */
  fun lastWorld(): LastWorld?
}
