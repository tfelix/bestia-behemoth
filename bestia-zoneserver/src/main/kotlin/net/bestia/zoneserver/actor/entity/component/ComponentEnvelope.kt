package net.bestia.zoneserver.actor.entity.component

import net.bestia.messages.Envelope
import net.bestia.zoneserver.entity.component.Component

internal data class ComponentEnvelope<out T : Component>(
    val componentType: Class<out T>,
    override val content: Any
) : Envelope