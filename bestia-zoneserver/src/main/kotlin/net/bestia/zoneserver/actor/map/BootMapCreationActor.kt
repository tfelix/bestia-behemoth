package net.bestia.zoneserver.actor.map

import akka.actor.AbstractActor
import akka.actor.Terminated
import mu.KotlinLogging
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.SpringExtension

private val LOG = KotlinLogging.logger { }

/**
 * Checks is a map is properly created in the database.
 * If not it will start to generate one.
 */
@Actor
class BootMapCreationActor(
) : AbstractActor() {
  override fun createReceive(): Receive {
    return receiveBuilder()
            .match(Terminated::class.java) { context.stop(self) }
        .build()
  }

  override fun preStart() {
    LOG.info { "Checking if proper map exists" }

    createMap()
  }

  private fun createMap() {
    LOG.info { "No map data found! Creating a new default map." }
    val mapCreationMaster = SpringExtension.actorOf(context, MapGeneratorMasterActor::class.java)
    context.watch(mapCreationMaster)
  }
}