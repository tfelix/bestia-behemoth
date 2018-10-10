package net.bestia.messages.entity

import akka.actor.ActorRef

internal data class EntityRequest(
    val requester: ActorRef,
    val context: Any? = null
)