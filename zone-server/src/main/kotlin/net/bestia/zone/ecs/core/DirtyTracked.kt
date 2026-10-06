package net.bestia.zone.ecs.core

/**
 * A component that knows whether it changed. The world only tracks the flag; what a change means to a client
 * is `Dirtyable`'s business, outside the kernel.
 */
interface DirtyTracked {
  /** Declared first in an implementation's body: setters running in its `init` already use it. */
  val dirtyFlag: DirtyFlag
}
