package net.bestia.zoneserver.actor.zone

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Terminated
import mu.KotlinLogging
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.map.BootMapCreationActor
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * This is a cluster singelton actor. It centralized the initialization control over
 * the whole Bestia cluster. Upon receiving control messages it performs centralized
 * orchestration like generating a new map or sending commands to server
 * instances.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class BootstrapActor : AbstractActor() {

  private val watchedActorSet = mutableSetOf<ActorRef>()

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(Terminated::class.java, this::onWatchedActorTerminated)
            .build()
  }

  override fun preStart() {
    LOG.info { "Starting bootstrapping the zone." }

    val bootActors = listOf(
            BootMapCreationActor::class.java
    )

    bootActors.forEach {
      LOG.debug { "Starting bootstrap actor: ${it.simpleName}" }
      val bootActor = SpringExtension.actorOf(context, it)
      watchUntilFinished(bootActor)
    }
  }

  private fun onWatchedActorTerminated(msg: Terminated) {
    watchedActorSet.remove(msg.actor)
    if(watchedActorSet.isEmpty()) {
      LOG.info { "All init actors have stopped. Initialization finished." }
      context.stop(self)
    }
  }

  private fun watchUntilFinished(ref: ActorRef) {
    context.watch(ref)
    watchedActorSet.add(ref)
  }
}