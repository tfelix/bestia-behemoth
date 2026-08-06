package net.bestia.zone.ecs.script

import net.bestia.zone.ecs.core.Component

/**
 * Marks an entity as driven by a server-side script identified by [scriptId]. A plain data holder -
 * what the script actually does is resolved elsewhere, by whatever registry ends up looking this id
 * up. Deliberately implements only [Component], not [net.bestia.zone.ecs.core.Dirtyable]: this never needs
 * to reach the client.
 */
data class ScriptComponent(
  val scriptId: String
) : Component {

  companion object {
    /** [net.bestia.zone.ecs.persistence.EntityPersister.kind] routing key for script entities. */
    const val KIND = "script"
  }
}
