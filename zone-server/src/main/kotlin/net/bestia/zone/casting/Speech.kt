package net.bestia.zone.casting

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Says a line out loud for everyone near [speakerId]. A port: chat sits above casting and implements it. */
interface Speech {
  fun say(world: World, speakerId: EntityId, text: String)
}
