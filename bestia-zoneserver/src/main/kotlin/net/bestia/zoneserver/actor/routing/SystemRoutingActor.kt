package net.bestia.zoneserver.actor.routing

import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.config.HousekeepingActor
import net.bestia.zoneserver.actor.config.RuntimeConfigActor

/**
 * Contains routing message logic for system messages which might get send from Services.
 */
@Actor
class SystemRoutingActor : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {}

  override fun preStart() {
    SpringExtension.actorOf(context, RuntimeConfigActor::class.java)
    SpringExtension.actorOf(context, HousekeepingActor::class.java)
  }

  companion object {
    const val NAME = "systemMessages"
  }
}
