package net.bestia.zoneserver.actor.entity.component

import akka.actor.ActorRef
import net.bestia.messages.Envelope

sealed class TestComponentMessage

data class InstallComponentMessage<out T : net.bestia.entity.component.Component>(
        val component: T
) : TestComponentMessage()

data class RequestComponentMessage(
        val requester: ActorRef
)

data class ResponseComponentMessage<out T : net.bestia.entity.component.Component>(
        val component: T
)

data class ComponentBroadcastEnvelope(
        override val content: Any
) : Envelope