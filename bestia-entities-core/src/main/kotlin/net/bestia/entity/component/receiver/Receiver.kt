package net.bestia.entity.component.receiver

sealed class Receiver
data class ActorReceiver(val entityId: Long): Receiver()
data class ClientReceiver(val accountId: Long): Receiver()