package net.bestia.bus

/**
 * Whether a new world is being born. The zone says so from the world's end until it is ready again, so the login
 * server can tell players why they cannot get in.
 */
interface WorldRebirth {

  fun begin()

  fun end()

  fun isUnderway(): Boolean
}
