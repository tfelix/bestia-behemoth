package net.bestia.zoneserver.actor.bootstrap

import akka.actor.AbstractActor
import mu.KotlinLogging
import net.bestia.zoneserver.actor.Actor
import java.time.Duration
import java.time.Instant
import kotlin.system.exitProcess

private val LOG = KotlinLogging.logger { }

/**
 * This actor controls the booting of the server node. Usually there is some work to be done before
 * a Zoneserver is fully operational. The NodeBoostrapActor will call all annotated
 *
 * @author Thomas Felix
 */
@Actor
class NodeBootstrapActor(
    private val bootSteps: List<NodeBootStep>
) : AbstractActor() {
  // This actor does not receive any messages.
  override fun createReceive(): Receive {
    return receiveBuilder().build()
  }

  override fun preStart() {
    val start = Instant.now()
    LOG.info { "Booting the node with ${bootSteps.size} steps: ${bootSteps.map { it.bootStepName }}" }

    bootSteps.forEach { step ->
      try {
        LOG.info { "Boot Step: ${step.bootStepName}" }
        step.execute()
      } catch (e: Exception) {
        LOG.error(e) { "There was an error during booting the node. Terminating." }
        exitProcess(1)
      }
    }

    val bootDuration = Duration.between(start, Instant.now())
    LOG.info { "Booting was completed in ${bootDuration.seconds}s. Node is operational." }
  }
}
