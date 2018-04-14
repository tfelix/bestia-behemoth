package net.bestia.messages.entity

import net.bestia.messages.Envelope

/**
 * Messages inside this component envelope are delivered to the component actor of the entity actor.
 */
data class ComponentEnvelope(
        val componentId: Long,
        override val content: Any
) : Envelope