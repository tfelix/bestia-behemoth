package net.bestia.zoneserver.actor.bootstrap

/**
 * All classes implementing this interface and picked up by Spring will
 * get executed automatically during booting.
 */
interface NodeBootStep {
  val bootStepName: String

  fun execute()
}