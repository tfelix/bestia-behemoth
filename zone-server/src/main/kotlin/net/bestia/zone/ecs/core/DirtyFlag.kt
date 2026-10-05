package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId

/**
 * A component's "changed since it was last sent" flag. Going from clean to dirty reports the entity to the
 * [Sink] of the store the component sits in, once, so the sync visits only what changed instead of
 * scanning every component every tick.
 */
class DirtyFlag {

  /** A fresh component is dirty: whoever can see its entity has never been told about it. */
  var isSet: Boolean = true
    private set

  private var entity: EntityId = 0L
  private var sink: Sink? = null

  fun set() {
    if (isSet) return

    isSet = true
    sink?.dirtied(entity)
  }

  fun clear() {
    isSet = false
  }

  /** Called by the store when the component joins [entity]; a flag that is already set is reported now. */
  fun attach(entity: EntityId, sink: Sink) {
    this.entity = entity
    this.sink = sink
    if (isSet) sink.dirtied(entity)
  }

  /** Only for the entity it is attached to: an instance moved to another entity keeps reporting there. */
  fun detachFrom(entity: EntityId) {
    if (this.entity == entity) sink = null
  }

  fun interface Sink {
    fun dirtied(entity: EntityId)
  }
}
