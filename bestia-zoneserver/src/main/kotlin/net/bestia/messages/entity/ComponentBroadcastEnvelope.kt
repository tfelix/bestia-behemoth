package net.bestia.messages.entity

import net.bestia.messages.Envelope

data class ComponentBroadcastEnvelope(
        override val content: Any
) : Envelope