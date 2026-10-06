package net.bestia.zone.sync

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.DirtyFlag

abstract class DirtyableComponent() : Dirtyable, Component {
  override val dirtyFlag = DirtyFlag()
}
