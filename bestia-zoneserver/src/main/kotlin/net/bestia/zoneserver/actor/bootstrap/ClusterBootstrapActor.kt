package net.bestia.zoneserver.actor.bootstrap

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Terminated
import mu.KotlinLogging
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import net.bestia.zoneserver.actor.BootStepFailedError
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.map.BootMapCreationActor
import org.springframework.beans.factory.annotation.Qualifier

private val LOG = KotlinLogging.logger { }

/**
 * This is a cluster singelton actor. It centralized the initialization control over
 * the whole Bestia cluster. Upon receiving control messages it performs centralized
 * orchestration like generating a new map or sending commands to server
 * instances.
 *
 * @author Thomas Felix
 */
@Actor
class ClusterBootstrapActor(
    private val bootSteps: List<ClusterBootStep>,
    @Qualifier(BQualifier.SYSTEM_ROUTER)
    private val router: ActorRef
) : AbstractActor() {

  private val watchedActorSet = mutableSetOf<ActorRef>()

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(Terminated::class.java, this::onWatchedActorTerminated)
        .build()
  }

  override fun preStart() {
    LOG.info { "Starting bootstrapping the zone." }

    executeBootSteps()

    val bootActors = listOf(
        BootMapCreationActor::class.java
    )

    bootActors.forEach {
      LOG.debug { "Starting bootstrap actor: ${it.simpleName}" }
      val bootActor = SpringExtension.actorOf(context, it)
      watchUntilFinished(bootActor)
    }
  }

  private fun watchUntilFinished(ref: ActorRef) {
    context.watch(ref)
    watchedActorSet.add(ref)
  }

  private fun executeBootSteps() {
    LOG.info { "Executing ${bootSteps.size} node boot steps: ${bootSteps.map { it.bootStepName }}" }

    bootSteps.forEach { step ->
      try {
        LOG.info { "Cluster Boot Step: ${step.bootStepName}" }
        step.execute()
      } catch (e: Exception) {
        LOG.error(e) { "There was an error during a cluster boot step. This is fatal" }
        router.tell(BootStepFailedError(e), self)
        context.stop(self)
        return
      }
    }
  }

  private fun onWatchedActorTerminated(msg: Terminated) {
    watchedActorSet.remove(msg.actor)
    if (watchedActorSet.isEmpty()) {
      LOG.info { "All init actors have stopped. Initialization finished." }
      context.stop(self)
    }
  }
}
