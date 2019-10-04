package net.bestia.zoneserver.actor.entity

import akka.actor.ActorRef

data class EntityRequest(
    val replyTo: ActorRef,
    val context: Any? = null
)