package net.bestia.zone.ecs.core

/** A component whose every step must re-index its entity, whether or not the step is sent to clients. */
interface SpatiallyIndexed {
  val movedFlag: DirtyFlag
}
