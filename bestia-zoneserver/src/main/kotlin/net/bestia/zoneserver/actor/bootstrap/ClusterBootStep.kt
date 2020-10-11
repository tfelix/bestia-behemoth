package net.bestia.zoneserver.actor.bootstrap

/**
 * All classes implementing this interface and picked up by Spring will
 * get executed automatically during booting in the cluster context.
 * This means its only executed globally once per cluster.
 */
interface ClusterBootStep {
  val bootStepName: String

  fun execute()
}