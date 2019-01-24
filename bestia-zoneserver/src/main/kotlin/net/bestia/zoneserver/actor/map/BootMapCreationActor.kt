package net.bestia.zoneserver.actor.map

import akka.actor.AbstractActor
import akka.actor.Terminated
import mu.KotlinLogging
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.ActorComponentNoComponent
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.map.MapService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Checks is a map is properly created in the database.
 * If not it will start to generate one.
 */
@ActorComponentNoComponent
class BootMapCreationActor(
        private val mapService: MapService
) : AbstractActor() {
  override fun createReceive(): Receive {
    return receiveBuilder()
            .match(Terminated::class.java) { context.stop(self) }
        .build()
  }

  override fun preStart() {
    LOG.info { "Checking if proper map exists" }
    if(mapService.isMapInitialized) {
      LOG.info { "Map exists and nothing needs to be done" }
      context.stop(self)
      return
    }

    createMap()
  }

  private fun createMap() {
    LOG.info { "No map data found! Creating a new default map." }
    val mapCreationMaster = SpringExtension.actorOf(context, MapGeneratorMasterActor::class.java)
    context.watch(mapCreationMaster)
  }
}