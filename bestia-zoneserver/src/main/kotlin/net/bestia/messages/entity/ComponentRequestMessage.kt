package net.bestia.messages.entity

import akka.actor.ActorRef
import net.bestia.zoneserver.entity.component.Component

data class ComponentRequestMessage<T : Component>(
    val componentClass: Class<T>,
    val requester: ActorRef
)