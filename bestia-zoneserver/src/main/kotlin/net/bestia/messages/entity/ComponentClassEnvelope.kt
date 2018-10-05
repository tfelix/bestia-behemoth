package net.bestia.messages.entity

import net.bestia.zoneserver.entity.component.Component
import net.bestia.messages.Envelope

data class ComponentClassEnvelope<T : Component>(
    val componentClass: Class<T>,
    override val content: Any
) : Envelope