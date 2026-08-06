package net.bestia.zone.ecs.core

abstract class DirtyableComponent() : Dirtyable, Component {
  private var dirty = true

  override fun isDirty(): Boolean = dirty

  override fun markDirty() {
    dirty = true
  }

  override fun clearDirty() {
    dirty = false
  }
}