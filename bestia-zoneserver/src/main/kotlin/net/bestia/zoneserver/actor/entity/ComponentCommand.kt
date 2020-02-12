package net.bestia.zoneserver.actor.entity

import net.bestia.zoneserver.entity.component.Component
import net.bestia.zoneserver.script.api.EntityCommand

// TODO This is a mess. See RequestComponent. Please unify this into one good set of Commands
//   which can be used within scripts as well. See also AddComponentCommand, better work with the EntityCommand interface.
sealed class ComponentCommand

// Possibly implement EntityMessage and drop the EntityCommand interface for this
data class AddComponentCommand<out T : Component>(
    val component: T
) : ComponentCommand(), EntityCommand {
  override fun toEntityEnvelope(): EntityEnvelope {
    return EntityEnvelope(component.entityId, this)
  }
}

data class DeleteComponentCommand<T : Component>(
    val componentClass: Class<T>
) : ComponentCommand()

data class UpdateComponentCommand<T : Component>(
    val component: T
) : ComponentCommand()