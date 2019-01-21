package net.bestia.zoneserver.actor.entity

import akka.actor.ActorRef
import net.bestia.zoneserver.entity.component.Component

sealed class ComponentMessage

data class AddComponentMessage<out T : Component>(
    val component: T
) : ComponentMessage()

data class RequestComponentMessage(
    val replyTo: ActorRef
) : ComponentMessage()

data class DeleteComponentMessage<T : Component>(
    val componentClass: Class<T>
) : ComponentMessage()

data class UpdateComponentMessage<T : Component>(
    val component: T
) : ComponentMessage()