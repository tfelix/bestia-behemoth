package net.bestia.zoneserver.actor.entity

import akka.actor.ActorRef

data class RequestComponent(
    val replyTo: ActorRef
)