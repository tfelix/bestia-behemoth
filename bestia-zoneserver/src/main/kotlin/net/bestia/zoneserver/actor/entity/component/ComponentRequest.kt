package net.bestia.zoneserver.actor.entity.component

import akka.actor.ActorRef

/**
 * Mostly used if the entity actor requests its component from the
 * actors.
 */
data class ComponentRequest(
    val replyTo: ActorRef
)