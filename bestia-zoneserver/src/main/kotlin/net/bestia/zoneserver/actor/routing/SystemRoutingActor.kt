package net.bestia.zoneserver.actor.routing

import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.config.WatchdogActor

/**
 * Contains routing message logic for system messages which might get send from Services.
 */
@Actor
class SystemRoutingActor : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {}

  override fun preStart() {
    SpringExtension.actorOf(context, WatchdogActor::class.java)
  }

  companion object {
    const val NAME = "systemMessages"
  }
}
