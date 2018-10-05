package net.bestia.messages.entity

import akka.actor.ActorRef

data class RequestComponentMessage(
    val requester: ActorRef
)