package net.bestia.zone.message

import net.bestia.zone.ecs.core.World

/** Runs on the tick thread with the world to itself. For gameplay messages that need no database. */
interface TickMessageHandler<T : CMSG> : IncomingMessageHandler<T> {
  /** Returns whether the message was handled. */
  fun handle(world: World, msg: T): Boolean
}
