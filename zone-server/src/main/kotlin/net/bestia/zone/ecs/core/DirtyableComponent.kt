package net.bestia.zone.ecs.core

abstract class DirtyableComponent() : Dirtyable, Component {
  override val dirtyFlag = DirtyFlag()
}
