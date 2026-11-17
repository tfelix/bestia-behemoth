package net.bestia.zone.script.ecs

import net.bestia.zone.ecs.core.Component

/**
 * Marks an entity as driven by a server-side script identified by [scriptId]. A plain data holder:
 * [net.bestia.zone.script.EntityScriptRegistry] resolves the id when a player clicks the entity. Deliberately
 * implements only [Component], not [net.bestia.zone.sync.Dirtyable]: this never needs to reach the client.
 */
data class ScriptComponent(
  val scriptId: String
) : Component {

  companion object {
    /** [net.bestia.zone.persistence.EntityPersister.kind] routing key for script entities. */
    const val KIND = "script"
  }
}
